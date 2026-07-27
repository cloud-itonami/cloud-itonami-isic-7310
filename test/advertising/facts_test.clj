(ns advertising.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [advertising.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

(deftest chn-has-a-cited-spec-basis
  (let [sb (facts/spec-basis "CHN")]
    (is (some? sb))
    (is (re-find #"^https://www\.samr\.gov\.cn/" (:provenance sb))
        "cites an official SAMR URL that was actually fetched")
    (is (= "2026-07-27" (:retrieved-at sb))
        "records when the citation was read, so staleness is visible")
    (is (= 4 (count (facts/evidence-checklist "CHN"))))
    (is (facts/required-evidence-satisfied? "CHN" (facts/evidence-checklist "CHN")))))

(deftest chn-row-names-what-this-actor-does-not-screen
  (testing "China's pre-publication review gate is a HARD gate this actor does not model"
    (let [sb (facts/spec-basis "CHN")]
      (is (= [:pre-publication-ad-review] (:out-of-scope-here sb))
          "the boundary is declared in data, not only in a comment")
      (is (re-find #"cloud-itonami-iso3166-chn-advertising" (:out-of-scope-note sb))
          "and points at the actor that does implement it"))))

(deftest every-catalog-row-carries-a-provenance-url
  (doseq [[iso3 sb] facts/catalog]
    (is (string? (:provenance sb)) (str iso3 " has a provenance string"))
    (is (re-find #"^https?://" (:provenance sb)) (str iso3 " provenance is a URL"))
    (is (= 4 (count (:required-evidence sb))) (str iso3 " has the 4-item evidence set"))))
