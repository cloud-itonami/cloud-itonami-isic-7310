(ns advertising.governor
  "Campaign Governor -- the independent compliance layer that earns
  the AdOps-LLM the right to commit. The LLM has no notion of
  advertising-standards/consumer-protection law, whether a campaign's
  own proposed media spend actually stays within its own recorded
  authorized budget, whether a misleading-claim risk against a
  campaign has actually stayed unresolved, or when an act stops being
  a draft and becomes a real-world campaign placement on the client's
  behalf, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD -- the advertising analog of `cloud-
  itonami-isic-6512`'s CasualtyGovernor.

  Four checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them (you don't get to approve your way
  past a fabricated jurisdiction spec-basis, incomplete evidence, a
  media spend exceeding its own authorized budget, or an unresolved
  misleading-claim risk). The confidence/actuation gate is SOFT: it
  asks a human to look (low confidence / actuation), and the human may
  approve -- but see `advertising.phase`: for `:stake :actuation/
  place-campaign` (a real client-facing act) NO phase ever allows
  auto-commit either. Two independent layers agree that actuation is
  always a human call.

    1. Spec-basis                  -- did the media-plan proposal cite
                                       an OFFICIAL source (`advertising.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/place-campaign`,
                                       has the campaign actually been
                                       assessed with a full client-
                                       brief-record/media-plan-record/
                                       creative-approval-record/
                                       budget-authorization-record
                                       evidence checklist on file?
    3. Media spend exceeds
       authorized budget              -- for `:actuation/place-
                                       campaign`, INDEPENDENTLY
                                       recompute whether the
                                       campaign's own proposed media
                                       spend exceeds its own recorded
                                       authorized budget (`advertising.
                                       registry/media-spend-exceeds-
                                       authorized-budget?`) -- needs no
                                       proposal inspection at all. The
                                       SEVENTH instance of this fleet's
                                       MAXIMUM-ceiling check family
                                       (`facility.governor/occupancy-
                                       exceeds-capacity-violations`/
                                       `school.governor/class-size-
                                       exceeds-maximum-violations`/
                                       `card.governor/settlement-
                                       amount-exceeds-authorized-
                                       violations`/`recovery.governor/
                                       contamination-percentage-
                                       exceeds-maximum-violations`/
                                       `care.governor/caregiver-
                                       workload-exceeds-maximum-
                                       violations`/`navigator.governor/
                                       eligibility-window-elapsed-
                                       exceeds-validity-violations`
                                       established the first six).
    4. Misleading-claim risk
       unresolved                     -- reported by THIS proposal
                                       itself (a `:risk/screen` that
                                       just found one), or already on
                                       file for the campaign (`:risk/
                                       screen`/`:actuation/place-
                                       campaign`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/...
                                       (thirty-seven prior siblings,
                                       most recently `banking.
                                       governor/sanctions-violations`)
                                       ...established -- the THIRTY-
                                       EIGHTH distinct application of
                                       this exact discipline overall,
                                       and a genuinely NEW concept
                                       (grep-verified absent from
                                       every prior sibling's check
                                       names before this claim was
                                       finalized), grounded directly in
                                       this blueprint's own Trust
                                       Control 'a fabricated media-buy
                                       or misleading-claim risk forces
                                       a hold, not an override'.
                                       Exercised in tests/demo via
                                       `:risk/screen` DIRECTLY, not via
                                       the actuation op against an
                                       unscreened campaign -- see this
                                       ns's own test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/place-
                                       campaign` (a REAL client-facing
                                       act) -> escalate.

  One more guard, double-placement prevention, is enforced but NOT
  listed as a numbered HARD check above because it needs no upstream
  comparison at all -- `already-placed-violations` refuses to place a
  campaign for the SAME campaign twice, off a dedicated `:campaign-
  placed?` fact (never a `:status` value) -- the SAME 'check a
  dedicated boolean, not status' discipline every prior sibling
  governor's guards establish, informed by `cloud-itonami-isic-6492`'s
  status-lifecycle bug (ADR-2607071320)."
  (:require [advertising.facts :as facts]
            [advertising.registry :as registry]
            [advertising.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Placing a real campaign on the client's behalf is the ONE real-world
  actuation event this actor performs -- a single-member set, matching
  `leasing`'s/`underwriting`'s/`testlab`'s/`clinic`'s/`veterinary`'s/
  `funeral`'s/`parksafety`'s/`salon`'s/`entertainment`'s/`facility`'s/
  `consulting`'s single-actuation shape, grounded directly in this
  blueprint's own README ('No automated proposal, by itself, can
  complete the following without governor approval and audit
  evidence: placing/publishing a campaign on the client's behalf')."
  #{:actuation/place-campaign})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:media-plan/verify` (or `:actuation/place-campaign`) proposal
  with no spec-basis citation is a HARD violation -- never invent a
  jurisdiction's advertising-standards requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:media-plan/verify :actuation/place-campaign} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は広告表示基準として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/place-campaign`, the jurisdiction's required
  client-brief-record/media-plan-record/creative-approval-record/
  budget-authorization-record evidence must actually be satisfied --
  do not trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (= op :actuation/place-campaign)
    (let [c (store/campaign st subject)
          plan (store/media-plan-of st subject)]
      (when-not (and plan
                     (facts/required-evidence-satisfied?
                      (:jurisdiction c) (:checklist plan)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(クライアントブリーフ記録/媒体計画記録/クリエイティブ承認記録/予算承認記録等)が充足していない状態での提案"}]))))

(defn- media-spend-exceeds-authorized-budget-violations
  "For `:actuation/place-campaign`, INDEPENDENTLY recompute whether the
  campaign's own proposed media spend exceeds its own recorded
  authorized budget via `advertising.registry/media-spend-exceeds-
  authorized-budget?` -- needs no proposal inspection at all, since
  its inputs are permanent ground-truth fields already on the
  campaign."
  [{:keys [op subject]} st]
  (when (= op :actuation/place-campaign)
    (let [c (store/campaign st subject)]
      (when (registry/media-spend-exceeds-authorized-budget? c)
        [{:rule :media-spend-exceeds-authorized-budget
          :detail (str subject " の提案媒体費(" (:proposed-media-spend c)
                      ")が承認予算(" (:authorized-budget c) ")を超過")}]))))

(defn- misleading-claim-risk-unresolved-violations
  "An unresolved misleading-claim risk -- reported by THIS proposal
  (e.g. a `:risk/screen` that itself just found one), or already on
  file in the store for the campaign (`:risk/screen`/`:actuation/
  place-campaign`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        campaign-id (when (contains? #{:risk/screen :actuation/place-campaign} op) subject)
        hit-on-file? (and campaign-id (= :unresolved (:verdict (store/risk-screen-of st campaign-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :misleading-claim-risk-unresolved
        :detail "未解決の誤認表示リスクがあるキャンペーンの出稿提案は進められない"}])))

(defn- already-placed-violations
  "For `:actuation/place-campaign`, refuses to place the SAME campaign
  twice, off a dedicated `:campaign-placed?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/place-campaign)
    (when (store/campaign-already-placed? st subject)
      [{:rule :already-placed
        :detail (str subject " は既にキャンペーン出稿済み")}])))

(defn check
  "Censors an AdOps-LLM proposal against the governor rules. Returns
  {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (media-spend-exceeds-authorized-budget-violations request st)
                           (misleading-claim-risk-unresolved-violations request proposal st)
                           (already-placed-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
