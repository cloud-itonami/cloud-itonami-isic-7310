(ns advertising.governor-contract-test
  "The governor contract as executable tests -- the advertising analog
  of `cloud-itonami-isic-6512`'s `casualty.governor-contract-test`.
  The single invariant under test:

    AdOps-LLM never places a campaign the Campaign Governor would
    reject, `:actuation/place-campaign` NEVER auto-commits at any
    phase, `:campaign/intake` (no direct capital risk) MAY auto-commit
    when clean, and every decision (commit OR hold) leaves exactly one
    ledger fact."
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
  "Walks `subject` through BOTH verification ops -> approve, leaving a
  media-plan assessment AND a media-platform conformance assessment on
  file -- the full evidence set `:actuation/place-campaign` requires
  since ADR-0002. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :media-plan/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify"))
  (exec-op actor (str tid-prefix "-pverify") {:op :platform/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-pverify")))

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

;; ---------------- media-platform governor family (ADR-0002) ----------------
;;
;; Each of these campaigns is CLEAN on every jurisdiction-side check --
;; lawful spend, no misleading-claim risk, a covered jurisdiction --
;; and is held purely by the media platform's own published ad policy.
;; That is the point of the second family: the two authorities are
;; independent, and clearing one says nothing about the other.

(deftest platform-verify-always-needs-approval
  (testing "platform conformance is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "p1" {:op :platform/verify :subject "campaign-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "p1")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:conformant? (store/platform-check-of db "campaign-1"))))))))

(deftest unknown-platform-is-held
  (testing "a campaign targeting a platform with no transcribed ad policy -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "p2" {:op :platform/verify :subject "campaign-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (not= :interrupted (:status res)))
      (is (some #{:no-platform-policy-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/platform-check-of db "campaign-5")) "no conformance assessment written"))))

(deftest platform-prohibited-category-is-held
  (testing "a category the platform's own policy names as prohibited -> HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "p3" {:op :platform/verify :subject "campaign-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:platform-prohibited-category} (-> (store/ledger db) first :basis))))))

(deftest platform-restricted-category-without-approval-is-held
  (testing "a restricted category with neither advertiser approval nor an allowed jurisdiction -> HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "p4" {:op :platform/verify :subject "campaign-7"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:platform-restricted-category-unapproved} (-> (store/ledger db) first :basis))))))

(deftest generative-surface-distinguishability-must-be-attested
  (testing "on a generative surface, an unattested ad/answer distinguishability -> HOLD (interface mimicry)"
    (let [[db actor] (fresh)
          res (exec-op actor "p5" {:op :platform/verify :subject "campaign-8"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:platform-attestation-missing} (-> (store/ledger db) first :basis))))))

(deftest excluded-placement-context-is-held
  (testing "a placement requested against a context the platform refuses to serve ads near -> HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "p6" {:op :platform/verify :subject "campaign-9"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:sensitive-placement-context} (-> (store/ledger db) first :basis))))))

(deftest place-campaign-without-platform-check-is-held
  (testing "clearing the JURISDICTION's evidence checklist does not clear the PLATFORM -- actuation still holds"
    (let [[db actor] (fresh)]
      (exec-op actor "p7-verify" {:op :media-plan/verify :subject "campaign-1"} operator)
      (approve! actor "p7-verify")
      (let [res (exec-op actor "p7" {:op :actuation/place-campaign :subject "campaign-1"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:platform-check-incomplete} (-> (store/ledger db) last :basis)))
        (is (empty? (store/placement-history db)))))))

(deftest platform-holds-survive-a-human-approver
  (testing "the platform family is HARD: a prohibited category never reaches request-approval at all, so there is nobody to override it"
    (let [[db actor] (fresh)]
      (verify! actor "p8pre" "campaign-6")
      (let [res (exec-op actor "p8" {:op :actuation/place-campaign :subject "campaign-6"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (not= :interrupted (:status res)) "never pauses for a human")
        (is (some #{:platform-prohibited-category} (-> (store/ledger db) last :basis)))
        (is (empty? (store/placement-history db)))))))

(deftest placement-record-names-the-platform
  (testing "a committed placement records WHICH platform it ran on"
    (let [[db actor] (fresh)]
      (verify! actor "p9pre" "campaign-1")
      (exec-op actor "p9" {:op :actuation/place-campaign :subject "campaign-1"} operator)
      (approve! actor "p9")
      (is (= "chatgpt-ads" (get (first (store/placement-history db)) "platform"))))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :campaign/intake :subject "campaign-1"
                          :patch {:id "campaign-1" :client-name "Sato Bakery"}} operator)
      (exec-op actor "b" {:op :media-plan/verify :subject "campaign-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
