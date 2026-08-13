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

  This actor has TWO actuation events, each acting on the same
  `campaign` entity but each with its OWN history collection, its OWN
  jurisdiction-scoped sequence counter and its OWN dedicated double-
  actuation-guard boolean (`:campaign-placed?` for placement,
  `:tieup-ordered?` for the creator tie-up -- never a `:status` value
  for either) -- the same discipline every prior sibling governor's
  guards establish, informed by `cloud-itonami-isic-6492`'s status-
  lifecycle bug (ADR-2607071320). Keeping the two guards, sequences
  and histories fully separate is deliberate: a campaign that has been
  placed has NOT thereby been ordered from a creator, and holding one
  must never mask the other (ADR-0002).

  The ledger stays append-only on every backend: 'which campaign was
  screened for an unresolved misleading-claim risk, which creator was
  screened for eligibility, which campaign was placed, which creator
  tie-up was ordered under which disclosure label, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a client trusting an agency needs,
  and the evidence an agency needs if a placement or a tie-up order is
  later disputed by the client, the creator or a regulator."
  (:require [advertising.registry :as registry]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (campaign [s id])
  (all-campaigns [s])
  (risk-screen-of [s campaign-id] "committed misleading-claim-risk screening verdict for a campaign, or nil")
  (media-plan-of [s campaign-id] "committed media-plan evidence assessment, or nil")
  (creator-screen-of [s campaign-id] "committed creator-eligibility screening verdict for a campaign's tie-up, or nil")
  (tieup-brief-of [s campaign-id] "committed creator-tie-up evidence assessment, or nil")
  (platform-check-of [s campaign-id] "committed media-platform ad-policy conformance assessment, or nil")
  (ledger [s])
  (placement-history [s] "the append-only campaign-placement history (advertising.registry drafts)")
  (tieup-order-history [s] "the append-only creator-tie-up-order history (advertising.registry drafts)")
  (next-placement-sequence [s jurisdiction] "next placement-number sequence for a jurisdiction")
  (next-tieup-sequence [s jurisdiction] "next tie-up-order-number sequence for a jurisdiction")
  (campaign-already-placed? [s campaign-id] "has this campaign already been placed?")
  (tieup-already-ordered? [s campaign-id] "has this campaign's creator tie-up already been ordered?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-campaigns [s campaigns] "replace/seed the campaign directory (map id->campaign)"))

;; ----------------------------- demo data -----------------------------

(def ^:private clean-platform-facts
  "The media-platform facts a campaign needs to clear every platform-
  side governor check on `chatgpt-ads`. Spelled out once so the demo
  campaigns that exercise OTHER failure modes are not accidentally
  also platform-dirty -- each demo campaign should fail for exactly
  the one reason it exists to demonstrate."
  {:target-platform "chatgpt-ads"
   :ad-category :local-services
   :advertiser-approval-on-file? false
   ;; every BASE attestation any seeded platform requires, so switching
   ;; :target-platform alone never makes a campaign dirty by accident.
   ;; Jurisdiction-scoped attestations (Meta's EU DSA disclosure) are
   ;; deliberately NOT here -- campaign-11 exists to be held by one.
   :attestations {:distinguishable-from-product-ui true
                  :landing-page-consistency true
                  :advertiser-identity-verified true
                  :editorial-standards true}
   :requested-placement-contexts []})

(def ^:private clean-tieup-facts
  "The creator-tie-up booleans every campaign carries, whether or not it
  has a tie-up. They exist on ALL campaigns so `MemStore` (raw EDN) and
  `DatomicStore` (which coerces an absent attribute to false on pull)
  agree -- store-contract parity is asserted field by field, and a nil
  on one backend against a false on the other is a real divergence, not
  a cosmetic one."
  {:creator-eligibility-issue? false
   :tieup-ordered? false})

(defn demo-data
  "A small, self-contained campaign set covering BOTH actuation
  lifecycles (placing a campaign; ordering a creator tie-up) so the
  actor + tests run offline.

  campaign-1..4 exercise the jurisdiction-side governor checks;
  campaign-5..9 the media-platform-side ones on `chatgpt-ads`;
  campaign-10..12 the fact that the four seeded platforms DISAGREE
  (ADR-0003) -- same category, same jurisdiction, different verdict;
  and campaign-21..25 the creator-tie-up lifecycle and its four
  DISTINCT HARD holds (ADR-0004) -- a combined media-spend-plus-fee
  past the client's own authorization, an ineligible creator, no
  recorded sponsorship-disclosure label, and a recorded label the
  jurisdiction's own authority does not publish.

  That last one is drawn from a real failure mode, not invented:
  「tie-up」 on its own is a word the industry uses that is NOT among
  the Consumer Affairs Agency's own published examples, so an operator
  who records it has recorded something -- and the governor must still
  hold.

  The tie-up campaigns merge `clean-platform-facts` too. The platform
  checks are scoped to `:platform/verify`/`:actuation/place-campaign`
  and so never see a tie-up order, but a tie-up campaign may also be
  PLACED, and a demo campaign should fail for exactly the one reason it
  exists to demonstrate."
  []
  {:campaigns
   (into {}
         (map (fn [[id c]] [id (merge clean-tieup-facts c)]))
         {"campaign-1" (merge clean-platform-facts
                 {:id "campaign-1" :client-name "Sato Bakery"
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "JPN" :status :intake})
    "campaign-2" (merge clean-platform-facts
                 {:id "campaign-2" :client-name "Atlantis Goods"
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "ATL" :status :intake})
    "campaign-3" (merge clean-platform-facts
                 {:id "campaign-3" :client-name "鈴木工務店"
                 :proposed-media-spend 900000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "JPN" :status :intake})
    "campaign-4" (merge clean-platform-facts
                 {:id "campaign-4" :client-name "田中青果"
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? true
                 :campaign-placed? false
                 :jurisdiction "JPN" :status :intake})
    ;; -- media-platform failure modes --
    ;; an ad network with no transcribed policy in advertising.platform
    "campaign-5" (merge clean-platform-facts
                 {:id "campaign-5" :client-name "Kaneko Tools"
                 :target-platform "acme-adnet"
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "JPN" :status :intake})
    ;; a category the platform's own policy names as prohibited
    "campaign-6" (merge clean-platform-facts
                 {:id "campaign-6" :client-name "Lucky Palace"
                 :ad-category :gambling
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "JPN" :status :intake})
    ;; a restricted category, unapproved advertiser AND outside the
    ;; platform's restricted-category jurisdiction set
    "campaign-7" (merge clean-platform-facts
                 {:id "campaign-7" :client-name "Marunouchi Lending"
                 :ad-category :financial-services
                 :advertiser-approval-on-file? false
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "JPN" :status :intake})
    ;; a generative surface with the interface-mimicry attestation absent
    "campaign-8" (merge clean-platform-facts
                 {:id "campaign-8" :client-name "Yamada Travel"
                 :ad-category :travel-experiences
                 :attestations {:landing-page-consistency true
                                :advertiser-identity-verified true}
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "JPN" :status :intake})
    ;; placement requested against a context the platform excludes
    "campaign-9" (merge clean-platform-facts
                 {:id "campaign-9" :client-name "Aoi Clinic Supplies"
                 :requested-placement-contexts [:mental-and-personal-health]
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "JPN" :status :intake})
    ;; -- cross-platform disagreement (ADR-0003) --
    ;; google-ads, OPEN category set: :local-services is unnamed by that
    ;; policy, so it resolves :permitted and this campaign is placeable --
    ;; the same category, on the same jurisdiction, as campaign-1.
    "campaign-10" (merge clean-platform-facts
                 {:id "campaign-10" :client-name "Ishikawa Dental"
                 :target-platform "google-ads"
                 :ad-category :local-services
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "JPN" :status :intake})
    ;; meta-ads in DEU: the EU DSA beneficiary/payer disclosure is required
    ;; only where it applies, and this campaign has not made it.
    "campaign-11" (merge clean-platform-facts
                 {:id "campaign-11" :client-name "Berliner Möbelhaus"
                 :target-platform "meta-ads"
                 :ad-category :lifestyle-household
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "DEU" :status :intake})
    ;; microsoft-advertising: :travel-experiences is RESTRICTED there while
    ;; being PERMITTED on chatgpt-ads, and the per-category country table
    ;; has not been transcribed -- so it holds on both counts.
    "campaign-12" (merge clean-platform-facts
                 {:id "campaign-12" :client-name "Hokkaido Onsen Tours"
                 :target-platform "microsoft-advertising"
                 :ad-category :travel-experiences
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "JPN" :status :intake})
    ;; line-yahoo-ads, :policy-read :partial. :lifestyle-household is a
    ;; category the operator's enumerated 掲載基準 very probably permits --
    ;; and this campaign HOLDS anyway, because that standard renders via
    ;; JavaScript and nobody has read it. It is the demo of the difference
    ;; between 'the platform said no' and 'we did not read what it said':
    ;; on any OPEN-set platform this identical campaign is placeable.
    "campaign-13" (merge clean-platform-facts
                 {:id "campaign-13" :client-name "Nagoya Futon"
                 :target-platform "line-yahoo-ads"
                 :ad-category :lifestyle-household
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "JPN" :status :intake})
    ;; youtube-ads incorporates google-ads by its own words, so :gambling is
    ;; RESTRICTED here without youtube-ads naming a single category itself --
    ;; and the country table it inherits is the untranscribed one, so it
    ;; holds for the same reason campaign-12 does. Buying YouTube is not
    ;; 'we support Google Ads'.
    "campaign-14" (merge clean-platform-facts
                 {:id "campaign-14" :client-name "Setouchi Boat Race"
                 :target-platform "youtube-ads"
                 :ad-category :gambling
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :campaign-placed? false
                 :jurisdiction "JPN" :status :intake})

    ;; ---- creator tie-up (YouTube / influencer) lifecycle, ADR-0004 ----
    ;; clean: 500000 media + 200000 fee = 700000, within the 800000 the
    ;; client authorized; creator screened clean; 「PR」 is a 消費者庁-
    ;; published disclosure label.
    "campaign-21" (merge clean-platform-facts
                 {:id "campaign-21" :client-name "Sato Bakery"
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :creator-eligibility-issue? false
                 :creator-handle "@sato-bakery-review" :creator-platform :youtube
                 :creator-tieup-fee 200000 :disclosure-label "PR"
                 :campaign-placed? false :tieup-ordered? false
                 :jurisdiction "JPN" :status :intake})
    ;; the fee alone (400000) fits the budget; 500000 + 400000 does not.
    "campaign-22" (merge clean-platform-facts
                 {:id "campaign-22" :client-name "関西グルメ舎"
                 :proposed-media-spend 500000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :creator-eligibility-issue? false
                 :creator-handle "@kansai-gourmet" :creator-platform :youtube
                 :creator-tieup-fee 400000 :disclosure-label "PR"
                 :campaign-placed? false :tieup-ordered? false
                 :jurisdiction "JPN" :status :intake})
    ;; creator carries an unresolved eligibility issue (e.g. a prior
    ;; undisclosed-endorsement finding) -- HARD hold at screening time.
    "campaign-23" (merge clean-platform-facts
                 {:id "campaign-23" :client-name "北野デンタル"
                 :proposed-media-spend 300000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :creator-eligibility-issue? true
                 :creator-handle "@kitano-clinic-fan" :creator-platform :instagram
                 :creator-tieup-fee 100000 :disclosure-label "PR"
                 :campaign-placed? false :tieup-ordered? false
                 :jurisdiction "JPN" :status :intake})
    ;; no disclosure label recorded at all.
    "campaign-24" (merge clean-platform-facts
                 {:id "campaign-24" :client-name "みどり不動産"
                 :proposed-media-spend 300000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :creator-eligibility-issue? false
                 :creator-handle "@midori-room-tour" :creator-platform :youtube
                 :creator-tieup-fee 100000 :disclosure-label nil
                 :campaign-placed? false :tieup-ordered? false
                 :jurisdiction "JPN" :status :intake})
    ;; a label IS recorded -- just not one the authority publishes.
    "campaign-25" (merge clean-platform-facts
                 {:id "campaign-25" :client-name "湘南サーフ用品"
                 :proposed-media-spend 300000 :authorized-budget 800000
                 :misleading-claim-risk-unresolved? false
                 :creator-eligibility-issue? false
                 :creator-handle "@shonan-surf-log" :creator-platform :youtube
                 :creator-tieup-fee 100000 :disclosure-label "タイアップ"
                 :campaign-placed? false :tieup-ordered? false
                 :jurisdiction "JPN" :status :intake})})})

;; ----------------------------- shared commit logic -----------------------------

(defn- place-campaign!
  "Backend-agnostic `:campaign/mark-placed` -- looks up the campaign
  via the protocol and drafts the placement record, and returns
  {:result .. :campaign-patch ..} for the caller to persist."
  [s campaign-id]
  (let [c (campaign s campaign-id)
        seq-n (next-placement-sequence s (:jurisdiction c))
        result (registry/register-campaign-placement campaign-id (:jurisdiction c) seq-n
                                                     (:target-platform c))]
    {:result result
     :campaign-patch {:campaign-placed? true
                     :placement-number (get result "placement_number")}}))

(defn- order-creator-tieup!
  "Backend-agnostic `:tieup/mark-ordered` -- looks up the campaign via
  the protocol and drafts the creator-tie-up order record, and returns
  {:result .. :campaign-patch ..} for the caller to persist. The exact
  shape of `place-campaign!` above, against its OWN sequence and its
  OWN guard boolean."
  [s campaign-id]
  (let [c (campaign s campaign-id)
        seq-n (next-tieup-sequence s (:jurisdiction c))
        result (registry/register-creator-tieup-order
                campaign-id (:jurisdiction c) (:creator-platform c)
                (:creator-handle c) seq-n)]
    {:result result
     :campaign-patch {:tieup-ordered? true
                     :tieup-order-number (get result "order_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (campaign [_ id] (get-in @a [:campaigns id]))
  (all-campaigns [_] (sort-by :id (vals (:campaigns @a))))
  (risk-screen-of [_ id] (get-in @a [:risk-screens id]))
  (media-plan-of [_ campaign-id] (get-in @a [:media-plans campaign-id]))
  (creator-screen-of [_ campaign-id] (get-in @a [:creator-screens campaign-id]))
  (tieup-brief-of [_ campaign-id] (get-in @a [:tieup-briefs campaign-id]))
  (platform-check-of [_ campaign-id] (get-in @a [:platform-checks campaign-id]))
  (ledger [_] (:ledger @a))
  (placement-history [_] (:placements @a))
  (tieup-order-history [_] (:tieup-orders @a))
  (next-placement-sequence [_ jurisdiction] (get-in @a [:placement-sequences jurisdiction] 0))
  (next-tieup-sequence [_ jurisdiction] (get-in @a [:tieup-sequences jurisdiction] 0))
  (campaign-already-placed? [_ campaign-id] (boolean (get-in @a [:campaigns campaign-id :campaign-placed?])))
  (tieup-already-ordered? [_ campaign-id] (boolean (get-in @a [:campaigns campaign-id :tieup-ordered?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :campaign/upsert
      (swap! a update-in [:campaigns (:id value)] merge value)

      :media-plan/set
      (swap! a assoc-in [:media-plans (first path)] payload)

      :risk-screen/set
      (swap! a assoc-in [:risk-screens (first path)] payload)

      :creator-screen/set
      (swap! a assoc-in [:creator-screens (first path)] payload)

      :tieup-brief/set
      (swap! a assoc-in [:tieup-briefs (first path)] payload)
      :platform-check/set
      (swap! a assoc-in [:platform-checks (first path)] payload)

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

      :tieup/mark-ordered
      (let [campaign-id (first path)
            {:keys [result campaign-patch]} (order-creator-tieup! s campaign-id)
            jurisdiction (:jurisdiction (campaign s campaign-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:tieup-sequences jurisdiction] (fnil inc 0))
                       (update-in [:campaigns campaign-id] merge campaign-patch)
                       (update :tieup-orders registry/append result))))
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
                           :media-plans {} :risk-screens {} :platform-checks {}
                           :ledger [] :placement-sequences {} :placements []
                           :creator-screens {} :tieup-briefs {} :tieup-sequences {}
                           :tieup-orders []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Compound values (media-plan/risk-screen payloads, ledger facts,
  placement records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses. The identity-schema builder, EDN-blob
  codec and seq-keyed event-log read/append are the shared
  kotoba-lang/langchain-store machinery (ADR-2607141600) -- the seam
  ~190 actors hand-roll; this store keeps only its domain wiring."
  (ls/identity-schema
   [:campaign/id :media-plan/campaign-id :risk-screen/campaign-id
    :platform-check/campaign-id
    :creator-screen/campaign-id :tieup-brief/campaign-id
    :ledger/seq :placement/seq :placement-sequence/jurisdiction
    :tieup-order/seq :tieup-sequence/jurisdiction]))

(defn- campaign->tx [{:keys [id client-name proposed-media-spend authorized-budget
                            misleading-claim-risk-unresolved?
                            campaign-placed?
                            jurisdiction status placement-number
                            target-platform ad-category advertiser-approval-on-file?
                            attestations requested-placement-contexts
                            creator-eligibility-issue?
                            creator-handle creator-platform creator-tieup-fee disclosure-label
                            tieup-ordered? tieup-order-number]}]
  (cond-> {:campaign/id id}
    client-name                                   (assoc :campaign/client-name client-name)
    proposed-media-spend                          (assoc :campaign/proposed-media-spend proposed-media-spend)
    authorized-budget                              (assoc :campaign/authorized-budget authorized-budget)
    (some? misleading-claim-risk-unresolved?)      (assoc :campaign/misleading-claim-risk-unresolved? misleading-claim-risk-unresolved?)
    (some? campaign-placed?)                       (assoc :campaign/campaign-placed? campaign-placed?)
    jurisdiction                                    (assoc :campaign/jurisdiction jurisdiction)
    status                                          (assoc :campaign/status status)
    placement-number                                (assoc :campaign/placement-number placement-number)
    ;; -- media platform (ADR-0002) --
    target-platform                                 (assoc :campaign/target-platform target-platform)
    ad-category                                     (assoc :campaign/ad-category ad-category)
    (some? advertiser-approval-on-file?)            (assoc :campaign/advertiser-approval-on-file? advertiser-approval-on-file?)
    ;; compound -> EDN blob, so langchain.db doesn't expand them into sub-entities
    (some? attestations)                            (assoc :campaign/attestations (ls/enc attestations))
    (some? requested-placement-contexts)            (assoc :campaign/requested-placement-contexts (ls/enc requested-placement-contexts))
    ;; -- creator tie-up (ADR-0004) --
    (some? creator-eligibility-issue?)             (assoc :campaign/creator-eligibility-issue? creator-eligibility-issue?)
    creator-handle                                  (assoc :campaign/creator-handle creator-handle)
    creator-platform                                (assoc :campaign/creator-platform creator-platform)
    creator-tieup-fee                               (assoc :campaign/creator-tieup-fee creator-tieup-fee)
    disclosure-label                                (assoc :campaign/disclosure-label disclosure-label)
    (some? tieup-ordered?)                         (assoc :campaign/tieup-ordered? tieup-ordered?)
    tieup-order-number                              (assoc :campaign/tieup-order-number tieup-order-number)))

(def ^:private campaign-pull
  [:campaign/id :campaign/client-name :campaign/proposed-media-spend :campaign/authorized-budget
   :campaign/misleading-claim-risk-unresolved? :campaign/campaign-placed?
   :campaign/jurisdiction :campaign/status :campaign/placement-number
   :campaign/target-platform :campaign/ad-category :campaign/advertiser-approval-on-file?
   :campaign/attestations :campaign/requested-placement-contexts
   :campaign/creator-eligibility-issue? :campaign/creator-handle :campaign/creator-platform
   :campaign/creator-tieup-fee :campaign/disclosure-label
   :campaign/tieup-ordered? :campaign/tieup-order-number])

(defn- pull->campaign [m]
  (when (:campaign/id m)
    {:id (:campaign/id m) :client-name (:campaign/client-name m)
     :proposed-media-spend (:campaign/proposed-media-spend m)
     :authorized-budget (:campaign/authorized-budget m)
     :misleading-claim-risk-unresolved? (boolean (:campaign/misleading-claim-risk-unresolved? m))
     :creator-eligibility-issue? (boolean (:campaign/creator-eligibility-issue? m))
     :creator-handle (:campaign/creator-handle m)
     :creator-platform (:campaign/creator-platform m)
     :creator-tieup-fee (:campaign/creator-tieup-fee m)
     :disclosure-label (:campaign/disclosure-label m)
     :campaign-placed? (boolean (:campaign/campaign-placed? m))
     :tieup-ordered? (boolean (:campaign/tieup-ordered? m))
     :jurisdiction (:campaign/jurisdiction m) :status (:campaign/status m)
     :placement-number (:campaign/placement-number m)
     :tieup-order-number (:campaign/tieup-order-number m)
     :target-platform (:campaign/target-platform m)
     :ad-category (:campaign/ad-category m)
     :advertiser-approval-on-file? (boolean (:campaign/advertiser-approval-on-file? m))
     :attestations (or (ls/dec* (:campaign/attestations m)) {})
     :requested-placement-contexts (or (ls/dec* (:campaign/requested-placement-contexts m)) [])}))

(defrecord DatomicStore [conn]
  Store
  (campaign [_ id]
    (pull->campaign (d/pull (d/db conn) campaign-pull [:campaign/id id])))
  (all-campaigns [_]
    (->> (d/q '[:find [?id ...] :where [?e :campaign/id ?id]] (d/db conn))
         (map #(pull->campaign (d/pull (d/db conn) campaign-pull [:campaign/id %])))
         (sort-by :id)))
  (risk-screen-of [_ id]
    (ls/dec* (d/q '[:find ?p . :in $ ?cid
                :where [?k :risk-screen/campaign-id ?cid] [?k :risk-screen/payload ?p]]
              (d/db conn) id)))
  (media-plan-of [_ campaign-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?cid
                :where [?a :media-plan/campaign-id ?cid] [?a :media-plan/payload ?p]]
              (d/db conn) campaign-id)))
  (creator-screen-of [_ campaign-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?cid
                :where [?k :creator-screen/campaign-id ?cid] [?k :creator-screen/payload ?p]]
              (d/db conn) campaign-id)))
  (tieup-brief-of [_ campaign-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?cid
                :where [?a :tieup-brief/campaign-id ?cid] [?a :tieup-brief/payload ?p]]
              (d/db conn) campaign-id)))
  (platform-check-of [_ campaign-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?cid
                :where [?a :platform-check/campaign-id ?cid] [?a :platform-check/payload ?p]]
              (d/db conn) campaign-id)))
  (ledger [_] (ls/read-stream conn :ledger/seq :ledger/fact))
  (placement-history [_] (ls/read-stream conn :placement/seq :placement/record))
  (tieup-order-history [_] (ls/read-stream conn :tieup-order/seq :tieup-order/record))
  (next-placement-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :placement-sequence/jurisdiction ?j] [?e :placement-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-tieup-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :tieup-sequence/jurisdiction ?j] [?e :tieup-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (campaign-already-placed? [s campaign-id]
    (boolean (:campaign-placed? (campaign s campaign-id))))
  (tieup-already-ordered? [s campaign-id]
    (boolean (:tieup-ordered? (campaign s campaign-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :campaign/upsert
      (d/transact! conn [(campaign->tx value)])

      :media-plan/set
      (d/transact! conn [{:media-plan/campaign-id (first path) :media-plan/payload (ls/enc payload)}])

      :risk-screen/set
      (d/transact! conn [{:risk-screen/campaign-id (first path) :risk-screen/payload (ls/enc payload)}])

      :creator-screen/set
      (d/transact! conn [{:creator-screen/campaign-id (first path) :creator-screen/payload (ls/enc payload)}])

      :tieup-brief/set
      (d/transact! conn [{:tieup-brief/campaign-id (first path) :tieup-brief/payload (ls/enc payload)}])
      :platform-check/set
      (d/transact! conn [{:platform-check/campaign-id (first path) :platform-check/payload (ls/enc payload)}])

      :campaign/mark-placed
      (let [campaign-id (first path)
            {:keys [result campaign-patch]} (place-campaign! s campaign-id)
            jurisdiction (:jurisdiction (campaign s campaign-id))
            next-n (inc (next-placement-sequence s jurisdiction))]
        (d/transact! conn
                     [(campaign->tx (assoc campaign-patch :id campaign-id))
                      {:placement-sequence/jurisdiction jurisdiction :placement-sequence/next next-n}
                      {:placement/seq (count (placement-history s)) :placement/record (ls/enc (get result "record"))}])
        result)

      :tieup/mark-ordered
      (let [campaign-id (first path)
            {:keys [result campaign-patch]} (order-creator-tieup! s campaign-id)
            jurisdiction (:jurisdiction (campaign s campaign-id))
            next-n (inc (next-tieup-sequence s jurisdiction))]
        (d/transact! conn
                     [(campaign->tx (assoc campaign-patch :id campaign-id))
                      {:tieup-sequence/jurisdiction jurisdiction :tieup-sequence/next next-n}
                      {:tieup-order/seq (count (tieup-order-history s)) :tieup-order/record (ls/enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (ls/enc fact)}])
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
