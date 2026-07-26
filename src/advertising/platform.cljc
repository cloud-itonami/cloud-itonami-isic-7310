(ns advertising.platform
  "Per-media-platform ad-policy catalog -- the platform-side twin of
  `advertising.facts`.

  `advertising.facts` answers 'what does this JURISDICTION's
  advertising-standards law require?'. This namespace answers 'what
  does this MEDIA PLATFORM's own published ad policy allow?'. Both are
  needed before a campaign is placed: a campaign can be perfectly
  lawful under 景表法/the FTC Act and still be rejected (or, worse,
  silently mis-served) because the platform it is bought on
  categorically disallows the ad category, or because the surface it
  renders on is a GENERATIVE one where an ad that mimics the product
  UI is indistinguishable from the assistant's own answer.

  Same honest-coverage discipline as `advertising.facts`, for the same
  reason: a platform not in this table has NO policy-basis, full stop
  -- the advisor must not fabricate one, and `advertising.governor`
  HARD-holds any placement that targets it. Adding a platform is
  additive and cheap: READ that platform's own published ad policy,
  transcribe its category taxonomy, cite the URL and the version/date
  you actually read, done. NEVER seed a platform from memory, from a
  search-result summary, or from a competitor's taxonomy -- an
  invented `:prohibited-categories` set is worse than an absent one,
  because an absent one holds and an invented one waves a campaign
  through.

  ## The four seeded platforms disagree, on purpose

  Every entry here was transcribed from a direct read of the
  platform's own published policy index (see each entry's
  `:provenance` and `:read-on`). They do NOT agree with each other,
  and that is the whole argument for modelling platforms separately
  rather than keeping one 'advertising rules' table:

  | category                | chatgpt-ads   | google-ads | meta-ads   | microsoft-advertising |
  |-------------------------|---------------|------------|------------|-----------------------|
  | `:travel-experiences`   | permitted     | permitted  | permitted  | RESTRICTED            |
  | `:legal-services`       | PROHIBITED    | permitted  | permitted  | restricted            |
  | `:political`            | prohibited    | RESTRICTED | restricted | PROHIBITED            |
  | `:beauty-cosmetics`     | not-permitted | permitted  | restricted | restricted            |

  A single 'is this ad OK?' predicate cannot be right for all four.
  The campaign's `:ad-category` is drawn from one shared
  `category-vocabulary` so the SAME declared category resolves
  differently per platform, which is exactly the fact an agency needs
  surfaced before it buys. `cross-platform-disposition` answers it
  directly.

  ## Why a GENERATIVE surface needs its own gate

  On a banner/search surface, 'is this an ad?' is answered by layout:
  the ad sits in a slot the user has learned to read as an ad. On a
  generative surface the assistant's own prose is the primary content,
  so an ad styled like that prose reads as the assistant's answer.
  ChatGPT's own ad policy makes this an explicit, standalone
  prohibition (`インターフェースの模倣` / interface mimicry: 広告は
  ChatGPT の製品体験とは明確に区別できるものでなければならない --
  ads must be clearly distinguishable from the product experience, and
  ads that mimic the look, function or presentation of ChatGPT or any
  other OpenAI interface may be removed or required to change). It is
  the same consumer-protection principle the FTC's native-advertising
  guidance states generally (an ad should be identifiable as an ad),
  which is why `:generative-surface?` is a per-platform FACT here and
  `:distinguishable-from-product-ui` a required attestation there.
  None of the other three seeded platforms is a generative surface,
  and none of them carries that attestation -- the requirement follows
  the surface, not the fleet."
  (:require [clojure.set :as set]))

