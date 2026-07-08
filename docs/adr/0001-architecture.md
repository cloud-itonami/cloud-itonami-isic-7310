# ADR-0001: AdOps-LLM ⊣ Campaign Governor architecture

## Status

Accepted. `cloud-itonami-isic-7310` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-7310` publishes an OSS business blueprint for
advertising: creating and placing advertising campaigns for clients
across media. Like every prior actor in this fleet, the blueprint
alone is not an implementation: this ADR records the governed-actor
architecture that promotes it to real, tested code, following the
same langgraph-clj StateGraph + independent Governor + Phase 0→3
rollout pattern established by `cloud-itonami-isic-6511` (life
insurance) and applied across fifty-three prior siblings, most
recently `cloud-itonami-isic-6419` (community banking).

## Decision

### Decision 1: single-actuation shape

Unlike most recent siblings, this blueprint's own text names only ONE
real-world act: the README's "No automated proposal, by itself, can
complete the following without governor approval and audit evidence:
placing/publishing a campaign on the client's behalf." Matching
`leasing`/`underwriting`/`testlab`/`clinic`/`veterinary`/`funeral`/
`parksafety`/`salon`/`entertainment`/`facility`/`consulting`'s single-
actuation shape, `high-stakes` here is a one-member set,
`#{:actuation/place-campaign}`.

### Decision 2: entity and op shape

The primary entity is a `campaign`. Four ops: `:campaign/intake`
(directory upsert, no capital risk), `:media-plan/verify` (per-
jurisdiction advertising-standards evidence checklist, never auto),
`:risk/screen` (misleading-claim-risk screening, unconditional-
evaluation discipline, never auto), and `:actuation/place-campaign`
(POSITIVE, high-stakes -- placing/publishing a real campaign on the
client's behalf).

### Decision 3: `media-spend-exceeds-authorized-budget?` -- the 7th MAXIMUM-ceiling check

Following `facility.registry/occupancy-exceeds-capacity?` (1st),
`school.registry/class-size-exceeds-maximum?` (2nd), `card.registry/
settlement-amount-exceeds-authorized?` (3rd), `recovery.registry/
contamination-percentage-exceeds-maximum?` (4th), `care.registry/
caregiver-workload-exceeds-maximum?` (5th) and `navigator.registry/
eligibility-window-elapsed-exceeds-validity?` (6th), `advertising.
registry/media-spend-exceeds-authorized-budget?` applies the SAME
ceiling-only comparison to a campaign's own proposed media spend
against its own recorded authorized budget -- a direct, natural
mapping onto real ad-agency media-buying practice, closely analogous
to `card`'s settlement/authorized-amount shape. Gates only
`:actuation/place-campaign`.

### Decision 4: `misleading-claim-risk-unresolved-violations` -- the 38th unconditional-evaluation screening grounding, a genuinely new concept

Before writing this check, every prior sibling's `governor.cljc` was
grepped for `misleading` -- one hit, `formation.governor`, examined
directly and confirmed to be an unrelated docstring mention (a
misleading AUDIT-TRAIL record from a mishandled address-change
proposal, not a misleading-claim-in-advertising concept). Confirms
this is a genuinely new concept, avoiding the false-precedent-claim
risk `leasing`'s ADR-0001 documents. `misleading-claim-risk-
unresolved-violations` reuses the unconditional-evaluation DISCIPLINE
(`casualty.governor/sanctions-violations`'s original fix) for the
38th distinct application overall, continuing the count established
across this window's builds (water=25th ... banking=37th,
advertising=38th). Grounded directly in this blueprint's own Trust
Control "a fabricated media-buy or misleading-claim risk forces a
hold, not an override." Gates `:risk/screen` and `:actuation/place-
campaign` specifically.

### Decision 5: dedicated double-actuation-guard boolean

`:campaign-placed?` is a dedicated boolean on the `campaign` record,
never a single `:status` value -- the same discipline every prior
sibling governor's guards establish, informed by `cloud-itonami-isic-
6492`'s real status-lifecycle bug (ADR-2607071320).

### Decision 6: Store protocol, MemStore + DatomicStore parity

`advertising.store/Store` is implemented by both `MemStore` (atom-
backed, default for dev/tests/demo) and `DatomicStore` (`langchain.
db`-backed), proven to satisfy the same contract in `test/
advertising/store_contract_test.clj` -- the same seam every sibling
actor uses so swapping the SSoT backend is a configuration change, not
a rewrite. The protocol's per-entity accessor is named `campaign`
directly -- not a Clojure special form, so no `-of` suffix workaround
was needed.

### Decision 7: Phase 0→3 rollout

Phase 3's `:auto` set has exactly one member, `:campaign/intake` (no
capital risk). `:media-plan/verify` and `:risk/screen` are never
auto-eligible at any phase (matching every sibling's screening-op
posture), and `:actuation/place-campaign` is permanently excluded from
every phase's `:auto` set -- a structural fact, not a rollout
milestone, enforced by BOTH `advertising.phase` and `advertising.
governor`'s `high-stakes` set independently.

### Decision 8: no bespoke domain capability lib

This vertical's campaign records are practice-specific rather than a
shared cross-operator data contract, so `advertising.*` runs on the
generic robotics/identity/forms/dmn/bpmn/audit-ledger stack only --
matching this blueprint's own already-correct `blueprint.edn`
(the ONLY inconsistency found this build was the missing `:maturity`
field itself; unlike most prior promotions, this repo's
`blueprint.edn` needed no `:id`/`:required-technologies` fixes).

### Decision 9: mock + LLM advisor pair

`advertising.advertisingadvisor` provides `mock-advisor`
(deterministic, default everywhere -- the actor graph and governor
contract run offline) and `llm-advisor` (backed by `langchain.model/
ChatModel`, with a defensive EDN-proposal parser so a malformed LLM
response degrades to a safe low-confidence noop rather than ever
auto-placing a campaign).

## Alternatives considered

- **A dual-actuation shape** (e.g. adding a separate "publish creative
  asset" actuation alongside campaign placement). Rejected: the
  blueprint's own README, business-model.md and operator-guide.md all
  consistently name only ONE real-world act (placing/publishing a
  campaign); inventing a second would not be grounded in the
  blueprint's own text, unlike this fleet's genuine dual-actuation
  siblings where the blueprint text itself names two distinct acts.
- **A single "campaign-safety" check merging budget and misleading-
  claim concerns.** Rejected: authorized-budget is a ground-truth
  numeric recompute needing no proposal inspection; misleading-claim-
  risk status is an unconditionally-evaluated flag that must also
  HARD-hold the screening op itself on its own finding -- merging them
  would lose the screening op's self-hold property.

## Consequences

- Fifty-fourth actor in this fleet (53 implemented before this
  build).
- Confirms the MAXIMUM-ceiling check family generalizes to a seventh,
  genuinely distinct domain (media-budget authorization).
- Establishes a genuinely NEW unconditional-evaluation-screening
  concept (misleading-claim-risk), grep-verified absent from every
  prior sibling before the claim was finalized.
- `MemStore` ‖ `DatomicStore` parity is proven by `test/advertising/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- `blueprint.edn` required no `:id`/`:required-technologies` fixes
  this time (already correct) -- only the `:maturity` flip itself.
