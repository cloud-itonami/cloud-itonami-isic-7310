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

(defn- screen!
  "Walks `subject` through misleading-claim-risk screening -> approve,
  leaving a resolved verdict on file."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :risk/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(defn- prepare-placement!
  "Everything `:actuation/place-campaign` now legitimately requires: a
  media-plan assessment AND a resolved misleading-claim-risk screening,
  both committed. Before `risk-screen-missing-violations` existed, the
  screening step was optional -- which is exactly the hole ADR-0002
  recorded and `placing-without-any-risk-screening-is-held` now
  guards."
  [actor tid-prefix subject]
  (verify! actor tid-prefix subject)
  (screen! actor tid-prefix subject))

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
          _ (prepare-placement! actor "t5pre" "campaign-3")
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
          _ (prepare-placement! actor "t7pre" "campaign-1")
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
          _ (prepare-placement! actor "t8pre" "campaign-1")
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

(defn- creator-screen!
  "Walks `subject` through creator-eligibility screening -> approve,
  leaving an eligible verdict on file."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-cscreen") {:op :creator/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-cscreen")))

(defn- prepare-tieup!
  "Everything `:actuation/order-creator-tieup` now legitimately
  requires: a tie-up evidence brief AND an eligible creator screening,
  both committed. The tie-up half of the same ADR-0002 boundary."
  [actor tid-prefix subject]
  (brief! actor tid-prefix subject)
  (creator-screen! actor tid-prefix subject))

(deftest tieup-verify-always-needs-approval
  (testing "tieup/verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "u1" {:op :tieup/verify :subject "campaign-21"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "u1")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/tieup-brief-of db "campaign-21")))))))

(deftest tieup-verify-without-spec-basis-is-held
  (testing "a tieup/verify proposal with no official disclosure basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "u2"
                    {:op :tieup/verify :subject "campaign-21" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/tieup-brief-of db "campaign-21")) "no tie-up brief written"))))

