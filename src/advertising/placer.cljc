(ns advertising.placer
  "The actuation seam: the one place where a governed, human-approved
  placement decision could become a real media buy, and -- since the
  2026-08-13 live pass -- a real charge.

  Everything else in this actor decides. This namespace is the only
  thing that could ACT, and it is built so that acting is the explicitly
  chosen exception rather than the default.

  ## Why a seam and not an integration

  `advertising.registry` builds the placement RECORD an agency keeps.
  Until this seam existed the audit ledger could say
  `:campaign/mark-placed` without saying whether anything had actually
  been bought. Those are different events -- one is bookkeeping, the
  other spends a client's money -- and a ledger that cannot tell them
  apart is the same defect as a gate that reports a pass it never
  measured. Every placement commit now carries a RECEIPT that states,
  in data, which of the two happened.

  ## Live and SPENDING are two decisions, not one

  This is the part worth reading before wiring a credential in.

  A Google Ads campaign created through the API with `status: PAUSED`
  is a real campaign object on a real account and it spends **nothing**.
  Only `ENABLED` spends. So `:live` (talk to the real API with a real
  credential) and `:spend?` (create the campaign in a state that can
  charge the client) are separate flags, and `:spend? true` is required
  on top of a live placer before a single request can enable a campaign.
  A live placer that is not `:spend? true` creates the budget and the
  campaign PAUSED -- everything real except the charge -- which is what
  an operator wants for the first run against a new account.

  On top of that, `:max-spend-micros` is an independent ceiling AT THE
  MONEY BOUNDARY. The Campaign Governor already recomputes the
  campaign's own proposed spend against its own client-authorized
  budget; this is a second, unrelated number the operator sets on the
  placer, and it is checked here, in the last function before the
  network. Two ceilings from two sources, because the failure this
  guards against is a campaign record that is internally consistent and
  wrong.

  ## The modes, and why none of them is silent

  - `:dry-run` (the default, and what every caller gets unless it
    injects otherwise) -- the platform-shaped requests are BUILT and
    returned, and nothing is sent. `:sent? false`. This is not a stub:
    the requests in the receipt are the requests that would go out, so
    an operator can read them, and a diff of them is a real review
    artifact.
  - `:live` -- sends, via an injected `:http-fn`. There is no ambient
    HTTP in this namespace and no credential anywhere in this
    repository; a live placer without an `:http-fn`, or without a
    complete credential set, THROWS at construction. It does not
    quietly fall back to a dry run, because a live placer that silently
    sends nothing is worse than either honest mode -- the operator
    believes the money moved.
  - `:unsupported` -- the platform has a transcribed ad POLICY but no
    request builder here, so this actor can say whether a campaign may
    run and cannot place it. `:sent? false`, and the receipt names the
    platform. Six of the eight catalogued platforms are in this state
    today, and the receipt is how that stays visible instead of reading
    like a successful placement.

  A receipt is never absent. `advertising.operation` appends one to the
  ledger on every `:actuation/place-campaign` commit, and
  `placer_test` pins that: 'placed' cannot appear in the audit trail
  without a statement of whether anything was sent.

  ## Credentials

  None are in this repository, and none may be added to it. They are
  read from the environment by `credentials-from-env`, which returns
  either a COMPLETE set or the list of names that are missing -- never a
  partially-populated map, because a request built with three of four
  credentials fails at the API with an error an operator has to decode,
  while a missing-name list says what to go and get.

  ## What a request builder may and may not do

  A builder is a pure function of the campaign + placement record +
  credentials -> a vector of request maps. It performs no I/O and
  decides nothing: by the time it runs, the Campaign Governor has
  cleared the campaign and a human has approved it. Adding a platform
  here is deliberately NOT the same act as adding it to
  `advertising.platform` -- reading a policy is free and safe, wiring a
  buying API spends money."
  (:require [clojure.string :as str]
            [advertising.platform :as platform]))

