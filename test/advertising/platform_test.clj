(ns advertising.platform-test
  "The media-platform catalog as executable tests (ADR-0002).

  Two things are under test. The first is ordinary: the taxonomy
  predicates classify categories, jurisdictions, attestations and
  placement contexts the way the transcribed policy says. The second
  is the one that actually protects anybody -- that an UNKNOWN
  platform is inert. Every predicate here must refuse to invent a
  permissive answer for a platform nobody has transcribed, because the
  governor's `no-platform-policy-basis` hold is the only thing standing
  between an unknown ad network and a real placement, and a helper
  that cheerfully answers ':permitted' for a platform it has never
  heard of would route around it.

  Since ADR-0003 a third thing is under test: that the seeded
  platforms are allowed to DISAGREE. The cross-platform tests below
  assert specific disagreements as transcribed, so a future 'tidy-up'
  that harmonises the taxonomies has to delete a test that says, in
  words, why it must not."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [advertising.platform :as platform]))

;; ----------------------------- catalog honesty -----------------------------

(deftest every-entry-carries-a-real-citation
  (testing "an entry with no verifiable provenance is worse than no entry: it looks authoritative"
    (doseq [[pid entry] platform/catalog]
      (testing pid
        (is (str/starts-with? (:provenance entry) "https://")
            "provenance must be the canonical URL of the platform's own policy")
        (is (seq (:policy-basis entry)) "the policy document's own title")
        (is (seq (:policy-version entry)) "the version/date the document states for itself")
        (is (seq (:read-on entry))
            "when this entry was transcribed from a DIRECT read -- a stale :read-on is a re-read task")))))

(deftest category-sets-do-not-overlap
  (testing "a category classified two ways would make the governor's verdict depend on check order"
    (doseq [[pid {:keys [permitted-categories restricted-categories prohibited-categories]}]
            platform/catalog]
      (testing pid
        (is (empty? (filter permitted-categories restricted-categories)))
        (is (empty? (filter permitted-categories prohibited-categories)))
        (is (empty? (filter restricted-categories prohibited-categories)))))))

(deftest every-catalogued-category-is-in-the-shared-vocabulary
  (testing "a category outside the vocabulary would resolve differently per platform by typo, not by policy"
    (doseq [[pid {:keys [permitted-categories restricted-categories prohibited-categories]}]
            platform/catalog]
      (testing pid
        (doseq [c (concat permitted-categories restricted-categories prohibited-categories)]
          (is (contains? platform/category-vocabulary c)
              (str c " must be a member of platform/category-vocabulary")))))))

(deftest coverage-reports-unread-platforms-as-missing
  (let [c (platform/coverage ["chatgpt-ads" "google-ads" "meta-ads" "acme-adnet"])]
    (is (= 4 (:requested c)))
    (is (= 3 (:covered c)))
    (is (= ["chatgpt-ads" "google-ads" "meta-ads"] (:covered-platforms c)))
    (is (= ["acme-adnet"] (:missing-platforms c))
        "a platform whose policy has not been transcribed is missing, not covered")))

;; ----------------------------- cross-platform disagreement -----------------------------

(deftest platforms-disagree-and-must-keep-disagreeing
  (testing "travel: permitted on chatgpt-ads, RESTRICTED on microsoft-advertising"
    (is (= :permitted (platform/category-disposition "chatgpt-ads" :travel-experiences)))
    (is (= :restricted (platform/category-disposition "microsoft-advertising" :travel-experiences))))
  (testing "political: prohibited outright on chatgpt-ads AND microsoft-advertising, merely restricted on google-ads and meta-ads"
    (is (= :prohibited (platform/category-disposition "chatgpt-ads" :political)))
    (is (= :prohibited (platform/category-disposition "microsoft-advertising" :political)))
    (is (= :restricted (platform/category-disposition "google-ads" :political)))
    (is (= :restricted (platform/category-disposition "meta-ads" :political))))
  (testing "legal services: prohibited on chatgpt-ads, restricted on microsoft-advertising, unnamed (permitted) on the other two"
    (is (= :prohibited (platform/category-disposition "chatgpt-ads" :legal-services)))
    (is (= :restricted (platform/category-disposition "microsoft-advertising" :legal-services)))
    (is (= :permitted (platform/category-disposition "google-ads" :legal-services)))
    (is (= :permitted (platform/category-disposition "meta-ads" :legal-services)))))

(deftest cross-platform-disposition-answers-where-can-this-run
  (is (= {"chatgpt-ads" :not-permitted
          "google-ads" :permitted
          "line-yahoo-ads" :not-transcribed
          "meta-ads" :restricted
          "microsoft-advertising" :restricted
          "telegram-ads" :permitted
          "x-ads" :permitted
          "youtube-ads" :permitted}
         (into {} (platform/cross-platform-disposition :beauty-cosmetics)))
      "one question, eight answers -- and the three kinds of 'no' stay distinguishable")
  (testing "a category the OPEN sets do not name resolves permitted there, and is held on the CLOSED one"
    (is (= {"chatgpt-ads" :not-permitted
            "google-ads" :permitted
            "line-yahoo-ads" :prohibited
            "meta-ads" :permitted
            "microsoft-advertising" :restricted
            "telegram-ads" :permitted
            "x-ads" :permitted
            "youtube-ads" :permitted}
           (into {} (platform/cross-platform-disposition :ticket-reselling)))
        "ticket reselling: unnamed by Google and Meta, restricted by Microsoft, outside ChatGPT's launch list, and newly written into LINEヤフー's 第4章 as prohibited")))

(deftest a-partially-read-policy-does-not-answer-permitted
  (testing "line-yahoo-ads is the only :policy-read :partial entry, and the whole point is that its silence is not a yes"
    (is (= :partial (:policy-read (platform/policy-basis "line-yahoo-ads"))))
    (is (= :not-transcribed (platform/category-disposition "line-yahoo-ads" :beauty-cosmetics))
        "unnamed on a partially-read policy -> we did not read it, so hold"))
  (testing "what the change document DID state is transcribed normally"
    (is (= :prohibited (platform/category-disposition "line-yahoo-ads" :tobacco)))
    (is (= :prohibited (platform/category-disposition "line-yahoo-ads" :ticket-reselling)))
    (is (= :restricted (platform/category-disposition "line-yahoo-ads" :legal-services))))
  (testing "and the Japanese professional-body gate is a real country limit, not the unenumerated sentinel"
    (is (true? (platform/restricted-category-allowed-jurisdiction? "line-yahoo-ads" "JPN")))
    (is (false? (platform/restricted-category-allowed-jurisdiction? "line-yahoo-ads" "USA"))))
  (testing "every other entry read its index end to end, so their silence IS a permission"
    (doseq [pid (keys platform/catalog)
            :when (not= pid "line-yahoo-ads")]
      (is (not= :partial (:policy-read (platform/policy-basis pid)))
          (str pid " must not quietly become partial without the disposition changing")))))

(deftest incorporation-resolves-through-the-platform-the-document-names
  (testing "YouTube's own overview says the Google Ads policies apply to it, so its categories are Google's -- not a copy that can drift"
    (is (= "google-ads" (:categories-incorporated-from (platform/policy-basis "youtube-ads"))))
    (is (empty? (:prohibited-categories (platform/policy-basis "youtube-ads")))
        "the sets are empty ON PURPOSE: a copy would be a second thing to correct")
    (doseq [c [:counterfeit :recreational-drugs :weapons :hacking-services]]
      (is (= :prohibited (platform/category-disposition "youtube-ads" c))
          (str c " is prohibited on youtube-ads because it is prohibited on google-ads")))
    (doseq [c [:gambling :healthcare-medical :political :cryptocurrency]]
      (is (= :restricted (platform/category-disposition "youtube-ads" c)))))
  (testing "the country rule travels with the taxonomy -- otherwise the incorporating surface would be FREER than the one it incorporated from"
    (is (false? (platform/restricted-category-allowed-jurisdiction? "youtube-ads" "JPN")))
    (is (false? (platform/restricted-category-allowed-jurisdiction? "google-ads" "JPN"))
        "google-ads is :per-category-unenumerated, and youtube-ads must inherit that hold rather than defaulting to 'no limit'"))
  (testing "YouTube's own additions are its own, and are not inherited by google-ads"
    (is (contains? (set (platform/required-attestations "youtube-ads")) :distinguishable-from-product-ui))
    (is (contains? (set (platform/required-attestations "youtube-ads")) :ugc-rights-cleared))
    (is (not (contains? (set (platform/required-attestations "google-ads")) :ugc-rights-cleared)))
    (is (= [:youtube-kids] (platform/excluded-context-hits "youtube-ads" [:youtube-kids :news]))
        "YouTube Kids has its own unread policy, so that context is excluded rather than silently servable"))
  (testing "incorporation is one hop: a chain would make a correction's blast radius unreadable"
    (doseq [[pid entry] platform/catalog
            :let [target (:categories-incorporated-from entry)]
            :when target]
      (is (some? (platform/policy-basis target))
          (str pid " incorporates " target ", which must itself be catalogued"))
      (is (nil? (:categories-incorporated-from (platform/policy-basis target)))
          (str pid " -> " target " -> ... : chains are not supported, and no read policy needs one")))))

(deftest x-and-telegram-disagree-with-the-rest-about-drugs
  (testing "transcribed as published, in opposite directions, from two documents read on the same day"
    (is (= :restricted (platform/category-disposition "x-ads" :recreational-drugs))
        "X allows drugs and paraphernalia with pre-authorisation")
    (is (= :restricted (platform/category-disposition "x-ads" :drug-paraphernalia)))
    (is (= :prohibited (platform/category-disposition "telegram-ads" :recreational-drugs)))
    (is (= :prohibited (platform/category-disposition "google-ads" :recreational-drugs)))
    (is (= :prohibited (platform/category-disposition "microsoft-advertising" :recreational-drugs))))
  (testing "and X's pre-authorisation is not a free pass: the country tables were not read, so it holds"
    (is (false? (platform/restricted-category-allowed-jurisdiction? "x-ads" "USA"))))
  (testing "Telegram prohibits political and religious advocacy outright, where X merely restricts political"
    (is (= :prohibited (platform/category-disposition "telegram-ads" :political)))
    (is (= :prohibited (platform/category-disposition "telegram-ads" :religious-content)))
    (is (= :restricted (platform/category-disposition "x-ads" :political))))
  (testing "Telegram's 5.7 prohibits the PREDATORY subset of finance, which the vocabulary cannot express, so the whole category is deliberately unnamed rather than over-prohibited"
    (is (= :permitted (platform/category-disposition "telegram-ads" :financial-services)))
    (is (not (contains? (:prohibited-categories (platform/policy-basis "telegram-ads"))
                        :financial-services))))
  (testing "Telegram publishes a licence gate and no country table, so a licensed advertiser may run medical anywhere"
    (is (= :restricted (platform/category-disposition "telegram-ads" :healthcare-medical)))
    (is (true? (platform/restricted-category-allowed-jurisdiction? "telegram-ads" "JPN")))))

(deftest open-and-closed-category-sets-behave-differently
  (testing "an unnamed category is permitted on an open set and held on a closed one"
    (is (= :permitted (platform/category-disposition "google-ads" :local-services))
        "google-ads does not name :local-services, and does not declare unnamed categories disallowed")
    (is (= :permitted (platform/category-disposition "chatgpt-ads" :local-services))
        "chatgpt-ads names it explicitly")
    (is (= :not-permitted (platform/category-disposition "chatgpt-ads" :food-products))
        "chatgpt-ads declares every unnamed category disallowed")
    (is (= :restricted (platform/category-disposition "microsoft-advertising" :food-products)))))

;; ----------------------------- unknown platform is inert -----------------------------

(deftest unknown-platform-has-no-policy-basis
  (is (nil? (platform/policy-basis "acme-adnet")))
  (is (nil? (platform/policy-basis nil))))

(deftest unknown-platform-never-answers-permissively
  (testing "every predicate must decline to speak for a platform nobody transcribed"
    (is (= :no-policy-basis (platform/category-disposition "acme-adnet" :local-services)))
    (is (= :no-policy-basis (platform/category-disposition nil :local-services)))
    (is (false? (platform/generative-surface? "acme-adnet")))
    (is (= [] (platform/required-attestations "acme-adnet")))
    (is (= [] (platform/excluded-context-hits "acme-adnet" [:suicide-self-harm])))
    (is (= [] (platform/compliance-checklist "acme-adnet")))
    (is (false? (platform/restricted-category-allowed-jurisdiction? "acme-adnet" "USA")))))

;; ----------------------------- category taxonomy -----------------------------

(deftest permitted-categories-are-permitted
  (is (= :permitted (platform/category-disposition "chatgpt-ads" :local-services)))
  (is (= :permitted (platform/category-disposition "chatgpt-ads" :travel-experiences))))

(deftest prohibited-categories-are-prohibited
  (is (= :prohibited (platform/category-disposition "chatgpt-ads" :gambling)))
  (is (= :prohibited (platform/category-disposition "chatgpt-ads" :political)))
  (is (= :prohibited (platform/category-disposition "chatgpt-ads" :legal-services))
      "recorded as prohibited on the conservative reading of a self-contradicting source"))

(deftest restricted-categories-are-restricted
  (is (= :restricted (platform/category-disposition "chatgpt-ads" :financial-services)))
  (is (= :restricted (platform/category-disposition "chatgpt-ads" :healthcare-medical))))

(deftest unnamed-category-under-a-closed-set-is-not-permitted
  (testing "the policy says every category it does not name is disallowed -- an unknown category holds, it does not shrug"
    (is (= :not-permitted (platform/category-disposition "chatgpt-ads" :cryptocurrency)))
    (is (= :not-permitted (platform/category-disposition "chatgpt-ads" nil)))))

(deftest restricted-categories-are-jurisdiction-limited
  (is (true? (platform/restricted-category-allowed-jurisdiction? "chatgpt-ads" "USA")))
  (is (false? (platform/restricted-category-allowed-jurisdiction? "chatgpt-ads" "JPN"))
      "the policy states non-US financial/healthcare ads are prohibited as a rule"))

(deftest an-untranscribed-country-table-holds-everywhere
  (testing ":per-category-unenumerated means 'this entry cannot say', and cannot-say is a hold -- never a quiet yes"
    (doseq [pid ["google-ads" "meta-ads" "microsoft-advertising"]]
      (testing pid
        (is (= :per-category-unenumerated
               (:restricted-category-jurisdictions (platform/policy-basis pid))))
        (doseq [iso3 ["USA" "JPN" "GBR" "DEU"]]
          (is (false? (platform/restricted-category-allowed-jurisdiction? pid iso3))
              (str pid " must hold restricted categories in " iso3
                   " until its per-category country table is transcribed")))))))

;; ----------------------------- jurisdiction-scoped attestations -----------------------------

(deftest jurisdiction-scoped-attestations-apply-only-where-they-apply
  (testing "Meta's EU DSA beneficiary/payer disclosure is required in DEU and not in JPN"
    (is (contains? (set (platform/required-attestations "meta-ads" "DEU"))
                   :eu-dsa-beneficiary-payer-disclosure))
    (is (not (contains? (set (platform/required-attestations "meta-ads" "JPN"))
                        :eu-dsa-beneficiary-payer-disclosure))
        "a JPN-only campaign must not be held for an EU-only requirement"))
  (testing "the base attestations apply in both"
    (is (contains? (set (platform/required-attestations "meta-ads" "JPN"))
                   :landing-page-consistency))))

(deftest jurisdiction-scoped-attestation-is-actually-missing-in-deu
  (let [attested {:landing-page-consistency true :advertiser-identity-verified true}]
    (is (= [:eu-dsa-beneficiary-payer-disclosure]
           (platform/missing-attestations "meta-ads" "DEU" attested)))
    (is (= [] (platform/missing-attestations "meta-ads" "JPN" attested)))))

(deftest distinguishability-follows-each-policy-not-the-generative-flag
  (testing "the generative surface requires ad/answer distinguishability, and it is not the only one that requires it"
    (is (true? (platform/generative-surface? "chatgpt-ads")))
    (is (contains? (set (platform/required-attestations "chatgpt-ads"))
                   :distinguishable-from-product-ui)))
  (testing "platforms that publish no such duty do not inherit it -- the attestation is transcribed, never propagated"
    (doseq [pid ["google-ads" "meta-ads" "microsoft-advertising" "x-ads" "telegram-ads"]]
      (is (false? (platform/generative-surface? pid)))
      (is (not (contains? (set (platform/required-attestations pid))
                          :distinguishable-from-product-ui))
          (str pid " publishes no product-mimicry rule and must not inherit the attestation"))))
  (testing "and two NON-generative platforms do require it, because their own documents impose it for their own reason -- so the attestation must never be re-derived from :generative-surface?"
    (doseq [pid ["youtube-ads" "line-yahoo-ads"]]
      (is (false? (platform/generative-surface? pid))
          (str pid " is not a generative surface"))
      (is (contains? (set (platform/required-attestations pid))
                     :distinguishable-from-product-ui)
          (str pid " forbids creatives that imitate its own product surface, generative or not")))))

;; ----------------------------- attestations -----------------------------

(deftest absence-is-not-consent
  (testing "a campaign that simply omits an attestation has not made it"
    (is (= [:advertiser-identity-verified :distinguishable-from-product-ui :landing-page-consistency]
           (platform/missing-attestations "chatgpt-ads" {})))
    (is (= [:distinguishable-from-product-ui]
           (platform/missing-attestations "chatgpt-ads"
                                          {:landing-page-consistency true
                                           :advertiser-identity-verified true})))))

(deftest a-falsy-attestation-is-not-an-attestation
  (testing "only an explicit true counts -- false and nil are both 'not attested'"
    (is (= [:distinguishable-from-product-ui]
           (platform/missing-attestations "chatgpt-ads"
                                          {:distinguishable-from-product-ui false
                                           :landing-page-consistency true
                                           :advertiser-identity-verified true})))
    (is (= [:distinguishable-from-product-ui]
           (platform/missing-attestations "chatgpt-ads"
                                          {:distinguishable-from-product-ui nil
                                           :landing-page-consistency true
                                           :advertiser-identity-verified true})))))

(deftest fully-attested-campaign-has-nothing-missing
  (is (= [] (platform/missing-attestations "chatgpt-ads"
                                           {:distinguishable-from-product-ui true
                                            :landing-page-consistency true
                                            :advertiser-identity-verified true}))))

;; ----------------------------- placement contexts -----------------------------

(deftest excluded-contexts-are-detected
  (is (= [:mental-and-personal-health]
         (platform/excluded-context-hits "chatgpt-ads" [:mental-and-personal-health])))
  (is (= [:emotional-reliance :suicide-self-harm]
         (platform/excluded-context-hits "chatgpt-ads"
                                         [:suicide-self-harm :emotional-reliance :cooking])))
  (is (= [] (platform/excluded-context-hits "chatgpt-ads" [:cooking :gardening])))
  (is (= [] (platform/excluded-context-hits "chatgpt-ads" []))))

(deftest chatgpt-ads-is-a-generative-surface
  (testing "the fact that makes ad/answer distinguishability a required attestation rather than a style note"
    (is (true? (platform/generative-surface? "chatgpt-ads")))
    (is (contains? (set (platform/required-attestations "chatgpt-ads"))
                   :distinguishable-from-product-ui))))

(deftest compliance-checklist-is-derived-not-invented
  (let [cl (platform/compliance-checklist "chatgpt-ads")]
    (is (seq cl))
    (is (some #(str/includes? % "distinguishable-from-product-ui") cl))
    (is (some #(str/includes? % "ChatGPT Ads") cl))))
