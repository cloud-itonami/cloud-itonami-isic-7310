(ns advertising.operation-test
  "The OperationActor graph's own contract -- the two paths every other
  test suite in this repo drives straight past.

  `governor-contract-test` always APPROVES at the interrupt and always
  runs at phase 3. That leaves the actor's two remaining decision
  surfaces unexercised, and both carry a guarantee the README makes in
  plain text:

    1. The human approval workflow is a real veto, not a formality.
       `advertising.phase`'s docstring says a human operator is always
       the one who actually places a campaign -- which is only true if
       REJECTING actually stops it, writes no record, and leaves the
       actuation still available to propose again after whatever
       concerned the approver is fixed.

    2. The rollout phases actually gate. `phase-test` asserts
       `advertising.phase/gate` as a pure function; nothing asserted
       that the actor honours its answer. A phase-0 deployment that
       wrote to the SSoT would be a serious, silent bug."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [advertising.store :as store]
            [advertising.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(defn- at-phase [n]
  {:actor-id "op-1" :actor-role :agency-operator :phase n})

(def operator (at-phase 3))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- resume! [actor tid status]
  (g/run* actor {:approval {:status status :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Everything `:actuation/place-campaign` requires before it can even
  reach a human: a committed media-plan assessment AND a committed
  resolved risk screening (ADR-0003)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-v") {:op :media-plan/verify :subject subject} operator)
  (resume! actor (str tid-prefix "-v") :approved)
  (exec-op actor (str tid-prefix "-s") {:op :risk/screen :subject subject} operator)
  (resume! actor (str tid-prefix "-s") :approved))

(defn- brief!
  "The tie-up equivalent: a committed evidence brief AND a committed
  eligible creator screening."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-b") {:op :tieup/verify :subject subject} operator)
  (resume! actor (str tid-prefix "-b") :approved)
  (exec-op actor (str tid-prefix "-cs") {:op :creator/screen :subject subject} operator)
  (resume! actor (str tid-prefix "-cs") :approved))

;; ----------------------------- the human veto -----------------------------

(deftest rejecting-a-campaign-placement-writes-nothing
  (testing "the approver's NO is the whole point of the interrupt"
    (let [[db actor] (fresh)
          _ (verify! actor "r1" "campaign-1")
          r1 (exec-op actor "r1" {:op :actuation/place-campaign :subject "campaign-1"} operator)]
      (is (= :interrupted (:status r1)))
      (let [r2 (resume! actor "r1" :rejected)]
        (is (= :hold (get-in r2 [:state :disposition])))
        (is (false? (:campaign-placed? (store/campaign db "campaign-1")))
            "a rejected placement must NOT flip the guard boolean")
        (is (empty? (store/placement-history db))
            "and must draft no record")))))

(deftest rejecting-a-creator-tieup-order-writes-nothing
  (testing "the same veto on the second actuation -- a creator must not be commissioned over an operator's objection"
    (let [[db actor] (fresh)
          _ (brief! actor "r2" "campaign-5")
          r1 (exec-op actor "r2" {:op :actuation/order-creator-tieup :subject "campaign-5"} operator)]
      (is (= :interrupted (:status r1)))
      (let [r2 (resume! actor "r2" :rejected)]
        (is (= :hold (get-in r2 [:state :disposition])))
        (is (false? (:tieup-ordered? (store/campaign db "campaign-5"))))
        (is (empty? (store/tieup-order-history db)))))))

(deftest a-rejection-is-auditable-under-its-own-basis
  (testing "a rejected act must be distinguishable in the ledger from a governor HARD hold -- 'a human said no' and 'the rules said no' are different facts about the business"
    (let [[db actor] (fresh)
          _ (verify! actor "r3" "campaign-1")
          _ (exec-op actor "r3" {:op :actuation/place-campaign :subject "campaign-1"} operator)
          _ (resume! actor "r3" :rejected)
          fact (last (store/ledger db))]
      (is (= :approval-rejected (:t fact)))
      (is (= :hold (:disposition fact)))
      (is (= [:approver-rejected] (:basis fact)))
      (is (= :actuation/place-campaign (:op fact)))
      (is (= "campaign-1" (:subject fact))))))

(deftest a-rejection-does-not-burn-the-actuation
  (testing "rejecting is not the same as forbidding. Once whatever concerned the approver is resolved, the SAME campaign must still be placeable -- the double-actuation guard keys on the act, not on the attempt."
    (let [[db actor] (fresh)]
      (verify! actor "r4" "campaign-1")
      (exec-op actor "r4a" {:op :actuation/place-campaign :subject "campaign-1"} operator)
      (resume! actor "r4a" :rejected)
      (is (empty? (store/placement-history db)))
      (testing "a second, approved attempt succeeds"
        (let [r (exec-op actor "r4b" {:op :actuation/place-campaign :subject "campaign-1"} operator)]
          (is (= :interrupted (:status r)) "not held by :already-placed -- nothing was placed")
          (resume! actor "r4b" :approved)
          (is (true? (:campaign-placed? (store/campaign db "campaign-1"))))
          (is (= 1 (count (store/placement-history db)))))))))

(deftest every-rejection-still-leaves-exactly-one-ledger-fact
  (let [[db actor] (fresh)]
    (verify! actor "r5" "campaign-1")
    (let [before (count (store/ledger db))]
      (exec-op actor "r5a" {:op :actuation/place-campaign :subject "campaign-1"} operator)
      (resume! actor "r5a" :rejected)
      (is (= (inc before) (count (store/ledger db)))
          "write-only-through-ledger holds for rejections too"))))

;; ----------------------------- the phase gate, end to end -----------------------------

(deftest phase-0-writes-nothing-through-the-actor
  (testing "a read-only deployment must be read-only in fact, not just in the phase table"
    (let [[db actor] (fresh)
          res (exec-op actor "p0" {:op :campaign/intake :subject "campaign-1"
                                   :patch {:id "campaign-1" :client-name "CHANGED"}}
                       (at-phase 0))]
      (is (= :hold (get-in res [:state :disposition])))
      (is (not= :interrupted (:status res)) "phase-disabled settles immediately, no human is asked")
      (is (= "Sato Bakery" (:client-name (store/campaign db "campaign-1")))
          "the SSoT is untouched")
      (is (= :phase-disabled (:phase-reason (last (store/ledger db))))
          "and the ledger records WHY -- a phase block, not a compliance violation"))))

(deftest phase-1-allows-intake-only
  (let [[db actor] (fresh)
        ctx (at-phase 1)]
    (testing "intake is enabled, but still needs a human at phase 1"
      (let [res (exec-op actor "p1a" {:op :campaign/intake :subject "campaign-1"
                                      :patch {:id "campaign-1" :client-name "Sato Bakery"}} ctx)]
        (is (= :interrupted (:status res)) "phase 1 has an empty :auto set")))
    (testing "verification is not yet enabled"
      (let [res (exec-op actor "p1b" {:op :media-plan/verify :subject "campaign-1"} ctx)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (= :phase-disabled (:phase-reason (last (store/ledger db)))))
        (is (nil? (store/media-plan-of db "campaign-1")))))))

(deftest phase-2-enables-both-lifecycles-screening-but-neither-actuation
  (let [[db actor] (fresh)
        ctx (at-phase 2)]
    (testing "both screening/brief ops become writable together"
      (doseq [[tid op subject] [["p2a" :media-plan/verify "campaign-1"]
                                ["p2b" :risk/screen "campaign-1"]
                                ["p2c" :creator/screen "campaign-5"]
                                ["p2d" :tieup/verify "campaign-5"]]]
        (is (= :interrupted (:status (exec-op actor tid {:op op :subject subject} ctx)))
            (str op " should be writable-with-approval at phase 2"))
        (resume! actor tid :approved)))
    ;; Both actuations are now governor-CLEAN: full evidence on file, both
    ;; ceilings satisfied, disclosure label published, neither yet actuated.
    ;; So whatever stops them below is the phase gate and nothing else --
    ;; without these approvals the governor would hard-hold on
    ;; :evidence-incomplete first and this test would prove nothing.
    (testing "neither actuation is reachable at phase 2, even with evidence on file"
      (doseq [[tid op subject] [["p2e" :actuation/place-campaign "campaign-1"]
                                ["p2f" :actuation/order-creator-tieup "campaign-5"]]]
        (let [res (exec-op actor tid {:op op :subject subject} ctx)]
          (is (= :hold (get-in res [:state :disposition])) (str op))
          (is (= :phase-disabled (:phase-reason (last (store/ledger db)))) (str op))))
      (is (empty? (store/placement-history db)))
      (is (empty? (store/tieup-order-history db))))))

(deftest a-governor-hard-hold-outranks-the-phase-gate
  (testing "compliance wins: a HARD violation is reported as itself, not masked as a phase block, even in a phase where the op is disabled anyway"
    (let [[db actor] (fresh)
          res (exec-op actor "p3" {:op :risk/screen :subject "campaign-4"} (at-phase 1))
          fact (last (store/ledger db))]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:misleading-claim-risk-unresolved} (:basis fact))
          "the unresolved risk is what the ledger must show")
      (is (nil? (:phase-reason fact))
          "a governor hold is never relabelled as :phase-disabled"))))
