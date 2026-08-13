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
evidence assessment, media-platform ad-policy conformance,
misleading-claim-risk screening and campaign placement, plus the
creator-tie-up lifecycle -- creator-eligibility screening,
per-jurisdiction tie-up evidence assessment and the tie-up order
itself.

Since 2026-08-13 it has an **actuation seam** (`advertising.placer`),
built so that acting is the explicitly chosen exception rather than the
default:

- **The default placer is a dry run.** It builds the platform-shaped
  buy request and sends nothing. An instance that injects no placer
  never reaches a network.
- **A live placer needs an injected `:http-fn` and throws without one.**
  This repository holds no credential and performs no ambient I/O. A
  live placer that could only ever behave as a dry run refuses to be
  constructed, because the operator would otherwise believe money moved.
- **Only `google-ads` (and therefore `youtube-ads`) has a request
  builder.** The other six catalogued platforms are policy-only: this
  actor can rule on them and cannot buy them, and the receipt says
  `:unsupported` rather than looking like a successful placement.
- **Every `:actuation/place-campaign` commit writes a RECEIPT to the
  audit ledger** stating `:mode`, `:sent?` and the platform. That is the
  point of the seam: before it, the ledger could say
  `:campaign/mark-placed` without saying whether anything had been
  bought, and bookkeeping and spending a client's money are not the same
  event. A dispatch that sends nothing writes `:sent? false` and says
  why; the receipt is never omitted, because an absent receipt and a
  dry-run receipt would read identically to whoever audits this later.

