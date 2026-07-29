(ns advertising.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: NO real-world actuation op -- `:actuation/place-campaign`
  or `:actuation/order-creator-tieup` -- may EVER be a member of any
  phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.set :as set]
            [advertising.phase :as phase]))

(deftest place-campaign-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real campaign placement"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/place-campaign))
          (str "phase " n " must not auto-commit :actuation/place-campaign")))))

(deftest order-creator-tieup-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real creator tie-up order either -- commissioning a named YouTube channel / influencer on a client's behalf is always a human call"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/order-creator-tieup))
          (str "phase " n " must not auto-commit :actuation/order-creator-tieup")))))

(deftest no-actuation-op-is-ever-auto-eligible
  (testing "the invariant stated over the SET, so a future actuation op added to actuation-ops is covered without a new test"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (empty? (set/intersection auto phase/actuation-ops))
          (str "phase " n " must not auto-commit any actuation op")))))

(deftest every-actuation-op-is-a-governed-write
  (testing "an actuation op that is not in write-ops would bypass the phase gate entirely"
    (is (set/subset? phase/actuation-ops phase/write-ops))))

(deftest risk-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :risk/screen))
          (str "phase " n " must not auto-commit :risk/screen")))))

(deftest creator-screen-never-auto-at-any-phase
  (testing "creator-eligibility screening takes the same posture as misleading-claim-risk screening"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :creator/screen))
          (str "phase " n " must not auto-commit :creator/screen")))))

(deftest tieup-ops-are-write-gated-from-phase-2
  (testing "the creator-tie-up lifecycle's non-actuation ops become writable at phase 2, alongside the placement lifecycle's"
    (is (not (contains? (:writes (get phase/phases 1)) :tieup/verify)))
    (is (contains? (:writes (get phase/phases 2)) :tieup/verify))
    (is (contains? (:writes (get phase/phases 2)) :creator/screen))
    (is (not (contains? (:writes (get phase/phases 2)) :actuation/order-creator-tieup))
        "the actuation itself stays disabled until phase 3")))
(deftest platform-verify-never-auto-at-any-phase
  (testing "signing off that a campaign conforms to a media platform's own ad policy is a compliance judgement, never an auto-commit (ADR-0002)"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :platform/verify))
          (str "phase " n " must not auto-commit :platform/verify")))))

(deftest platform-verify-is-enabled-from-phase-2
  (testing "platform conformance rides with the other verification writes"
    (is (not (contains? (:writes (get phase/phases 1)) :platform/verify)))
    (is (contains? (:writes (get phase/phases 2)) :platform/verify))
    (is (contains? (:writes (get phase/phases 3)) :platform/verify))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":campaign/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:campaign/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :campaign/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/place-campaign} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/order-creator-tieup} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :campaign/intake} :commit)))))
