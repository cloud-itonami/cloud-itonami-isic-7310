(ns advertising.platform
  "Per-media-platform ad-policy catalog -- the platform-side twin of
  `advertising.facts`.

  `advertising.facts` answers 'what does this JURISDICTION's
  advertising-standards law require?'. This namespace answers 'what
  does this MEDIA PLATFORM's own published ad policy allow?'. Both are
  needed before a campaign is placed: a campaign can be perfectly
  lawful under 景表法/FTC Act and still be rejected (or, worse,
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

  That discipline is why this catalog currently has exactly ONE entry.
  `google-ads` (https://support.google.com/adspolicy/answer/6008942),
  `meta-ads` (https://transparency.meta.com/policies/ad-standards/)
  and Microsoft Advertising all publish real policies at real URLs,
  and are deliberately ABSENT here: at the time this namespace was
  written those policies had not been read end-to-end, so there is no
  honest taxonomy to transcribe. `coverage` reports that gap rather
  than hiding it. Do not add them until someone actually reads them.

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
  a HARD governor check there, not a creative-review nicety."
  (:require [clojure.set :as set]))

(def catalog
  "platform-id -> policy map, transcribed from the platform's OWN
  published ad policy.

  Keys:
    :name / :operator            -- who publishes and enforces it.
    :policy-basis                -- the document's own title.
    :policy-version              -- the version/date the DOCUMENT
                                    states for itself.
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
                                    categories may run at all.
    :excluded-placement-contexts -- conversation/page contexts the
                                    policy refuses to place ads near.
    :required-attestations       -- per-campaign facts the agency must
                                    positively assert before placing.

  The `chatgpt-ads` entry is transcribed from a direct read of the
  OpenAI 広告ポリシー page (v1.3, the document's own 更新: 2026年7月15日)
  on 2026-07-26. Where that document is internally ambiguous, this
  entry takes the CONSERVATIVE reading and says so -- see
  `:transcription-notes`."
  {"chatgpt-ads"
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
         "encoded as :restricted-category-jurisdictions #{\"USA\"}.")}})

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
  "May a `:restricted` category run in `iso3` at all? A platform that
  declares no jurisdiction limit does not limit them."
  [platform-id iso3]
  (if-let [allowed (:restricted-category-jurisdictions (policy-basis platform-id))]
    (contains? allowed iso3)
    (some? (policy-basis platform-id))))

(defn excluded-context-hits
  "The requested placement contexts that this platform's policy refuses
  to place ads near. Empty when the platform is unknown -- an unknown
  platform is held on `no-platform-policy-basis`, not here."
  [platform-id contexts]
  (if-let [{:keys [excluded-placement-contexts]} (policy-basis platform-id)]
    (vec (sort (set/intersection (set excluded-placement-contexts) (set contexts))))
    []))

(defn required-attestations [platform-id]
  (vec (sort (:required-attestations (policy-basis platform-id) #{}))))

(defn missing-attestations
  "Which of the platform's required attestations has this campaign NOT
  positively asserted? Absence is never treated as consent: a campaign
  that simply omits the field is missing the attestation."
  [platform-id attested]
  (let [have (set (keep (fn [[k v]] (when (true? v) k)) attested))]
    (vec (sort (remove have (required-attestations platform-id))))))

(defn compliance-checklist
  "The per-platform evidence checklist the advisor drafts and the
  agency operator signs off on -- the platform-side analog of
  `advertising.facts/evidence-checklist`."
  [platform-id]
  (if-let [p (policy-basis platform-id)]
    (vec (concat [(str (:name p) " ad-policy conformance ("
                       (:policy-basis p) ", " (:policy-version p) ")")
                  "Ad-category disposition under the platform's own taxonomy"
                  "Placement-context exclusion review"]
                 (map #(str "Attestation: " (name %))
                      (required-attestations platform-id))))
    []))

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
                 "platform's own published ad policy. Platforms whose "
                 "policy URL is known but UNREAD (google-ads, meta-ads, "
                 "microsoft-advertising) are deliberately absent -- "
                 "extend `advertising.platform/catalog` by reading the "
                 "policy, never by inferring one platform's taxonomy "
                 "from another's.")})))