(def category-vocabulary
  "The shared, closed vocabulary a campaign's `:ad-category` is drawn
  from. Every category named by ANY catalog entry must be a member
  (`platform_test.clj` pins this), so that one declared category
  resolves consistently -- and, where the platforms disagree,
  DIFFERENTLY -- across platforms.

  These are business categories: what the campaign advertises. Ad-
  quality and site-quality failures that some platforms list alongside
  their category taxonomy (Microsoft's 'Inaccessible site',
  'Non-Indexed Sites', 'Unmoderated User-Generated Content'; Google's
  editorial and technical requirements; Meta's profanity and privacy
  rules) are deliberately NOT categories -- they are properties of a
  specific creative or landing page, checked at creative review, not
  things a campaign is FOR."
  #{;; consumer verticals
    :lifestyle-household :local-services :travel-experiences
    :digital-products-education :food-products :ticket-reselling
    ;; money
    :financial-services :insurance :cryptocurrency :fundraising :pricing
    ;; health & body
    :healthcare-medical :health-claims :addiction-treatment
    :beauty-cosmetics :tattoos-piercings
    ;; professional & institutional
    :legal-services :government-services :religious-content
    :psychic-services :people-finder :software-download
    ;; age-gated
    :adult-sexual :nudity-suggestive :dating :alcohol :tobacco
    :recreational-drugs :drug-paraphernalia :gambling :online-gaming
    :entertainment-age-rated
    ;; civic
    :political :social-issues :sensitive-events
    ;; rights
    :counterfeit :copyrighted-content :trademark :piracy
    ;; harm & deception
    :fraud-deception :malware-phishing :hacking-services
    :traffic-inflation :forged-documents :misinformation
    :vaccine-discouragement
    ;; dangerous goods
    :weapons :explosives :surveillance-equipment
    ;; violence & exploitation
    :explicit-violence :hate-harassment :human-exploitation
    ;; trade in restricted things
    :endangered-species :live-animals :historical-artifacts
    :human-body-parts})

