(ns advertising.sim
  "Demo driver -- `clojure -M:dev:run`. Walks BOTH governed lifecycles
  and every rule the Campaign Governor can raise.

  Lifecycle 1 (campaign placement): intake -> media-plan verification
  -> media-platform ad-policy conformance -> misleading-claim-risk
  screening -> campaign-placement proposal (always escalates) -> human
  approval -> commit. Then the HARD holds it cannot get past.

  Jurisdiction-side holds: a jurisdiction with no spec-basis, a
  proposed media spend exceeding its own authorized budget, an
  unresolved misleading-claim risk screened directly via `:risk/screen`
  (never via an actuation op against an unscreened campaign -- see this
  actor's own governor ns docstring), a placement attempted with no
  screening on file at all (ADR-0005), and a double placement.

  Media-platform-side holds (ADR-0002/0003): a platform with no
  transcribed ad policy, a category the platform itself prohibits, a
  restricted category with neither advertiser approval nor an allowed
  jurisdiction, a generative surface where ad/answer distinguishability
  was never attested, and a placement requested against a context the
  platform refuses to serve ads near.

  Lifecycle 2 (creator tie-up -- YouTube / influencer, ADR-0004):
  creator-eligibility screening -> tie-up evidence/disclosure brief ->
  tie-up order proposal (always escalates) -> human approval -> commit.
  Then its own HARD holds: a combined media-spend-plus-tie-up-fee past
  the client's own authorization, an ineligible creator, a tie-up with
  NO recorded sponsorship-disclosure label, one whose recorded label is
  not among the jurisdiction's own published examples, an order
  attempted with no creator screening on file (ADR-0005), and a double
  order.

  Ends by printing the audit ledger, the draft placement records and
  the draft tie-up order records."
  (:require [langgraph.graph :as g]
            [advertising.platform :as platform]
            [advertising.store :as store]
            [advertising.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :agency-operator :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== campaign/intake campaign-1 (JPN, clean; spend 500000 within budget 800000, no misleading-claim risk) ==")
    (println (exec! actor "t1" {:op :campaign/intake :subject "campaign-1"
                                :patch {:id "campaign-1" :client-name "Sato Bakery"}} operator))

    (println "== actuation/place-campaign campaign-1 with nothing assessed yet (-> HARD hold: evidence, platform check AND screening all missing) ==")
    (println (exec! actor "t1b" {:op :actuation/place-campaign :subject "campaign-1"} operator))

    (println "== media-plan/verify campaign-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :media-plan/verify :subject "campaign-1"} operator))
    (println (approve! actor "t2"))

    (println "== platform/verify campaign-1 (chatgpt-ads, :local-services permitted; escalates -- human approves) ==")
    (println (exec! actor "t2b" {:op :platform/verify :subject "campaign-1"} operator))
    (println (approve! actor "t2b"))

    (println "== risk/screen campaign-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :risk/screen :subject "campaign-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/place-campaign campaign-1 (always escalates -- actuation/place-campaign) ==")
    (let [r (exec! actor "t4" {:op :actuation/place-campaign :subject "campaign-1"} operator)]
      (println r)
      (println "-- human agency-operator approves --")
      (println (approve! actor "t4")))

    (println "== media-plan/verify campaign-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t5" {:op :media-plan/verify :subject "campaign-2" :no-spec? true} operator))

    (println "== media-plan/verify + platform/verify campaign-3 (escalates -- human approves; sets up the budget-exceeded test) ==")
    (println (exec! actor "t6" {:op :media-plan/verify :subject "campaign-3"} operator))
    (println (approve! actor "t6"))
    (println (exec! actor "t6b" {:op :platform/verify :subject "campaign-3"} operator))
    (println (approve! actor "t6b"))

    (println "== actuation/place-campaign campaign-3 BEFORE any risk screening (-> HARD hold, ADR-0003) ==")
    (println (exec! actor "t6b" {:op :actuation/place-campaign :subject "campaign-3"} operator))

    (println "== risk/screen campaign-3 (clean; escalates -- human approves) ==")
    (println (exec! actor "t6c" {:op :risk/screen :subject "campaign-3"} operator))
    (println (approve! actor "t6c"))

    (println "== actuation/place-campaign campaign-3 (spend 900000 > authorized 800000 -> HARD hold) ==")
    (println (exec! actor "t7" {:op :actuation/place-campaign :subject "campaign-3"} operator))

    (println "== risk/screen campaign-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t8" {:op :risk/screen :subject "campaign-4"} operator))

    (println "== actuation/place-campaign campaign-1 AGAIN (double-placement -> HARD hold) ==")
    (println (exec! actor "t9" {:op :actuation/place-campaign :subject "campaign-1"} operator))

    ;; ---------------- creator tie-up (YouTube / influencer) ----------------

    (println "== creator/screen campaign-21 (@sato-bakery-review / youtube, clean; escalates -- human approves) ==")
    (println (exec! actor "u1" {:op :creator/screen :subject "campaign-21"} operator))
    (println (approve! actor "u1"))

    (println "== actuation/order-creator-tieup campaign-21 before any tie-up brief (-> HARD hold: tieup-evidence-incomplete) ==")
    (println (exec! actor "u1b" {:op :actuation/order-creator-tieup :subject "campaign-21"} operator))

    (println "== tieup/verify campaign-21 (JPN ステマ規制 開示基準を引用; escalates -- human approves) ==")
    (println (exec! actor "u2" {:op :tieup/verify :subject "campaign-21"} operator))
    (println (approve! actor "u2"))

    (println "== actuation/order-creator-tieup campaign-21 (always escalates -- actuation/order-creator-tieup) ==")
    (let [r (exec! actor "u3" {:op :actuation/order-creator-tieup :subject "campaign-21"} operator)]
      (println r)
      (println "-- human agency-operator approves --")
      (println (approve! actor "u3")))

    (println "== tieup/verify campaign-22 (escalates -- human approves; sets up the combined-spend test) ==")
    (println (exec! actor "u4" {:op :tieup/verify :subject "campaign-22"} operator))
    (println (approve! actor "u4"))

    (println "== actuation/order-creator-tieup campaign-22 BEFORE any creator screening (-> HARD hold, ADR-0003) ==")
    (println (exec! actor "u4b" {:op :actuation/order-creator-tieup :subject "campaign-22"} operator))

    (println "== creator/screen campaign-22 (clean; escalates -- human approves) ==")
    (println (exec! actor "u4c" {:op :creator/screen :subject "campaign-22"} operator))
    (println (approve! actor "u4c"))

    (println "== actuation/order-creator-tieup campaign-22 (media 500000 + fee 400000 > authorized 800000 -> HARD hold) ==")
    (println (exec! actor "u5" {:op :actuation/order-creator-tieup :subject "campaign-22"} operator))

    (println "== creator/screen campaign-23 (ineligible creator -> HARD hold, never reaches a human) ==")
    (println (exec! actor "u6" {:op :creator/screen :subject "campaign-23"} operator))

    (println "== tieup/verify + creator/screen campaign-24 (escalates -- human approves; sets up the no-disclosure test) ==")
    (println (exec! actor "u7" {:op :tieup/verify :subject "campaign-24"} operator))
    (println (approve! actor "u7"))
    (println (exec! actor "u7b" {:op :creator/screen :subject "campaign-24"} operator))
    (println (approve! actor "u7b"))

    (println "== actuation/order-creator-tieup campaign-24 (開示表示なし -> HARD hold) ==")
    (println (exec! actor "u8" {:op :actuation/order-creator-tieup :subject "campaign-24"} operator))

    (println "== tieup/verify + creator/screen campaign-25 (escalates -- human approves; sets up the unrecognized-label test) ==")
    (println (exec! actor "u9" {:op :tieup/verify :subject "campaign-25"} operator))
    (println (approve! actor "u9"))
    (println (exec! actor "u9b" {:op :creator/screen :subject "campaign-25"} operator))
    (println (approve! actor "u9b"))

    (println "== actuation/order-creator-tieup campaign-25 (開示表示「タイアップ」は当局の公表例に無い -> HARD hold) ==")
    (println (exec! actor "u10" {:op :actuation/order-creator-tieup :subject "campaign-25"} operator))

    (println "== actuation/order-creator-tieup campaign-21 AGAIN (double-order -> HARD hold) ==")
    (println (exec! actor "u11" {:op :actuation/order-creator-tieup :subject "campaign-21"} operator))
    ;; ---- media-platform HARD holds (ADR-0002) ----
    (println "== platform/verify campaign-5 (media platform with no transcribed policy -> HARD hold) ==")
    (println (exec! actor "t10" {:op :platform/verify :subject "campaign-5"} operator))

    (println "== platform/verify campaign-6 (:gambling — prohibited by the platform's own policy -> HARD hold) ==")
    (println (exec! actor "t11" {:op :platform/verify :subject "campaign-6"} operator))

    (println "== platform/verify campaign-7 (:financial-services — restricted, unapproved advertiser + non-US -> HARD hold) ==")
    (println (exec! actor "t12" {:op :platform/verify :subject "campaign-7"} operator))

    (println "== platform/verify campaign-8 (generative surface, distinguishability not attested -> HARD hold) ==")
    (println (exec! actor "t13" {:op :platform/verify :subject "campaign-8"} operator))

    (println "== platform/verify campaign-9 (placement requested against an excluded context -> HARD hold) ==")
    (println (exec! actor "t14" {:op :platform/verify :subject "campaign-9"} operator))

    ;; ---- the four platforms disagree (ADR-0003) ----
    (println "== cross-platform disposition, one category at a time ==")
    (doseq [c [:local-services :travel-experiences :legal-services :political :ticket-reselling]]
      (println c "->" (platform/cross-platform-disposition c)))

    (println "== platform/verify campaign-10 (google-ads, :local-services unnamed on an OPEN set -> clean, escalates) ==")
    (println (exec! actor "t15" {:op :platform/verify :subject "campaign-10"} operator))
    (println (approve! actor "t15"))

    (println "== platform/verify campaign-11 (meta-ads in DEU, EU DSA beneficiary/payer disclosure not attested -> HARD hold) ==")
    (println (exec! actor "t16" {:op :platform/verify :subject "campaign-11"} operator))

    (println "== platform/verify campaign-12 (microsoft-advertising, :travel-experiences RESTRICTED there but permitted on chatgpt-ads -> HARD hold) ==")
    (println (exec! actor "t17" {:op :platform/verify :subject "campaign-12"} operator))

    (println "== platform/verify campaign-13 (line-yahoo-ads, :policy-read :partial -> :not-transcribed, HARD hold: we did not read it, the platform did not refuse it) ==")
    (println (exec! actor "t18" {:op :platform/verify :subject "campaign-13"} operator))

    (println "== platform/verify campaign-14 (youtube-ads, :gambling restricted BY INCORPORATION from google-ads, country table untranscribed -> HARD hold) ==")
    (println (exec! actor "t19" {:op :platform/verify :subject "campaign-14"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft campaign-placement records ==")
    (doseq [r (store/placement-history db)] (println r))

    (println "== draft creator-tie-up order records ==")
    (doseq [r (store/tieup-order-history db)] (println r))))
