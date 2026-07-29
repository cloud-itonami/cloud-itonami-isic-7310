(ns advertising.advertisingadvisor-test
  "The AdOps-LLM boundary as executable tests.

  `advertising.governor-contract-test` proves the governor censors the
  advisor. This ns proves the complementary half: that the advisor
  hands the governor something SAFE to censor even when the model
  underneath it misbehaves.

  The invariant under test:

    A real LLM's response is untrusted input. Any parse failure, any
    wrong shape, any missing key must degrade to a low-confidence
    `:noop` -- never to a high-confidence proposal, and never to an
    exception that escapes the actor. A model hiccup can therefore
    never place a campaign or order a creator tie-up.

  This is the one path in the actor that a live deployment exercises
  on every single request and the mock advisor never touches at all."
  (:require [clojure.test :refer [deftest is testing]]
            [advertising.advertisingadvisor :as advisor]
            [advertising.facts :as facts]
            [advertising.governor :as governor]
            [advertising.store :as store]
            [langchain.model :as model]))

(defn- canned
  "A ChatModel returning `content` verbatim, capturing the messages it
  was handed so we can assert what the advisor actually sent."
  [content sent]
  (reify model/ChatModel
    (-generate [_ messages _opts]
      (reset! sent messages)
      {:role :assistant :content content})))

(defn- advise-with [content]
  (let [sent (atom nil)
        a (advisor/llm-advisor (canned content sent))
        p (advisor/-advise a (store/seed-db) {:op :media-plan/verify :subject "campaign-1"})]
    [p @sent]))

;; ----------------------------- defensive parsing -----------------------------

(deftest unparseable-llm-response-degrades-to-safe-noop
  (testing "a model that answers in prose instead of EDN cannot produce a usable proposal"
    (doseq [junk ["申し訳ありませんが、お答えできません。"
                  "{:summary \"unbalanced"
                  ""
                  "   "]]
      (let [[p _] (advise-with junk)]
        (is (= :noop (:effect p)) (str "junk: " (pr-str junk)))
        (is (= 0.0 (:confidence p)))
        (is (nil? (:stake p)) "a failed parse must never claim an actuation stake")
        (is (= [] (:cites p)))))))

(deftest non-map-edn-degrades-to-safe-noop
  (testing "valid EDN of the wrong shape is still not a proposal"
    (doseq [junk ["[1 2 3]" "\"just a string\"" "42" "nil" ":keyword"]]
      (let [[p _] (advise-with junk)]
        (is (= :noop (:effect p)) (str "junk: " (pr-str junk)))
        (is (= 0.0 (:confidence p)))))))

(deftest a-parsed-proposal-is-normalized-not-trusted
  (testing "missing keys are filled with SAFE defaults, not optimistic ones"
    (let [[p _] (advise-with "{:summary \"s\" :rationale \"r\"}")]
      (is (= :noop (:effect p)) "no :effect -> :noop, never a guessed mutation")
      (is (= 0.0 (:confidence p)) "no :confidence -> 0.0, never assumed-confident")
      (is (= [] (:cites p)) "no :cites -> empty, so the spec-basis gate holds")))
  (testing "a non-numeric confidence is coerced to 0.0 rather than trusted"
    (let [[p _] (advise-with "{:effect :media-plan/set :confidence \"very high\"}")]
      (is (= 0.0 (:confidence p)))))
  (testing "a well-formed proposal survives intact"
    (let [[p _] (advise-with (str "{:summary \"ok\" :rationale \"r\" :cites [\"c\"] "
                                  ":effect :media-plan/set :confidence 0.9}"))]
      (is (= :media-plan/set (:effect p)))
      (is (= 0.9 (:confidence p)))
      (is (= ["c"] (:cites p))))))

(deftest a-model-claiming-an-actuation-still-only-proposes-it
  (testing "a model CAN emit a stake -- the advisor is not the layer that stops it. advertising.governor/high-stakes and advertising.phase are, and both treat this exactly like the mock advisor's own actuation proposal."
    (let [[p _] (advise-with (str "{:effect :campaign/mark-placed "
                                  ":stake :actuation/place-campaign :confidence 1.0}"))]
      (is (= :actuation/place-campaign (:stake p)))
      (is (contains? governor/high-stakes (:stake p))
          "which means it can never auto-commit, whatever confidence the model claimed"))))

;; ----------------------------- what the model is told -----------------------------

