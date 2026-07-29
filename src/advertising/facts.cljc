(ns advertising.facts
  "Per-jurisdiction advertising-standards/consumer-protection
  regulatory catalog -- the G2-style spec-basis table the Campaign
  Governor checks every `:media-plan/verify` proposal against ('did
  the advisor cite an OFFICIAL public source for this jurisdiction's
  advertising-standards and misleading-representation framework, or
  did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official advertising-
  standards/consumer-protection authority (see `:provenance`); they
  are a STARTING catalog, not a from-scratch survey of all ~194
  jurisdictions. Extending coverage is additive: add one map to
  `catalog`, cite a real source, done -- never invent a jurisdiction's
  requirements to make coverage look bigger.

  ## Creator tie-up (`:disclosure`)

  Each entry carries a SECOND, independently-cited requirement block
  for the creator-tie-up lifecycle (`:tieup/verify`/`:actuation/order-
  creator-tieup`): the jurisdiction's **sponsorship-disclosure**
  framework -- the law that says a paid post by a YouTube channel or
  an influencer must be identifiable as advertising. This is a
  genuinely separate legal basis from the general advertising-
  standards one above (Japan's 2023 ステマ規制 designation, the FTC
  Endorsement Guides, the CAP Code's recognition rules, UWG § 5a
  Abs. 4), so it is cited separately rather than folded into
  `:legal-basis` -- an operator disputing a tie-up order needs the
  disclosure citation specifically.

  `:accepted-disclosure-labels` lists the disclosure wordings the
  AUTHORITY ITSELF publishes as examples in the cited source. It is
  deliberately NOT presented as an exhaustive legal whitelist -- no
  authority publishes one -- and `disclosure-acceptable?` is scoped
  accordingly: it is a floor (did the operator record a label the
  authority has itself named?), not a legal opinion that the resulting
  post is compliant. Widening the list is additive and must cite the
  same official source."
  (:require [clojure.string :as str]))

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  client-brief-record/media-plan-record/creative-approval-record/
  budget-authorization-record evidence set every prior sibling's
  evidence checklist submits in some form; `:legal-basis` /
  `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any `:actuation/place-campaign` proposal can
  commit.

  `:tieup-required-evidence` and `:disclosure` are the same two things
  for the creator-tie-up lifecycle (`:actuation/order-creator-tieup`)
  -- separately cited because sponsorship disclosure is a separate
  legal instrument in every jurisdiction seeded here."
  {"JPN" {:name "Japan"
          :owner-authority "消費者庁 (Consumer Affairs Agency)"
          :legal-basis "不当景品類及び不当表示防止法 (Act against Unjustifiable Premiums and Misleading Representations, 景品表示法)"
          :national-spec "広告表示における優良誤認・有利誤認表示の禁止および媒体購入の適正化要件"
          :provenance "https://www.caa.go.jp/policies/policy/representation/"
          :required-evidence ["クライアントブリーフ記録 (client-brief-record)"
                              "媒体計画記録 (media-plan-record)"
                              "クリエイティブ承認記録 (creative-approval-record)"
                              "予算承認記録 (budget-authorization-record)"]
          :tieup-required-evidence ["起用契約記録 (creator-engagement-record)"
                                    "開示表示記録 (disclosure-record)"
                                    "報酬承認記録 (fee-authorization-record)"
                                    "クリエイター適格性記録 (creator-eligibility-record)"]
          :disclosure
          {:owner-authority "消費者庁 (Consumer Affairs Agency)"
           :legal-basis "景品表示法第5条第3号に基づく指定告示「一般消費者が事業者の表示であることを判別することが困難である表示」(令和5年内閣府告示第19号、いわゆるステルスマーケティング規制)"
           :requirement "事業者が第三者(クリエイター/インフルエンサー)に依頼した表示は、一般消費者が事業者の表示であることを明瞭に判別できるよう表示しなければならない"
           :provenance "https://www.caa.go.jp/policies/policy/representation/"
           :accepted-disclosure-labels ["広告" "宣伝" "プロモーション" "PR"]}}
   "USA" {:name "United States"
          :owner-authority "Federal Trade Commission (FTC)"
          :legal-basis "FTC Act Section 5 (unfair or deceptive acts or practices), 15 U.S.C. § 45 / FTC Endorsement Guides"
          :national-spec "Advertising-agency truth-in-advertising and media-buy-authorization requirements"
          :provenance "https://www.ftc.gov/business-guidance/advertising-marketing"
          :required-evidence ["Client-brief record"
                              "Media-plan record"
                              "Creative-approval record"
                              "Budget-authorization record"]
          :tieup-required-evidence ["Creator-engagement record"
                                    "Disclosure record"
                                    "Fee-authorization record"
                                    "Creator-eligibility record"]
          :disclosure
          {:owner-authority "Federal Trade Commission (FTC)"
           :legal-basis "FTC Endorsement Guides, 16 CFR Part 255 (Guides Concerning the Use of Endorsements and Testimonials in Advertising)"
           :requirement "A material connection between the endorser and the advertiser must be disclosed clearly and conspicuously in the endorsement itself"
           :provenance "https://www.ftc.gov/business-guidance/resources/disclosures-101-social-media-influencers"
           :accepted-disclosure-labels ["Ad" "Advertisement" "Sponsored" "#ad" "Paid partnership"]}}
   "GBR" {:name "United Kingdom"
          :owner-authority "Advertising Standards Authority (ASA) / Committee of Advertising Practice (CAP)"
          :legal-basis "UK Code of Non-broadcast Advertising, Sales Promotion and Direct Marketing (CAP Code)"
          :national-spec "Regulated advertising-agency creative and media-placement compliance requirements"
          :provenance "https://www.asa.org.uk/codes-and-rulings/advertising-codes.html"
          :required-evidence ["Client-brief record"
                              "Media-plan record"
                              "Creative-approval record"
                              "Budget-authorization record"]
          :tieup-required-evidence ["Creator-engagement record"
                                    "Disclosure record"
                                    "Fee-authorization record"
                                    "Creator-eligibility record"]
          :disclosure
          {:owner-authority "Advertising Standards Authority (ASA) / Competition and Markets Authority (CMA)"
           :legal-basis "CAP Code section 2 (Recognition of marketing communications)"
           :requirement "Marketing communications must be obviously identifiable as such; an influencer's paid post must be labelled up front"
           :provenance "https://www.asa.org.uk/resource/influencers-guide.html"
           :accepted-disclosure-labels ["Ad" "Advert" "Advertisement" "#ad"]}}
   "DEU" {:name "Germany"
          :owner-authority "Deutscher Werberat"
          :legal-basis "Gesetz gegen den unlauteren Wettbewerb (UWG, Act Against Unfair Competition)"
          :national-spec "Anforderungen an Werbeagenturen zur lauteren Kampagnengestaltung und Mediaplatzierung"
          :provenance "https://www.werberat.de/verhaltensregeln"
          :required-evidence ["Kundenbriefingprotokoll (client-brief-record)"
                              "Medienplanprotokoll (media-plan-record)"
                              "Kreativfreigabeprotokoll (creative-approval-record)"
                              "Budgetfreigabeprotokoll (budget-authorization-record)"]
          :tieup-required-evidence ["Creator-Beauftragungsprotokoll (creator-engagement-record)"
                                    "Kennzeichnungsprotokoll (disclosure-record)"
                                    "Vergütungsfreigabeprotokoll (fee-authorization-record)"
                                    "Creator-Eignungsprotokoll (creator-eligibility-record)"]
          :disclosure
          {:owner-authority "Wettbewerbszentrale / Deutscher Werberat"
           :legal-basis "UWG § 5a Abs. 4 (Nichtkenntlichmachung des kommerziellen Zwecks einer geschäftlichen Handlung)"
           :requirement "Der kommerzielle Zweck eines beauftragten Creator-Beitrags muss kenntlich gemacht werden"
           :provenance "https://www.gesetze-im-internet.de/uwg_2004/__5a.html"
           :accepted-disclosure-labels ["Werbung" "Anzeige"]}}
   ;; Unlike GBR/DEU, where an industry self-regulatory body (ASA/CAP,
   ;; Werberat) is the primary standard-setter, China's advertising
   ;; framework is primarily STATUTORY and enforced by a regulator, so
   ;; `:owner-authority` names SAMR rather than a trade association.
   ;; 中国广告协会 (CAA) does publish self-regulatory codes, but no official
   ;; CAA source was fetched and read for this entry, so it is not cited
   ;; here -- add it when someone actually reads one.
   "CHN" {:name "People's Republic of China"
          :owner-authority "国家市场监督管理总局 (State Administration for Market Regulation, SAMR)"
          :legal-basis "中华人民共和国广告法 (Advertising Law of the PRC, 2021-04-29 第二次修正) / 互联网广告管理办法 (SAMR Decree No. 72, in force 2023-05-01)"
          :national-spec "广告可识别性 (广告法第十四条)・虚假广告禁止 (第二十八条)・广告代言人义务 (第三十八条)・发布前广告审查 (第四十六条)・互联网广告の显著标明と一键关闭 (办法第九条・第十条)"
          :provenance "https://www.samr.gov.cn/zw/zfxxgk/fdzdgknr/fgs/art/2023/art_5474cf75173c45d6a0379730fb4e8d97.html"
          :retrieved-at "2026-07-27"
          :required-evidence ["客户委托记录 (client-brief-record)"
                              "媒介投放计划记录 (media-plan-record)"
                              "广告内容审核记录 (creative-approval-record)"
                              "媒介投放授权记录 (budget-authorization-record)"]
          ;; This actor screens misleading claims and media placement. It
          ;; does NOT model China's pre-publication review gate
          ;; (广告法第四十六条's 广告批准文号 for
          ;; 医疗/药品/医疗器械/农药/兽药/保健食品/特殊医学用途配方食品) --
          ;; that is a conditional, category-dependent HARD gate with its
          ;; own validity window, which a catalog row cannot express.
          ;; `cloud-itonami-iso3166-chn-advertising` implements it.
          ;; Naming the boundary here so a CHN campaign is not mistaken
          ;; for fully screened by this actor alone.
          :out-of-scope-here [:pre-publication-ad-review]
          :out-of-scope-note "发布前广告审查 (广告批准文号) は本 actor では判定しない — cloud-itonami-iso3166-chn-advertising を参照"}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to place a
  campaign on it."
  [iso3]
  (get catalog iso3))

(defn disclosure-basis
  "The jurisdiction's SPONSORSHIP-DISCLOSURE requirement map, or nil.
  nil means this jurisdiction has no cited disclosure framework in
  this catalog, and the governor must hold any proposal that tries to
  order a creator tie-up on it -- the tie-up analog of `spec-basis`,
  and for the same reason: never invent a jurisdiction's disclosure
  rule."
  [iso3]
  (:disclosure (spec-basis iso3)))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :disclosure-covered (count (filter disclosure-basis iso3s))
      :note (str "cloud-itonami-isic-7310 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis, "
                 (count (filter disclosure-basis (keys catalog)))
                 " of them also with an official sponsorship-disclosure "
                 "basis for creator tie-ups. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `advertising.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))

