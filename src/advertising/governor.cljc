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

  Nine checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them (you don't get to approve your way
  past a fabricated jurisdiction spec-basis, incomplete evidence, a
  media spend exceeding its own authorized budget, an unresolved
  misleading-claim risk, an ineligible creator, or a creator tie-up
  ordered with no recognized sponsorship disclosure). The
  confidence/actuation gate is SOFT: it
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
    5. Risk screening missing      -- for `:actuation/place-campaign`,
                                       a misleading-claim-risk screening
                                       must actually be ON FILE with a
                                       resolved verdict. Check 4 only
                                       fires when a record EXISTS and
                                       says `:unresolved`, so until this
                                       check was added (ADR-0003) a
                                       campaign that was never screened
                                       placed cleanly. 'We never looked'
                                       is not a safer state than 'we
                                       looked and found something' -- it
                                       is the same exposure with no
                                       record of it.
    6. Creator ineligible          -- reported by THIS proposal itself
                                       (a `:creator/screen` that just
                                       found a disqualifying issue with
                                       the YouTube channel / influencer
                                       the campaign wants to commission
                                       -- e.g. a prior undisclosed-
                                       endorsement finding), or already
                                       on file for the campaign
                                       (`:creator/screen`/`:actuation/
                                       order-creator-tieup`). Evaluated
                                       UNCONDITIONALLY, the same
                                       discipline as check 4 above, so
                                       the screening op itself can HARD-
                                       hold on its own finding.
    7. Creator tie-up evidence
       incomplete                     -- for `:actuation/order-creator-
                                       tieup`, has the tie-up actually
                                       been assessed with a full
                                       creator-engagement-record/
                                       disclosure-record/fee-
                                       authorization-record/creator-
                                       eligibility-record checklist on
                                       file (`advertising.facts/tieup-
                                       evidence-satisfied?`)? The
                                       tie-up analog of check 2, against
                                       its OWN separately-cited evidence
                                       set.
    8. Sponsorship disclosure
       missing                        -- for `:actuation/order-creator-
                                       tieup`, INDEPENDENTLY recompute
                                       whether the campaign has recorded
                                       a sponsorship-disclosure label
                                       that the jurisdiction's OWN
                                       authority publishes (`advertising.
                                       registry/disclosure-label-
                                       missing?` + `advertising.facts/
                                       disclosure-acceptable?`) -- needs
                                       no proposal inspection at all.
                                       A genuinely NEW concept in this
                                       fleet, grounded in the cited
                                       disclosure frameworks
                                       (Japan's 2023 ステマ規制
                                       designation under 景表法5条3号,
                                       the FTC Endorsement Guides at
                                       16 CFR Part 255, CAP Code
                                       section 2, UWG § 5a Abs. 4) --
                                       the ONE thing that separates a
                                       lawful creator tie-up from an
                                       unlawful stealth-marketing post,
                                       and the one an LLM has no way to
                                       verify for itself.
    9. Creator screening missing   -- the tie-up half of check 5: for
                                       `:actuation/order-creator-tieup`,
                                       an eligible creator screening
                                       must be ON FILE. Closed at the
                                       same time as check 5 so the two
                                       lifecycles stay consistent, which
                                       is what ADR-0002 asked for.
   10. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/place-
                                       campaign` or `:actuation/order-
                                       creator-tieup` (REAL client- and
                                       creator-facing acts) -> escalate.

  Two more guards, double-actuation prevention for EACH actuation, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-placed-violations`
  refuses to place the SAME campaign twice off a dedicated `:campaign-
  placed?` fact, and `already-ordered-violations` refuses to order the
  SAME campaign's creator tie-up twice off a dedicated `:tieup-
  ordered?` fact (never a `:status` value, for either) -- the SAME
  'check a dedicated boolean, not status' discipline every prior
  sibling governor's guards establish, informed by `cloud-itonami-isic-
  6492`'s status-lifecycle bug (ADR-2607071320). The two guards are
  kept independent on purpose: placing a campaign is not ordering its
  tie-up, and a campaign may legitimately do one without the other."
  (:require [advertising.facts :as facts]
            [advertising.registry :as registry]
            [advertising.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean. This
  actor performs TWO real-world actuation events, and both are members:

    :actuation/place-campaign      -- placing/publishing a campaign on
                                      the client's behalf
    :actuation/order-creator-tieup -- commissioning a paid post from a
                                      named YouTube channel /
                                      influencer on the client's behalf

  grounded directly in this blueprint's own README ('No automated
  proposal, by itself, can complete the following without governor
  approval and audit evidence: placing/publishing a campaign on the
  client's behalf; ordering a creator tie-up on the client's behalf').
  The second is not a lesser act than the first: it commits the client
  to a named third party under that party's own audience, and it is
  the act a regulator prosecutes when the resulting post is not
  disclosed as advertising."
  #{:actuation/place-campaign :actuation/order-creator-tieup})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:media-plan/verify`/`:tieup/verify` (or either actuation)
  proposal with no spec-basis citation is a HARD violation -- never
  invent a jurisdiction's advertising-standards or sponsorship-
  disclosure requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:media-plan/verify :actuation/place-campaign
                     :tieup/verify :actuation/order-creator-tieup} op)
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