(def google-ads-api-version
  "The Google Ads REST API version this builder targets. Pinned rather
  than tracked: an API version bump changes resource shapes, and a
  builder that silently followed 'latest' would change what it sends to
  a real account without anyone reviewing a diff."
  "v18")

(def ^:private google-ads-credential-env
  "env var -> the credential key the builder needs. All four are
  required; see `credentials-from-env` on why a partial set is refused."
  {"GOOGLE_ADS_DEVELOPER_TOKEN" :developer-token
   "GOOGLE_ADS_ACCESS_TOKEN"    :access-token
   "GOOGLE_ADS_CUSTOMER_ID"     :customer-id
   "GOOGLE_ADS_LOGIN_CUSTOMER_ID" :login-customer-id})

(defn credentials-from-env
  "Read the Google Ads credential set from the environment.

  -> `{:ok credentials}` or `{:missing [\"GOOGLE_ADS_...\" ...]}`.

  Never returns a partially-populated map. A request built from three of
  four credentials fails at the API with an error the operator has to
  decode; a list of missing names says what to go and get. The values
  themselves are never logged, never placed in a receipt, and never
  written to this repository.

  `getenv` is injectable so this is testable without mutating the
  process environment."
  ([] (credentials-from-env #?(:clj #(System/getenv %) :cljs #(aget js/process.env %))))
  ([getenv]
   (let [found (into {} (keep (fn [[env k]]
                                (let [v (getenv env)]
                                  (when (and v (not= "" (str/trim (str v))))
                                    [k (str/trim (str v))])))
                              google-ads-credential-env))
         missing (->> google-ads-credential-env
                      (remove (fn [[_ k]] (contains? found k)))
                      (map key)
                      sort
                      vec)]
     (if (seq missing) {:missing missing} {:ok found}))))

(defn- google-ads-headers [{:keys [developer-token access-token login-customer-id]}]
  {"Authorization" (str "Bearer " access-token)
   "developer-token" developer-token
   "login-customer-id" login-customer-id
   "Content-Type" "application/json"})

(def ^:private budget-resource-placeholder
  "What a DRY-RUN receipt shows where a live run would splice in the
  budget resource name returned by step 1. It is deliberately an
  obviously-unreal string: a dry-run receipt must not look like it
  already knows an id it cannot know."
  "{campaignBudgets/... from step 1}")

(defn- google-ads-steps
  "The two requests a real Google Ads campaign creation actually is: the
  budget resource, then the campaign that references it. Modelling this
  as one request (which the first version of this namespace did) is not
  a simplification, it is wrong -- `campaigns:mutate` rejects a campaign
  with an inline budget.

  `status` is PAUSED unless the caller explicitly asked to spend. See
  the ns docstring: live and spending are two decisions."
  [{:keys [campaign placement-record credentials status]}]
  (let [{:keys [customer-id]} credentials
        base (str "https://googleads.googleapis.com/" google-ads-api-version
                  "/customers/" customer-id)
        headers (google-ads-headers credentials)
        micros (* 1000000 (or (:proposed-media-spend campaign) 0))
        record-id (get placement-record "record_id")]
    [{:step :campaign-budget
      :method :post
      :url (str base "/campaignBudgets:mutate")
      :headers headers
      :body {"operations" [{"create" {"name" (str "budget " record-id)
                                      "amountMicros" (str micros)
                                      "deliveryMethod" "STANDARD"
                                      "explicitlyShared" false}}]}}
     {:step :campaign
      :method :post
      :url (str base "/campaigns:mutate")
      :headers headers
      :body {"operations" [{"create" {"name" (str (:client-name campaign) " / " (:id campaign))
                                      "status" status
                                      "campaignBudget" budget-resource-placeholder
                                      "advertisingChannelType" "SEARCH"}}]}}]))

(def request-builders
  "platform-id -> pure request builder. A platform absent from this map
  is `:unsupported`: this actor can rule on it and cannot buy it.

  Only `google-ads` is here, and `youtube-ads` shares it for the reason
  the platform catalog already records -- YouTube's own overview makes
  Google Ads its buying surface. The remaining six are policy-only on
  purpose: each one added here is a real integration with a real
  credential and a real invoice behind it, which is a decision for
  whoever operates the instance, not a gap to be closed for symmetry."
  {"google-ads" google-ads-steps
   "youtube-ads" google-ads-steps})

(defn supported?
  "Can this actor build a buy request for `platform-id`? Unknown and
  policy-only platforms are both false -- the difference between them is
  `advertising.platform`'s question, not this one."
  [platform-id]
  (contains? request-builders platform-id))

(defn dry-run-placer
  "The default. Builds the requests, sends nothing, and says so.

  Takes no credentials and needs none: the requests it builds carry the
  header NAMES a live run would set, with placeholder values, so the
  shape under review is the shape that would go out."
  ([] (dry-run-placer {}))
  ([opts]
   {:mode :dry-run
    :opts (merge {:credentials {:developer-token "{GOOGLE_ADS_DEVELOPER_TOKEN}"
                                :access-token "{GOOGLE_ADS_ACCESS_TOKEN}"
                                :customer-id "{GOOGLE_ADS_CUSTOMER_ID}"
                                :login-customer-id "{GOOGLE_ADS_LOGIN_CUSTOMER_ID}"}}
                 opts)}))

(defn live-placer
  "A placer that actually talks to the platform, through the `:http-fn`
  the caller injects.

  opts:
    :http-fn           (required) request map -> response map.
    :credentials       (required) a COMPLETE set, from
                       `credentials-from-env`. Never literals in source.
    :spend? false      when true, campaigns are created ENABLED and CAN
                       CHARGE the client. Default false -> PAUSED, which
                       is a real campaign on a real account that spends
                       nothing.
    :max-spend-micros  (required when :spend? is true) the ceiling this
                       placer will not exceed, checked at dispatch. It
                       is deliberately a SECOND number, set by the
                       operator, independent of the campaign's own
                       recorded authorized budget which the governor
                       already checks.

  Throws on a missing `:http-fn` or an incomplete credential set. That
  is the whole point: a live placer is requested explicitly, and a
  request to act that cannot act must fail loudly at construction rather
  than resolve into a dry run at dispatch, where the operator would read
  `:sent? false` on a placement they believe they authorised for real."
  [{:keys [http-fn credentials spend? max-spend-micros] :as opts}]
  (when-not (fn? http-fn)
    (throw (ex-info (str "live-placer requires an :http-fn -- this namespace holds no "
                         "credential and performs no ambient I/O. Refusing to construct "
                         "a live placer that could only ever behave as a dry run.")
                    {:opts (vec (keys opts))})))
  (let [missing (remove #(seq (str (get credentials %)))
                        [:developer-token :access-token :customer-id :login-customer-id])]
    (when (seq missing)
      (throw (ex-info (str "live-placer requires a COMPLETE credential set; missing "
                           (pr-str (vec missing)) ". Use credentials-from-env -- a request "
                           "built from a partial set fails at the API with an error the "
                           "operator has to decode.")
                      {:missing (vec missing)}))))
  (when (and spend? (not (and (number? max-spend-micros) (pos? max-spend-micros))))
    (throw (ex-info (str "a spending placer requires a positive :max-spend-micros. "
                         "Refusing to construct a placer that can charge a client with "
                         "no ceiling of its own -- the campaign's own authorized budget "
                         "is checked by the governor, and this is the second, "
                         "operator-set number that is checked at the network boundary.")
                    {:max-spend-micros max-spend-micros})))
  {:mode :live :opts opts})

(defn- ceiling-exceeded
  "The campaign's spend in micros, when it is over the placer's own
  ceiling -- else nil. Only meaningful for a spending placer; a PAUSED
  campaign charges nothing whatever its budget field says."
  [{:keys [spend? max-spend-micros]} campaign]
  (when spend?
    (let [micros (* 1000000 (or (:proposed-media-spend campaign) 0))]
      (when (> micros max-spend-micros) micros))))

(defn- splice-budget
  "Replace the placeholder budget resource in the campaign request with
  the resource name step 1 actually returned."
  [request resource-name]
  (assoc-in request [:body "operations" 0 "create" "campaignBudget"] resource-name))

(defn- budget-resource-name
  "The budget resource name in a `campaignBudgets:mutate` response, or
  nil. nil is a dispatch failure, not something to paper over with a
  guessed id."
  [response]
  (get-in response [:body "results" 0 "resourceName"]))

(defn place!
  "Dispatch one approved placement and return its RECEIPT.

  Called by `advertising.operation`'s commit node, after the Campaign
  Governor has cleared the campaign and a human has approved it -- never
  before, and never as part of deciding.

  The receipt always states `:mode`, `:sent?`, `:spend?` and
  `:platform`, and carries the built `:requests` whenever they could be
  built. A dispatch that fails is `:sent? false` with the `:error`; it is
  never reported as a send. Credential VALUES never appear in a receipt
  -- the ledger is an audit trail, not a secret store."
  [placer {:keys [campaign placement-record]}]
  (let [pid (:target-platform campaign)
        {:keys [spend? max-spend-micros]} (:opts placer)
        base {:t :placement-dispatch
              :platform pid
              :campaign-id (:id campaign)
              :placement-number (get placement-record "record_id")
              :policy-basis (:policy-basis (platform/policy-basis pid))
              :spend? (boolean spend?)}
        redact (fn [reqs] (mapv #(update % :headers dissoc "Authorization" "developer-token") reqs))]
    (if-let [build (get request-builders pid)]
      (let [status (if spend? "ENABLED" "PAUSED")
            requests (build {:campaign campaign
                             :placement-record placement-record
                             :credentials (:credentials (:opts placer))
                             :status status})]
        (if-let [over (ceiling-exceeded (:opts placer) campaign)]
          (assoc base :mode (:mode placer) :sent? false
                 :error (str "spend ceiling exceeded: campaign would spend " over
                             " micros against a placer ceiling of " max-spend-micros
                             " -- nothing was sent"))
          (case (:mode placer)
            :dry-run (assoc base :mode :dry-run :sent? false :requests (redact requests)
                            :campaign-status status
                            :note (str "built and not sent -- inject a live placer to send"
                                       (when-not spend?
                                         "; status PAUSED, which charges nothing even live")))
            :live    (try
                       (let [http (:http-fn (:opts placer))
                             budget-resp (http (first requests))
                             resource (budget-resource-name budget-resp)]
                         (if-not resource
                           (assoc base :mode :live :sent? false :requests (redact requests)
                                  :error (str "campaignBudgets:mutate returned no resourceName; "
                                              "refusing to guess a budget id for the campaign "
                                              "request. Response: " (pr-str budget-resp)))
                           (let [campaign-req (splice-budget (second requests) resource)
                                 campaign-resp (http campaign-req)]
                             (assoc base :mode :live :sent? true
                                    :campaign-status status
                                    :requests (redact [(first requests) campaign-req])
                                    :responses [budget-resp campaign-resp]))))
                       (catch #?(:clj Exception :cljs :default) e
                         (assoc base :mode :live :sent? false :requests (redact requests)
                                :error #?(:clj (.getMessage e) :cljs (str e)))))
            (assoc base :mode (:mode placer) :sent? false :requests (redact requests)
                   :error (str "unknown placer mode " (pr-str (:mode placer)))))))
      (assoc base :mode :unsupported :sent? false
             :note (str "no buy-request builder for platform " (pr-str pid)
                        " -- this actor can rule on it and cannot place it")))))
