(ns advertising.governor-contract-test
  "The governor contract as executable tests -- the advertising analog
  of `cloud-itonami-isic-6512`'s `casualty.governor-contract-test`.
  The single invariant under test:

    AdOps-LLM never places a campaign -- or orders a creator tie-up --
    that the Campaign Governor would reject, NEITHER actuation op
    auto-commits at any phase, `:campaign/intake` (no direct capital
    risk) MAY auto-commit when clean, and every decision (commit OR
    hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [advertising.store :as store]
            [advertising.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :agency-operator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving a media-plan
  assessment on file. Uses distinct thread-ids per call site by
  suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :media-plan/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :campaign/intake :subject "campaign-1"
                   :patch {:id "campaign-1" :client-name "Sato Bakery"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sato Bakery" (:client-name (store/campaign db "campaign-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest media-plan-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :media-plan/verify :subject "campaign-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/media-plan-of db "campaign-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a media-plan/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :media-plan/verify :subject "campaign-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/media-plan-of db "campaign-1")) "no media-plan assessment written"))))

(deftest place-campaign-without-media-plan-is-held
  (testing "actuation/place-campaign before any media-plan verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/place-campaign :subject "campaign-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest media-spend-exceeds-authorized-budget-is-held
  (testing "a campaign whose own proposed media spend exceeds its own authorized budget -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "campaign-3")
          res (exec-op actor "t5" {:op :actuation/place-campaign :subject "campaign-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:media-spend-exceeds-authorized-budget} (-> (store/ledger db) last :basis)))
      (is (empty? (store/placement-history db))))))

(deftest misleading-claim-risk-is-held-and-unoverridable
  (testing "an unresolved misleading-claim risk on a campaign -> HOLD, and never reaches request-approval -- exercised via :risk/screen DIRECTLY, not via the actuation op against an unscreened campaign (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's, behavioral's, secondary's, card's, water's, telecom's, aerospace's, recovery's, consulting's, union's, congregation's, fab's, energy's, care's, navigator's, learning's and banking's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :risk/screen :subject "campaign-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:misleading-claim-risk-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/risk-screen-of db "campaign-4")) "no clearance written"))))

(deftest place-campaign-always-escalates-then-human-decides
  (testing "a clean, fully-assessed campaign still ALWAYS interrupts for human approval -- actuation/place-campaign is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "campaign-1")
          r1 (exec-op actor "t7" {:op :actuation/place-campaign :subject "campaign-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, placement record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:campaign-placed? (store/campaign db "campaign-1"))))
          (is (= 1 (count (store/placement-history db))) "one draft placement record"))))))

(deftest place-campaign-double-placement-is-held
  (testing "placing the same campaign twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "campaign-1")
          _ (exec-op actor "t8a" {:op :actuation/place-campaign :subject "campaign-1"} operator)
          _ (approve! actor "t8a")
          res (exec-op actor "t8" {:op :actuation/place-campaign :subject "campaign-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-placed} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/placement-history db))) "still only the one earlier placement"))))

;; ---------------- creator tie-up (YouTube / influencer), ADR-0002 ----------------

(defn- brief!
  "Walks `subject` through tieup/verify -> approve, leaving a creator-
  tie-up evidence assessment on file."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-brief") {:op :tieup/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-brief")))

(deftest tieup-verify-always-needs-approval
  (testing "tieup/verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "u1" {:op :tieup/verify :subject "campaign-5"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "u1")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/tieup-brief-of db "campaign-5")))))))

(deftest tieup-verify-without-spec-basis-is-held
  (testing "a tieup/verify proposal with no official disclosure basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "u2"
                    {:op :tieup/verify :subject "campaign-5" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/tieup-brief-of db "campaign-5")) "no tie-up brief written"))))

(deftest order-tieup-without-brief-is-held
  (testing "actuation/order-creator-tieup before any tieup/verify -> HOLD (tie-up evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "u3" {:op :actuation/order-creator-tieup :subject "campaign-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:tieup-evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest combined-spend-exceeding-authorized-budget-is-held
  (testing "a tie-up fee that fits on its own but pushes media-spend + fee past the client's own authorization -> HOLD"
    (let [[db actor] (fresh)
          c (store/campaign db "campaign-6")]
      (is (< (:creator-tieup-fee c) (:authorized-budget c))
          "precondition: the fee ALONE is affordable -- a fee-only check would clear this")
      (brief! actor "u4pre" "campaign-6")
      (let [res (exec-op actor "u4" {:op :actuation/order-creator-tieup :subject "campaign-6"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:creator-tieup-fee-exceeds-authorized-budget} (-> (store/ledger db) last :basis)))
        (is (empty? (store/tieup-order-history db)))))))

(deftest ineligible-creator-is-held-and-unoverridable
  (testing "an unresolved creator-eligibility issue -> HOLD, and never reaches request-approval -- exercised via :creator/screen DIRECTLY, not via the actuation op against an unscreened creator (the same discipline this repo's :risk/screen test records)"
    (let [[db actor] (fresh)
          res (exec-op actor "u5" {:op :creator/screen :subject "campaign-7"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:creator-ineligible} (-> (store/ledger db) first :basis)))
      (is (nil? (store/creator-screen-of db "campaign-7")) "no clearance written"))))

(deftest tieup-with-no-disclosure-label-is-held
  (testing "ordering a creator tie-up with NO recorded sponsorship-disclosure label -> HOLD"
    (let [[db actor] (fresh)
          _ (brief! actor "u6pre" "campaign-8")
          res (exec-op actor "u6" {:op :actuation/order-creator-tieup :subject "campaign-8"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:sponsorship-disclosure-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/tieup-order-history db))))))

(deftest tieup-with-unpublished-disclosure-label-is-held
  (testing "a label IS recorded, but 「タイアップ」 is not among the 消費者庁's own published examples -> the SAME HARD hold. Recording something must not be mistakable for recording something compliant."
    (let [[db actor] (fresh)
          c (store/campaign db "campaign-9")]
      (is (some? (:disclosure-label c)) "precondition: a label really is on file")
      (brief! actor "u7pre" "campaign-9")
      (let [res (exec-op actor "u7" {:op :actuation/order-creator-tieup :subject "campaign-9"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:sponsorship-disclosure-missing} (-> (store/ledger db) last :basis)))
        (is (empty? (store/tieup-order-history db)))))))

(deftest order-tieup-always-escalates-then-human-decides
  (testing "a clean, fully-assessed creator tie-up still ALWAYS interrupts for human approval -- actuation/order-creator-tieup is never auto"
    (let [[db actor] (fresh)
          _ (brief! actor "u8pre" "campaign-5")
          r1 (exec-op actor "u8" {:op :actuation/order-creator-tieup :subject "campaign-5"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, tie-up order record drafted"
        (let [r2 (approve! actor "u8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:tieup-ordered? (store/campaign db "campaign-5"))))
          (is (= 1 (count (store/tieup-order-history db))) "one draft tie-up order record")
          (is (= "youtube" (get (first (store/tieup-order-history db)) "platform"))))))))

(deftest order-tieup-double-order-is-held
  (testing "ordering the same campaign's creator tie-up twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (brief! actor "u9pre" "campaign-5")
          _ (exec-op actor "u9a" {:op :actuation/order-creator-tieup :subject "campaign-5"} operator)
          _ (approve! actor "u9a")
          res (exec-op actor "u9" {:op :actuation/order-creator-tieup :subject "campaign-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-ordered} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/tieup-order-history db))) "still only the one earlier order"))))

(deftest the-two-actuation-guards-are-independent
  (testing "placing a campaign does NOT mark its tie-up ordered, and ordering a tie-up does NOT mark the campaign placed -- separate booleans, separate sequences, separate histories"
    (let [[db actor] (fresh)]
      (verify! actor "u10pre" "campaign-5")
      (exec-op actor "u10a" {:op :actuation/place-campaign :subject "campaign-5"} operator)
      (approve! actor "u10a")
      (is (true? (:campaign-placed? (store/campaign db "campaign-5"))))
      (is (false? (:tieup-ordered? (store/campaign db "campaign-5")))
          "placement must not satisfy the tie-up guard")
      (brief! actor "u10b" "campaign-5")
      (let [res (exec-op actor "u10c" {:op :actuation/order-creator-tieup :subject "campaign-5"} operator)]
        (is (= :interrupted (:status res)) "the tie-up is still available to order")
        (approve! actor "u10c"))
      (is (= 1 (count (store/placement-history db))))
      (is (= 1 (count (store/tieup-order-history db))))
      (is (= "JPN-PLC-000000" (get (first (store/placement-history db)) "record_id")))
      (is (= "JPN-TIE-000000" (get (first (store/tieup-order-history db)) "record_id"))
          "each actuation runs its own jurisdiction-scoped sequence"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :campaign/intake :subject "campaign-1"
                          :patch {:id "campaign-1" :client-name "Sato Bakery"}} operator)
      (exec-op actor "b" {:op :media-plan/verify :subject "campaign-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