The seam sits **downstream of the governor and the human approval** -- a
held campaign builds no request at all. This actor still governs the
DECISION to place a campaign, and `:actuation/place-campaign` still
drafts the placement RECORD an agency keeps; what changed is that the
audit trail now states, per placement, whether that decision became an
act. It does **not**, by itself, hold any professional license required to
operate as an advertising agency in a given jurisdiction, and it does
not claim to. It also does **not** create the creative work itself, or
judge the artistic/strategic merit of a campaign --
`advertising.registry/media-spend-exceeds-authorized-budget?` and
`advertising.registry/creator-tieup-fee-exceeds-authorized-budget?`
are pure ceiling recomputes against the campaign's own recorded
fields, not creative or strategic assessments.

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
the other (see ADR-0004 and
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
campaign intake
   + jurisdiction facts (advertising.facts,   spec-cited)
   + media-platform facts (advertising.platform, policy-cited)
        |
        v
   ┌──────────────┐   proposal    ┌──────────────────────────────────┐
   │ AdOps-LLM    │ ───────────▶  │ Campaign Governor                │ (independent
   │ (sealed)     │  + citations  │                                  │  system)
   └──────────────┘               │ jurisdiction side:               │
          │               commit ◀┤   spec-basis · evidence-         │
          │                       │   incomplete · media-spend-      │
    record + ledger               │   exceeds-authorized-budget      │
          │                       │   (ceiling) · misleading-claim-  │
          │             escalate ◀┤   risk-unresolved (uncond.) ·    │
          │        (ALWAYS for    │   already-placed                 │
          │         :actuation/   │                                  │
          │         place-        │ media-platform side (ADR-0002):  │
          ▼         campaign)     │   no-platform-policy-basis ·     │
    human approval                │   platform-check-incomplete ·    │
                                  │   platform-prohibited-category · │
                                  │   platform-restricted-category-  │
                                  │   unapproved · platform-         │
                                  │   attestation-missing ·          │
                                  │   sensitive-placement-context    │
                                  └──────────────────────────────────┘
```

**The AdOps-LLM never places a campaign the Campaign Governor would
reject, and never does so without a human sign-off.** Hard violations
(fabricated regulatory requirements; unsupported evidence; a media
spend past its own authorized budget; an unresolved misleading-claim
risk; a double placement; a media platform whose ad policy nobody has
read; an ad category that platform itself refuses; a generative
surface where ad/answer distinguishability was never attested) force
**hold** and *cannot* be approved past; a clean placement proposal
still always routes to a human.

**The two check families are independent authorities and neither
subsumes the other.** A campaign can be perfectly lawful under 景表法
or the FTC Act and still be categorically disallowed on the platform
it was bought on; it can equally satisfy every platform policy and
still be unlawful where it runs. So both families run on every
`:actuation/place-campaign`, and clearing one clears nothing about the
other.

## Run

```bash
clojure -M:dev:run       # walk both clean actuation lifecycles + every HARD-hold rule through the actor
clojure -M:dev:test      # governor contract · phase invariants · store parity · registry conformance · facts + platform coverage · advisor boundary · drivers
clojure -M:dev:coverage  # cloverage over src, driven by the same suite
clojure -M:lint          # clj-kondo (errors fail; CI mirrors this)
```

134 tests / 881 assertions, 0 failures.

Coverage: 97.72% forms / 98.87% lines, measured by `clojure -M:dev:coverage`
with **nothing excluded** — not an estimate, and not a subset chosen to
flatter the number. Every namespace, including both `-main` drivers, is
at or above 96% on forms — the drivers included, because
`drivers_test.clj` runs them rather than leaving them to a nightly cron.

Three of the nine suites exist specifically to cover paths the main
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
| `src/advertising/governor.cljc` | **Campaign Governor** -- 15 HARD checks (9 jurisdiction- and creator-side, 6 media-platform-side) (spec-basis · evidence-incomplete · media-spend-exceeds-authorized-budget, pure ground-truth ceiling recompute · misleading-claim-risk-unresolved, unconditional evaluation, the THIRTY-EIGHTH grounding of this discipline · risk-screen-missing · creator-ineligible, unconditional · tieup-evidence-incomplete · sponsorship-disclosure-missing, pure ground-truth recompute against the jurisdiction's own published labels, a genuinely new concept in this fleet · creator-screen-missing) + already-placed and already-ordered guards + 1 soft (confidence/actuation gate) |
| `src/advertising/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (BOTH actuations always human, enforced structurally via `actuation-ops`; campaign intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/advertising/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + campaign-placement history. No dynamically-filed sub-record -- the actuation op acts directly on a pre-seeded campaign, and the double-actuation guard checks a dedicated `:campaign-placed?` boolean rather than a `:status` value |
| `src/advertising/registry.cljc` | Campaign-placement draft records, plus `media-spend-exceeds-authorized-budget?` -- the SEVENTH instance of this fleet's MAXIMUM-ceiling check family (`facility`/`school`/`card`/`recovery`/`care`/`navigator` established the first six) |
| `src/advertising/facts.cljc` | Per-jurisdiction advertising-standards catalog with an official spec-basis citation per entry, honest coverage reporting. **CHN carries an explicit `:out-of-scope-here`**: China's pre-publication review gate (广告法第四十六条's 广告批准文号) is a conditional, category-dependent HARD gate with its own validity window — a catalog row cannot express it, so [`cloud-itonami-iso3166-chn-advertising`](https://github.com/cloud-itonami/cloud-itonami-iso3166-chn-advertising) implements it and this row names the boundary rather than implying full coverage |
| `src/advertising/platform.cljc` | Per-media-platform ad-policy catalog (ADR-0002 / ADR-0003) -- 8 platforms (`chatgpt-ads`, `google-ads`, `meta-ads`, `microsoft-advertising`, `x-ads`, `telegram-ads`, `youtube-ads`, `line-yahoo-ads`) over one shared `category-vocabulary`; category taxonomy, open-vs-closed category sets, restricted-category jurisdictions, excluded placement contexts, base + jurisdiction-scoped attestations and `:generative-surface?`, each transcribed from a DIRECT read of the platform's own published policy with the URL, version and read date recorded; `cross-platform-disposition` + honest coverage reporting. Two entries carry the states a two-valued gate cannot express: `:policy-read :partial` (LINEヤフー -- an unnamed category resolves `:not-transcribed` and HOLDS, because an unread standard must not answer `:permitted`) and `:categories-incorporated-from` (YouTube -- categories and the country rule resolve through `google-ads`, which YouTube's own overview says applies to it) |
| `src/advertising/advertisingadvisor.cljc` | **AdOps-LLM** -- `mock-advisor` ‖ `llm-advisor`; intake/media-plan-verification/media-platform-conformance/misleading-claim-risk-screening/campaign-placement proposals |
| `src/advertising/governor.cljc` | **Campaign Governor** -- 4 jurisdiction-side HARD checks (spec-basis · evidence-incomplete · media-spend-exceeds-authorized-budget, pure ground-truth ceiling recompute · misleading-claim-risk-unresolved, unconditional evaluation, the THIRTY-EIGHTH grounding of this discipline, a genuinely new concept grounded in this blueprint's own Trust Control text) + already-placed guard + 6 media-platform-side HARD checks (ADR-0002) + 1 soft (confidence/actuation gate) |
| `src/advertising/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify (media-plan + media-platform conformance) → supervised (campaign placement always human; campaign intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/advertising/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/advertising/sim.cljc` | demo driver |
| `test/advertising/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage · media-platform taxonomy + unknown-platform inertness |

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
misleading-claim risk, `:eligible` for the creator. Until ADR-0005
those checks only fired when a screening record existed AND reported a
problem, so skipping the screening step entirely was the way through;
four tests in this repo encoded that hole without anyone noticing. A
campaign nobody screened carries the same exposure as one screened
badly, plus no record that anyone looked — strictly worse to defend if
the placement is later disputed.

A known R0 boundary that remains: the evidence checklists behind
`:media-plan/verify` and `:tieup/verify` are still operator-submitted,
so an operator can assert documents that do not exist. ADR-0005 makes
the screening *verdicts* first-class governor inputs; it does not make
the checklists self-proving. That needs per-document attestation
records.
| Media-platform ad-policy conformance against the target platform's OWN published policy -- category taxonomy, restricted-category advertiser approval + jurisdiction limits, excluded placement contexts, required attestations (`:platform/verify`, ADR-0002) | Real ad-platform API integration (bidding, creative upload, delivery reporting) -- this actor governs the DECISION to place, not the mechanics of placing |
| Campaign placement, HARD-gated on full evidence, the campaign's own authorized-budget ceiling AND the target platform's own policy, plus a double-placement guard (`:actuation/place-campaign`) | |
| Immutable audit ledger for every intake/verification/conformance/screening/placement decision, recording which media platform each placement ran on | |

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

## Media-platform coverage (honest)

`advertising.platform/coverage` reports how many requested media
platforms actually have a transcribed ad policy in
`advertising.platform/catalog`. **Four are seeded**, each transcribed
from a direct read of the platform's own published policy:

| platform | policy read | on |
|---|---|---|
| `chatgpt-ads` | [OpenAI 広告ポリシー](https://openai.com/policies/ad-policies/) (v1.3 / 更新 2026年7月15日) | 2026-07-26 |
| `google-ads` | [Google 広告のポリシー](https://support.google.com/adspolicy/answer/6008942) | 2026-07-27 |
| `meta-ads` | [Meta 広告規定](https://transparency.meta.com/policies/ad-standards/) | 2026-07-27 |
| `microsoft-advertising` | [Disallowed Content](https://about.ads.microsoft.com/en-us/policies/disallowed-content) + [Restricted Content](https://about.ads.microsoft.com/en-us/policies/restricted-categories) | 2026-07-27 |

**They disagree, and the catalog keeps them disagreeing** — that is the
whole reason platforms are modelled separately instead of merged into
one "advertising rules" table:

| category | `chatgpt-ads` | `google-ads` | `meta-ads` | `microsoft-advertising` |
|---|---|---|---|---|
| `:travel-experiences` | permitted | permitted | permitted | **restricted** |
| `:legal-services` | **prohibited** | permitted | permitted | restricted |
| `:political` | prohibited | **restricted** | **restricted** | prohibited |
| `:ticket-reselling` | **not-permitted** | permitted | permitted | restricted |

`advertising.platform/cross-platform-disposition` answers this directly
— the question an agency asks before it buys ("where can I run this at
all?"). A campaign's `:ad-category` is drawn from one shared
`category-vocabulary`, so the same declared category is what resolves
differently, not a different word per platform.

Two structural differences fall out of the transcription and are worth
knowing before reading a verdict:

- **Open vs closed category sets.** ChatGPT states that every category
  it does not name is disallowed at launch, so an unnamed category
  resolves `:not-permitted` there. The other three enumerate what is
  prohibited and restricted without closing the set, so an unnamed
  category resolves `:permitted`. This is transcribed per platform
  (`:closed-category-set?`), not assumed.
- **Untranscribed country tables hold.** Google, Meta and Microsoft all
  limit restricted categories by country, but those tables live on
  per-category sub-pages that were not read. Those entries record
  `:restricted-category-jurisdictions :per-category-unenumerated`,
  which makes `restricted-category-allowed-jurisdiction?` return
  **false everywhere** — every restricted-category placement HARD-holds
  until someone transcribes the relevant table. Not knowing is a hold,
  never a quiet yes.

Every entry's `:transcription-notes` states what it does **not**
capture. Most importantly, every platform keeps an open-ended reserve
clause (Google's 「その他の制限付きビジネス」, Microsoft's "Areas of
Questionable Legality" / "Other Market Restricted Products and
Services", Telegram's closing "All examples on this page are
non-exhaustive") which is not enumerable — so **a clean verdict from
this catalog is a necessary, not a sufficient, condition for platform
approval.**

Two entries are not simply "read and transcribed", and say so in data
rather than only in prose:

- **`line-yahoo-ads` carries `:policy-read :partial`.** LINEヤフー's
  enumerated 掲載基準 renders via JavaScript and served a loading error
  to every fetch, so only the operator's published change document was
  read. Under the open-set rule the entry would have resolved every
  unread category to `:permitted` — waving through precisely what
  nobody read. Instead an unnamed category resolves `:not-transcribed`
  and the governor **holds**, with a detail that says *we did not read
  this*, not *the platform refused it*. Reading the rest is the
  extension task; relaxing the flag is not.
- **`youtube-ads` carries `:categories-incorporated-from "google-ads"`.**
  YouTube's own overview states it — *"To place ads on YouTube, you'll
  have to comply with: Google Ad Policies"* — so its categories **and
  the restricted-category country rule** resolve through `google-ads`
  rather than being copied into a second table that can drift. Its own
  two additions (mimicking YouTube site elements, user-generated
  content in ads) are recorded as attestations, and YouTube Kids, whose
  policy has not been read, is an excluded placement context rather
  than silently servable. "We support Google Ads" does not answer "can
  we run this on YouTube?" — this entry is what answers it.

Adding a platform is additive and cheap — read that platform's own
published policy, transcribe its taxonomy, record the URL, the version
the document states for itself, and the date you read it. Never seed a
platform from memory, from a search-result summary, or by inferring one
network's taxonomy from another's: an absent platform HARD-holds on
`no-platform-policy-basis`, while an invented one waves campaigns
through on made-up rules.

### Why `:generative-surface?` is a first-class fact

On a banner or search surface, "is this an ad?" is answered by layout:
the ad sits in a slot users have learned to read as an ad. On a
generative surface the assistant's own prose is the primary content,
so an ad styled like that prose reads as the assistant's answer.
ChatGPT's ad policy makes this a standalone prohibition
(`インターフェースの模倣` — ads must be clearly distinguishable from
the ChatGPT product experience, and ads mimicking the look, function
or presentation of an OpenAI interface may be removed or required to
change), which is the same consumer-protection principle behind the
FTC's [native-advertising guidance](https://www.ftc.gov/business-guidance/resources/native-advertising-guide-businesses)
(an ad should be identifiable as an ad). That is why
`:distinguishable-from-product-ui` is a **required attestation** the
agency must positively assert — absence is never treated as consent —
and why an unattested campaign HARD-holds rather than passing
creative review with a note.

## Maturity

`:implemented` -- `AdOps-LLM` + `Campaign Governor` run as real,
tested code (see `Run` above), promoted from the originally-published
`:blueprint`-tier design, modeled closely on the fifty-three prior
actors' architecture. See `docs/adr/0001-architecture.md` for the
history and design, `docs/adr/0002-media-platform-layer.md` for the
media-platform layer (`advertising.platform`, `:platform/verify` and
the six platform-side governor checks),
`docs/adr/0003-multi-platform-catalog.md` for the expansion to four
platforms, the shared category vocabulary and jurisdiction-scoped
attestations,
[`docs/adr/0004-creator-tieup.md`](docs/adr/0004-creator-tieup.md) for
the creator-tie-up (YouTube / influencer) lifecycle -- why it became a
second actuation on this actor rather than a new repository, and why
sponsorship disclosure is a governor check rather than an advisor
responsibility -- and
[`docs/adr/0005-screening-must-be-on-file.md`](docs/adr/0005-screening-must-be-on-file.md),
which closes the boundary ADR-0004 left open, on both lifecycles at
once.

## License

Code and implementation templates are AGPL-3.0-or-later.
