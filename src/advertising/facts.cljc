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
  requirements to make coverage look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  client-brief-record/media-plan-record/creative-approval-record/
  budget-authorization-record evidence set every prior sibling's
  evidence checklist submits in some form; `:legal-basis` /
  `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any `:actuation/place-campaign` proposal can
  commit."
  {"JPN" {:name "Japan"
          :owner-authority "消費者庁 (Consumer Affairs Agency)"
          :legal-basis "不当景品類及び不当表示防止法 (Act against Unjustifiable Premiums and Misleading Representations, 景品表示法)"
          :national-spec "広告表示における優良誤認・有利誤認表示の禁止および媒体購入の適正化要件"
          :provenance "https://www.caa.go.jp/policies/policy/representation/"
          :required-evidence ["クライアントブリーフ記録 (client-brief-record)"
                              "媒体計画記録 (media-plan-record)"
                              "クリエイティブ承認記録 (creative-approval-record)"
                              "予算承認記録 (budget-authorization-record)"]}
   "USA" {:name "United States"
          :owner-authority "Federal Trade Commission (FTC)"
          :legal-basis "FTC Act Section 5 (unfair or deceptive acts or practices), 15 U.S.C. § 45 / FTC Endorsement Guides"
          :national-spec "Advertising-agency truth-in-advertising and media-buy-authorization requirements"
          :provenance "https://www.ftc.gov/business-guidance/advertising-marketing"
          :required-evidence ["Client-brief record"
                              "Media-plan record"
                              "Creative-approval record"
                              "Budget-authorization record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Advertising Standards Authority (ASA) / Committee of Advertising Practice (CAP)"
          :legal-basis "UK Code of Non-broadcast Advertising, Sales Promotion and Direct Marketing (CAP Code)"
          :national-spec "Regulated advertising-agency creative and media-placement compliance requirements"
          :provenance "https://www.asa.org.uk/codes-and-rulings/advertising-codes.html"
          :required-evidence ["Client-brief record"
                              "Media-plan record"
                              "Creative-approval record"
                              "Budget-authorization record"]}
   "DEU" {:name "Germany"
          :owner-authority "Deutscher Werberat"
          :legal-basis "Gesetz gegen den unlauteren Wettbewerb (UWG, Act Against Unfair Competition)"
          :national-spec "Anforderungen an Werbeagenturen zur lauteren Kampagnengestaltung und Mediaplatzierung"
          :provenance "https://www.werberat.de/verhaltensregeln"
          :required-evidence ["Kundenbriefingprotokoll (client-brief-record)"
                              "Medienplanprotokoll (media-plan-record)"
                              "Kreativfreigabeprotokoll (creative-approval-record)"
                              "Budgetfreigabeprotokoll (budget-authorization-record)"]}
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
      :note (str "cloud-itonami-isic-7310 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
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
