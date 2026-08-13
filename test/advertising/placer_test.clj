(ns advertising.placer-test
  "The actuation seam as executable tests.

  One invariant carries the rest: **a placement can never be recorded
  without a statement of whether anything was sent.** Everything else
  here exists to make that statement impossible to fake -- the default
  sends nothing, a live placer that could not send refuses to exist, a
  platform with no adapter says so rather than looking successful, a
  failed send is `:sent? false` rather than an omission, and CHARGING a
  client is a second decision on top of going live, with its own
  operator-set ceiling checked in the last function before the network."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [advertising.placer :as placer]
            [advertising.store :as store]
            [advertising.operation :as op]))

(def ^:private campaign
  {:id "campaign-1" :client-name "Sato Bakery" :target-platform "google-ads"
   :proposed-media-spend 500000 :jurisdiction "JPN"})

(def ^:private placement-record {"record_id" "JPN-PLC-000000"})

(def ^:private creds
  {:developer-token "dev-tok" :access-token "acc-tok"
   :customer-id "5551234567" :login-customer-id "9998887777"})

(defn- ok-http
  "An http-fn that answers both steps the way the real API does, and
  records what it was asked."
  [sent]
  (fn [req]
    (swap! sent conj req)
    (if (str/includes? (:url req) "campaignBudgets")
      {:status 200 :body {"results" [{"resourceName" "customers/5551234567/campaignBudgets/77"}]}}
      {:status 200 :body {"results" [{"resourceName" "customers/5551234567/campaigns/88"}]}})))

;; ----------------------------- credentials -----------------------------

(deftest a-partial-credential-set-is-reported-as-missing-names-not-a-partial-map
  (testing "three of four credentials would fail at the API with an error the operator has to decode; a list of names says what to go and get"
    (let [env {"GOOGLE_ADS_DEVELOPER_TOKEN" "d" "GOOGLE_ADS_ACCESS_TOKEN" "a"}]
      (is (= {:missing ["GOOGLE_ADS_CUSTOMER_ID" "GOOGLE_ADS_LOGIN_CUSTOMER_ID"]}
             (placer/credentials-from-env env)))))
  (testing "blank and whitespace-only values are missing, not present -- an empty env var is the commonest way a credential is 'set'"
    (let [env {"GOOGLE_ADS_DEVELOPER_TOKEN" "d" "GOOGLE_ADS_ACCESS_TOKEN" "  "
               "GOOGLE_ADS_CUSTOMER_ID" "" "GOOGLE_ADS_LOGIN_CUSTOMER_ID" "l"}]
      (is (= {:missing ["GOOGLE_ADS_ACCESS_TOKEN" "GOOGLE_ADS_CUSTOMER_ID"]}
             (placer/credentials-from-env env)))))
  (testing "a complete set comes back keyed for the builder"
    (is (= {:ok creds}
           (placer/credentials-from-env {"GOOGLE_ADS_DEVELOPER_TOKEN" "dev-tok"
                                         "GOOGLE_ADS_ACCESS_TOKEN" "acc-tok"
                                         "GOOGLE_ADS_CUSTOMER_ID" "5551234567"
                                         "GOOGLE_ADS_LOGIN_CUSTOMER_ID" "9998887777"})))))

;; ----------------------------- dry run -----------------------------

