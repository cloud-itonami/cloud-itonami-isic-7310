(ns advertising.phase
  "Phase 0->3 staged rollout -- the advertising analog of `cloud-
  itonami-isic-6512`'s `casualty.phase`.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- campaign intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-verify  -- adds media-plan verification, media-
                                 platform ad-policy conformance
                                 (`:platform/verify`, ADR-0002),
                                 misleading-claim-risk screening,
                                 creator-eligibility screening and
                                 creator-tie-up brief writes, still
                                 approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:campaign/intake` (no capital risk
                                 yet) may auto-commit. NEITHER
                                 `:actuation/place-campaign` NOR
                                 `:actuation/order-creator-tieup` ever
                                 auto-commits, at any phase.

  BOTH actuation ops are deliberately ABSENT from every phase's
  `:auto` set, including phase 3 -- a permanent structural fact, not a
  rollout milestone still to come. Placing a real campaign on the
  client's behalf, and commissioning a paid post from a named YouTube
  channel / influencer on the client's behalf, are the TWO real-world
  advertising acts this actor performs; each is always a human agency-
  operator call. `advertising.governor`'s `high-stakes` set enforces
  the same invariant independently for both -- two layers, not one,
  agree on this. `:risk/screen` and `:creator/screen` are likewise
  never auto-eligible, at any phase -- the same posture every sibling's
  screening op has. Phase 3's `:auto` set here has only ONE member
  (`:campaign/intake`) -- this domain has no separate no-capital-risk
  'file' lifecycle distinct from the campaign record itself."
  (:require [clojure.set :as set]))

(def read-ops  #{})

(def placement-ops
  "The campaign-placement lifecycle, including the media-platform
  ad-policy conformance op added by ADR-0002."
  #{:campaign/intake :media-plan/verify :platform/verify :risk/screen
    :actuation/place-campaign})

(def tieup-ops
  "The creator-tie-up (YouTube / influencer) lifecycle, added in
  ADR-0004. Its own screening op, its own evidence-brief op, its own
  actuation."
  #{:creator/screen :tieup/verify :actuation/order-creator-tieup})

(def write-ops (set/union placement-ops tieup-ops))

(def actuation-ops
  "Every op that performs a REAL-WORLD act. The single source of truth
  the phase table below is built from, so a future actuation cannot be
  added to `write-ops` and silently omitted from this invariant --
  `phases` subtracts this set from every `:auto` set by construction,
  and `advertising.phase-test` asserts the result."
  #{:actuation/place-campaign :actuation/order-creator-tieup})

;; NOTE the invariant: both members of `actuation-ops` are members of
;; `write-ops` (governor-gated like any write) but are NEVER members of
;; any phase's `:auto` set below. `auto-set` enforces that structurally
;; rather than by convention -- do not bypass it.
(defn- auto-set
  "An `:auto` set with every real-world actuation op removed, whatever
  the caller passed. Structural, not advisory."
  [ops]
  (set/difference (set ops) actuation-ops))

(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                  :auto (auto-set #{})}
   1 {:label "assisted-intake"  :writes #{:campaign/intake}                   :auto (auto-set #{})}
   2 {:label "assisted-verify"  :writes #{:campaign/intake :media-plan/verify :platform/verify
                                          :risk/screen :creator/screen :tieup/verify}
      :auto (auto-set #{})}
   3 {:label "supervised-auto"  :writes write-ops
      :auto (auto-set #{:campaign/intake})}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - neither actuation op is ever auto-eligible at any phase, so each
    always escalates once the governor clears it (or holds if the
    governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Campaign Governor verdict to a base disposition before the
  phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
