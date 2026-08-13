(ns advertising.placer-test
  "The actuation seam as executable tests.

  One invariant carries the rest: **a placement can never be recorded
  without a statement of whether anything was sent.** Everything else
  here exists to make that statement impossible to fake -- the default
  sends nothing, a live placer that could not send refuses to exist, a
  platform with no adapter says so rather than looking successful, and
  a failed send is `:sent? false` rather than an omission."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [advertising.placer :as placer]
            [advertising.store :as store]
            [advertising.operation :as op]))

(def ^:private campaign
  {:id "campaign-1" :client-name "Sato Bakery" :target-platform "google-ads"
   :proposed-media-spend 500000 :jurisdiction "JPN"})

(def ^:private placement-record {"record_id" "JPN-PLC-000000"})

(deftest the-default-builds-the-request-and-sends-nothing
  (let [r (placer/place! (placer/dry-run-placer)
                         {:campaign campaign :placement-record placement-record})]
    (is (= :dry-run (:mode r)))
    (is (false? (:sent? r)))
    (is (= "google-ads" (:platform r)))
    (is (= "JPN-PLC-000000" (:placement-number r)))
    (testing "a dry run is not a stub -- the receipt carries the request that WOULD go out, so it can be reviewed and diffed"
      (is (= :post (get-in r [:request :method])))
      (is (re-find #"googleads\.googleapis\.com" (get-in r [:request :url])))
      (is (= 500000000000 (get-in r [:request :body "operations" 0 "create" "campaignBudget" "amountMicros"])))
      (is (= "JPN-PLC-000000" (get-in r [:request :body "agencyPlacementRecord"]))))))

(deftest a-live-placer-that-cannot-send-refuses-to-exist
  (testing "constructing it without an :http-fn throws, rather than yielding something that behaves as a dry run"
    (is (thrown? clojure.lang.ExceptionInfo (placer/live-placer {})))
    (is (thrown? clojure.lang.ExceptionInfo (placer/live-placer {:http-fn "not-a-fn"})))
    (is (thrown? clojure.lang.ExceptionInfo (placer/live-placer {:customer-id "123"}))))
  (testing "the failure has to happen at CONSTRUCTION: a placer that failed silently at dispatch would write :sent? false onto a placement the operator believes they authorised for real"
    (try (placer/live-placer {})
         (is false "should have thrown")
         (catch clojure.lang.ExceptionInfo e
           (is (re-find #"http-fn" (ex-message e)))))))

(deftest a-live-placer-sends-through-the-injected-fn-and-records-the-response
  (let [sent (atom [])
        p (placer/live-placer {:http-fn (fn [req] (swap! sent conj req) {:status 200 :id "cmp-9"})
                               :customer-id "555"})
        r (placer/place! p {:campaign campaign :placement-record placement-record})]
    (is (= :live (:mode r)))
    (is (true? (:sent? r)))
    (is (= {:status 200 :id "cmp-9"} (:response r)))
    (is (= 1 (count @sent)))
    (is (re-find #"/customers/555/" (:url (first @sent)))
        "injected opts reach the request builder, so a dry run and a live send build the SAME shape"))
  (testing "a send that throws is reported as NOT sent, with the error -- never as a success and never as an omission"
    (let [p (placer/live-placer {:http-fn (fn [_] (throw (ex-info "402 payment required" {})))})
          r (placer/place! p {:campaign campaign :placement-record placement-record})]
      (is (= :live (:mode r)))
      (is (false? (:sent? r)))
      (is (re-find #"402" (:error r)))
      (is (some? (:request r)) "the request that failed is kept, because that is what an operator retries"))))

(deftest a-platform-this-actor-can-rule-on-but-cannot-buy-says-so
  (testing "seven of the eight catalogued platforms have a transcribed POLICY and no buy adapter, and the receipt is how that stays visible"
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
      (is (= "campaign-10" (:campaign-id (first rs))))))
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
        actor (op/build db {:placer (placer/live-placer
                                     {:http-fn (fn [req] (swap! sent conj req) {:status 200})})})
        rs (receipts (placed-through db actor "campaign-10"))]
    (is (= 1 (count @sent)) "the buy request left through the injected fn, and only once")
    (is (true? (:sent? (first rs))))
    (is (= :live (:mode (first rs)))))
  (testing "and injecting a live placer does NOT make an unsupported platform buyable -- the adapter, not the mode, decides whether a request exists"
    (let [db (store/seed-db)
          sent (atom [])
          actor (op/build db {:placer (placer/live-placer
                                       {:http-fn (fn [req] (swap! sent conj req) {:status 200})})})
          rs (receipts (placed-through db actor "campaign-1"))]
      (is (= [] @sent))
      (is (= :unsupported (:mode (first rs)))))))

(deftest a-held-placement-dispatches-nothing
  (testing "campaign-3's media spend exceeds its own authorized budget, so the governor holds -- and the seam is downstream of that, so no request is ever built"
    (let [db (store/seed-db)
          sent (atom [])
          actor (op/build db {:placer (placer/live-placer
                                       {:http-fn (fn [req] (swap! sent conj req) {:status 200})})})]
      (g/run* actor {:request {:op :actuation/place-campaign :subject "campaign-3"}
                     :context {:actor-id "op-1" :actor-role :agency-operator :phase 3}}
              {:thread-id "pl-h"})
      (is (= [] @sent) "a governed hold must reach the network as nothing at all")
      (is (empty? (receipts (store/ledger db)))))))
