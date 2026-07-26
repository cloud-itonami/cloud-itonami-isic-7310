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
  heard of would route around it."
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

(deftest coverage-reports-unread-platforms-as-missing
  (let [c (platform/coverage ["chatgpt-ads" "google-ads" "meta-ads"])]
    (is (= 3 (:requested c)))
    (is (= 1 (:covered c)))
    (is (= ["chatgpt-ads"] (:covered-platforms c)))
    (is (= ["google-ads" "meta-ads"] (:missing-platforms c))
        "a platform whose policy URL is known but UNREAD is missing, not covered")))

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