(def catalog
  "platform-id -> policy map, transcribed from the platform's OWN
  published ad policy.

  Keys:
    :name / :operator            -- who publishes and enforces it.
    :policy-basis                -- the document's own title.
    :policy-version              -- the version/date the DOCUMENT
                                    states for itself, or the page
                                    identity when it states none.
    :provenance                  -- canonical URL of that document.
    :provenance-secondary        -- supporting operator doc, if any.
    :read-on                     -- when this entry was transcribed
                                    from a direct read of :provenance.
                                    An entry whose :read-on has gone
                                    stale is a re-read task, not a
                                    reason to guess.
    :generative-surface?         -- does the ad render inside
                                    model-generated content?
    :permitted-categories        -- categories the policy names as
                                    currently servable.
    :restricted-categories       -- categories servable ONLY for a
                                    pre-approved advertiser, and only
                                    where :restricted-category-
                                    jurisdictions allows.
    :prohibited-categories       -- categories the policy names as not
                                    servable.
    :closed-category-set?        -- true when the policy states that
                                    every category it does NOT name is
                                    disallowed. Under a closed set an
                                    UNKNOWN category is a hold, not a
                                    shrug.
    :restricted-category-jurisdictions
                                 -- ISO3 set where :restricted-
                                    categories may run at all, or the
                                    sentinel :per-category-unenumerated
                                    (see `restricted-category-allowed-
                                    jurisdiction?`).
    :excluded-placement-contexts -- contexts the policy refuses to
                                    place ads near.
    :required-attestations       -- per-campaign facts the agency must
                                    positively assert before placing.
    :jurisdiction-attestations   -- iso3 -> additional attestations
                                    required only when running there.
    :transcription-notes         -- what this entry does NOT capture,
                                    and where a conservative reading
                                    was taken."
  {;; ------------------------------------------------------------------
   "chatgpt-ads"
   {:name "ChatGPT Ads"
    :operator "OpenAI"
    :policy-basis "OpenAI 広告ポリシー (OpenAI Ad policies)"
    :policy-version "v1.3 (2026-07)"
    :provenance "https://openai.com/policies/ad-policies/"
    :provenance-secondary "https://help.openai.com/en/articles/20001207-ads-in-chatgpt-the-basics"
    :read-on "2026-07-26"
    :generative-surface? true
    :closed-category-set? true
    :permitted-categories #{:lifestyle-household
                            :local-services
                            :travel-experiences
                            :digital-products-education}
    :restricted-categories #{:financial-services
                             :healthcare-medical}
    :restricted-category-jurisdictions #{"USA"}
    :prohibited-categories #{:adult-sexual
                             :nudity-suggestive
                             :dating
                             :legal-services
                             :health-claims
                             :alcohol
                             :tobacco
                             :recreational-drugs
                             :gambling
                             :political
                             :counterfeit
                             :fraud-deception
                             :weapons
                             :explicit-violence
                             :sensitive-events}
    :excluded-placement-contexts #{:child-safety
                                   :safeguard-circumvention
                                   :cyberbullying
                                   :dangerous-acts
                                   :controversial-social-content
                                   :fraud-deception
                                   :sexual-exploitative-content
                                   :graphic-violence
                                   :hate-harassment
                                   :illegal-content
                                   :ip-infringement
                                   :misinformation
                                   :obscenity
                                   :political-content
                                   :privacy
                                   :regulated-goods
                                   :suicide-self-harm
                                   :terrorism
                                   :weapons
                                   ;; vulnerable user-model interactions
                                   :emotional-reliance
                                   :mental-and-personal-health
                                   :sensitive-user-journey}
    :required-attestations #{:distinguishable-from-product-ui
                             :landing-page-consistency
                             :advertiser-identity-verified}
    :jurisdiction-attestations {}
    :transcription-notes
    (str "1) :legal-services is recorded as PROHIBITED even though the "
         "policy's own section-2 preamble lists 法務サービス alongside "
         "financial and healthcare as case-by-case approvable: the "
         "policy's dedicated 法的サービス section states that ads for "
         "legal advice, representation or legal services are not "
         "permitted. The document contradicts itself, so this entry "
         "takes the conservative reading -- a compliance gate holds on "
         "ambiguity. Reclassify only on written confirmation from the "
         "operator. "
         "2) The policy prohibits ads that mimic the ChatGPT product "
         "experience but does not, on the page read, mandate a specific "
         "label string, so :required-attestations asserts "
         "DISTINGUISHABILITY, not the presence of any particular word. "
         "3) 米国外の金融サービス/ヘルスケアサービス広告は原則禁止 is "
         "encoded as :restricted-category-jurisdictions #{\"USA\"}.")}

   ;; ------------------------------------------------------------------
   "google-ads"
   {:name "Google Ads"
    :operator "Google"
    :policy-basis "Google 広告のポリシー (Google Ads policies)"
    :policy-version "policy centre index, read 2026-07-27 (document states no version number)"
    :provenance "https://support.google.com/adspolicy/answer/6008942"
    :read-on "2026-07-27"
    :generative-surface? false
    :closed-category-set? false
    :permitted-categories #{}
    :restricted-categories #{:adult-sexual
                             :nudity-suggestive
                             :alcohol
                             :copyrighted-content
                             :trademark
                             :gambling
                             :online-gaming
                             :healthcare-medical
                             :political
                             :financial-services
                             :cryptocurrency
                             :dating}
    :restricted-category-jurisdictions :per-category-unenumerated
    :prohibited-categories #{:counterfeit
                             :recreational-drugs
                             :drug-paraphernalia
                             :weapons
                             :explosives
                             :tobacco
                             :hacking-services
                             :traffic-inflation
                             :forged-documents
                             :hate-harassment
                             :explicit-violence
                             :endangered-species
                             :fraud-deception
                             :malware-phishing}
    :excluded-placement-contexts #{}
    :required-attestations #{:landing-page-consistency
                             :editorial-standards}
    :jurisdiction-attestations {}
    :transcription-notes
    (str "Transcribed from the four-category policy index (禁止コンテンツ / "
         "禁止されている行為 / 制限されているコンテンツと機能 / 編集基準と技術要件). "
         "1) OPEN category set: the index enumerates prohibited and restricted "
         "content and does not declare unnamed categories disallowed, so "
         ":closed-category-set? is false and :permitted-categories is empty "
         "(an unnamed category resolves :permitted). "
         "2) Restricted categories require Google 広告認定 (certification) AND are "
         "limited to 承認された地域 -- but the per-category country tables live on "
         "the individual policy sub-pages, which were NOT read. Recorded as "
         ":per-category-unenumerated, which HOLDS every restricted-category "
         "placement until someone transcribes the relevant table. That is the "
         "conservative reading, and the gap is the extension task. "
         "3) 編集基準と技術要件 (editorial standards, destination requirements, "
         "technical requirements, ad-format requirements) are creative/landing-page "
         "properties, not ad categories; the two an agency can meaningfully attest "
         "per campaign are recorded as :required-attestations. "
         "4) The index's 'その他の制限付きビジネス' is an explicitly open-ended "
         "reserve clause and is not enumerable -- it is NOT captured here, so a "
         "clean verdict from this entry is not a guarantee of Google approval.")}

   ;; ------------------------------------------------------------------
   "meta-ads"
   {:name "Meta Ads (Facebook / Instagram)"
    :operator "Meta"
    :policy-basis "Meta 広告規定 (Meta Advertising Standards)"
    :policy-version "Transparency Center ad-standards index, read 2026-07-27 (document states no version number)"
    :provenance "https://transparency.meta.com/policies/ad-standards/"
    :read-on "2026-07-27"
    :generative-surface? false
    :closed-category-set? false
    :permitted-categories #{}
    :restricted-categories #{:alcohol
                             :dating
                             :beauty-cosmetics
                             :health-claims
                             :addiction-treatment
                             :financial-services
                             :insurance
                             :cryptocurrency
                             :gambling
                             :online-gaming
                             :political
                             :social-issues
                             :entertainment-age-rated
                             :healthcare-medical}
    :restricted-category-jurisdictions :per-category-unenumerated
    :prohibited-categories #{:human-exploitation
                             :hate-harassment
                             :misinformation
                             :vaccine-discouragement
                             :fraud-deception
                             :malware-phishing
                             :historical-artifacts
                             :human-body-parts
                             :endangered-species
                             :live-animals
                             :tobacco
                             :weapons
                             :explosives
                             :recreational-drugs
                             :adult-sexual
                             :nudity-suggestive
                             :explicit-violence
                             :counterfeit
                             :sensitive-events}
    :excluded-placement-contexts #{}
    :required-attestations #{:landing-page-consistency
                             :advertiser-identity-verified}
    :jurisdiction-attestations {"DEU" #{:eu-dsa-beneficiary-payer-disclosure}}
    :transcription-notes
    (str "Transcribed from the Advertising Standards index (許容されないコンテンツ / "
         "詐欺行為および詐欺的手法 / 制限されている商品およびサービス / 不適切なコンテンツ / "
         "知的財産権の侵害 / 社会問題、選挙または政治に関連する広告 / 製品別およびフォーマット別). "
         "1) OPEN category set, same reading as google-ads. "
         "2) Most restricted categories require 書面による事前の許可 (prior written "
         "permission) and/or an identity/regulator verification; addiction treatment "
         "additionally requires LegitScript certification. Country eligibility is "
         "per-category and not enumerated on the index, hence "
         ":per-category-unenumerated. "
         "3) EU DSA 受益者/支払者フィールド is a hard per-ad disclosure for EU-targeted "
         "ads. It is recorded as a JURISDICTION-scoped attestation rather than a base "
         "one so a JPN-only campaign is not held for an EU-only requirement. Only DEU "
         "is enumerated because DEU is the only EU jurisdiction present in "
         "advertising.facts/catalog -- extend both together. "
         "4) Meta's US/CA/EU 'special ad categories' (housing / employment / credit, "
         "which force declared status and restricted targeting) are NOT modelled: they "
         "constrain TARGETING rather than admissibility, and this actor does not model "
         "targeting. Recorded here so the gap is visible. "
         "5) Discriminatory practices, bullying, privacy violation, suicide/self-harm "
         "and profanity are prohibited CONTENT properties rather than ad categories, "
         "and are left to creative review -- see category-vocabulary's docstring.")}

   ;; ------------------------------------------------------------------
   "microsoft-advertising"
   {:name "Microsoft Advertising"
    :operator "Microsoft"
    :policy-basis "Microsoft Advertising Disallowed Content / Restricted Content policies"
    :policy-version "policy index, read 2026-07-27 (document states no version number)"
    :provenance "https://about.ads.microsoft.com/en-us/policies/disallowed-content"
    :provenance-secondary "https://about.ads.microsoft.com/en-us/policies/restricted-categories"
    :read-on "2026-07-27"
    :generative-surface? false
    :closed-category-set? false
    :permitted-categories #{}
    :restricted-categories #{:adult-sexual
                             :nudity-suggestive
                             :alcohol
                             :beauty-cosmetics
                             :dating
                             :financial-services
                             :food-products
                             :gambling
                             :online-gaming
                             :government-services
                             :legal-services
                             :people-finder
                             :healthcare-medical
                             :pricing
                             :psychic-services
                             :religious-content
                             :software-download
                             :fundraising
                             :surveillance-equipment
                             :tattoos-piercings
                             :ticket-reselling
                             :travel-experiences}
    :restricted-category-jurisdictions :per-category-unenumerated
    :prohibited-categories #{:fraud-deception
                             :recreational-drugs
                             :drug-paraphernalia
                             :live-animals
                             :endangered-species
                             :explosives
                             :misinformation
                             :malware-phishing
                             :explicit-violence
                             :hate-harassment
                             :piracy
                             :political
                             :sensitive-events
                             :tobacco
                             :human-exploitation
                             :weapons}
    :excluded-placement-contexts #{}
    :required-attestations #{:landing-page-consistency
                             :editorial-standards}
    :jurisdiction-attestations {}
    :transcription-notes
    (str "Transcribed from the two policy index pages (Disallowed Content, "
         "Restricted Content). "
         "1) OPEN category set, same reading as google-ads. "
         "2) NOTE the two headline disagreements with the other seeded platforms, "
         "both transcribed as published: :political is DISALLOWED here (restricted on "
         "google-ads and meta-ads), and :travel-experiences is RESTRICTED here "
         "(permitted on chatgpt-ads). Do not 'harmonise' these -- the disagreement is "
         "the fact the agency needs. "
         "3) 'Areas of Questionable Legality', 'Other Market Restricted Products and "
         "Services' and 'Promotion of third-party products and services' are open-ended "
         "reserve clauses and are not enumerable, so a clean verdict from this entry is "
         "not a guarantee of Microsoft approval. "
         "4) 'Inaccessible site', 'Non-Indexed Sites' and 'Unmoderated User-Generated "
         "Content' are landing-page/site-quality failures rather than ad categories and "
         "are deliberately excluded from category-vocabulary. "
         "5) Country eligibility for restricted categories is not enumerated on these "
         "index pages, hence :per-category-unenumerated.")}})

