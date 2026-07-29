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

;; ---------------- sponsorship disclosure (creator tie-up), ADR-0002 ----------------

(deftest a-disclosure-basis-is-cited-separately-where-it-is-cited-at-all
  (testing "sponsorship disclosure is its own legal instrument -- an operator disputing a tie-up order needs THAT citation, not the general advertising-standards one"
    (doseq [iso3 (keys facts/catalog)
            :let [d (facts/disclosure-basis iso3)]
            :when d]
      (is (string? (:legal-basis d)))
      (is (string? (:provenance d)))
      (is (seq (:accepted-disclosure-labels d)))
      (is (not= (:legal-basis d) (:legal-basis (facts/spec-basis iso3)))
          (str iso3 "'s disclosure basis must be a DISTINCT citation")))))

(deftest an-advertising-basis-does-not-imply-a-disclosure-basis
  (testing "CHN is seeded for advertising standards but its sponsorship-disclosure framework has not been read and cited. That is reported, not papered over: a tie-up there HOLDS. Inventing a disclosure rule to make the catalog look uniform is exactly what advertising.facts refuses to do."
    (is (some? (facts/spec-basis "CHN")))
    (is (nil? (facts/disclosure-basis "CHN")))
    (is (not (facts/disclosure-acceptable? "CHN" "广告"))
        "no cited basis -> no label is acceptable, however plausible")
    (let [report (facts/coverage ["JPN" "CHN"])]
      (is (= 2 (:covered report)))
      (is (= 1 (:disclosure-covered report))
          "the two coverages are reported separately, and honestly"))))

(deftest unknown-jurisdiction-has-no-fabricated-disclosure-basis
  (is (nil? (facts/disclosure-basis "ATL")))
  (is (= [] (facts/accepted-disclosure-labels "ATL")))
  (is (= [] (facts/tieup-evidence-checklist "ATL"))))

(deftest disclosure-acceptable-only-for-published-labels
  (testing "a label the authority itself publishes"
    (is (facts/disclosure-acceptable? "JPN" "PR"))
    (is (facts/disclosure-acceptable? "JPN" "広告"))
    (is (facts/disclosure-acceptable? "USA" "#ad"))
    (is (facts/disclosure-acceptable? "DEU" "Werbung")))
  (testing "a plausible industry word the authority does NOT publish is not acceptable"
    (is (not (facts/disclosure-acceptable? "JPN" "タイアップ")))
    (is (not (facts/disclosure-acceptable? "JPN" "提供"))))
  (testing "nothing recorded is never acceptable, and neither is a jurisdiction with no cited basis"
    (is (not (facts/disclosure-acceptable? "JPN" nil)))
    (is (not (facts/disclosure-acceptable? "JPN" "")))
    (is (not (facts/disclosure-acceptable? "JPN" "   ")))
    (is (not (facts/disclosure-acceptable? "ATL" "PR"))))
  (testing "labels do not leak across jurisdictions"
    (is (not (facts/disclosure-acceptable? "DEU" "PR")))
    (is (not (facts/disclosure-acceptable? "JPN" "Werbung")))))

(deftest tieup-evidence-satisfied-needs-every-item
  (let [all (facts/tieup-evidence-checklist "JPN")]
    (is (= 4 (count all)))
    (is (facts/tieup-evidence-satisfied? "JPN" all))
    (is (not (facts/tieup-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/tieup-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied"))
  (testing "the tie-up checklist is its own set, not the placement one"
    (is (not= (set (facts/tieup-evidence-checklist "JPN"))
              (set (facts/evidence-checklist "JPN"))))
    (is (not (facts/tieup-evidence-satisfied? "JPN" (facts/evidence-checklist "JPN")))
        "satisfying the placement checklist must not satisfy the tie-up one")))

(deftest coverage-reports-disclosure-coverage-honestly
  (let [report (facts/coverage ["JPN" "ATL"])]
    (is (= 1 (:disclosure-covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))))

(deftest a-recorded-label-is-matched-on-its-content-not-its-whitespace
  (testing "the function already trims to decide whether a label is blank; matching the untrimmed string against the published set contradicts that, and rejects a label the operator did record"
    (is (facts/disclosure-acceptable? "JPN" " PR "))
    (is (facts/disclosure-acceptable? "JPN" "PR\n"))
    (is (facts/disclosure-acceptable? "USA" "  #ad")))
  (testing "trimming only -- case is NOT folded, because a wording an authority did not publish in that form is not a wording it published"
    (is (not (facts/disclosure-acceptable? "USA" "AD")))
    (is (not (facts/disclosure-acceptable? "DEU" "werbung")))
    (is (not (facts/disclosure-acceptable? "JPN" " タイアップ "))
        "trimming must not turn a non-published wording into a published one")))
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
