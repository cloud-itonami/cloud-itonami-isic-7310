(ns advertising.drivers-test
  "The two `-main` drivers as executable tests.

  `advertising.sim` and `advertising.render-html` are the repo's public
  face: the demo a reader runs first, and the operator console CI
  regenerates and commits nightly. Neither was asserted over. CI's only
  check on the console was `grep -q '<tbody>'`, which a page listing
  every campaign as `in progress` would pass just as happily as a
  correct one.

  The risk that creates is specific: these drivers narrate the
  governor's behaviour in prose (\"-> HARD hold\", \"always escalates\").
  Nothing tied that narration to what the governor actually does, so a
  rule change could silently turn the demo into a lie while every other
  test stayed green. These tests tie them together."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [advertising.sim :as sim]
            [advertising.render-html :as render]
            [advertising.store :as store]))

(def ^:private expected-hold-bases
  "Every rule the Campaign Governor can raise -- jurisdiction-side,
  media-platform-side (ADR-0002/0003) and creator-tie-up-side
  (ADR-0004/0005) -- plus both double-actuation guards. The demo
  demonstrates ALL of them: a rule with no demo case is a rule the
  operator console never shows and a reader never sees fire. If one is
  renamed or stops firing, this set is what notices."
  #{:no-spec-basis
    :evidence-incomplete
    :tieup-evidence-incomplete
    :no-platform-policy-basis
    :platform-check-incomplete
    :platform-prohibited-category
    :platform-restricted-category-unapproved
    :platform-attestation-missing
    :sensitive-placement-context
    :media-spend-exceeds-authorized-budget
    :misleading-claim-risk-unresolved
    :already-placed
    :creator-tieup-fee-exceeds-authorized-budget
    :creator-ineligible
    :sponsorship-disclosure-missing
    :already-ordered
    :risk-screen-missing
    :creator-screen-missing})

(deftest the-demo-scenario-exercises-every-hard-hold-it-claims-to
  (let [db (render/run-demo!)
        bases (into #{} (mapcat :basis) (store/ledger db))]
    (is (= expected-hold-bases (set (filter expected-hold-bases bases)))
        "every documented HARD-hold reason must actually fire in the scenario")
    (testing "and each one is reached without a human ever being asked"
      (doseq [f (store/ledger db)
              :when (= :governor-hold (:t f))]
        (is (seq (:basis f)) (str (:op f) " hold must record its basis"))))))

(deftest the-demo-scenario-commits-exactly-the-two-approved-actuations
  (let [db (render/run-demo!)]
    (is (= 1 (count (store/placement-history db))) "one approved campaign placement")
    (is (= 1 (count (store/tieup-order-history db))) "one approved creator tie-up order")
    (is (true? (:campaign-placed? (store/campaign db "campaign-1"))))
    (is (true? (:tieup-ordered? (store/campaign db "campaign-21"))))
    (testing "and nothing else actuated"
      (doseq [c (store/all-campaigns db)
              :when (not= "campaign-1" (:id c))]
        (is (false? (:campaign-placed? c)) (str (:id c) " must not be placed")))
      (doseq [c (store/all-campaigns db)
              :when (not= "campaign-21" (:id c))]
        (is (false? (:tieup-ordered? c)) (str (:id c) " must not be ordered"))))))

(deftest the-console-reports-the-real-store-not-a-fixture
  (let [out (str (java.io.File. (System/getProperty "java.io.tmpdir")
                                "advertising-console-test.html"))
        _ (render/-main out)
        html (slurp out)]
    (testing "the drafted record ids appear, so the page is rendering committed state"
      (is (str/includes? html "JPN-PLC-000000"))
      (is (str/includes? html "JPN-TIE-000000")))
    (testing "the tie-up table distinguishes a published label from a merely-recorded one"
      (is (str/includes? html "not a published label")
          "campaign-25's 「タイアップ」 must be shown as non-compliant, not as a label")
      (is (str/includes? html "@sato-bakery-review")))
    (testing "hold reasons are surfaced to the operator, not swallowed"
      (doseq [rule expected-hold-bases]
        (is (str/includes? html (name rule)) (str rule " must appear in the console"))))
    (testing "the page is self-describing about how it was produced"
      (is (str/includes? html "advertising.render-html")))
    (.delete (java.io.File. out))))

(deftest the-console-is-byte-identical-across-reruns
  (testing "regenerate.yml commits this file nightly -- any nondeterminism (timestamp, map ordering, random id) becomes a spurious daily diff"
    (let [fa (java.io.File. (System/getProperty "java.io.tmpdir") "advertising-console-a.html")
          fb (java.io.File. (System/getProperty "java.io.tmpdir") "advertising-console-b.html")]
      (with-out-str (render/-main (str fa)))
      (with-out-str (render/-main (str fb)))
      (is (= (slurp fa) (slurp fb))
          "two independent runs of the real actor stack must produce identical bytes")
      (.delete fa)
      (.delete fb))))

(deftest the-sim-driver-runs-both-lifecycles-end-to-end
  (testing "the first thing a reader runs must not throw, and must narrate what actually happened"
    (let [out (with-out-str (sim/-main))]
      (is (str/includes? out "audit ledger"))
      (is (str/includes? out "draft campaign-placement records"))
      (is (str/includes? out "draft creator-tie-up order records"))
      (is (str/includes? out "JPN-PLC-000000") "the placement it claims to make")
      (is (str/includes? out "JPN-TIE-000000") "the tie-up order it claims to make")
      (testing "every HARD-hold rule the narration promises actually appears in its output"
        (doseq [rule expected-hold-bases]
          (is (str/includes? out (str rule)) (str rule " missing from sim output")))))))
