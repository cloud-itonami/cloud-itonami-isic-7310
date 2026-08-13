(ns advertising.placer
  "The actuation seam: the one place where a governed, human-approved
  placement decision could become a real media buy.

  Everything else in this actor decides. This namespace is the only
  thing that could ACT, and it is built so that acting is the explicitly
  chosen, injected exception rather than the default.

  ## Why a seam and not an integration

  `advertising.registry` builds the placement RECORD an agency keeps.
  Until now that was the end of the line, and the audit ledger could
  therefore say `:campaign/mark-placed` without saying whether anything
  had actually been bought. Those are different events -- one is
  bookkeeping, the other spends a client's money -- and a ledger that
  cannot tell them apart is the same defect as a gate that reports a
  pass it never measured. Every placement commit now carries a RECEIPT
  that states, in data, which of the two happened.

  ## The three modes, and why none of them is silent

  - `:dry-run` (the default, and what every caller gets unless it
    injects otherwise) -- the platform-shaped request is BUILT and
    returned, and nothing is sent. `:sent? false`. This is not a stub:
    the request in the receipt is the request that would go out, so an
    operator can read it, and a diff of it is a real review artifact.
  - `:live` -- sends, via an injected `:http-fn`. There is no ambient
    HTTP in this namespace and no credential anywhere in this
    repository; a live placer without an `:http-fn` THROWS at
    construction. It does not quietly fall back to a dry run, because a
    live placer that silently sends nothing is worse than either honest
    mode -- the operator believes the money moved.
  - `:unsupported` -- the platform has a transcribed ad POLICY but no
    request builder here, so this actor can say whether a campaign may
    run and cannot place it. `:sent? false`, and the receipt names the
    platform. Seven of the eight catalogued platforms are in this state
    today, and the receipt is how that stays visible instead of reading
    like a successful placement.

  A receipt is never absent. `advertising.operation` appends one to the
  ledger on every `:actuation/place-campaign` commit, and
  `operation_test` pins that: 'placed' cannot appear in the audit trail
  without a statement of whether anything was sent.

  ## What a request builder may and may not do

  A builder is a pure function of the campaign + placement record ->
  a request map. It performs no I/O, holds no credential, and does not
  decide anything: by the time it runs, the Campaign Governor has
  cleared the campaign and a human has approved it. Adding a platform
  here is deliberately NOT the same act as adding it to
  `advertising.platform` -- reading a policy is free and safe, wiring a
  buying API spends money."
  (:require [advertising.platform :as platform]))

(defn- google-ads-request
  "The Google Ads mutate shape, built from the campaign's own recorded
  fields. Transcribed from the API's documented resource path; no
  credential appears here, and the caller's `:http-fn` supplies auth."
  [{:keys [campaign placement-record customer-id]}]
  {:method :post
   :url (str "https://googleads.googleapis.com/v18/customers/"
             (or customer-id "{customer-id}")
             "/campaigns:mutate")
   :body {"operations"
          [{"create"
            {"name" (str (:client-name campaign) " / " (:id campaign))
             "status" "PAUSED"
             "campaignBudget" {"amountMicros" (* 1000000 (or (:proposed-media-spend campaign) 0))}
             "advertisingChannelType" "SEARCH"}}]
          "agencyPlacementRecord" (get placement-record "record_id")}})

(def request-builders
  "platform-id -> pure request builder. A platform absent from this map
  is `:unsupported`: this actor can rule on it and cannot buy it.

  Only `google-ads` is here, and only because YouTube's own overview
  makes it the buying surface for two of the eight catalogued
  platforms. The remaining six are policy-only on purpose -- each one
  added here is a real integration with a real credential and a real
  invoice behind it, which is a decision for whoever operates the
  instance, not a gap to be closed for symmetry."
  {"google-ads" google-ads-request
   "youtube-ads" google-ads-request})

(defn supported?
  "Can this actor build a buy request for `platform-id`? Unknown and
  policy-only platforms are both false -- the difference between them
  is `advertising.platform`'s question, not this one."
  [platform-id]
  (contains? request-builders platform-id))

(defn dry-run-placer
  "The default. Builds the request, sends nothing, and says so.

  `opts` are merged into every build (e.g. `:customer-id`), so the
  request in a dry-run receipt is shaped like the one a live placer
  would send rather than a different, friendlier object."
  ([] (dry-run-placer {}))
  ([opts]
   {:mode :dry-run :opts opts}))

(defn live-placer
  "A placer that actually sends, through the `:http-fn` the caller
  injects. `http-fn` takes the request map and returns the response.

  Throws when `:http-fn` is missing. That is the whole point: a live
  placer is requested explicitly, and a request to act that cannot act
  must fail loudly at construction rather than resolve into a dry run
  at dispatch, where the operator would read `:sent? false` on a
  placement they believe they authorised for real."
  [{:keys [http-fn] :as opts}]
  (when-not (fn? http-fn)
    (throw (ex-info (str "live-placer requires an :http-fn -- this namespace holds no "
                         "credential and performs no ambient I/O. Refusing to construct "
                         "a live placer that could only ever behave as a dry run.")
                    {:opts (vec (keys opts))})))
  {:mode :live :opts opts})

(defn place!
  "Dispatch one approved placement and return its RECEIPT.

  Called by `advertising.operation`'s commit node, after the Campaign
  Governor has cleared the campaign and a human has approved it -- never
  before, and never as part of deciding.

  The receipt always states `:mode`, `:sent?` and `:platform`, and
  carries the built `:request` whenever one could be built. A dispatch
  that fails is `:sent? false` with the `:error` message; it is never
  reported as a send."
  [placer {:keys [campaign placement-record]}]
  (let [pid (:target-platform campaign)
        base {:t :placement-dispatch
              :platform pid
              :campaign-id (:id campaign)
              :placement-number (get placement-record "record_id")
              :policy-basis (:policy-basis (platform/policy-basis pid))}]
    (if-let [build (get request-builders pid)]
      (let [request (build (merge {:campaign campaign :placement-record placement-record}
                                  (:opts placer)))]
        (case (:mode placer)
          :dry-run (assoc base :mode :dry-run :sent? false :request request
                          :note "built and not sent -- inject a live placer to send")
          :live    (try
                     (let [response ((:http-fn (:opts placer)) request)]
                       (assoc base :mode :live :sent? true :request request
                              :response response))
                     (catch #?(:clj Exception :cljs :default) e
                       (assoc base :mode :live :sent? false :request request
                              :error #?(:clj (.getMessage e) :cljs (str e)))))
          (assoc base :mode (:mode placer) :sent? false :request request
                 :error (str "unknown placer mode " (pr-str (:mode placer))))))
      (assoc base :mode :unsupported :sent? false
             :note (str "no buy-request builder for platform " (pr-str pid)
                        " -- this actor can rule on it and cannot place it")))))
