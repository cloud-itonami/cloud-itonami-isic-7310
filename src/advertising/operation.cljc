(ns advertising.operation
  "OperationActor -- one advertising operation = one supervised actor
  run, expressed as a langgraph-clj StateGraph. The advisor (AdOps-
  LLM) is sealed into a single node (:advise); its proposal is ALWAYS
  routed through the Campaign Governor (:govern) and the rollout phase
  gate (:decide) before anything commits to the SSoT.

  Everything the actor depends on is injected, so each is a swap, not a
  rewrite:
    - the Store    (MemStore today; Datomic/kotoba-server is the next seam) - `store` arg
    - the Advisor  (mock | real LLM)                                       - :advisor opt
    - the Phase    (0->3 rollout)                                          - :phase in ctx
    - the Placer   (dry-run | live media buy)                              - :placer opt

  The Placer is the newest of the four and the only one that can spend
  money, so it is the one whose DEFAULT matters: it is a dry run. An
  instance that never injects a placer builds every buy request and
  sends none of them, and every placement commit still writes a receipt
  saying so -- see the commit node.

  One graph run = one advertising operation (intake -> advise ->
  govern -> decide -> commit | hold | approval). No unbounded inner
  loop -- each operation is auditable and checkpointed.

  Human-in-the-loop = real approval workflow:
  `interrupt-before #{:request-approval}` pauses the actor and hands the
  decision to a human operator (agency operator). The approver resumes
  with `{:approval {:status :approved}}` (or :rejected). `:actuation/
  place-campaign` ALWAYS reaches this node when the governor is clean
  -- see `advertising.phase`."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [advertising.advertisingadvisor :as advertisingadvisor]
            [advertising.governor :as governor]
            [advertising.placer :as placer]
            [advertising.phase :as phase]
            [advertising.store :as store]))

(defn- commit-fact [request context proposal]
  {:t          :committed
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :commit
   :basis      (:cites proposal)
   :summary    (:summary proposal)})

(defn- commit-record [request _context proposal]
  {:effect  (:effect proposal)
   :path    [(:subject request)]
   :value   (or (:value proposal) {})
   :payload (:value proposal)})

(defn build
  "Compiles an OperationActor graph bound to `store` (any
  `advertising.store/Store`).
  opts:
    :advisor      -- an `advertising.advertisingadvisor/Advisor` (default: mock-advisor)
    :checkpointer -- langgraph checkpointer (default: in-mem)
    :placer       -- an `advertising.placer` placer (default: dry-run).
                     The default sends nothing; a live placer is an
                     explicit injection and needs an :http-fn."
  [store & [{:keys [advisor checkpointer placer]
             :or   {advisor      (advertisingadvisor/mock-advisor)
                    checkpointer (cp/mem-checkpointer)
                    placer       (placer/dry-run-placer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}   ; injected actor-id/role/phase
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}   ; :commit | :hold | :escalate
         :record      {:default nil}
         :approval    {:default nil}
         :audit       {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      ;; AdOps-LLM inference (the contained intelligence node) -- proposal only.
      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [p (advertisingadvisor/-advise advisor store request)]
            {:proposal p :audit [(advertisingadvisor/trace request p)]})))

      ;; Campaign Governor -- independent censor (separate system than the LLM).
      (g/add-node :govern
        (fn [{:keys [request context proposal]}]
          {:verdict (governor/check request context proposal store)}))

      ;; Decide: governor disposition, then the rollout-phase gate (which can
      ;; only add caution). HARD governor violations -> HOLD (no override).
      (g/add-node :decide
        (fn [{:keys [request context proposal verdict]}]
          (let [base (phase/verdict->disposition verdict)
                ph   (:phase context phase/default-phase)
                {:keys [disposition reason]} (phase/gate ph request base)]
            (case disposition
              :hold
              {:disposition :hold
               :audit [(cond-> (governor/hold-fact request context verdict)
                         reason (assoc :phase-reason reason :phase ph))]}

              :escalate
              {:disposition :escalate
               :audit [{:t :approval-requested
                        :op (:op request) :subject (:subject request)
                        :reason (or reason
                                    (cond (:high-stakes? verdict) :actuation
                                          :else :low-confidence))
                        :phase ph
                        :confidence (:confidence verdict)}]}

              :commit
              {:disposition :commit
               :record (commit-record request context proposal)}))))

      ;; Approval handoff -- paused by interrupt-before; a human operator
      ;; resumes with :approval. Then route commit/hold.
      (g/add-node :request-approval
        (fn [{:keys [request context proposal approval verdict]}]
          (if (= :approved (:status approval))
            {:disposition :commit
             :record (assoc (commit-record request context proposal)
                            :payload (assoc (:value proposal)
                                            :approved-by (:by approval)))
             :audit [{:t :approval-granted :op (:op request)
                      :subject (:subject request) :by (:by approval)}]}
            {:disposition :hold
             :audit [(merge (governor/hold-fact request context
                                                (assoc verdict :violations
                                                       [{:rule :approver-rejected}]))
                            {:t :approval-rejected})]})))

      ;; Commit -- the ONLY node that writes the SSoT + audit ledger, and
      ;; the ONLY node that may dispatch a real placement.
      ;;
      ;; The dispatch happens HERE and nowhere earlier: by this point the
      ;; Campaign Governor has cleared the campaign and (for both actuation
      ;; ops, at every phase) a human has approved it. And it happens on
      ;; EVERY `:actuation/place-campaign` commit, unconditionally -- the
      ;; receipt is what stops `:campaign/mark-placed` from appearing in the
      ;; ledger without saying whether anything was actually bought. A
      ;; dispatch that sends nothing writes `:sent? false` and says why; it
      ;; is never omitted, because an absent receipt and a dry-run receipt
      ;; would read identically to whoever audits this later.
      (g/add-node :commit
        (fn [{:keys [request context proposal record]}]
          (let [placement (store/commit-record! store record)
                f (commit-fact request context proposal)]
            (store/append-ledger! store f)
            (if (= :actuation/place-campaign (:op request))
              (let [receipt (placer/place!
                             placer
                             {:campaign (store/campaign store (:subject request))
                              :placement-record (get placement "record")})]
                (store/append-ledger! store receipt)
                {:audit [f receipt]})
              {:audit [f]}))))

      ;; Hold -- write the rejection to the ledger; no SSoT mutation.
      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:governor-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store (assoc hf :disposition :hold)))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [disposition]}]
          (if (= :commit disposition) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
