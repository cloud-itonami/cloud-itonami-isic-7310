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
                              "Budgetfreigabeprotokoll (budget-authorization-record)"]}})

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