(defn- risk-screen-missing-violations
  "For `:actuation/place-campaign`, a misleading-claim-risk screening
  must actually be ON FILE with a resolved verdict.

  Closes the R0 boundary ADR-0002 recorded: until this check existed,
  `misleading-claim-risk-unresolved-violations` only fired when a
  screening record existed AND said `:unresolved`, so a campaign that
  was NEVER screened placed cleanly. 'We never looked' is not a safer
  state than 'we looked and found something' -- it is the same
  exposure with no record of it, which is worse.

  `:unresolved` is deliberately excluded from the trigger set: the
  more specific rule above already reports it, and two basis entries
  for one underlying fact makes the ledger harder to read."
  [{:keys [op subject]} st]
  (when (= op :actuation/place-campaign)
    (let [verdict (:verdict (store/risk-screen-of st subject))]
      (when-not (contains? #{:resolved :unresolved} verdict)
        [{:rule :risk-screen-missing
          :detail (str subject " に誤認表示リスクスクリーニングの完了記録が無い"
                      (when verdict (str " (verdict=" verdict ")")))}]))))

(defn- creator-screen-missing-violations
  "For `:actuation/order-creator-tieup`, a creator-eligibility
  screening must actually be ON FILE with an eligible verdict -- the
  tie-up half of the same R0 boundary, closed at the same time so the
  two lifecycles stay consistent (ADR-0002 called for exactly that:
  'would be worth applying to both lifecycles at once')."
  [{:keys [op subject]} st]
  (when (= op :actuation/order-creator-tieup)
    (let [verdict (:verdict (store/creator-screen-of st subject))]
      (when-not (contains? #{:eligible :ineligible} verdict)
        [{:rule :creator-screen-missing
          :detail (str subject " にクリエイター適格性スクリーニングの適格判定記録が無い"
                      (when verdict (str " (verdict=" verdict ")")))}]))))

(defn- already-placed-violations
  "For `:actuation/place-campaign`, refuses to place the SAME campaign
  twice, off a dedicated `:campaign-placed?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/place-campaign)
    (when (store/campaign-already-placed? st subject)
      [{:rule :already-placed
        :detail (str subject " は既にキャンペーン出稿済み")}])))

;; -------------------- creator tie-up (YouTube / influencer) --------------------

(defn- creator-ineligible-violations
  "A creator (YouTube channel / influencer) carrying an unresolved
  eligibility issue -- reported by THIS proposal (e.g. a `:creator/
  screen` that itself just found one), or already on file in the store
  for the campaign (`:creator/screen`/`:actuation/order-creator-
  tieup`) -- is a HARD, un-overridable hold. Evaluated UNCONDITIONALLY
  (not scoped to a specific op) so the screening op itself can HARD-
  hold on its own finding, exactly as
  `misleading-claim-risk-unresolved-violations` does above."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :ineligible (get-in proposal [:value :verdict]))
        campaign-id (when (contains? #{:creator/screen :actuation/order-creator-tieup} op) subject)
        hit-on-file? (and campaign-id (= :ineligible (:verdict (store/creator-screen-of st campaign-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :creator-ineligible
        :detail "適格性に未解決の問題があるクリエイターへのタイアップ発注提案は進められない"}])))

(defn- tieup-evidence-incomplete-violations
  "For `:actuation/order-creator-tieup`, the jurisdiction's required
  creator-engagement-record/disclosure-record/fee-authorization-record/
  creator-eligibility-record evidence must actually be satisfied -- the
  tie-up analog of `evidence-incomplete-violations`, against its own
  separately-cited checklist."
  [{:keys [op subject]} st]
  (when (= op :actuation/order-creator-tieup)
    (let [c (store/campaign st subject)
          brief (store/tieup-brief-of st subject)]
      (when-not (and brief
                     (facts/tieup-evidence-satisfied?
                      (:jurisdiction c) (:checklist brief)))
        [{:rule :tieup-evidence-incomplete
          :detail "法域の必要書類(起用契約記録/開示表示記録/報酬承認記録/クリエイター適格性記録等)が充足していない状態での発注提案"}]))))

(defn- tieup-fee-exceeds-authorized-budget-violations
  "For `:actuation/order-creator-tieup`, INDEPENDENTLY recompute
  whether the campaign's own media spend PLUS its own creator tie-up
  fee exceeds its own recorded authorized budget (`advertising.
  registry/creator-tieup-fee-exceeds-authorized-budget?`) -- needs no
  proposal inspection at all, since its inputs are permanent ground-
  truth fields already on the campaign. The tie-up fee is spent ON TOP
  of the media plan, so a fee that looks affordable on its own can
  still blow the client's authorization."
  [{:keys [op subject]} st]
  (when (= op :actuation/order-creator-tieup)
    (let [c (store/campaign st subject)]
      (when (registry/creator-tieup-fee-exceeds-authorized-budget? c)
        [{:rule :creator-tieup-fee-exceeds-authorized-budget
          :detail (str subject " の媒体費(" (:proposed-media-spend c)
                      ")とタイアップ報酬(" (:creator-tieup-fee c)
                      ")の合計が承認予算(" (:authorized-budget c) ")を超過")}]))))

(defn- sponsorship-disclosure-missing-violations
  "For `:actuation/order-creator-tieup`, INDEPENDENTLY recompute
  whether the campaign has recorded a sponsorship-disclosure label
  that the jurisdiction's OWN authority publishes. Nothing recorded,
  and something recorded that the authority does not publish, are the
  SAME HARD hold -- in both cases the agency cannot show a regulator
  the post was identifiable as advertising, which is the whole point
  of Japan's ステマ規制 designation, the FTC Endorsement Guides, CAP
  Code section 2 and UWG § 5a Abs. 4.

  This is the check an LLM structurally cannot stand in for: it has no
  way to know which wordings a given authority has actually published,
  and a plausible-sounding invented one (「タイアップ」, 「提供」) is
  exactly what produces an undisclosed-advertising finding."
  [{:keys [op subject]} st]
  (when (= op :actuation/order-creator-tieup)
    (let [c (store/campaign st subject)]
      (cond
        (registry/disclosure-label-missing? c)
        [{:rule :sponsorship-disclosure-missing
          :detail (str subject " にスポンサーシップ開示表示(「広告」「PR」等)が記録されていない")}]

        (not (facts/disclosure-acceptable? (:jurisdiction c) (:disclosure-label c)))
        [{:rule :sponsorship-disclosure-missing
          :detail (str subject " の開示表示「" (:disclosure-label c)
                      "」は " (:jurisdiction c)
                      " の当局が公表する表示例に含まれない")}]))))

(defn- already-ordered-violations
  "For `:actuation/order-creator-tieup`, refuses to order the SAME
  campaign's creator tie-up twice, off a dedicated `:tieup-ordered?`
  fact (never a `:status` value, and never the placement guard's
  `:campaign-placed?` -- the two actuations are independent)."
  [{:keys [op subject]} st]
  (when (= op :actuation/order-creator-tieup)
    (when (store/tieup-already-ordered? st subject)
      [{:rule :already-ordered
        :detail (str subject " は既にクリエイタータイアップ発注済み")}])))

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
                           (risk-screen-missing-violations request st)
                           (already-placed-violations request st)
                           (creator-ineligible-violations request proposal st)
                           (creator-screen-missing-violations request st)
                           (tieup-evidence-incomplete-violations request st)
                           (tieup-fee-exceeds-authorized-budget-violations request st)
                           (sponsorship-disclosure-missing-violations request st)
                           (already-ordered-violations request st)))
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
