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

  `creator-tieup-fee-exceeds-authorized-budget?` is the EIGHTH
  instance of that same family, and the one that makes the family's
  shape earn its keep: a creator tie-up fee is not spent INSTEAD of
  the media plan, it is spent ON TOP of it, so the ceiling compares
  the campaign's own `:proposed-media-spend` PLUS its own
  `:creator-tieup-fee` against the same recorded `:authorized-budget`.
  A tie-up fee that individually looks affordable but pushes the
  campaign's combined spend past the client's authorization is exactly
  the failure an agency gets sued over, and it is invisible to any
  check that looks at the fee alone.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real campaign-management/media-buying system, and NO
  call to any creator platform (YouTube/Instagram/TikTok/X) either. It
  builds the RECORD an advertising agency would keep, not the act of
  placing the campaign or ordering the tie-up itself (those are
  `advertising.operation`'s `:actuation/place-campaign` and
  `:actuation/order-creator-tieup`, both always human-gated -- see
  README `Actuation`)."
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

(defn- uncomputable-amounts?
  "Does `campaign` record any of `ks` as a non-nil NON-number?

  nil means 'not recorded', which every ceiling in this family already
  has a documented answer for. A non-nil non-number is different: it is
  corrupt data, and a ceiling check that cannot be computed must not
  report the campaign clean. Coercing such a value away (to 0, or by
  failing a `number?` guard into `false`) makes the ceiling MORE
  lenient -- the one direction a ceiling check must never fail in,
  because the result is a real-world act committed against a limit
  nobody actually verified.

  So the family answers `true` (hold) on corrupt input. The governor
  reports the ceiling rule with the offending value in its detail
  string, which is what an operator needs in order to go fix the
  record."
  [campaign ks]
  (boolean (some #(let [v (get campaign %)]
                    (and (some? v) (not (number? v))))
                 ks)))

(defn media-spend-exceeds-authorized-budget?
  "Does `campaign`'s own `:proposed-media-spend` exceed its own
  recorded `:authorized-budget`? A pure ground-truth check against the
  campaign's own permanent fields -- no upstream comparison needed.
  The SEVENTH instance of this fleet's MAXIMUM-ceiling check family
  (see ns docstring)."
  [{:keys [proposed-media-spend authorized-budget] :as campaign}]
  (or (uncomputable-amounts? campaign [:proposed-media-spend :authorized-budget])
      (and (number? proposed-media-spend) (number? authorized-budget)
           (> proposed-media-spend authorized-budget))))

(defn creator-tieup-fee-exceeds-authorized-budget?
  "Does `campaign`'s own `:proposed-media-spend` PLUS its own
  `:creator-tieup-fee` exceed its own recorded `:authorized-budget`? A
  pure ground-truth check against the campaign's own permanent fields
  -- no upstream comparison needed. The EIGHTH instance of this
  fleet's MAXIMUM-ceiling check family, and the first in it to sum two
  of the subject's own committed amounts before the comparison (see ns
  docstring): a tie-up fee is spent ON TOP of the media plan, so
  checking the fee alone against the budget would clear a combined
  spend the client never authorized.

  A campaign with no `:creator-tieup-fee` recorded cannot exceed
  anything -- this returns false rather than guessing a fee. A campaign
  that records one as a NON-number is a different case entirely, and
  answers true -- see `uncomputable-amounts?`."
  [{:keys [proposed-media-spend creator-tieup-fee authorized-budget] :as campaign}]
  (or (uncomputable-amounts? campaign [:proposed-media-spend :creator-tieup-fee :authorized-budget])
      (and (number? creator-tieup-fee) (number? authorized-budget)
           (> (+ (if (number? proposed-media-spend) proposed-media-spend 0)
                 creator-tieup-fee)
              authorized-budget))))

(defn disclosure-label-missing?
  "Has `campaign` failed to record ANY sponsorship-disclosure label for
  its creator tie-up? Pure presence check on the campaign's own field
  -- whether the recorded label is one the jurisdiction's authority
  actually publishes is `advertising.facts/disclosure-acceptable?`'s
  question, deliberately kept separate so the governor can report
  'nothing recorded' and 'recorded something unrecognized' as the same
  HARD hold without this namespace needing the catalog."
  [{:keys [disclosure-label]}]
  (or (nil? disclosure-label)
      (= "" (str/trim (str disclosure-label)))))

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

(defn register-creator-tieup-order
  "Validate + construct the CREATOR-TIE-UP ORDER registration DRAFT --
  the agency's own act of commissioning a paid post from a named
  YouTube channel / influencer on the client's behalf. Pure function
  -- it does NOT call YouTube, Instagram, TikTok or X, and does not
  send anything to the creator; it builds the RECORD an agency would
  keep of having placed that order.

  `advertising.governor` independently re-verifies the creator's
  eligibility screening, the campaign's combined-spend ceiling and the
  recorded sponsorship-disclosure label, and blocks a double order for
  the same campaign, before this is ever allowed to commit.

  `platform` and `creator-handle` are recorded verbatim rather than
  validated against any platform's real account namespace -- this
  actor has no integration to check them with, and inventing a
  validity rule for a handle would be exactly the fabrication
  `advertising.facts` refuses to do for jurisdictions."
  [campaign-id jurisdiction platform creator-handle sequence]
  (when-not (and campaign-id (not= campaign-id ""))
    (throw (ex-info "creator-tieup-order: campaign_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "creator-tieup-order: jurisdiction required" {})))
  (when-not (and platform (not= (str platform) ""))
    (throw (ex-info "creator-tieup-order: platform required" {})))
  (when-not (and creator-handle (not= (str/trim (str creator-handle)) ""))
    (throw (ex-info "creator-tieup-order: creator_handle required" {})))
  (when (< sequence 0)
    (throw (ex-info "creator-tieup-order: sequence must be >= 0" {})))
  (let [order-number (str (str/upper-case jurisdiction) "-TIE-" (zero-pad sequence 6))
        record {"record_id" order-number
                "kind" "creator-tieup-order-draft"
                "campaign_id" campaign-id
                "jurisdiction" jurisdiction
                "platform" (name platform)
                "creator_handle" creator-handle
                "immutable" true}]
    {"record" record "order_number" order-number
     "certificate" (unsigned-certificate "CreatorTieupOrder" order-number order-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
