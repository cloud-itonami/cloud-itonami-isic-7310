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
      (is (= ["campaign-1" "campaign-2" "campaign-3" "campaign-4"
              "campaign-5" "campaign-6" "campaign-7" "campaign-8" "campaign-9"]
             (mapv :id (store/all-campaigns s))))
      (is (nil? (store/risk-screen-of s "campaign-1")))
      (is (nil? (store/media-plan-of s "campaign-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/placement-history s)))
      (is (zero? (store/next-placement-sequence s "JPN")))
      (is (false? (store/campaign-already-placed? s "campaign-1")))
      (testing "creator tie-up fields read back identically on both backends"
        (is (= "@sato-bakery-review" (:creator-handle (store/campaign s "campaign-5"))))
        (is (= :youtube (:creator-platform (store/campaign s "campaign-5"))))
        (is (= 200000 (:creator-tieup-fee (store/campaign s "campaign-5"))))
        (is (= "PR" (:disclosure-label (store/campaign s "campaign-5"))))
        (is (false? (:creator-eligibility-issue? (store/campaign s "campaign-5"))))
        (is (true? (:creator-eligibility-issue? (store/campaign s "campaign-7"))))
        (is (nil? (:disclosure-label (store/campaign s "campaign-8"))))
        (is (= "タイアップ" (:disclosure-label (store/campaign s "campaign-9"))))
        (is (nil? (:creator-handle (store/campaign s "campaign-1")))
            "the placement-only campaigns carry no tie-up at all")
        (is (nil? (store/creator-screen-of s "campaign-5")))
        (is (nil? (store/tieup-brief-of s "campaign-5")))
        (is (= [] (store/tieup-order-history s)))
        (is (zero? (store/next-tieup-sequence s "JPN")))
        (is (false? (store/tieup-already-ordered? s "campaign-5")))))))

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
      (testing "creator-screen / tieup-brief payloads commit and read back"
        (store/commit-record! s {:effect :creator-screen/set :path ["campaign-5"]
                                 :payload {:campaign-id "campaign-5" :verdict :eligible}})
        (is (= {:campaign-id "campaign-5" :verdict :eligible} (store/creator-screen-of s "campaign-5")))
        (store/commit-record! s {:effect :tieup-brief/set :path ["campaign-5"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/tieup-brief-of s "campaign-5")))
        (is (nil? (store/risk-screen-of s "campaign-5"))
            "the two screening collections are separate keyspaces"))
      (testing "creator tie-up order drafts a record and advances its OWN sequence"
        (store/commit-record! s {:effect :tieup/mark-ordered :path ["campaign-5"]})
        (is (= "JPN-TIE-000000" (get (first (store/tieup-order-history s)) "record_id")))
        (is (= "creator-tieup-order-draft" (get (first (store/tieup-order-history s)) "kind")))
        (is (= "youtube" (get (first (store/tieup-order-history s)) "platform")))
        (is (= "@sato-bakery-review" (get (first (store/tieup-order-history s)) "creator_handle")))
        (is (true? (:tieup-ordered? (store/campaign s "campaign-5"))))
        (is (= 1 (count (store/tieup-order-history s))))
        (is (= 1 (store/next-tieup-sequence s "JPN")))
        (is (true? (store/tieup-already-ordered? s "campaign-5")))
        (is (false? (store/tieup-already-ordered? s "campaign-6"))))
      (testing "the two actuations keep separate guards, sequences and histories"
        (is (false? (store/campaign-already-placed? s "campaign-5"))
            "ordering a tie-up must not mark the campaign placed")
        (is (false? (store/tieup-already-ordered? s "campaign-1"))
            "placing campaign-1 above must not mark a tie-up ordered")
        (is (= 1 (store/next-placement-sequence s "JPN")))
        (is (= 1 (store/next-tieup-sequence s "JPN")))
        (is (= 1 (count (store/placement-history s))))
        (is (= 1 (count (store/tieup-order-history s)))))
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
    (is (= [] (store/tieup-order-history s)))
    (is (zero? (store/next-placement-sequence s "JPN")))
    (is (zero? (store/next-tieup-sequence s "JPN")))
    (store/with-campaigns s {"x" {:id "x" :client-name "n"
                               :proposed-media-spend 500000 :authorized-budget 800000
                               :misleading-claim-risk-unresolved? false
                               :creator-eligibility-issue? false
                               :campaign-placed? false :tieup-ordered? false
                               :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:client-name (store/campaign s "x"))))))
