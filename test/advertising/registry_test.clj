(ns advertising.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [advertising.registry :as r]))

;; ----------------------------- media-spend-exceeds-authorized-budget? -----------------------------

(deftest not-exceeded-when-within-authorized-budget
  (is (not (r/media-spend-exceeds-authorized-budget? {:proposed-media-spend 500000 :authorized-budget 800000})))
  (is (not (r/media-spend-exceeds-authorized-budget? {:proposed-media-spend 800000 :authorized-budget 800000}))))

(deftest exceeded-when-over-authorized-budget
  (is (r/media-spend-exceeds-authorized-budget? {:proposed-media-spend 900000 :authorized-budget 800000}))
  (is (r/media-spend-exceeds-authorized-budget? {:proposed-media-spend 800001 :authorized-budget 800000})))

(deftest exceeded-is-false-on-missing-fields
  (is (not (r/media-spend-exceeds-authorized-budget? {})))
  (is (not (r/media-spend-exceeds-authorized-budget? {:proposed-media-spend 900000}))))

;; ----------------------------- register-campaign-placement -----------------------------

(deftest placement-is-a-draft-not-a-real-placement
  (let [result (r/register-campaign-placement "campaign-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest placement-assigns-placement-number
  (let [result (r/register-campaign-placement "campaign-1" "JPN" 7)]
    (is (= (get result "placement_number") "JPN-PLC-000007"))
    (is (= (get-in result ["record" "campaign_id"]) "campaign-1"))
    (is (= (get-in result ["record" "kind"]) "campaign-placement-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest placement-validation-rules
  (is (thrown? Exception (r/register-campaign-placement "" "JPN" 0)))
  (is (thrown? Exception (r/register-campaign-placement "campaign-1" "" 0)))
  (is (thrown? Exception (r/register-campaign-placement "campaign-1" "JPN" -1))))

;; ----------------------------- creator-tieup-fee-exceeds-authorized-budget? -----------------------------

(deftest tieup-fee-is-checked-on-top-of-the-media-plan-not-alone
  (testing "the whole point of the combined ceiling: a fee that is affordable ON ITS OWN can still blow the client's authorization"
    (let [c {:proposed-media-spend 500000 :creator-tieup-fee 400000 :authorized-budget 800000}]
      (is (< (:creator-tieup-fee c) (:authorized-budget c)) "the fee alone fits")
      (is (r/creator-tieup-fee-exceeds-authorized-budget? c) "the combined spend does not"))))

(deftest tieup-fee-not-exceeded-when-combined-spend-fits
  (is (not (r/creator-tieup-fee-exceeds-authorized-budget?
            {:proposed-media-spend 500000 :creator-tieup-fee 200000 :authorized-budget 800000})))
  (is (not (r/creator-tieup-fee-exceeds-authorized-budget?
            {:proposed-media-spend 500000 :creator-tieup-fee 300000 :authorized-budget 800000}))
      "exactly at the ceiling is not over it"))

(deftest tieup-fee-exceeded-is-false-on-missing-fields
  (testing "a campaign with no fee recorded cannot exceed anything -- never guess a fee"
    (is (not (r/creator-tieup-fee-exceeds-authorized-budget? {})))
    (is (not (r/creator-tieup-fee-exceeds-authorized-budget?
              {:proposed-media-spend 900000 :authorized-budget 800000}))
        "the media-spend ceiling is a DIFFERENT check's job")
    (is (not (r/creator-tieup-fee-exceeds-authorized-budget? {:creator-tieup-fee 900000}))))
  (testing "a tie-up on a campaign with no media plan yet is still ceiling-checked"
    (is (r/creator-tieup-fee-exceeds-authorized-budget?
         {:creator-tieup-fee 900000 :authorized-budget 800000}))))

;; ----------------------------- disclosure-label-missing? -----------------------------

(deftest disclosure-label-missing-on-blank-or-absent
  (is (r/disclosure-label-missing? {}))
  (is (r/disclosure-label-missing? {:disclosure-label nil}))
  (is (r/disclosure-label-missing? {:disclosure-label ""}))
  (is (r/disclosure-label-missing? {:disclosure-label "   "}))
  (is (not (r/disclosure-label-missing? {:disclosure-label "PR"})))
  (testing "presence only -- whether the label is one an authority publishes is advertising.facts' question"
    (is (not (r/disclosure-label-missing? {:disclosure-label "タイアップ"})))))

;; ----------------------------- register-creator-tieup-order -----------------------------

(deftest tieup-order-is-a-draft-not-a-real-order
  (let [result (r/register-creator-tieup-order "campaign-5" "JPN" :youtube "@sato-bakery-review" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest tieup-order-assigns-order-number-and-records-the-creator
  (let [result (r/register-creator-tieup-order "campaign-5" "JPN" :youtube "@sato-bakery-review" 7)]
    (is (= (get result "order_number") "JPN-TIE-000007"))
    (is (= (get-in result ["record" "campaign_id"]) "campaign-5"))
    (is (= (get-in result ["record" "kind"]) "creator-tieup-order-draft"))
    (is (= (get-in result ["record" "platform"]) "youtube"))
    (is (= (get-in result ["record" "creator_handle"]) "@sato-bakery-review"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest tieup-order-number-namespace-is-distinct-from-placement
  (testing "an agency reading a record id must be able to tell a placement from a tie-up order"
    (is (not= (get (r/register-creator-tieup-order "campaign-5" "JPN" :youtube "@c" 0) "order_number")
              (get (r/register-campaign-placement "campaign-5" "JPN" 0) "placement_number")))))

(deftest tieup-order-validation-rules
  (is (thrown? Exception (r/register-creator-tieup-order "" "JPN" :youtube "@c" 0)))
  (is (thrown? Exception (r/register-creator-tieup-order "campaign-5" "" :youtube "@c" 0)))
  (is (thrown? Exception (r/register-creator-tieup-order "campaign-5" "JPN" nil "@c" 0)))
  (is (thrown? Exception (r/register-creator-tieup-order "campaign-5" "JPN" :youtube nil 0)))
  (is (thrown? Exception (r/register-creator-tieup-order "campaign-5" "JPN" :youtube "  " 0)))
  (is (thrown? Exception (r/register-creator-tieup-order "campaign-5" "JPN" :youtube "@c" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-campaign-placement "campaign-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-campaign-placement "campaign-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-PLC-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-PLC-000001" (get-in hist2 [1 "record_id"])))))