(deftest the-default-builds-both-requests-and-sends-nothing
  (let [r (placer/place! (placer/dry-run-placer)
                         {:campaign campaign :placement-record placement-record})]
    (is (= :dry-run (:mode r)))
    (is (false? (:sent? r)))
    (is (false? (:spend? r)))
    (is (= "google-ads" (:platform r)))
    (is (= "JPN-PLC-000000" (:placement-number r)))
    (testing "a real campaign creation is TWO requests -- the budget resource, then the campaign that references it. One request was not a simplification, it was wrong"
      (is (= 2 (count (:requests r))))
      (is (= [:campaign-budget :campaign] (mapv :step (:requests r))))
      (is (re-find #"/v18/customers/.*/campaignBudgets:mutate" (:url (first (:requests r)))))
      (is (re-find #"/v18/customers/.*/campaigns:mutate" (:url (second (:requests r))))))
    (testing "and the default status is PAUSED, which is a real campaign that charges nothing"
      (is (= "PAUSED" (:campaign-status r)))
      (is (= "PAUSED" (get-in (second (:requests r)) [:body "operations" 0 "create" "status"]))))
    (testing "the dry run does not pretend to know the budget id it cannot know"
      (is (re-find #"from step 1"
                   (get-in (second (:requests r)) [:body "operations" 0 "create" "campaignBudget"]))))))

(deftest a-receipt-never-carries-a-credential-value
  (testing "the ledger is an audit trail, not a secret store -- and this is the function that decides what lands in it"
    (let [sent (atom [])
          p (placer/live-placer {:http-fn (ok-http sent) :credentials creds})
          r (placer/place! p {:campaign campaign :placement-record placement-record})
          receipt-text (pr-str r)]
      (is (true? (:sent? r)))
      (doseq [secret ["dev-tok" "acc-tok"]]
        (is (not (str/includes? receipt-text secret))
            (str secret " must not appear anywhere in the receipt")))
      (testing "while the requests that ACTUALLY went out did carry them"
        (is (= "Bearer acc-tok" (get-in (first @sent) [:headers "Authorization"])))
        (is (= "dev-tok" (get-in (first @sent) [:headers "developer-token"])))))))

;; ----------------------------- refusing to exist -----------------------------

(deftest a-live-placer-that-cannot-send-refuses-to-exist
  (testing "no :http-fn -- throws at CONSTRUCTION rather than yielding something that behaves as a dry run"
    (is (thrown? clojure.lang.ExceptionInfo (placer/live-placer {:credentials creds})))
    (is (thrown? clojure.lang.ExceptionInfo (placer/live-placer {:http-fn "not-a-fn" :credentials creds}))))
  (testing "an INCOMPLETE credential set is refused too, for the same reason a partial map is never returned"
    (doseq [k [:developer-token :access-token :customer-id :login-customer-id]]
      (is (thrown? clojure.lang.ExceptionInfo
                   (placer/live-placer {:http-fn identity :credentials (dissoc creds k)}))
          (str "missing " k " must fail at construction"))))
  (testing "a SPENDING placer with no ceiling of its own is refused -- the campaign's authorized budget is the governor's number, not the operator's"
    (is (thrown? clojure.lang.ExceptionInfo
                 (placer/live-placer {:http-fn identity :credentials creds :spend? true})))
    (is (thrown? clojure.lang.ExceptionInfo
                 (placer/live-placer {:http-fn identity :credentials creds :spend? true
                                      :max-spend-micros 0}))))
  (testing "the messages name what to fix"
    (try (placer/live-placer {:credentials creds})
         (is false "should have thrown")
         (catch clojure.lang.ExceptionInfo e (is (re-find #"http-fn" (ex-message e)))))
    (try (placer/live-placer {:http-fn identity :credentials (dissoc creds :customer-id)})
         (is false "should have thrown")
         (catch clojure.lang.ExceptionInfo e (is (re-find #"customer-id" (ex-message e)))))))

;; ----------------------------- live, and spending -----------------------------

(deftest going-live-is-not-the-same-decision-as-spending
  (testing "a live placer without :spend? creates a REAL campaign on a REAL account, PAUSED -- everything real except the charge"
    (let [sent (atom [])
          p (placer/live-placer {:http-fn (ok-http sent) :credentials creds})
          r (placer/place! p {:campaign campaign :placement-record placement-record})]
      (is (true? (:sent? r)))
      (is (false? (:spend? r)))
      (is (= "PAUSED" (:campaign-status r)))
      (is (= "PAUSED" (get-in (second @sent) [:body "operations" 0 "create" "status"])))))
  (testing "only :spend? true creates it ENABLED, which is the state that can charge"
    (let [sent (atom [])
          p (placer/live-placer {:http-fn (ok-http sent) :credentials creds
                                 :spend? true :max-spend-micros 1000000000000})
          r (placer/place! p {:campaign campaign :placement-record placement-record})]
      (is (true? (:sent? r)))
      (is (true? (:spend? r)))
      (is (= "ENABLED" (:campaign-status r)))
      (is (= "ENABLED" (get-in (second @sent) [:body "operations" 0 "create" "status"]))))))

(deftest the-two-step-creation-threads-the-real-budget-id
  (let [sent (atom [])
        p (placer/live-placer {:http-fn (ok-http sent) :credentials creds})
        r (placer/place! p {:campaign campaign :placement-record placement-record})]
    (is (= 2 (count @sent)))
    (is (= "customers/5551234567/campaignBudgets/77"
           (get-in (second @sent) [:body "operations" 0 "create" "campaignBudget"]))
        "the campaign request references the budget the API actually returned")
    (is (= 2 (count (:responses r))) "and both responses are kept on the receipt")
    (is (not (re-find #"from step 1" (pr-str (:requests r))))
        "the receipt shows the resolved request, not the placeholder")))

(deftest a-budget-step-that-returns-no-resource-name-refuses-to-guess
  (testing "the second request cannot be built without the first's answer, and a guessed budget id would attach a campaign to the wrong money"
    (let [p (placer/live-placer {:http-fn (fn [_] {:status 200 :body {}}) :credentials creds})
          r (placer/place! p {:campaign campaign :placement-record placement-record})]
      (is (false? (:sent? r)))
      (is (re-find #"refusing to guess" (:error r))))))

(deftest the-operator-ceiling-is-checked-in-the-last-function-before-the-network
  (testing "a campaign whose own record is internally consistent is still stopped by the operator's own number"
    (let [sent (atom [])
          p (placer/live-placer {:http-fn (ok-http sent) :credentials creds
                                 :spend? true :max-spend-micros 100000})
          r (placer/place! p {:campaign campaign :placement-record placement-record})]
      (is (false? (:sent? r)))
      (is (= [] @sent) "nothing reached the network")
      (is (re-find #"spend ceiling exceeded" (:error r)))
      (is (re-find #"500000000000" (:error r)) "the receipt states both numbers")))
  (testing "the ceiling is a SPENDING concept: a PAUSED live run is not stopped by it, because a paused campaign charges nothing"
    (let [sent (atom [])
          p (placer/live-placer {:http-fn (ok-http sent) :credentials creds})
          r (placer/place! p {:campaign campaign :placement-record placement-record})]
      (is (true? (:sent? r)))
      (is (= 2 (count @sent)))))
  (testing "a send that throws is reported as NOT sent, with the error -- never as a success and never as an omission"
    (let [p (placer/live-placer {:http-fn (fn [_] (throw (ex-info "402 payment required" {})))
                                 :credentials creds})
          r (placer/place! p {:campaign campaign :placement-record placement-record})]
      (is (= :live (:mode r)))
      (is (false? (:sent? r)))
      (is (re-find #"402" (:error r)))
      (is (seq (:requests r)) "the requests that failed are kept, because that is what an operator retries"))))

;; ----------------------------- unsupported platforms -----------------------------

(deftest a-platform-this-actor-can-rule-on-but-cannot-buy-says-so
  (testing "six of the eight catalogued platforms have a transcribed POLICY and no buy adapter, and the receipt is how that stays visible"
    (doseq [pid ["chatgpt-ads" "meta-ads" "microsoft-advertising" "x-ads"
                 "telegram-ads" "line-yahoo-ads"]]
      (is (false? (placer/supported? pid)))
      (let [r (placer/place! (placer/dry-run-placer)
                             {:campaign (assoc campaign :target-platform pid)
                              :placement-record placement-record})]
        (is (= :unsupported (:mode r)) (str pid " has no request builder"))
        (is (false? (:sent? r)))
        (is (= pid (:platform r)) "the receipt names the platform rather than shrugging"))))
  (testing "and the two that ARE buyable are buyable for a transcribed reason: YouTube's own overview makes Google Ads its buying surface"
    (is (true? (placer/supported? "google-ads")))
    (is (true? (placer/supported? "youtube-ads")))))

;; ------------------- the seam inside the actor -------------------

(defn- placed-through
  "Walk `cid` to a committed placement through `actor`, returning the
  ledger."
  [db actor cid]
  (let [go (fn [tid req] (g/run* actor {:request req :context {:actor-id "op-1" :actor-role :agency-operator :phase 3}}
                                {:thread-id tid}))
        ok (fn [tid] (g/run* actor {:approval {:status :approved :by "op-1"}}
                             {:thread-id tid :resume? true}))
        t  (fn [n] (str "pl-" cid "-" n))]
    (go (t 1) {:op :media-plan/verify :subject cid}) (ok (t 1))
    (go (t 2) {:op :platform/verify :subject cid})   (ok (t 2))
    (go (t 3) {:op :risk/screen :subject cid})       (ok (t 3))
    (go (t 4) {:op :actuation/place-campaign :subject cid}) (ok (t 4))
    (store/ledger db)))

(defn- receipts [ledger]
  (filter #(= :placement-dispatch (:t %)) ledger))

(deftest a-placement-never-reaches-the-ledger-without-a-receipt
  (testing "the whole point of the seam: :campaign/mark-placed commits, and the ledger states in the next fact whether money moved"
    (let [db (store/seed-db)
          ledger (placed-through db (op/build db) "campaign-10")
          committed (filter #(= :actuation/place-campaign (:op %)) ledger)
          rs (receipts ledger)]
      (is (= 1 (count (filter #(= :commit (:disposition %)) committed)))
          "the placement did commit")
      (is (= 1 (count rs)) "and exactly one receipt was written for it")
      (is (false? (:sent? (first rs)))
          "with the DEFAULT placer, nothing was sent -- and the ledger says so rather than being silent")
      (is (= :dry-run (:mode (first rs))))
      (is (= "campaign-10" (:campaign-id (first rs))))
      (testing "and the receipt is tied to the placement record the store actually wrote -- a receipt that cannot name its placement cannot be reconciled against the agency's book of record"
        (is (= "JPN-PLC-000000" (:placement-number (first rs))))
        (is (re-find #"JPN-PLC-000000"
                     (get-in (first rs) [:requests 0 :body "operations" 0 "create" "name"]))
            "and the budget the buy request names carries it too"))))
  (testing "a campaign bought on a platform with no adapter ALSO gets a receipt, and it is the receipt that keeps 'we recorded a placement' from reading as 'we bought an ad'"
    (let [db (store/seed-db)
          rs (receipts (placed-through db (op/build db) "campaign-1"))]
      (is (= 1 (count rs)))
      (is (= :unsupported (:mode (first rs))))
      (is (false? (:sent? (first rs))))
      (is (= "chatgpt-ads" (:platform (first rs)))))))

(deftest an-injected-live-placer-is-what-makes-a-real-buy-happen
  (let [db (store/seed-db)
        sent (atom [])
        actor (op/build db {:placer (placer/live-placer {:http-fn (ok-http sent) :credentials creds})})
        rs (receipts (placed-through db actor "campaign-10"))]
    (is (= 2 (count @sent)) "both requests left through the injected fn")
    (is (true? (:sent? (first rs))))
    (is (= :live (:mode (first rs))))
    (is (false? (:spend? (first rs))) "and still did not charge, because :spend? was not asked for"))
  (testing "and injecting a live placer does NOT make an unsupported platform buyable -- the adapter, not the mode, decides whether a request exists"
    (let [db (store/seed-db)
          sent (atom [])
          actor (op/build db {:placer (placer/live-placer {:http-fn (ok-http sent) :credentials creds})})
          rs (receipts (placed-through db actor "campaign-1"))]
      (is (= [] @sent))
      (is (= :unsupported (:mode (first rs)))))))

(deftest a-held-placement-dispatches-nothing
  (testing "campaign-3's media spend exceeds its own authorized budget, so the governor holds -- and the seam is downstream of that, so no request is ever built"
    (let [db (store/seed-db)
          sent (atom [])
          actor (op/build db {:placer (placer/live-placer
                                       {:http-fn (ok-http sent) :credentials creds
                                        :spend? true :max-spend-micros 1000000000000})})]
      (g/run* actor {:request {:op :actuation/place-campaign :subject "campaign-3"}
                     :context {:actor-id "op-1" :actor-role :agency-operator :phase 3}}
              {:thread-id "pl-h"})
      (is (= [] @sent) "a governed hold must reach the network as nothing at all")
      (is (empty? (receipts (store/ledger db)))))))
