# cloud-itonami-isic-7310

Open Business Blueprint for **ISIC Rev.5 7310**: Advertising.

This repository publishes an advertising actor -- campaign intake,
advertising-standards evidence assessment, misleading-claim-risk
screening and campaign placement -- as an OSS business that any
qualified, licensed advertising operator can fork, deploy, run,
improve and sell, so a community or independent professional never
surrenders customer data and ledgers to a closed SaaS.

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
> place a real campaign on a client's behalf, and no way to know on
> its own whether a misleading-claim risk against a campaign has
> actually stayed unresolved**. Letting it place a campaign directly
> invites fabricated regulatory citations, a media spend blowing past
> its own authorized budget, and a misleading claim being quietly
> published -- and liability, and consumer-protection risk, for
> whoever runs it. This project seals the AdOps-LLM into a single node
> and wraps it with an independent **Campaign Governor**, a human
> **approval workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers campaign intake through advertising-standards
evidence assessment, media-platform ad-policy conformance,
misleading-claim-risk screening and campaign placement. It does
**not** integrate with any real ad platform's buying API -- it governs
the DECISION to place a campaign, not the mechanics of placing it, so
`:actuation/place-campaign` drafts a placement RECORD an agency would
keep rather than calling a media network. It does **not**, by itself,
hold any professional license
required to operate as an advertising agency in a given jurisdiction,
and it does not claim to. It also does **not** create the creative
work itself, or judge the artistic/strategic merit of a campaign --
`advertising.registry/media-spend-exceeds-authorized-budget?` is a
pure ceiling recompute against the campaign's own recorded fields, not
a creative or strategic assessment. Whoever deploys and operates a
live instance (a licensed advertising agency) supplies any
jurisdiction-specific license, the real creative work and the real
media-network integrations, and bears that jurisdiction's liability --
the software supplies the governed, spec-cited, audited execution
scaffold so that agency does not have to build the compliance layer
from scratch.

### Actuation