(defn policy-basis
  "The platform's policy map, or nil -- nil means NO policy-basis, and
  the governor must hold any proposal that targets it."
  [platform-id]
  (get catalog platform-id))

(defn generative-surface?
  "Does this platform render ads inside model-generated content?
  Unknown platform -> false, because an unknown platform is already a
  HARD hold on `no-platform-policy-basis` -- this predicate must never
  be the thing standing between an unknown platform and a placement."
  [platform-id]
  (boolean (:generative-surface? (policy-basis platform-id))))

(defn category-disposition
  "How does `platform-id`'s own policy treat `category`?
  -> :permitted | :restricted | :prohibited | :not-permitted | :no-policy-basis

  `:not-permitted` is returned for a category the policy does not name
  at all when the policy declares a CLOSED category set (ChatGPT's
  'all other categories are not allowed at launch'). When the set is
  open, an unnamed category is `:permitted` -- the platform has not
  spoken, and this namespace does not speak for it."
  [platform-id category]
  (if-let [{:keys [permitted-categories restricted-categories
                   prohibited-categories closed-category-set?]}
           (policy-basis platform-id)]
    (cond
      (contains? (or prohibited-categories #{}) category) :prohibited
      (contains? (or restricted-categories #{}) category) :restricted
      (contains? (or permitted-categories #{}) category)  :permitted
      closed-category-set?                                :not-permitted
      :else                                               :permitted)
    :no-policy-basis))

(defn restricted-category-allowed-jurisdiction?
  "May a `:restricted` category run in `iso3` at all?

  Three cases, and the middle one is the interesting one:

  - an explicit ISO3 set -> membership test.
  - `:per-category-unenumerated` -> **false, always**. The platform DOES
    limit restricted categories by country, but the per-category tables
    were not transcribed, so this entry cannot say whether `iso3` is
    eligible. Not knowing is a hold: answering `true` here would let a
    campaign run in a country the platform may well forbid it in, on
    the strength of a table nobody read. The fix is to transcribe the
    table, not to relax the predicate.
  - no key at all -> the platform declares no jurisdiction limit."
  [platform-id iso3]
  (let [allowed (:restricted-category-jurisdictions (policy-basis platform-id) ::absent)]
    (cond
      (= ::absent allowed)                   (some? (policy-basis platform-id))
      (= :per-category-unenumerated allowed) false
      :else                                  (contains? allowed iso3))))

(defn excluded-context-hits
  "The requested placement contexts that this platform's policy refuses
  to place ads near. Empty when the platform is unknown -- an unknown
  platform is held on `no-platform-policy-basis`, not here."
  [platform-id contexts]
  (if-let [{:keys [excluded-placement-contexts]} (policy-basis platform-id)]
    (vec (sort (set/intersection (set excluded-placement-contexts) (set contexts))))
    []))

(defn required-attestations
  "The attestations this platform requires. The 2-arity adds any
  attestation required only when running in `iso3` (Meta's EU DSA
  beneficiary/payer disclosure is the seeded example), so a campaign is
  never held for a requirement that does not apply where it runs."
  ([platform-id] (required-attestations platform-id nil))
  ([platform-id iso3]
   (let [p (policy-basis platform-id)]
     (vec (sort (set/union (:required-attestations p #{})
                           (get (:jurisdiction-attestations p) iso3 #{})))))))

(defn missing-attestations
  "Which of the platform's required attestations has this campaign NOT
  positively asserted, for a campaign running in `iso3`? Absence is
  never treated as consent: a campaign that simply omits the field is
  missing the attestation, and only an explicit `true` counts."
  ([platform-id attested] (missing-attestations platform-id nil attested))
  ([platform-id iso3 attested]
   (let [have (set (keep (fn [[k v]] (when (true? v) k)) attested))]
     (vec (sort (remove have (required-attestations platform-id iso3)))))))

(defn compliance-checklist
  "The per-platform evidence checklist the advisor drafts and the
  agency operator signs off on -- the platform-side analog of
  `advertising.facts/evidence-checklist`."
  ([platform-id] (compliance-checklist platform-id nil))
  ([platform-id iso3]
   (if-let [p (policy-basis platform-id)]
     (vec (concat [(str (:name p) " ad-policy conformance ("
                        (:policy-basis p) ", " (:policy-version p) ")")
                   "Ad-category disposition under the platform's own taxonomy"
                   "Placement-context exclusion review"]
                  (map #(str "Attestation: " (name %))
                       (required-attestations platform-id iso3))))
     [])))

(defn coverage
  "Honest coverage report -- how many of the requested platforms
  actually have a transcribed policy-basis. Never reports a missing
  platform as covered, and never counts a platform whose policy was
  merely located as one whose policy was read."
  ([] (coverage (keys catalog)))
  ([platform-ids]
   (let [have (filter catalog platform-ids)
         missing (remove catalog platform-ids)]
     {:requested (count platform-ids)
      :covered (count have)
      :covered-platforms (vec (sort have))
      :missing-platforms (vec (sort missing))
      :note (str "cloud-itonami-isic-7310 media-platform layer: "
                 (count catalog)
                 " platform(s) transcribed from a direct read of the "
                 "platform's own published ad policy. Each entry's "
                 ":transcription-notes states what it does NOT capture "
                 "-- notably the open-ended reserve clauses every platform "
                 "keeps ('その他の制限付きビジネス', 'Areas of Questionable "
                 "Legality'), which mean a clean verdict here is a "
                 "necessary and not a sufficient condition for platform "
                 "approval. Extend by reading the policy, never by "
                 "inferring one platform's taxonomy from another's.")})))

(defn cross-platform-disposition
  "How every catalogued platform treats one category, sorted by
  platform id -- the question an agency actually asks first ('where can
  I run this at all?'), answerable only because the platforms are
  modelled separately rather than merged into one rule table."
  [category]
  (into (sorted-map)
        (map (fn [pid] [pid (category-disposition pid category)]))
        (keys catalog)))
