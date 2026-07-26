(ns advertising.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean campaign through
  intake -> media-plan verification -> media-platform ad-policy
  conformance -> misleading-claim-risk screening
  -> campaign-placement proposal (always escalates) -> human approval
  -> commit, then shows the HARD holds.

  Five of them are media-platform-side (ADR-0002): a platform with no
  transcribed ad policy, a category the platform itself prohibits, a
  restricted category with neither advertiser approval nor an allowed
  jurisdiction, a generative surface where ad/answer distinguishability
  was never attested, and a placement requested against a context the
  platform refuses to serve ads near.

  The rest are jurisdiction-side (a jurisdiction with no spec-
  basis, a proposed media spend exceeding its own authorized budget,
  an unresolved misleading-claim risk screened directly via `:risk/
  screen` [never via an actuation op against an unscreened campaign --
  see this actor's own governor ns docstring / the lesson
  `parksafety`'s ADR-2607071922 Decision 5, `eldercare`'s, `museum`'s,
  `conservation`'s, `salon`'s, `entertainment`'s, `casework`'s,
  `hospital`'s, `facility`'s, `school`'s, `association`'s, `leasing`'s,
  `behavioral`'s, `secondary`'s, `card`'s, `water`'s, `telecom`'s,
  `aerospace`'s, `recovery`'s, `consulting`'s, `union`'s,
  `congregation`'s, `fab`'s, `energy`'s, `care`'s, `navigator`'s,
  `learning`'s and `banking`'s ADR-0001s already recorded], and a
  double placement of an already-processed campaign) that never reach
  a human at all, and prints the audit ledger + the draft placement
  records."
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

    (println "== actuation/place-campaign campaign-3 (spend 900000 > authorized 800000 -> HARD hold) ==")
    (println (exec! actor "t7" {:op :actuation/place-campaign :subject "campaign-3"} operator))

    (println "== risk/screen campaign-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t8" {:op :risk/screen :subject "campaign-4"} operator))

    (println "== actuation/place-campaign campaign-1 AGAIN (double-placement -> HARD hold) ==")
    (println (exec! actor "t9" {:op :actuation/place-campaign :subject "campaign-1"} operator))

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

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft campaign-placement records ==")
    (doseq [r (store/placement-history db)] (println r))))
