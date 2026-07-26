# ADR-0002: media-platform layer — gate placements on the platform's own ad policy

## Status

accepted (2026-07-26)

Extends ADR-0001. Supersedes nothing.

Superproject mirror: `com-junkawasaki/root`
`90-docs/adr/2607261200-advertising-7310-media-platform-layer.edn`.

## Context

ADR-0001 built this actor around ONE authority: the jurisdiction. Every
HARD check asked a variant of "is this campaign lawful where it runs?"
— `advertising.facts` catalogs 景表法 / the FTC Act / the CAP Code /
the UWG, and the governor holds anything that cites a jurisdiction it
cannot source.

That leaves a real gap. An advertising agency does not only have to be
lawful; it has to be **placeable**. Every media platform publishes its
own ad policy with its own category taxonomy, its own advertiser-
approval regime and its own placement exclusions, and those rules are
not a restatement of any jurisdiction's law. A campaign can be
perfectly lawful under 景表法 and still be categorically refused by the
network it was bought on. Before this ADR the actor had no
representation of that at all: `advertising.store`'s campaign entity
did not record which platform a campaign was for, and
`:actuation/place-campaign` would happily draft a placement record for
a campaign whose category the target platform prohibits outright.

The immediate trigger was a concrete question — "can this actor run
ChatGPT ads?" — but the gap it exposed is general.

Ads on an LLM answer surface also introduce a failure mode print,
search and display advertising do not have. On a banner surface, "is
this an ad?" is answered by layout: the ad occupies a slot users have
learned to read as an ad. On a generative surface the assistant's own
prose is the primary content, so an ad styled like that prose reads as
the assistant's answer. OpenAI's ad policy makes this a standalone
prohibition (`インターフェースの模倣` — ads must be clearly
distinguishable from the ChatGPT product experience; ads mimicking the
look, function or presentation of an OpenAI interface may be removed
or required to change), which is the platform-level expression of the
same principle behind the FTC's native-advertising guidance: an ad
should be identifiable as an ad. That deserves a structural gate, not a
creative-review checklist item.

## Decision

### Decision 1: a second catalog, not an extension of `advertising.facts`

`advertising.platform` is a sibling of `advertising.facts`, not an
addition to it. The two answer different questions on behalf of
different authorities (a regulator vs a private platform), they have
different failure modes, and they are keyed differently (ISO3 vs
platform id). Merging them would force one shape onto both and make
"which authority refused this?" unanswerable from the ledger.

`advertising.platform` inherits `advertising.facts`'s honesty
discipline verbatim: an untranscribed platform has NO policy-basis,
the advisor must not invent one, and the governor holds.

### Decision 2: the catalog ships with exactly ONE platform

`chatgpt-ads` only, transcribed from a direct read of
<https://openai.com/policies/ad-policies/> (the document's own v1.3 /
更新 2026年7月15日) on 2026-07-26, cross-referenced against
<https://help.openai.com/en/articles/20001207-ads-in-chatgpt-the-basics>.

`google-ads`, `meta-ads` and Microsoft Advertising publish real
policies at real, verified URLs and are **deliberately absent**. Their
policies had not been read end-to-end, so there was no honest taxonomy
to transcribe. Seeding them from memory or from search-result
summaries would have produced a catalog that looks four times as
capable and is strictly more dangerous: an absent platform HARD-holds
on `no-platform-policy-basis`, while an invented one waves campaigns
through on rules nobody wrote. `coverage` reports the gap rather than
hiding it — the same discipline `advertising.facts/coverage` applies
to the ~190 unseeded jurisdictions.

This is the ADR's least satisfying decision to read and its most
important one to keep.

### Decision 3: conservative reading of a self-contradicting source

The OpenAI policy contradicts itself on legal services: its section-2
preamble lists 法務サービス alongside financial and healthcare as
case-by-case approvable, while its dedicated 法的サービス section
states that ads for legal advice, representation or legal services are
not permitted. `:legal-services` is recorded as **prohibited** — the
conservative reading — with the contradiction recorded verbatim in the
entry's `:transcription-notes`, because a compliance gate that resolves
ambiguity permissively is not a gate. Reclassifying requires written
confirmation from the operator, not a re-reading.

### Decision 4: six HARD checks, computed from the campaign's own fields

