(ns advertising.registry-test
  (:require [clojure.test :refer [deftest is]]
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

(deftest history-is-append-only
  (let [c1 (r/register-campaign-placement "campaign-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-campaign-placement "campaign-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-PLC-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-PLC-000001" (get-in hist2 [1 "record_id"])))))