**Placing a real campaign on the client's behalf is never autonomous,
at any phase, by construction.** Two independent layers enforce this
(`advertising.governor`'s `:actuation/place-campaign` high-stakes gate
and `advertising.phase`'s phase table, which never puts `:actuation/
place-campaign` in any phase's `:auto` set) -- see `advertising.
phase`'s docstring and `test/advertising/phase_test.clj`'s
`place-campaign-never-auto-at-any-phase`. The actor may draft, check
and recommend; a human agency operator is always the one who actually
places a campaign. Unlike most recent siblings, this actor has ONE
actuation event -- matching `leasing`'s/`underwriting`'s/`testlab`'s/
`clinic`'s/`veterinary`'s/`funeral`'s/`parksafety`'s/`salon`'s/
`entertainment`'s/`facility`'s/`consulting`'s single-actuation shape,
grounded directly in this blueprint's own README text ("No automated
proposal, by itself, can complete the following without governor
approval and audit evidence: placing/publishing a campaign on the
client's behalf") -- a POSITIVE actuation (placing/publishing a real
record), matching this fleet's majority actuation shape (`3600`/
`6190` are the fleet's two NEGATIVE-actuation exceptions).

## The core contract

```
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
clojure -M:dev:run     # walk one clean single-actuation lifecycle + ten HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

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

## Layout

| File | Role |
|---|---|
| `src/advertising/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + campaign-placement history. No dynamically-filed sub-record -- the actuation op acts directly on a pre-seeded campaign, and the double-actuation guard checks a dedicated `:campaign-placed?` boolean rather than a `:status` value |
| `src/advertising/registry.cljc` | Campaign-placement draft records, plus `media-spend-exceeds-authorized-budget?` -- the SEVENTH instance of this fleet's MAXIMUM-ceiling check family (`facility`/`school`/`card`/`recovery`/`care`/`navigator` established the first six) |
| `src/advertising/facts.cljc` | Per-jurisdiction advertising-standards catalog with an official spec-basis citation per entry, honest coverage reporting. **CHN carries an explicit `:out-of-scope-here`**: China's pre-publication review gate (广告法第四十六条's 广告批准文号) is a conditional, category-dependent HARD gate with its own validity window — a catalog row cannot express it, so [`cloud-itonami-iso3166-chn-advertising`](https://github.com/cloud-itonami/cloud-itonami-iso3166-chn-advertising) implements it and this row names the boundary rather than implying full coverage |
| `src/advertising/platform.cljc` | Per-media-platform ad-policy catalog (ADR-0002 / ADR-0003) -- 4 platforms (`chatgpt-ads`, `google-ads`, `meta-ads`, `microsoft-advertising`) over one shared `category-vocabulary`; category taxonomy, open-vs-closed category sets, restricted-category jurisdictions, excluded placement contexts, base + jurisdiction-scoped attestations and `:generative-surface?`, each transcribed from a DIRECT read of the platform's own published policy with the URL, version and read date recorded; `cross-platform-disposition` + honest coverage reporting |
| `src/advertising/advertisingadvisor.cljc` | **AdOps-LLM** -- `mock-advisor` ‖ `llm-advisor`; intake/media-plan-verification/media-platform-conformance/misleading-claim-risk-screening/campaign-placement proposals |
| `src/advertising/governor.cljc` | **Campaign Governor** -- 4 jurisdiction-side HARD checks (spec-basis · evidence-incomplete · media-spend-exceeds-authorized-budget, pure ground-truth ceiling recompute · misleading-claim-risk-unresolved, unconditional evaluation, the THIRTY-EIGHTH grounding of this discipline, a genuinely new concept grounded in this blueprint's own Trust Control text) + already-placed guard + 6 media-platform-side HARD checks (ADR-0002) + 1 soft (confidence/actuation gate) |
| `src/advertising/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify (media-plan + media-platform conformance) → supervised (campaign placement always human; campaign intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/advertising/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/advertising/sim.cljc` | demo driver |
| `test/advertising/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage · media-platform taxonomy + unknown-platform inertness |

## Business-process coverage (honest)

This actor covers campaign intake through advertising-standards
evidence assessment, misleading-claim-risk screening and campaign
placement -- the core governed lifecycle this blueprint's own
`docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Campaign intake + per-jurisdiction advertising-standards checklisting, HARD-gated on an official spec-basis citation (`:campaign/intake`/`:media-plan/verify`) | Real media-network integration, real creative production itself (see `advertising.facts`'s docstring) |
| Misleading-claim-risk screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:risk/screen`) | Any creative/strategic judgment itself -- deliberately outside this actor's competence |
| Media-platform ad-policy conformance against the target platform's OWN published policy -- category taxonomy, restricted-category advertiser approval + jurisdiction limits, excluded placement contexts, required attestations (`:platform/verify`, ADR-0002) | Real ad-platform API integration (bidding, creative upload, delivery reporting) -- this actor governs the DECISION to place, not the mechanics of placing |
| Campaign placement, HARD-gated on full evidence, the campaign's own authorized-budget ceiling AND the target platform's own policy, plus a double-placement guard (`:actuation/place-campaign`) | |
| Immutable audit ledger for every intake/verification/conformance/screening/placement decision, recording which media platform each placement ran on | |

Extending coverage is additive: add the next gate (e.g. a creative-
rights-clearance check) as its own governed op with its own HARD
checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`advertising.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `advertising.facts/catalog`
-- currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `advertising.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

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
capture. Most importantly, all four platforms keep open-ended reserve
clauses (Google's 「その他の制限付きビジネス」, Microsoft's "Areas of
Questionable Legality" / "Other Market Restricted Products and
Services") which are not enumerable — so **a clean verdict from this
catalog is a necessary, not a sufficient, condition for platform
approval.**

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
`:blueprint`-tier scaffold, modeled closely on the fifty-three prior
actors' architecture. See `docs/adr/0001-architecture.md` for the
history and design, `docs/adr/0002-media-platform-layer.md` for the
media-platform layer (`advertising.platform`, `:platform/verify` and
the six platform-side governor checks), and
`docs/adr/0003-multi-platform-catalog.md` for the expansion to four
platforms, the shared category vocabulary and jurisdiction-scoped
attestations.

## License

Code and implementation templates are AGPL-3.0-or-later.
