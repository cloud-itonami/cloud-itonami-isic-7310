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

  ## The seeded platforms disagree, on purpose

  Every entry here was transcribed from a direct read of the
  platform's own published policy index (see each entry's
  `:provenance` and `:read-on`). They do NOT agree with each other,
  and that is the whole argument for modelling platforms separately
  rather than keeping one 'advertising rules' table:

  | category                | chatgpt-ads   | google-ads | meta-ads   | microsoft-advertising | x-ads      | telegram-ads |
  |-------------------------|---------------|------------|------------|-----------------------|------------|--------------|
  | `:travel-experiences`   | permitted     | permitted  | permitted  | RESTRICTED            | permitted  | permitted    |
  | `:legal-services`       | PROHIBITED    | permitted  | permitted  | restricted            | permitted  | permitted    |
  | `:political`            | prohibited    | RESTRICTED | restricted | PROHIBITED            | RESTRICTED | PROHIBITED   |
  | `:beauty-cosmetics`     | not-permitted | permitted  | restricted | restricted            | permitted  | permitted    |
  | `:recreational-drugs`   | prohibited    | PROHIBITED | prohibited | PROHIBITED            | RESTRICTED | prohibited   |

  The last row is the newest one and the sharpest: X puts recreational
  drugs and paraphernalia behind pre-authorisation where every other
  seeded platform refuses them outright. A single 'is this ad OK?'
  predicate cannot be right for all of them. The campaign's
  `:ad-category` is drawn from one shared `category-vocabulary` so the
  SAME declared category resolves differently per platform, which is
  exactly the fact an agency needs surfaced before it buys.
  `cross-platform-disposition` answers it directly.

  ## Two things an entry can be, besides right or absent

  A platform-side gate has more than two states, and flattening them is
  how it stops protecting anyone:

  - **`:policy-read :partial`** -- the standard exists and is published,
    but only part of it could be read (`line-yahoo-ads`: the enumerated
    掲載基準 renders via JavaScript and served a loading error to every
    fetch). Under the open-set rule an unnamed category would resolve
    `:permitted`, so the entry would wave through precisely the
    categories nobody read. Instead it resolves `:not-transcribed` and
    the governor HOLDS. Reading the rest is the extension task;
    relaxing the flag is not.
  - **`:categories-incorporated-from`** -- the platform's own document
    says another platform's policy applies to it (`youtube-ads`: 'To
    place ads on YouTube, you'll have to comply with: Google Ad
    Policies'). Categories AND the restricted-category country rule
    resolve through that platform, so a correction there reaches here
    instead of drifting. This is only ever set from a transcribed
    sentence -- never because two surfaces look similar, which the
    paragraph above forbids.

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

  The requirement follows the surface, not the fleet -- and, since the
  2026-08-13 additions, that cuts BOTH ways. `chatgpt-ads` is still the
  only `:generative-surface?` entry, but two non-generative platforms
  now carry the same attestation because their own documents impose it
  for a different reason: YouTube forbids ads that mimic YouTube site
  elements, and LINEヤフー forbids creatives that imitate LINEヤフー
  service design. The duty is confusability with the product's own
  content; a model is one way to create that, and a feed that looks
  like the messenger around it is another. Reading
  `:distinguishable-from-product-ui` as 'the generative-surface flag'
  would therefore be wrong, and would drop it from two platforms that
  publish it."
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
    :policy-read                 -- :index-complete when the policy
                                    index itself was read end to end,
                                    so 'this category is unnamed' is a
                                    fact about the POLICY. :partial
                                    when only part of the published
                                    standard could be read, so an
                                    unnamed category is a fact about
                                    THIS ENTRY and resolves
                                    :not-transcribed (a hold). Absent
                                    means :index-complete.
    :categories-incorporated-from
                                 -- another platform-id whose category
                                    taxonomy this policy adopts BY ITS
                                    OWN WORDS. Only ever set when the
                                    platform's document says so; it is
                                    not a shortcut for 'these two look
                                    similar', which the ns docstring
                                    forbids.
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
         "index pages, hence :per-category-unenumerated.")}

   ;; ------------------------------------------------------------------
   "x-ads"
   {:name "X Ads"
    :operator "X Corp."
    :policy-basis "X Advertising Policies"
    :policy-version "policy index, read 2026-08-13 (document states no version number)"
    :provenance "https://business.x.com/en/help/ads-policies.html"
    :read-on "2026-08-13"
    :generative-surface? false
    :closed-category-set? false
    :policy-read :index-complete
    :permitted-categories #{}
    :restricted-categories #{:adult-sexual
                             :alcohol
                             :recreational-drugs
                             :drug-paraphernalia
                             :financial-services
                             :gambling
                             :online-gaming
                             :healthcare-medical
                             :political
                             :tobacco}
    :restricted-category-jurisdictions :per-category-unenumerated
    :prohibited-categories #{:fraud-deception
                             :weapons}
    :excluded-placement-contexts #{}
    :required-attestations #{:advertiser-identity-verified}
    :jurisdiction-attestations {}
    :transcription-notes
    (str "Transcribed from the policy index's own two headings (Prohibited "
         "Content / Restricted Content). "
         "1) NOTE the headline disagreement, transcribed as published: X puts "
         ":recreational-drugs and :drug-paraphernalia under RESTRICTED "
         "(pre-authorisation), where google-ads and microsoft-advertising both "
         "PROHIBIT them, and telegram-ads prohibits them too. Do not harmonise. "
         "2) The index lists 'Prohibited Content for Minors' and 'Unacceptable "
         "Content' as prohibited headings, but their enumerations live on sub-pages "
         "that were NOT read, so no categories were inferred from the heading names. "
         "An entry that guessed 'Unacceptable Content' meant hate + violence would be "
         "asserting a taxonomy nobody read. "
         "3) X states pre-authorisation must be APPROVED before a restricted-category "
         "campaign launches, which the governor already enforces via "
         ":advertiser-approval-on-file?; the per-category country tables are on the "
         "sub-pages, hence :per-category-unenumerated. "
         "4) 'Paid Partnerships policy' and 'Anti-Discriminatory Targeting Policy' are "
         "listed under Campaign Considerations and are targeting/disclosure duties "
         "rather than ad categories -- the disclosure half is already modelled "
         "jurisdiction-side in advertising.facts.")}

   ;; ------------------------------------------------------------------
   "telegram-ads"
   {:name "Telegram Ads"
    :operator "Telegram"
    :policy-basis "Telegram Ad Policies and Guidelines"
    :policy-version "guidelines page, read 2026-08-13 (document states no version number)"
    :provenance "https://promote.telegram.org/guidelines"
    :read-on "2026-08-13"
    :generative-surface? false
    :closed-category-set? false
    :policy-read :index-complete
    :permitted-categories #{}
    :restricted-categories #{:healthcare-medical}
    :prohibited-categories #{:adult-sexual
                             :nudity-suggestive
                             :dating
                             :explicit-violence
                             :hate-harassment
                             :counterfeit
                             :copyrighted-content
                             :trademark
                             :piracy
                             :political
                             :religious-content
                             :sensitive-events
                             :gambling
                             :health-claims
                             :alcohol
                             :tobacco
                             :recreational-drugs
                             :drug-paraphernalia
                             :weapons
                             :explosives
                             :malware-phishing
                             :hacking-services
                             :traffic-inflation
                             :forged-documents
                             :human-exploitation
                             :human-body-parts}
    :excluded-placement-contexts #{}
    :required-attestations #{:landing-page-consistency
                             :editorial-standards}
    :jurisdiction-attestations {}
    :transcription-notes
    (str "Transcribed from section 5 (Prohibited content) of the guidelines, "
         "clause by clause. "
         "1) Telegram publishes NO pre-authorisation tier and NO country table. The "
         "single :restricted entry is 5.8, which permits medical products only where "
         "'sellers must be properly licensed' -- a licence gate, so it is recorded as "
         "restricted with no :restricted-category-jurisdictions key at all (the "
         "platform declares no country limit), and the governor's advertiser-approval "
         "check carries the licence. "
         "2) 5.7 prohibits DECEPTIVE OR HARMFUL financial products (payday lending, "
         "pyramid schemes, guaranteed-return offers) and not financial services as a "
         "category, so :financial-services is deliberately NOT listed as prohibited. "
         "The vocabulary cannot express 'the predatory subset of a category', and "
         "widening the prohibition to the whole category would hold campaigns Telegram "
         "does accept. "
         "3) 5.4 (deceptive/misleading advertising), section 2 (editorial requirements) "
         "and section 4 (destination requirements) are creative and landing-page "
         "properties rather than ad categories, and are recorded as the two "
         "attestations an agency can meaningfully assert per campaign. "
         "4) The document ends 'All examples on this page are non-exhaustive', which is "
         "an explicit reserve clause: a clean verdict here is necessary, not "
         "sufficient. Hence :closed-category-set? false.")}

   ;; ------------------------------------------------------------------
   "youtube-ads"
   {:name "YouTube Ads"
    :operator "Google"
    :policy-basis "YouTube Ad policy overview"
    :policy-version "policy overview page, read 2026-08-13 (document states no version number)"
    :provenance "https://support.google.com/youtube/answer/188570"
    :provenance-secondary "https://support.google.com/adspolicy/answer/6008942"
    :read-on "2026-08-13"
    :generative-surface? false
    :closed-category-set? false
    :policy-read :index-complete
    :categories-incorporated-from "google-ads"
    :permitted-categories #{}
    :restricted-categories #{}
    :prohibited-categories #{}
    :excluded-placement-contexts #{:youtube-kids}
    :required-attestations #{:landing-page-consistency
                             :editorial-standards
                             :distinguishable-from-product-ui
                             :ugc-rights-cleared}
    :jurisdiction-attestations {}
    :transcription-notes
    (str "This entry exists because 'we support Google Ads' does NOT answer 'can we "
         "run this on YouTube?'. YouTube's own overview states the incorporation in "
         "its own words -- 'To place ads on YouTube, you'll have to comply with: "
         "Google Ad Policies' -- so :categories-incorporated-from is a transcribed "
         "fact, not a guess that two Google surfaces must be alike. Every category "
         "therefore resolves through google-ads, and a future correction to that "
         "entry reaches YouTube automatically instead of drifting. "
         "1) The YouTube-SPECIFIC policies the overview names on top of the Google set "
         "are 'Mimicking YouTube site elements' and 'User-generated content in ads'. "
         "Those are creative properties, recorded as the two extra attestations. Note "
         "that :distinguishable-from-product-ui appears here on a NON-generative "
         "surface: the requirement follows confusability with the product's own "
         "content, not the presence of a model. "
         "2) YouTube Kids has its own advertising policies (answer/6168681) which were "
         "NOT read, so :youtube-kids is recorded as an EXCLUDED placement context "
         "rather than left silently servable. Transcribe that page to lift it. "
         "3) Ad formats & features and Targeting & serving are also named on the "
         "overview and are not category policy; they are not captured here.")}

   ;; ------------------------------------------------------------------
   ;; The only :policy-read :partial entry, and the reason that key exists.
   "line-yahoo-ads"
   {:name "LINEヤフー広告 (LINE Ads / Yahoo! JAPAN Ads, unified)"
    :operator "LINEヤフー株式会社 (LY Corporation)"
    :policy-basis "LINEヤフー広告 広告アカウント審査基準・広告掲載基準の統一について"
    :policy-version "2025/11/17 (document states this date; new criteria apply from 2026 spring)"
    :provenance "https://www.lycbiz.com/sites/default/files/media/jp/terms-and-policies/pdf/ly/LINE%E3%83%A4%E3%83%95%E3%83%BC%E5%BA%83%E5%91%8A%E3%82%A2%E3%82%AB%E3%82%A6%E3%83%B3%E3%83%88%E5%AF%A9%E6%9F%BB%E5%9F%BA%E6%BA%96%E3%83%BB%E5%BA%83%E5%91%8A%E6%8E%B2%E8%BC%89%E5%9F%BA%E6%BA%96%E3%81%AE%E7%B5%B1%E4%B8%80%E3%81%AB%E3%81%A4%E3%81%84%E3%81%A6.pdf"
    :provenance-secondary "https://www.lycbiz.com/jp/service/ly/quality/adreview/"
    :read-on "2026-08-13"
    :generative-surface? false
    :closed-category-set? false
    :policy-read :partial
    :permitted-categories #{}
    :restricted-categories #{:legal-services}
    :restricted-category-jurisdictions #{"JPN"}
    :prohibited-categories #{:tobacco
                             :ticket-reselling}
    :excluded-placement-contexts #{}
    :required-attestations #{:distinguishable-from-product-ui}
    :jurisdiction-attestations {}
    :transcription-notes
    (str "READ ONLY IN PART, and the entry says so in data (:policy-read :partial) "
         "rather than only in this note. The enumerated 広告掲載基準 lives on "
         "ads-help.yahoo-net.jp, which renders its content with JavaScript and served "
         "nothing but a loading error to every fetch attempted on :read-on. What WAS "
         "read is the operator's own published change document (the PDF in "
         ":provenance), which quotes the standard's structure and several clauses "
         "verbatim. "
         "1) Because the read is partial, a category this entry does not name resolves "
         ":not-transcribed and the governor HOLDS. Under the open-set rule the other "
         "entries use it would have resolved :permitted -- that is, the entry would "
         "have waved through every category nobody read, which is the failure this "
         "actor exists to prevent. Reading the help pages is the extension task; "
         "relaxing :policy-read is not. "
         "2) :tobacco is 第4章3(12)たばこ. The document records a live carve-out: "
         "電子たばこ moves OUT of the prohibited-products list but is expected to remain "
         "unservable on most surfaces via 掲載制限, with the affected surfaces published "
         "only from 2026-04-01. The vocabulary cannot split :tobacco, so the "
         "conservative whole-category prohibition stands until that list is readable. "
         "3) :ticket-reselling is 第4章3(12)チケット不正転売, newly written into the text "
         "having previously been held under the catch-all その他当社が不適切と判断したもの. "
         "4) :legal-services is 第5章 業種、商品、サービスごとの掲載基準があるもの for "
         "国家資格を有する業種 (弁護士・司法書士・行政書士・弁理士・公認会計士・税理士): "
         "servable only with 所属会 registration, published fee schedule and no "
         "outstanding disciplinary action -- a Japanese professional-body gate, hence "
         ":restricted-category-jurisdictions #{\"JPN\"}. "
         "5) 第2章(3)/第8章(2) forbid creatives that imitate LINEヤフー service design, "
         "which is the same distinguishability duty ChatGPT states for a generative "
         "surface, arrived at from the opposite direction -- a feed that looks like the "
         "messenger around it.")}})

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
  spoken, and this namespace does not speak for it.

  `:not-transcribed` is the third answer, and the one that keeps the
  other two honest. It is returned when the entry declares
  `:policy-read :partial`: the platform HAS spoken about this category,
  we simply have not read what it said. That is a fact about this
  catalog, not about the policy, and it must not wear the same face as
  `:permitted` -- an unread standard resolving to 'allowed' is exactly
  how a gate reports a pass it never measured. The governor holds on it.

  A `:categories-incorporated-from` entry resolves through the platform
  it names, because its own document says that platform's policies
  apply to it. The delegation is one hop by construction (a chain would
  mean a platform incorporating a platform that incorporates another,
  which no read policy does) and `platform_test` pins that."
  [platform-id category]
  (if-let [{:keys [permitted-categories restricted-categories
                   prohibited-categories closed-category-set?
                   policy-read categories-incorporated-from]}
           (policy-basis platform-id)]
    (cond
      (contains? (or prohibited-categories #{}) category) :prohibited
      (contains? (or restricted-categories #{}) category) :restricted
      (contains? (or permitted-categories #{}) category)  :permitted
      categories-incorporated-from
      (category-disposition categories-incorporated-from category)
      closed-category-set?                                :not-permitted
      (= :partial policy-read)                            :not-transcribed
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
  - no key at all -> the platform declares no jurisdiction limit,
    UNLESS the entry incorporates another platform's taxonomy, in which
    case the country rule is incorporated with it. Without that hop a
    surface would inherit a category as `:restricted` and then be freer
    about WHERE it may run than the policy it inherited it from, which
    is a hole shaped exactly like the one this predicate exists to
    close."
  [platform-id iso3]
  (let [entry   (policy-basis platform-id)
        allowed (:restricted-category-jurisdictions entry ::absent)]
    (cond
      (and (= ::absent allowed) (:categories-incorporated-from entry))
      (restricted-category-allowed-jurisdiction?
       (:categories-incorporated-from entry) iso3)
      (= ::absent allowed)                   (some? entry)
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
