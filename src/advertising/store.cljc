(ns advertising.store
  "SSoT for the advertising actor, behind a `Store` protocol so the
  backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/advertising/store_contract_test.clj), which is the whole
  point: the actor, the Campaign Governor and the audit ledger never
  know which SSoT they run on.

  Like `leasing`/`underwriting`/`testlab`/`clinic`/`veterinary`/
  `funeral`/`parksafety`/`salon`/`entertainment`/`facility`/
  `consulting`, this actor has ONE actuation event (placing a real
  campaign on the client's behalf) acting on a `campaign` entity, with
  its OWN history collection, sequence counter and dedicated double-
  actuation-guard boolean (`:campaign-placed?`, never a `:status`
  value) -- the same discipline every prior sibling governor's guards
  establish, informed by `cloud-itonami-isic-6492`'s status-lifecycle
  bug (ADR-2607071320).

  The ledger stays append-only on every backend: 'which campaign was
  screened for an unresolved misleading-claim risk, which campaign was
  placed, on what jurisdictional basis, approved by whom' is always a
  query over an immutable log -- the audit trail a client trusting an
  agency needs, and the evidence an agency needs if a placement
  decision is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [advertising.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (campaign [s id])
  (all-campaigns [s])
  (risk-screen-of [s campaign-id] "committed misleading-claim-risk screening verdict for a campaign, or nil")
  (media-plan-of [s campaign-id] "committed media-plan evidence assessment, or nil")
  (ledger [s])
  (placement-history [s] "the append-only campaign-placement history (advertising.registry drafts)")
  (next-placement-sequence [s jurisdiction] "next placement-number sequence for a jurisdiction")
  (campaign-already-placed? [s campaign-id] "has this campaign already been placed?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-campaigns [s campaigns] "replace/seed the campaign directory (map id->campaign)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained campaign set covering the actuation
  lifecycle (placing a campaign) so the actor + tests run offline."
  []
  {:campaigns
   {"campaign-1" {:id "campaign-1" :client-name "Sato Bakery"
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "JPN" :status :intake}
    "campaign-2" {:id "campaign-2" :client-name "Atlantis Goods"
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "ATL" :status :intake}
    "campaign-3" {:id "campaign-3" :client-name "鈴木工務店"
                 :proposed-media-spend 900000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "JPN" :status :intake}
    "campaign-4" {:id "campaign-4" :client-name "田中青果"
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? true
                 :campaign-placed? false
                 :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- place-campaign!
  "Backend-agnostic `:campaign/mark-placed` -- looks up the campaign
  via the protocol and drafts the placement record, and returns
  {:result .. :campaign-patch ..} for the caller to persist."
  [s campaign-id]
  (let [c (campaign s campaign-id)
        seq-n (next-placement-sequence s (:jurisdiction c))
        result (registry/register-campaign-placement campaign-id (:jurisdiction c) seq-n)]
    {:result result
     :campaign-patch {:campaign-placed? true
                     :placement-number (get result "placement_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (campaign [_ id] (get-in @a [:campaigns id]))
  (all-campaigns [_] (sort-by :id (vals (:campaigns @a))))
  (risk-screen-of [_ id] (get-in @a [:risk-screens id]))
  (media-plan-of [_ campaign-id] (get-in @a [:media-plans campaign-id]))
  (ledger [_] (:ledger @a))
  (placement-history [_] (:placements @a))
  (next-placement-sequence [_ jurisdiction] (get-in @a [:placement-sequences jurisdiction] 0))
  (campaign-already-placed? [_ campaign-id] (boolean (get-in @a [:campaigns campaign-id :campaign-placed?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :campaign/upsert
      (swap! a update-in [:campaigns (:id value)] merge value)

      :media-plan/set
      (swap! a assoc-in [:media-plans (first path)] payload)

      :risk-screen/set
      (swap! a assoc-in [:risk-screens (first path)] payload)

      :campaign/mark-placed
      (let [campaign-id (first path)
            {:keys [result campaign-patch]} (place-campaign! s campaign-id)
            jurisdiction (:jurisdiction (campaign s campaign-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:placement-sequences jurisdiction] (fnil inc 0))
                       (update-in [:campaigns campaign-id] merge campaign-patch)
                       (update :placements registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-campaigns [s campaigns] (when (seq campaigns) (swap! a assoc :campaigns campaigns)) s))

(defn seed-db
  "A MemStore seeded with the demo campaign set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :media-plans {} :risk-screens {} :ledger [] :placement-sequences {}
                           :placements []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Compound values (media-plan/risk-screen payloads, ledger facts,
  placement records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses."
  {:campaign/id                        {:db/unique :db.unique/identity}
   :media-plan/campaign-id             {:db/unique :db.unique/identity}
   :risk-screen/campaign-id            {:db/unique :db.unique/identity}
   :ledger/seq                        {:db/unique :db.unique/identity}
   :placement/seq                     {:db/unique :db.unique/identity}
   :placement-sequence/jurisdiction   {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- campaign->tx [{:keys [id client-name proposed-media-spend authorized-budget
                            misleading-claim-risk-unresolved?
                            campaign-placed?
                            jurisdiction status placement-number]}]
  (cond-> {:campaign/id id}
    client-name                                   (assoc :campaign/client-name client-name)
    proposed-media-spend                          (assoc :campaign/proposed-media-spend proposed-media-spend)
    authorized-budget                              (assoc :campaign/authorized-budget authorized-budget)
    (some? misleading-claim-risk-unresolved?)      (assoc :campaign/misleading-claim-risk-unresolved? misleading-claim-risk-unresolved?)
    (some? campaign-placed?)                       (assoc :campaign/campaign-placed? campaign-placed?)
    jurisdiction                                    (assoc :campaign/jurisdiction jurisdiction)
    status                                          (assoc :campaign/status status)
    placement-number                                (assoc :campaign/placement-number placement-number)))

(def ^:private campaign-pull
  [:campaign/id :campaign/client-name :campaign/proposed-media-spend :campaign/authorized-budget
   :campaign/misleading-claim-risk-unresolved? :campaign/campaign-placed?
   :campaign/jurisdiction :campaign/status :campaign/placement-number])

(defn- pull->campaign [m]
  (when (:campaign/id m)
    {:id (:campaign/id m) :client-name (:campaign/client-name m)
     :proposed-media-spend (:campaign/proposed-media-spend m)
     :authorized-budget (:campaign/authorized-budget m)
     :misleading-claim-risk-unresolved? (boolean (:campaign/misleading-claim-risk-unresolved? m))
     :campaign-placed? (boolean (:campaign/campaign-placed? m))
     :jurisdiction (:campaign/jurisdiction m) :status (:campaign/status m)
     :placement-number (:campaign/placement-number m)}))

(defrecord DatomicStore [conn]
  Store
  (campaign [_ id]
    (pull->campaign (d/pull (d/db conn) campaign-pull [:campaign/id id])))
  (all-campaigns [_]
    (->> (d/q '[:find [?id ...] :where [?e :campaign/id ?id]] (d/db conn))
         (map #(pull->campaign (d/pull (d/db conn) campaign-pull [:campaign/id %])))
         (sort-by :id)))
  (risk-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?cid
                :where [?k :risk-screen/campaign-id ?cid] [?k :risk-screen/payload ?p]]
              (d/db conn) id)))
  (media-plan-of [_ campaign-id]
    (dec* (d/q '[:find ?p . :in $ ?cid
                :where [?a :media-plan/campaign-id ?cid] [?a :media-plan/payload ?p]]
              (d/db conn) campaign-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (placement-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :placement/seq ?s] [?e :placement/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-placement-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :placement-sequence/jurisdiction ?j] [?e :placement-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (campaign-already-placed? [s campaign-id]
    (boolean (:campaign-placed? (campaign s campaign-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :campaign/upsert
      (d/transact! conn [(campaign->tx value)])

      :media-plan/set
      (d/transact! conn [{:media-plan/campaign-id (first path) :media-plan/payload (enc payload)}])

      :risk-screen/set
      (d/transact! conn [{:risk-screen/campaign-id (first path) :risk-screen/payload (enc payload)}])

      :campaign/mark-placed
      (let [campaign-id (first path)
            {:keys [result campaign-patch]} (place-campaign! s campaign-id)
            jurisdiction (:jurisdiction (campaign s campaign-id))
            next-n (inc (next-placement-sequence s jurisdiction))]
        (d/transact! conn
                     [(campaign->tx (assoc campaign-patch :id campaign-id))
                      {:placement-sequence/jurisdiction jurisdiction :placement-sequence/next next-n}
                      {:placement/seq (count (placement-history s)) :placement/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-campaigns [s campaigns]
    (when (seq campaigns) (d/transact! conn (mapv campaign->tx (vals campaigns)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:campaigns ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [campaigns]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-campaigns s campaigns))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo campaign set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