(deftest order-tieup-without-brief-is-held
  (testing "actuation/order-creator-tieup before any tieup/verify -> HOLD (tie-up evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "u3" {:op :actuation/order-creator-tieup :subject "campaign-21"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:tieup-evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest combined-spend-exceeding-authorized-budget-is-held
  (testing "a tie-up fee that fits on its own but pushes media-spend + fee past the client's own authorization -> HOLD"
    (let [[db actor] (fresh)
          c (store/campaign db "campaign-22")]
      (is (< (:creator-tieup-fee c) (:authorized-budget c))
          "precondition: the fee ALONE is affordable -- a fee-only check would clear this")
      (prepare-tieup! actor "u4pre" "campaign-22")
      (let [res (exec-op actor "u4" {:op :actuation/order-creator-tieup :subject "campaign-22"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:creator-tieup-fee-exceeds-authorized-budget} (-> (store/ledger db) last :basis)))
        (is (empty? (store/tieup-order-history db)))))))

(deftest ineligible-creator-is-held-and-unoverridable
  (testing "an unresolved creator-eligibility issue -> HOLD, and never reaches request-approval -- exercised via :creator/screen DIRECTLY, not via the actuation op against an unscreened creator (the same discipline this repo's :risk/screen test records)"
    (let [[db actor] (fresh)
          res (exec-op actor "u5" {:op :creator/screen :subject "campaign-23"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:creator-ineligible} (-> (store/ledger db) first :basis)))
      (is (nil? (store/creator-screen-of db "campaign-23")) "no clearance written"))))

(deftest tieup-with-no-disclosure-label-is-held
  (testing "ordering a creator tie-up with NO recorded sponsorship-disclosure label -> HOLD"
    (let [[db actor] (fresh)
          _ (prepare-tieup! actor "u6pre" "campaign-24")
          res (exec-op actor "u6" {:op :actuation/order-creator-tieup :subject "campaign-24"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:sponsorship-disclosure-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/tieup-order-history db))))))

(deftest tieup-with-unpublished-disclosure-label-is-held
  (testing "a label IS recorded, but 「タイアップ」 is not among the 消費者庁's own published examples -> the SAME HARD hold. Recording something must not be mistakable for recording something compliant."
    (let [[db actor] (fresh)
          c (store/campaign db "campaign-25")]
      (is (some? (:disclosure-label c)) "precondition: a label really is on file")
      (prepare-tieup! actor "u7pre" "campaign-25")
      (let [res (exec-op actor "u7" {:op :actuation/order-creator-tieup :subject "campaign-25"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:sponsorship-disclosure-missing} (-> (store/ledger db) last :basis)))
        (is (empty? (store/tieup-order-history db)))))))

(deftest order-tieup-always-escalates-then-human-decides
  (testing "a clean, fully-assessed creator tie-up still ALWAYS interrupts for human approval -- actuation/order-creator-tieup is never auto"
    (let [[db actor] (fresh)
          _ (prepare-tieup! actor "u8pre" "campaign-21")
          r1 (exec-op actor "u8" {:op :actuation/order-creator-tieup :subject "campaign-21"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, tie-up order record drafted"
        (let [r2 (approve! actor "u8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:tieup-ordered? (store/campaign db "campaign-21"))))
          (is (= 1 (count (store/tieup-order-history db))) "one draft tie-up order record")
          (is (= "youtube" (get (first (store/tieup-order-history db)) "platform"))))))))

(deftest order-tieup-double-order-is-held
  (testing "ordering the same campaign's creator tie-up twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (prepare-tieup! actor "u9pre" "campaign-21")
          _ (exec-op actor "u9a" {:op :actuation/order-creator-tieup :subject "campaign-21"} operator)
          _ (approve! actor "u9a")
          res (exec-op actor "u9" {:op :actuation/order-creator-tieup :subject "campaign-21"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-ordered} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/tieup-order-history db))) "still only the one earlier order"))))

(deftest the-two-actuation-guards-are-independent
  (testing "placing a campaign does NOT mark its tie-up ordered, and ordering a tie-up does NOT mark the campaign placed -- separate booleans, separate sequences, separate histories"
    (let [[db actor] (fresh)]
      (prepare-placement! actor "u10pre" "campaign-21")
      (exec-op actor "u10a" {:op :actuation/place-campaign :subject "campaign-21"} operator)
      (approve! actor "u10a")
      (is (true? (:campaign-placed? (store/campaign db "campaign-21"))))
      (is (false? (:tieup-ordered? (store/campaign db "campaign-21")))
          "placement must not satisfy the tie-up guard")
      (prepare-tieup! actor "u10b" "campaign-21")
      (let [res (exec-op actor "u10c" {:op :actuation/order-creator-tieup :subject "campaign-21"} operator)]
        (is (= :interrupted (:status res)) "the tie-up is still available to order")
        (approve! actor "u10c"))
      (is (= 1 (count (store/placement-history db))))
      (is (= 1 (count (store/tieup-order-history db))))
      (is (= "JPN-PLC-000000" (get (first (store/placement-history db)) "record_id")))
      (is (= "JPN-TIE-000000" (get (first (store/tieup-order-history db)) "record_id"))
          "each actuation runs its own jurisdiction-scoped sequence"))))

;; ---------------- "we never looked" is not a clean state (ADR-0005) ----------------

(deftest placing-without-any-risk-screening-is-held
  (testing "a fully-evidenced campaign that was NEVER screened for misleading-claim risk must not place. Until ADR-0005 this passed: the unresolved-risk rule only fired when a screening record existed AND said :unresolved, so skipping the screening entirely was the way through."
    (let [[db actor] (fresh)
          _ (verify! actor "s1pre" "campaign-1")]
      (is (nil? (store/risk-screen-of db "campaign-1")) "precondition: nothing on file")
      (let [res (exec-op actor "s1" {:op :actuation/place-campaign :subject "campaign-1"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:risk-screen-missing} (-> (store/ledger db) last :basis)))
        (is (empty? (store/placement-history db)))))))

(deftest ordering-without-any-creator-screening-is-held
  (testing "the tie-up half of the same hole: a briefed tie-up whose creator was never screened must not order"
    (let [[db actor] (fresh)
          _ (brief! actor "s2pre" "campaign-21")]
      (is (nil? (store/creator-screen-of db "campaign-21")) "precondition: nothing on file")
      (let [res (exec-op actor "s2" {:op :actuation/order-creator-tieup :subject "campaign-21"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:creator-screen-missing} (-> (store/ledger db) last :basis)))
        (is (empty? (store/tieup-order-history db)))))))

(deftest a-screening-that-never-cleared-is-not-a-clearance
  (testing "an :unknown verdict -- what a typo'd subject or an absent creator produces -- must not satisfy the requirement either"
    (let [[db actor] (fresh)]
      ;; campaign-1 has no creator at all, so screening it yields :unknown.
      (exec-op actor "s3pre" {:op :creator/screen :subject "campaign-1"} operator)
      (approve! actor "s3pre")
      (is (= :unknown (:verdict (store/creator-screen-of db "campaign-1")))
          "precondition: a record exists, but it is not a clearance")
      (brief! actor "s3b" "campaign-1")
      (let [res (exec-op actor "s3" {:op :actuation/order-creator-tieup :subject "campaign-1"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:creator-screen-missing} (-> (store/ledger db) last :basis))
            "a non-clearing verdict on file is treated as no clearance")))))

(deftest the-missing-screen-rule-does-not-double-report-a-found-risk
  (testing "when a screening DID run and found something, the ledger names that finding -- not the generic 'no screening on file'. Two basis entries for one fact would make the ledger harder to read, and would obscure which of the two problems the operator has."
    (let [[db actor] (fresh)]
      (verify! actor "s4pre" "campaign-4")
      ;; campaign-4's screening HARD-holds on its own finding, so nothing commits;
      ;; drive the verdict onto the record directly to reach the actuation op.
      (store/commit-record! db {:effect :risk-screen/set :path ["campaign-4"]
                                :payload {:campaign-id "campaign-4" :verdict :unresolved}})
      (let [res (exec-op actor "s4" {:op :actuation/place-campaign :subject "campaign-4"} operator)
            basis (-> (store/ledger db) last :basis)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:misleading-claim-risk-unresolved} basis))
        (is (not (some #{:risk-screen-missing} basis))
            "the specific finding is reported, not the generic absence")))))
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
      (prepare-placement! actor "p9pre" "campaign-1")
      (exec-op actor "p9" {:op :actuation/place-campaign :subject "campaign-1"} operator)
      (approve! actor "p9")
      (is (= "chatgpt-ads" (get (first (store/placement-history db)) "platform"))))))

