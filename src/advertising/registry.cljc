(ns advertising.registry
  "Pure-function campaign-placement record construction -- an append-
  only advertising-agency book-of-record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a campaign-placement
  reference number -- every media network/jurisdiction assigns its
  own reference format. This namespace does NOT invent one; it builds
  a jurisdiction-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline
  `advertising.facts` uses.

  `media-spend-exceeds-authorized-budget?` is the SEVENTH instance of
  this fleet's MAXIMUM-ceiling check family (`facility.registry/
  occupancy-exceeds-capacity?` established the first, `school.
  registry/class-size-exceeds-maximum?` the second, `card.registry/
  settlement-amount-exceeds-authorized?` the third, `recovery.
  registry/contamination-percentage-exceeds-maximum?` the fourth,
  `care.registry/caregiver-workload-exceeds-maximum?` the fifth,
  `navigator.registry/eligibility-window-elapsed-exceeds-validity?`
  the sixth), applying the SAME ceiling-only comparison to a
  campaign's own proposed media spend against its own recorded
  client-authorized budget -- a direct, natural mapping onto real ad-
  agency media-buying practice, closely analogous to `card.registry/
  settlement-amount-exceeds-authorized?`'s own settlement/authorized-
  amount shape (both compare a proposed spend/settlement against a
  client's own recorded authorization ceiling).

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real campaign-management/media-buying system. It builds
  the RECORD an advertising agency would keep, not the act of placing
  the campaign itself (that is `advertising.operation`'s `:actuation/
  place-campaign`, always human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the agency's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn media-spend-exceeds-authorized-budget?
  "Does `campaign`'s own `:proposed-media-spend` exceed its own
  recorded `:authorized-budget`? A pure ground-truth check against the
  campaign's own permanent fields -- no upstream comparison needed.
  The SEVENTH instance of this fleet's MAXIMUM-ceiling check family
  (see ns docstring)."
  [{:keys [proposed-media-spend authorized-budget]}]
  (and (number? proposed-media-spend) (number? authorized-budget)
       (> proposed-media-spend authorized-budget)))

(defn register-campaign-placement
  "Validate + construct the CAMPAIGN-PLACEMENT registration DRAFT --
  the agency's own act of placing/publishing a real campaign on the
  client's behalf. Pure function -- does not touch any real media-
  buying system; it builds the RECORD an agency would keep.
  `advertising.governor` independently re-verifies the campaign's own
  authorized-budget ceiling and misleading-claim-risk resolution
  status, and blocks a double-placement for the same campaign, before
  this is ever allowed to commit."
  [campaign-id jurisdiction sequence]
  (when-not (and campaign-id (not= campaign-id ""))
    (throw (ex-info "campaign-placement: campaign_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "campaign-placement: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "campaign-placement: sequence must be >= 0" {})))
  (let [placement-number (str (str/upper-case jurisdiction) "-PLC-" (zero-pad sequence 6))
        record {"record_id" placement-number
                "kind" "campaign-placement-draft"
                "campaign_id" campaign-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "placement_number" placement-number
     "certificate" (unsigned-certificate "CampaignPlacement" placement-number placement-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
