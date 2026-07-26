# Operator Guide

## First Deployment

1. Register the operator's license, jurisdiction and responsible principals.
2. Import historical engagements/clients and counterparties.
3. Run read-only validation of existing records against this blueprint's
   contracts.
4. Configure the Campaign Governor's hold/escalation policy.
5. Transcribe the ad policy of every media platform you intend to buy on into
   `advertising.platform/catalog` — see "Adding a media platform" below. Until
   a platform is in the catalog, every placement targeting it HARD-holds.
6. Publish a dry-run operation and audit export.

## Adding a media platform

Placements are gated on the target platform's OWN published ad policy
(ADR-0002). To add one:

1. **Read that platform's published ad policy end-to-end.** Not a summary, not
   a search result, not another network's policy.
2. Add one map to `advertising.platform/catalog` recording the category
   taxonomy (permitted / restricted / prohibited), whether the policy declares
   an otherwise-unnamed category disallowed (`:closed-category-set?`), the
   jurisdictions restricted categories may run in, the placement contexts the
   policy excludes, the attestations the agency must positively assert, and
   whether ads render inside model-generated content
   (`:generative-surface?`).
3. Record `:provenance` (the canonical URL), `:policy-version` (the version
   the document states for itself) and `:read-on` (the date you read it).
4. Where the source is internally ambiguous, take the **conservative** reading
   and record why in `:transcription-notes`. A compliance gate holds on
   ambiguity.

**Never** seed a platform from memory or by inferring one network's taxonomy
from another's. An absent platform holds; an invented one waves campaigns
through on rules nobody wrote. `advertising.platform/coverage` reports what is
actually transcribed, and re-reading a policy whose `:read-on` has gone stale
is a scheduled operator task — ad policies change (the seeded ChatGPT entry
carries its own changelog: v1.0 in 2026-03, v1.3 by 2026-07).

## Why your restricted-category campaigns are all holding

Four platforms ship transcribed (ADR-0003), but only `chatgpt-ads` has its
restricted-category country eligibility recorded. Google, Meta and Microsoft
all limit restricted categories by country on per-category sub-pages that were
not read, so their entries carry
`:restricted-category-jurisdictions :per-category-unenumerated` and **every
restricted-category placement on them HARD-holds**, in every jurisdiction.

This is deliberate: the catalog cannot say whether your country is eligible,
and "cannot say" is a hold. To clear it, read the relevant per-category policy
sub-page for the category you are buying, replace the sentinel with the actual
ISO3 set, and record the URL and read date. Do not relax the predicate.

Note also that all four platforms keep open-ended reserve clauses (Google's
「その他の制限付きビジネス」, Microsoft's "Areas of Questionable Legality" and
"Other Market Restricted Products and Services"). These are not enumerable, so
**a clean verdict from this actor is a necessary and not a sufficient condition
for platform approval** — the platform's own review still decides.

## Minimum Production Controls

- spec-basis citation required before any customer-facing determination
- platform ad-policy basis required before any placement: an unread platform
  is a hold, not a default-allow
- placing/publishing a campaign on the client's behalf always requires a human sign-off
- audit export for every hold, approval and delivery
- periodic re-read of every catalogued platform's ad policy against its
  recorded `:read-on`
- backup manual process for governor/system outage

## Certification

Certified operators must prove engagement-record integrity, governor
independence, evidence-backed reporting and human review for every
high-stakes action.