(deftest the-model-is-instructed-not-to-invent-a-jurisdiction
  (let [[_ sent] (advise-with "{}")
        system (:content (first (filter #(= :system (:role %)) sent)))]
    (is (re-find #"創作" system) "the system prompt must forbid inventing requirements")
    (is (re-find #"spec-basis" system))))

(deftest the-model-is-handed-only-facts-from-the-store
  (testing "the user message carries the campaign record, so a wrong answer is the model's, not a missing-context artifact"
    (let [[_ sent] (advise-with "{}")
          user (:content (first (filter #(= :user (:role %)) sent)))]
      (is (re-find #"campaign-1" user))
      (is (re-find #"Sato Bakery" user)))))

;; ----------------------------- routing -----------------------------

(deftest every-declared-op-routes-to-a-real-generator
  (testing "no op silently falls through to the unsupported-op branch"
    (let [db (store/seed-db)]
      (doseq [[op subject] [[:campaign/intake "campaign-1"]
                            [:media-plan/verify "campaign-1"]
                            [:risk/screen "campaign-1"]
                            [:actuation/place-campaign "campaign-1"]
                            [:creator/screen "campaign-21"]
                            [:tieup/verify "campaign-21"]
                            [:actuation/order-creator-tieup "campaign-21"]]]
        (let [p (advisor/infer db {:op op :subject subject :patch {:id subject}})]
          (is (not= :noop (:effect p)) (str op " must have a generator"))
          (is (string? (:summary p)) (str op " must produce a human-facing summary")))))))

(deftest an-unknown-op-is-a-safe-noop
  (let [p (advisor/infer (store/seed-db) {:op :actuation/wire-money :subject "campaign-1"})]
    (is (= :noop (:effect p)))
    (is (= 0.0 (:confidence p)))
    (is (nil? (:stake p)))))

;; ----------------------------- missing-record branches -----------------------------

(deftest screening-an-absent-campaign-yields-unknown-not-a-clearance
  (testing "the failure mode that matters: a typo'd subject must not read as 'screened clean'"
    (let [db (store/seed-db)]
      (doseq [op [:risk/screen :creator/screen]]
        (let [p (advisor/infer db {:op op :subject "no-such-campaign"})]
          (is (= :unknown (get-in p [:value :verdict])) (str op))
          (is (= 0.0 (:confidence p)) (str op " must not be confident about a record it never found")))))))

(deftest screening-a-campaign-with-no-creator-yields-unknown
  (testing "campaign-1 has no tie-up at all -- screening it must not invent an eligible creator"
    (let [p (advisor/infer (store/seed-db) {:op :creator/screen :subject "campaign-1"})]
      (is (= :unknown (get-in p [:value :verdict])))
      (is (= 0.0 (:confidence p))))))

(deftest a-tieup-brief-reports-the-recorded-label-and-never-proposes-one
  (testing "the advisor must not author a disclosure label -- that is what makes the governor's published-label check meaningful (ADR-0002)"
    (let [db (store/seed-db)
          p8 (advisor/infer db {:op :tieup/verify :subject "campaign-24"})
          p9 (advisor/infer db {:op :tieup/verify :subject "campaign-25"})]
      (is (nil? (get-in p8 [:value :recorded-disclosure-label]))
          "campaign-24 records no label; the advisor must report nil, not fill one in")
      (is (= "タイアップ" (get-in p9 [:value :recorded-disclosure-label]))
          "campaign-25's non-compliant label is reported verbatim, not corrected")
      (is (= (facts/accepted-disclosure-labels "JPN")
             (get-in p9 [:value :accepted-disclosure-labels]))
          "the authority's own list travels with the brief so a human can see the mismatch"))))

(deftest a-tieup-brief-for-an-uncatalogued-jurisdiction-cites-nothing
  (let [p (advisor/infer (store/seed-db) {:op :tieup/verify :subject "campaign-21" :no-spec? true})]
    (is (= [] (:cites p)) "no citations -> the governor's spec-basis gate HARD-holds it")
    (is (nil? (get-in p [:value :spec-basis])))))

;; ----------------------------- trace -----------------------------

(deftest the-trace-carries-the-decision-grounds
  (testing "the audit ledger's advisor record must show what the proposal was based on"
    (let [db (store/seed-db)
          req {:op :media-plan/verify :subject "campaign-1"}
          t (advisor/trace req (advisor/infer db req))]
      (is (= :advertisingadvisor-proposal (:t t)))
      (is (= :media-plan/verify (:op t)))
      (is (= "campaign-1" (:subject t)))
      (is (seq (:cites t)))
      (is (number? (:confidence t))))))
