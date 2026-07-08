(ns advertising.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/place-campaign` must NEVER be a member of
  any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [advertising.phase :as phase]))

(deftest place-campaign-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real campaign placement"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/place-campaign))
          (str "phase " n " must not auto-commit :actuation/place-campaign")))))

(deftest risk-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :risk/screen))
          (str "phase " n " must not auto-commit :risk/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":campaign/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:campaign/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :campaign/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/place-campaign} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :campaign/intake} :commit)))))