;; ---------------- cross-platform disagreement (ADR-0003) ----------------

(deftest same-category-same-jurisdiction-different-platform-different-verdict
  (testing "campaign-1 and campaign-10 are both JPN :local-services; only the target platform differs, and both clear"
    (let [[db actor] (fresh)]
      (exec-op actor "x1" {:op :platform/verify :subject "campaign-1"} operator)
      (approve! actor "x1")
      (exec-op actor "x2" {:op :platform/verify :subject "campaign-10"} operator)
      (approve! actor "x2")
      (is (= "chatgpt-ads" (:platform (store/platform-check-of db "campaign-1"))))
      (is (= "google-ads" (:platform (store/platform-check-of db "campaign-10"))))
      (is (true? (:conformant? (store/platform-check-of db "campaign-10")))
          "google-ads does not name :local-services and does not close its category set"))))

(deftest microsoft-holds-a-category-chatgpt-permits
  (testing "campaign-12 is :travel-experiences -- permitted on chatgpt-ads, restricted on microsoft-advertising with no transcribed country table"
    (let [[db actor] (fresh)
          res (exec-op actor "x3" {:op :platform/verify :subject "campaign-12"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:platform-restricted-category-unapproved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/platform-check-of db "campaign-12"))))))

(deftest a-policy-nobody-finished-reading-holds-the-campaign
  (testing "campaign-13 is :lifestyle-household on line-yahoo-ads -- a category the OPEN-set platforms all permit, held here only because that operator's enumerated 掲載基準 has not been read"
    (let [[db actor] (fresh)
          res (exec-op actor "x9" {:op :platform/verify :subject "campaign-13"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:platform-prohibited-category} (-> (store/ledger db) first :basis)))
      (is (nil? (store/platform-check-of db "campaign-13")))))
  (testing "the hold SAYS which kind of no it is -- an unread standard must not wear the same words as a refusal"
    (let [[db actor] (fresh)]
      (exec-op actor "x10" {:op :platform/verify :subject "campaign-13"} operator)
      (let [detail (->> (store/ledger db) first :violations
                        (filter #(= :platform-prohibited-category (:rule %)))
                        first :detail)]
        (is (some? detail))
        (is (re-find #"policy-read :partial" detail)
            "the operator reading this must be able to tell 'the platform said no' from 'we did not read it'")
        (is (not (re-find #"明示的に禁止" detail))))))
  (testing "the identical category on an open-set platform is placeable, which is what makes the hold above informative rather than noise"
    (let [[db actor] (fresh)]
      (exec-op actor "x11" {:op :platform/verify :subject "campaign-10"} operator)
      (approve! actor "x11")
      (is (true? (:conformant? (store/platform-check-of db "campaign-10")))))))

(deftest youtube-is-not-simply-google
  (testing "campaign-14 is :gambling on youtube-ads, which names no categories of its own -- it holds because it incorporates google-ads' restriction AND google-ads' untranscribed country table"
    (let [[db actor] (fresh)
          res (exec-op actor "x12" {:op :platform/verify :subject "campaign-14"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:platform-restricted-category-unapproved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/platform-check-of db "campaign-14"))))))

(deftest jurisdiction-scoped-attestation-holds-only-where-it-applies
  (testing "campaign-11 runs on meta-ads in DEU and has not made the EU DSA beneficiary/payer disclosure"
    (let [[db actor] (fresh)
          res (exec-op actor "x4" {:op :platform/verify :subject "campaign-11"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:platform-attestation-missing} (-> (store/ledger db) first :basis)))))
  (testing "the same campaign facts in JPN would NOT be held -- the requirement follows the jurisdiction"
    (let [[db actor] (fresh)]
      (store/commit-record! db {:effect :campaign/upsert
                                :value {:id "campaign-11" :jurisdiction "JPN"}})
      (exec-op actor "x5" {:op :platform/verify :subject "campaign-11"} operator)
      (approve! actor "x5")
      (is (true? (:conformant? (store/platform-check-of db "campaign-11")))))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :campaign/intake :subject "campaign-1"
                          :patch {:id "campaign-1" :client-name "Sato Bakery"}} operator)
      (exec-op actor "b" {:op :media-plan/verify :subject "campaign-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