| rule | fires when |
|---|---|
| `:no-platform-policy-basis` | the campaign's target platform has no transcribed policy |
| `:platform-check-incomplete` | actuation attempted with no committed conformance assessment on file |
| `:platform-prohibited-category` | the category is named prohibited, OR unnamed under a closed category set |
| `:platform-restricted-category-unapproved` | a restricted category without advertiser approval, or outside the platform's allowed jurisdictions |
| `:platform-attestation-missing` | a required attestation was not positively asserted |
| `:sensitive-placement-context` | placement requested against a context the policy excludes |

All are HARD (un-overridable by a human approver) and all are computed
from the campaign's own permanent fields against the transcribed
policy — no proposal inspection, the same ground-truth discipline
`media-spend-exceeds-authorized-budget-violations` uses. They are
evaluated for `:platform/verify` as well as
`:actuation/place-campaign`, so the conformance op HARD-holds on its
OWN finding rather than writing a clean assessment that a later
actuation would read as evidence — the discipline
`misleading-claim-risk-unresolved-violations` established.

An unknown platform does not cascade: `category-disposition` returns
`:no-policy-basis` rather than `:prohibited`, and every other
predicate returns an empty/false result, so exactly one rule
(`no-platform-policy-basis`) fires. `test/advertising/platform_test.clj`
pins this — every predicate must decline to speak for a platform
nobody transcribed, because a helper that answers `:permitted` for an
unknown platform would route around the only check protecting it.

### Decision 5: absence is never consent

`missing-attestations` counts only an explicit `true`. A campaign that
omits `:distinguishable-from-product-ui`, or sets it `false` or `nil`,
has not attested it. This is the mechanism by which
`:generative-surface?` becomes load-bearing rather than decorative: the
platform is flagged as a generative surface, that flag is why the
policy demands distinguishability, and the attestation is what the
agency must positively assert before the governor will pass a
placement.

### Decision 6: `:platform/verify` is a new write op, never auto-eligible

It joins `:media-plan/verify` and `:risk/screen` in phase 2's `:writes`
and appears in no phase's `:auto` set, for the reason every sibling
screening op has: signing off that a campaign conforms to a platform's
ad policy is a compliance judgement, not a normalization.

### Decision 7: no second actuation op

`high-stakes` stays the single-member `#{:actuation/place-campaign}`
that ADR-0001 Decision 1 grounded in this blueprint's own text. The
platform is a *parameter* of the one real-world act, not a second act.
Per-platform actuation ops would multiply the single most dangerous
op in the repo by the size of the catalog and give every future
platform its own chance to get the human-approval invariant wrong.

Consequently the placement NUMBER stays jurisdiction-scoped —
re-scoping the sequence per platform would silently renumber every
placement already drafted — and the platform is recorded as a FIELD on
the placement record, which is what an auditor reconstructing "where
did this ad actually run?" needs.

## Alternatives considered

**Fold platform rules into `advertising.facts`.** Rejected: see
Decision 1. The ledger could no longer say which authority refused a
placement.

**Seed google/meta/microsoft from general knowledge.** Rejected: see
Decision 2. This is precisely the failure mode the whole repo exists
to prevent, and doing it in the catalog that gates placements would be
self-refuting.

**Make platform conformance a soft check (escalate, not hold).**
Rejected. A human agency operator cannot approve their way past a
platform's categorical refusal any more than past a jurisdiction's —
the campaign simply will not run, or will run and be pulled. Escalating
would train operators to click through a gate that is not negotiable.

**Treat the generative-surface mimicry rule as a creative-review
item.** Rejected: it is the platform's own standalone prohibition and
the point at which an ad stops being identifiable as an ad, which is
what truth-in-advertising law is about. It belongs with the HARD
checks.

## Consequences

- Placements now require BOTH `:media-plan/verify` and
  `:platform/verify` on file. `governor_contract_test.clj`'s `verify!`
  helper runs both; existing actuation tests were updated accordingly.
- `advertising.store`'s campaign entity gained `:target-platform`,
  `:ad-category`, `:advertiser-approval-on-file?`, `:attestations` and
  `:requested-placement-contexts`. The last two are compound and ride
  the EDN-blob codec on the Datomic backend; store-contract parity
  covers the round-trip.
- The demo store grew campaigns 5–9, each jurisdiction-clean and held
  by exactly one platform-side rule.
- Operators inherit a recurring obligation: ad policies change (the
  seeded entry's own changelog runs v1.0 in 2026-03 to v1.3 in
  2026-07), so `:read-on` going stale is a re-read task. Recorded in
  `docs/operator-guide.md`.
- The catalog's one-entry coverage is a real limitation, honestly
  reported. It is cheap to extend and expensive to fake, which is the
  intended asymmetry.
