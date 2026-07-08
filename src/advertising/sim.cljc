(ns advertising.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean campaign through
  intake -> media-plan verification -> misleading-claim-risk screening
  -> campaign-placement proposal (always escalates) -> human approval
  -> commit, then shows four HARD holds (a jurisdiction with no spec-
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

    (println "== media-plan/verify campaign-3 (escalates -- human approves; sets up the budget-exceeded test) ==")
    (println (exec! actor "t6" {:op :media-plan/verify :subject "campaign-3"} operator))
    (println (approve! actor "t6"))

    (println "== actuation/place-campaign campaign-3 (spend 900000 > authorized 800000 -> HARD hold) ==")
    (println (exec! actor "t7" {:op :actuation/place-campaign :subject "campaign-3"} operator))

    (println "== risk/screen campaign-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t8" {:op :risk/screen :subject "campaign-4"} operator))

    (println "== actuation/place-campaign campaign-1 AGAIN (double-placement -> HARD hold) ==")
    (println (exec! actor "t9" {:op :actuation/place-campaign :subject "campaign-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft campaign-placement records ==")
    (doseq [r (store/placement-history db)] (println r))))
