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

## Placing a real buy (and the two flags that charge)

`clojure -M:buy` is the only command in this repository that opens a
socket. It has three modes and you should use them in order:

```bash
clojure -M:buy --campaign <id>                    # dry run. No socket, no credential needed.
clojure -M:buy --campaign <id> --live             # real account, campaign created PAUSED. Zero charge.
clojure -M:buy --campaign <id> --live --spend --max-spend-jpy 50000   # ENABLED. This charges.
```

**Run the middle one first against any new account.** It exercises your
credentials, the two-step budget-then-campaign creation and the whole
governor path, and the only thing it does not do is charge. If it fails,
the third would have failed *after* taking your client's money. The
campaign it leaves behind is a real, paused campaign you can inspect in
the Google Ads UI and delete.

Credentials come from the environment and are never read from this
repository:

```bash
export GOOGLE_ADS_DEVELOPER_TOKEN=...   GOOGLE_ADS_ACCESS_TOKEN=...
export GOOGLE_ADS_CUSTOMER_ID=...       GOOGLE_ADS_LOGIN_CUSTOMER_ID=...
```

An incomplete set is refused **by name** before anything opens, so a
missing credential looks like a missing credential rather than an API
error you have to decode. No credential value is ever written to a
receipt or the audit ledger.

`--max-spend-jpy` is **your** ceiling, and it is deliberately a second
number: the campaign's own client-authorized budget is already
recomputed by the governor, and this one is checked again in the last
function before the network. Set it to what you are willing to lose if
the campaign record is wrong, not to the campaign's budget.

Six of the eight catalogued platforms have no buy adapter. A placement
targeting them still commits its record and its receipt says
`:unsupported` — this actor can rule on them and cannot buy them.

### What you have to obtain yourself

The Google Ads account, the developer token, the OAuth client and the
payment method. Those need your company's identity and payment details;
nothing here can stand in for them.

## Minimum Production Controls

- spec-basis citation required before any customer-facing determination
- platform ad-policy basis required before any placement: an unread platform
  is a hold, not a default-allow
- placing/publishing a campaign on the client's behalf always requires a human sign-off
- ordering a creator tie-up (a paid post from a named YouTube channel /
  influencer) on the client's behalf always requires a human sign-off
- audit export for every hold, approval and delivery
- periodic re-read of every catalogued platform's ad policy against its
  recorded `:read-on`
- backup manual process for governor/system outage

## Creator tie-ups: what the operator must supply

The actor produces the audited ORDER RECORD; it never contacts a
creator or a platform. Before enabling the tie-up lifecycle:

1. Record the jurisdiction's sponsorship-disclosure basis in
   `advertising.facts/catalog` if it is not already seeded (JPN, USA,
   GBR, DEU ship seeded), citing the authority's own published source.
   Never add a disclosure label an authority has not published --
   `advertising.governor` will accept any label present in that list,
   so the list is a trust boundary, not a convenience.
2. Record per campaign: `:creator-handle`, `:creator-platform`,
   `:creator-tieup-fee`, and the `:disclosure-label` actually agreed
   with the creator. The actor deliberately does NOT propose a
   disclosure label -- choosing one is a legal act.
3. Run `:creator/screen` before contracting the creator. A `:creator-
   eligibility-issue?` on the record HARD-holds the screening op
   itself; that hold is not overridable by an approver.
4. Note the R0 boundary in the README's coverage table: the tie-up
   evidence checklist attests that a creator-eligibility record
   exists, it does not prove the screening ran clean. Operators should
   keep the screening result in their own review policy until that
   gate is tightened.

Post-publication verification -- that the creator's actual post
carried the agreed disclosure -- is **outside** this actor. It has no
observation of published posts, and reports no verdict about them.

## Certification

Certified operators must prove engagement-record integrity, governor
independence, evidence-backed reporting and human review for every
high-stakes action.