(defn tieup-evidence-checklist
  "The creator-tie-up evidence checklist for `iso3` -- empty when the
  jurisdiction has no spec-basis at all."
  [iso3]
  (:tieup-required-evidence (spec-basis iso3) []))

(defn tieup-evidence-satisfied?
  "Does `submitted` satisfy every creator-tie-up evidence item listed
  for `iso3`? Missing spec-basis -> never satisfied, the same posture
  `required-evidence-satisfied?` takes."
  [iso3 submitted]
  (when-let [required (seq (tieup-evidence-checklist iso3))]
    (let [need (count required)
          have (count (filter (set submitted) required))]
      (= need have))))

(defn accepted-disclosure-labels
  "Disclosure wordings the jurisdiction's OWN authority publishes as
  examples (see this ns's docstring on why this is a floor, not an
  exhaustive whitelist). Empty when the jurisdiction has no cited
  disclosure basis."
  [iso3]
  (:accepted-disclosure-labels (disclosure-basis iso3) []))

(defn disclosure-acceptable?
  "Is `label` one of the disclosure wordings `iso3`'s own authority
  publishes? A missing disclosure basis, or a blank/absent label, is
  NEVER acceptable -- the governor holds rather than guessing whether
  an unrecorded wording would satisfy a regulator.

  The recorded label is TRIMMED before matching, and only trimmed.
  Trimming is safe because surrounding whitespace carries no meaning --
  an operator who recorded 「 PR 」 with stray spaces recorded PR, and
  holding on it would be a false hold on a label they did record. Case is
  deliberately NOT folded: a wording an authority did not publish in
  that form is not a wording it published, and case-folding across
  Japanese, German and English published examples would quietly widen
  a trust boundary this function exists to keep narrow."
  [iso3 label]
  (let [trimmed (some-> label str str/trim)]
    (boolean (and trimmed
                  (not= "" trimmed)
                  (contains? (set (accepted-disclosure-labels iso3)) trimmed)))))
