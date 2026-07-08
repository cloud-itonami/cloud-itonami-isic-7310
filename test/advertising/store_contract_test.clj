(ns advertising.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [advertising.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sato Bakery" (:client-name (store/campaign s "campaign-1"))))
      (is (= "JPN" (:jurisdiction (store/campaign s "campaign-1"))))
      (is (= 500000 (:proposed-media-spend (store/campaign s "campaign-1"))))
      (is (= 800000 (:authorized-budget (store/campaign s "campaign-1"))))
      (is (false? (:misleading-claim-risk-unresolved? (store/campaign s "campaign-1"))))
      (is (= 900000 (:proposed-media-spend (store/campaign s "campaign-3"))))
      (is (true? (:misleading-claim-risk-unresolved? (store/campaign s "campaign-4"))))
      (is (false? (:campaign-placed? (store/campaign s "campaign-1"))))
      (is (= ["campaign-1" "campaign-2" "campaign-3" "campaign-4"]
             (mapv :id (store/all-campaigns s))))
      (is (nil? (store/risk-screen-of s "campaign-1")))
      (is (nil? (store/media-plan-of s "campaign-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/placement-history s)))
      (is (zero? (store/next-placement-sequence s "JPN")))
      (is (false? (store/campaign-already-placed? s "campaign-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :campaign/upsert
                                 :value {:id "campaign-1" :client-name "Sato Bakery"}})
        (is (= "Sato Bakery" (:client-name (store/campaign s "campaign-1"))))
        (is (= 800000 (:authorized-budget (store/campaign s "campaign-1"))) "unrelated field preserved"))
      (testing "media-plan / risk-screen payloads commit and read back"
        (store/commit-record! s {:effect :media-plan/set :path ["campaign-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/media-plan-of s "campaign-1")))
        (store/commit-record! s {:effect :risk-screen/set :path ["campaign-1"]
                                 :payload {:campaign-id "campaign-1" :verdict :resolved}})
        (is (= {:campaign-id "campaign-1" :verdict :resolved} (store/risk-screen-of s "campaign-1"))))
      (testing "campaign placement drafts a record and advances the sequence"
        (store/commit-record! s {:effect :campaign/mark-placed :path ["campaign-1"]})
        (is (= "JPN-PLC-000000" (get (first (store/placement-history s)) "record_id")))
        (is (= "campaign-placement-draft" (get (first (store/placement-history s)) "kind")))
        (is (true? (:campaign-placed? (store/campaign s "campaign-1"))))
        (is (= 1 (count (store/placement-history s))))
        (is (= 1 (store/next-placement-sequence s "JPN")))
        (is (true? (store/campaign-already-placed? s "campaign-1")))
        (is (false? (store/campaign-already-placed? s "campaign-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/campaign s "nope")))
    (is (= [] (store/all-campaigns s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/placement-history s)))
    (is (zero? (store/next-placement-sequence s "JPN")))
    (store/with-campaigns s {"x" {:id "x" :client-name "n"
                               :proposed-media-spend 500000 :authorized-budget 800000
                               :misleading-claim-risk-unresolved? false
                               :campaign-placed? false
                               :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:client-name (store/campaign s "x"))))))
