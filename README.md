# cloud-itonami-isic-7310

Open Business Blueprint for **ISIC Rev.5 7310**: Advertising.

This repository publishes an advertising actor -- campaign intake,
advertising-standards evidence assessment, misleading-claim-risk
screening, campaign placement, plus **creator tie-up** (commissioning
a paid post from a named YouTube channel or influencer) with
creator-eligibility screening and sponsorship-disclosure gating -- as
an OSS business that any qualified, licensed advertising operator can
fork, deploy, run, improve and sell, so a community or independent
professional never surrenders customer data and ledgers to a closed
SaaS.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720),
[`8521`](https://github.com/cloud-itonami/cloud-itonami-isic-8521),
[`6619`](https://github.com/cloud-itonami/cloud-itonami-isic-6619),
[`3600`](https://github.com/cloud-itonami/cloud-itonami-isic-3600),
[`6190`](https://github.com/cloud-itonami/cloud-itonami-isic-6190),
[`3030`](https://github.com/cloud-itonami/cloud-itonami-isic-3030),
[`3830`](https://github.com/cloud-itonami/cloud-itonami-isic-3830),
[`7020`](https://github.com/cloud-itonami/cloud-itonami-isic-7020),
[`9420`](https://github.com/cloud-itonami/cloud-itonami-isic-9420),
[`9491`](https://github.com/cloud-itonami/cloud-itonami-isic-9491),
[`2610`](https://github.com/cloud-itonami/cloud-itonami-isic-2610),
[`3512`](https://github.com/cloud-itonami/cloud-itonami-isic-3512),
[`8810`](https://github.com/cloud-itonami/cloud-itonami-isic-8810),
[`8691`](https://github.com/cloud-itonami/cloud-itonami-isic-8691),
[`8569`](https://github.com/cloud-itonami/cloud-itonami-isic-8569),
[`6419`](https://github.com/cloud-itonami/cloud-itonami-isic-6419)) --
here it is **AdOps-LLM ⊣ Campaign Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a
> campaign-intake summary, normalizing records, and checking whether a
> campaign's own proposed media spend actually stays within its own
> recorded authorized budget -- but it has **no notion of which
> jurisdiction's advertising-standards law is official, no license to
> place a real campaign or commission a real creator on a client's
> behalf, and no way to know on its own whether a misleading-claim
> risk against a campaign has actually stayed unresolved, or which
> sponsorship-disclosure wordings a regulator has actually
> published**. Letting it place a campaign or order a tie-up directly
> invites fabricated regulatory citations, a media spend blowing past
> its own authorized budget, a misleading claim being quietly
> published, and a paid influencer post going out with a
> plausible-but-unrecognized disclosure label (or none) -- and
> liability, and consumer-protection risk, for whoever runs it. This
> project seals the AdOps-LLM into a single node and wraps it with an
> independent **Campaign Governor**, a human **approval workflow**,
> and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers campaign intake through advertising-standards
evidence assessment, misleading-claim-risk screening and campaign
placement, plus the creator-tie-up lifecycle -- creator-eligibility
screening, per-jurisdiction tie-up evidence assessment and the tie-up
order itself. It does **not**, by itself, hold any professional
license required to operate as an advertising agency in a given
jurisdiction, and it does not claim to. It also does **not** create
the creative work itself, or judge the artistic/strategic merit of a
campaign -- `advertising.registry/media-spend-exceeds-authorized-
budget?` and `advertising.registry/creator-tieup-fee-exceeds-
authorized-budget?` are pure ceiling recomputes against the campaign's
own recorded fields, not creative or strategic assessments.

For creator tie-ups specifically, it does **not** talk to YouTube,
Instagram, TikTok or X: there is no API call, no order sent to a
creator, no post published. `advertising.registry/register-creator-
tieup-order` builds the ORDER RECORD an agency keeps; a human operator
places the actual order. It also does **not** verify that a creator
handle exists, that follower counts are genuine, or that a published
post actually carried its disclosure -- those need integrations and
observation this actor deliberately does not have, and inventing a
verdict for them would be exactly the fabrication `advertising.facts`
refuses to do for jurisdictions.

Whoever deploys and operates a live instance (a licensed advertising
agency) supplies any jurisdiction-specific license, the real creative
work, the real media-network integrations and the real creator
relationships, and bears that jurisdiction's liability -- the software
supplies the governed, spec-cited, audited execution layer so that
agency does not have to build the compliance machinery from scratch.

### Actuation

**Neither real-world act is ever autonomous, at any phase, by
construction.** This actor performs TWO actuation events:

| Actuation | What it commits the client to |
|---|---|
| `:actuation/place-campaign` | placing/publishing a campaign on the client's behalf |
| `:actuation/order-creator-tieup` | commissioning a paid post from a named YouTube channel / influencer on the client's behalf |

Two independent layers enforce human sign-off for both
(`advertising.governor`'s `high-stakes` set, and `advertising.phase`'s
phase table, which subtracts `advertising.phase/actuation-ops` from
every phase's `:auto` set *structurally* rather than by convention) --
see `advertising.phase`'s docstring and
`test/advertising/phase_test.clj`'s
`place-campaign-never-auto-at-any-phase`,
`order-creator-tieup-never-auto-at-any-phase` and
`no-actuation-op-is-ever-auto-eligible`. The actor may draft, check
and recommend; a human agency operator is always the one who actually
places a campaign or orders a tie-up. Both are POSITIVE actuations
(committing a real record), matching this fleet's majority actuation
shape (`3600`/`6190` are the fleet's two NEGATIVE-actuation
exceptions).

The two actuations are kept fully independent -- separate guard
booleans (`:campaign-placed?` / `:tieup-ordered?`), separate
jurisdiction-scoped sequences (`JPN-PLC-000000` / `JPN-TIE-000000`)
and separate histories. A campaign that has been placed has **not**
thereby been ordered from a creator, and holding one must never mask
the other (see ADR-0002 and
`the-two-actuation-guards-are-independent`).

## The core contract

```
campaign intake + jurisdiction facts (advertising.facts, spec-cited:
advertising standards AND sponsorship disclosure, cited separately)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────────────┐
   │ AdOps-LLM    │ ─────────────▶ │ Campaign Governor:            │  (independent system)
   │ (sealed)     │  + citations    │ spec-basis · evidence-        │
   └──────────────┘                 │ incomplete · media-spend-     │
          │                 commit ◀┼ exceeds-authorized-budget     │
          │                         │ (ceiling) · misleading-claim- │
    record + ledger                 │ risk-unresolved               │
          │                         │ (unconditional) ·             │
          │                         │ already-placed                │
          │                         │ ── creator tie-up ──          │
          │                         │ creator-ineligible            │
          │              escalate   │ (unconditional) · tieup-      │
          │            (ALWAYS for  │ evidence-incomplete ·         │
          │             BOTH        │ creator-tieup-fee-exceeds-    │
          │             actuations) │ authorized-budget (combined   │
          │                        ◀┼ ceiling) · sponsorship-       │
          ▼                         │ disclosure-missing ·          │
      human approval                │ already-ordered               │
                                    └───────────────────────────────┘
```

**The AdOps-LLM never places a campaign, or orders a creator tie-up,
that the Campaign Governor would reject -- and never does either
without a human sign-off.** Hard violations (fabricated regulatory
requirements; unsupported evidence; a media spend past its own
authorized budget; an unresolved misleading-claim risk; a double
placement; an ineligible creator; a combined media-spend-plus-tie-up-
fee past the client's own authorization; a tie-up with no recognized
sponsorship disclosure; a double order; or either actuation attempted
with no screening on file at all) force **hold** and *cannot* be
approved past; a clean proposal for either actuation still always
routes to a human.

### The sponsorship-disclosure gate

The one check an LLM structurally cannot stand in for. A creator tie-up
is lawful or unlawful largely on one question -- *was the post
identifiable as advertising?* -- and the answer is jurisdiction-
specific published fact, not reasoning:

| Jurisdiction | Cited disclosure basis |
|---|---|
| JPN | 景表法5条3号 指定告示「一般消費者が事業者の表示であることを判別することが困難である表示」(令和5年内閣府告示第19号、ステマ規制) |
| USA | FTC Endorsement Guides, 16 CFR Part 255 |
| GBR | CAP Code section 2 (Recognition of marketing communications) |
| DEU | UWG § 5a Abs. 4 |

`advertising.governor` independently recomputes, against the
campaign's own recorded `:disclosure-label`, whether that label is one
the jurisdiction's **own authority publishes**. Nothing recorded and
*something recorded that the authority does not publish* are the SAME
HARD hold -- 「タイアップ」 is a real word the industry uses that is not
among the 消費者庁's published examples (「広告」「宣伝」「プロモーション」
「PR」), and a governor that accepted it because it *sounds* like a
disclosure would be worse than useless. See
`tieup-with-unpublished-disclosure-label-is-held`.

## Run

```bash
clojure -M:dev:run       # walk both clean actuation lifecycles + eight HARD-hold cases through the actor
clojure -M:dev:test      # governor contract · phase invariants · store parity · registry conformance · facts coverage · advisor boundary
clojure -M:dev:coverage  # cloverage over src, driven by the same suite
clojure -M:lint          # clj-kondo (errors fail; CI mirrors this)
```

89 tests / 510 assertions, 0 failures.

Coverage: 97.64% forms / 99.03% lines, measured by `clojure -M:dev:coverage`
with **nothing excluded** — not an estimate, and not a subset chosen to
flatter the number. Every namespace, including both `-main` drivers, is
at or above 95%.

Three of the seven suites exist specifically to cover paths the main
governor-contract suite drives straight past — each was written after
measuring, because the measurement is what revealed they were missing:

| Suite | The guarantee it holds down |
|---|---|
| `advertisingadvisor_test.clj` | A real LLM's response is untrusted input. Every malformed shape — prose, broken EDN, right-EDN-wrong-type, missing keys, a non-numeric confidence — must degrade to a low-confidence `:noop` with no `:stake`, never to a usable proposal. This path runs on every request in production and the mock advisor never touches it. |
| `operation_test.clj` | The approver's **rejection** is a real veto: it writes no record, does not flip a guard boolean, and does not burn the actuation (the same campaign stays placeable once the objection is resolved). And the rollout phases gate *in fact*, not just in the phase table. |
| `drivers_test.clj` | The demo and the operator console narrate the governor's behaviour in prose. These tests tie that narration to what the governor actually does, so a rule change cannot quietly turn the demo into a lie — and they assert the console is byte-identical across reruns, which is what `regenerate.yml` depends on. |

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a print/signage production
robot handles physical ad-material production where used, under the
actor, gated by the independent **Campaign Governor**. The governor
never dispatches hardware itself; `:high`/`:safety-critical` actions
require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Campaign Governor, campaign-placement draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`7310`). This vertical's campaign records are practice-specific rather
than a shared cross-operator data contract, so `advertising.*` runs on
the generic robotics/identity/forms/dmn/bpmn/audit-ledger stack only
-- no bespoke domain capability lib to reference at all.

This holds for the creator-tie-up lifecycle too, deliberately. The
workspace does ship platform adapters an operator could wire in
downstream of a human-approved tie-up order --
[`kotoba-lang/com-youtube`](https://github.com/kotoba-lang/com-youtube)
(YouTube Data API v3),
[`kotoba-lang/com-googleads`](https://github.com/kotoba-lang/com-googleads),
[`kotoba-lang/adnet`](https://github.com/kotoba-lang/adnet) -- and this
repo depends on **none** of them. Taking such a dependency would put a
real network call behind the governor's actuation gate, which is
precisely the boundary `Actuation` above draws: this actor produces
the audited order record, and the operator's own integration, if any,
acts on it afterwards.

## Layout

| File | Role |
|---|---|
| `src/advertising/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + campaign-placement history + creator-tie-up-order history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded campaign, and each double-actuation guard checks its OWN dedicated boolean (`:campaign-placed?` / `:tieup-ordered?`) rather than a `:status` value |
| `src/advertising/registry.cljc` | Campaign-placement and creator-tie-up-order draft records, plus `media-spend-exceeds-authorized-budget?` -- the SEVENTH instance of this fleet's MAXIMUM-ceiling check family (`facility`/`school`/`card`/`recovery`/`care`/`navigator` established the first six) -- and `creator-tieup-fee-exceeds-authorized-budget?`, the EIGHTH, and the first in the family to SUM two of the subject's own amounts before comparing |
| `src/advertising/facts.cljc` | Per-jurisdiction advertising-standards catalog with an official spec-basis citation per entry, PLUS a separately-cited sponsorship-disclosure basis and published-label set per entry, honest coverage reporting for both |
| `src/advertising/advertisingadvisor.cljc` | **AdOps-LLM** -- `mock-advisor` ‖ `llm-advisor`; intake/media-plan-verification/misleading-claim-risk-screening/campaign-placement proposals, plus creator-eligibility-screening/tie-up-brief/tie-up-order proposals. Never proposes a disclosure label -- it reports the recorded one verbatim, so the governor's published-label check cannot be defeated by a plausible invention |
| `src/advertising/governor.cljc` | **Campaign Governor** -- 9 HARD checks (spec-basis · evidence-incomplete · media-spend-exceeds-authorized-budget, pure ground-truth ceiling recompute · misleading-claim-risk-unresolved, unconditional evaluation, the THIRTY-EIGHTH grounding of this discipline · risk-screen-missing · creator-ineligible, unconditional · tieup-evidence-incomplete · sponsorship-disclosure-missing, pure ground-truth recompute against the jurisdiction's own published labels, a genuinely new concept in this fleet · creator-screen-missing) + already-placed and already-ordered guards + 1 soft (confidence/actuation gate) |
| `src/advertising/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (BOTH actuations always human, enforced structurally via `actuation-ops`; campaign intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/advertising/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/advertising/sim.cljc` | demo driver |
| `test/advertising/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers campaign intake through advertising-standards
evidence assessment, misleading-claim-risk screening and campaign
placement, plus creator-eligibility screening through creator-tie-up
ordering -- the core governed lifecycles this blueprint's own
`docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Campaign intake + per-jurisdiction advertising-standards checklisting, HARD-gated on an official spec-basis citation (`:campaign/intake`/`:media-plan/verify`) | Real media-network integration, real creative production itself (see `advertising.facts`'s docstring) |
| Misleading-claim-risk screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:risk/screen`) | Any creative/strategic judgment itself -- deliberately outside this actor's competence |
| Campaign placement, HARD-gated on full evidence and the campaign's own authorized-budget ceiling, plus a double-placement guard (`:actuation/place-campaign`) | |
| Creator-eligibility screening for the YouTube channel / influencer a campaign wants to commission, evaluated unconditionally (`:creator/screen`) | Any platform API call -- no YouTube/Instagram/TikTok/X integration, no order actually sent to a creator |
| Per-jurisdiction creator-tie-up evidence + sponsorship-disclosure checklisting, citing the disclosure framework specifically (`:tieup/verify`) | Verifying a creator handle exists, that follower counts are genuine, or that a published post actually carried its disclosure |
| Creator-tie-up ordering, HARD-gated on tie-up evidence, the COMBINED media-spend-plus-fee ceiling and a disclosure label the jurisdiction's own authority publishes, plus a double-order guard (`:actuation/order-creator-tieup`) | Negotiating or pricing the tie-up, and any judgment about whether a given creator suits the brand |
| Immutable audit ledger for every intake/verification/screening/placement/tie-up-order decision | |

**"We never looked" is not a clean state.** Both actuations HARD-require
a committed screening verdict that actually clears -- `:resolved` for
misleading-claim risk, `:eligible` for the creator. Until ADR-0003
those checks only fired when a screening record existed AND reported a
problem, so skipping the screening step entirely was the way through;
four tests in this repo encoded that hole without anyone noticing. A
campaign nobody screened carries the same exposure as one screened
badly, plus no record that anyone looked — strictly worse to defend if
the placement is later disputed.

A known R0 boundary that remains: the evidence checklists behind
`:media-plan/verify` and `:tieup/verify` are still operator-submitted,
so an operator can assert documents that do not exist. ADR-0003 makes
the screening *verdicts* first-class governor inputs; it does not make
the checklists self-proving. That needs per-document attestation
records.

Extending coverage is additive: add the next gate (e.g. a creative-
rights-clearance check, or the screening-verdict requirement above) as
its own governed op with its own HARD checks and tests, following the
SAME "an independent governor re-verifies against the actor's own
records before any real-world act" pattern both of this repo's
actuation ops already establish.

## Jurisdiction coverage (honest)

`advertising.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `advertising.facts/catalog`
-- currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide -- and, separately, `:disclosure-covered`: how many of those
also carry an official sponsorship-disclosure basis for creator
tie-ups (currently the same 4). The two are reported separately
because they are separate legal instruments; a jurisdiction could
plausibly be added with one and not the other, and the governor holds
on whichever is missing. This is a starting catalog to prove the
governor contract end-to-end, not a claim of global coverage. Adding a
jurisdiction is additive: one map entry in
`advertising.facts/catalog`, citing a real official source for each
basis -- never fabricate a jurisdiction's requirements, or a
disclosure label an authority has not published, to make coverage look
bigger.

`:accepted-disclosure-labels` deserves its own honesty note: it lists
the wordings each authority itself publishes as examples, and is
deliberately NOT an exhaustive legal whitelist -- no authority
publishes one. `disclosure-acceptable?` is therefore a **floor** (did
the operator record a label the authority has itself named?), not a
legal opinion that the resulting post is compliant.

## Maturity

`:implemented` -- `AdOps-LLM` + `Campaign Governor` run as real,
tested code (see `Run` above), promoted from the originally-published
`:blueprint`-tier design, modeled closely on the fifty-three prior
actors' architecture. See `docs/adr/0001-architecture.md` for the
history and design, and
[`docs/adr/0002-creator-tieup.md`](docs/adr/0002-creator-tieup.md) for
the creator-tie-up (YouTube / influencer) lifecycle -- why it became a
second actuation on this actor rather than a new repository, and why
sponsorship disclosure is a governor check rather than an advisor
responsibility. [`docs/adr/0003-screening-must-be-on-file.md`](docs/adr/0003-screening-must-be-on-file.md)
closes the boundary ADR-0002 left open, on both lifecycles at once.

## License

Code and implementation templates are AGPL-3.0-or-later.
