# ADR-0003: four seeded platforms, one shared category vocabulary

## Status

accepted (2026-07-27)

Extends ADR-0002. Supersedes ADR-0002's Decision 2 (ship exactly one
platform); every other ADR-0002 decision stands unchanged.

Superproject mirror: `com-junkawasaki/root`
`90-docs/adr/2607271400-advertising-7310-multi-platform-catalog.edn`.

## Context

ADR-0002 built the media-platform layer and deliberately shipped ONE
transcribed platform, `chatgpt-ads`, with Google, Meta and Microsoft
recorded as known-but-unread. That was the honest call at the time:
their policies had not been read, and an invented taxonomy is worse
than an absent one.

The reason to read them was never "the catalog looks thin". It is that
**a one-platform catalog cannot express the thing the layer exists to
express.** With one entry, `category-disposition` is indistinguishable
from a global rule table — nothing in the code or the tests would break
if a future change quietly assumed all platforms behave like ChatGPT.
The layer's whole claim is that platforms are independent authorities
that disagree, and that claim was untested.

Reading the other three made the disagreement concrete and, in two
cases, sharp enough to invert a verdict:

| category | chatgpt-ads | google-ads | meta-ads | microsoft-advertising |
|---|---|---|---|---|
| `:travel-experiences` | permitted | permitted | permitted | **restricted** |
| `:legal-services` | **prohibited** | permitted | permitted | restricted |
| `:political` | prohibited | **restricted** | **restricted** | prohibited |
| `:ticket-reselling` | **not-permitted** | permitted | permitted | restricted |

A travel campaign that ChatGPT serves without ceremony is a restricted
category on Microsoft. Political advertising is outright disallowed on
Microsoft and merely certification-gated on Google and Meta. Any single
"is this ad OK?" predicate would be wrong for at least one platform.

## Decision

### Decision 1: transcribe all three, from their own policy indexes

- `google-ads` — <https://support.google.com/adspolicy/answer/6008942>,
  the four-category index (禁止コンテンツ / 禁止されている行為 /
  制限されているコンテンツと機能 / 編集基準と技術要件), read 2026-07-27.
- `meta-ads` — <https://transparency.meta.com/policies/ad-standards/>,
  the Advertising Standards index, read 2026-07-27.
- `microsoft-advertising` — the two policy indexes
  <https://about.ads.microsoft.com/en-us/policies/disallowed-content>
  and <https://about.ads.microsoft.com/en-us/policies/restricted-categories>,
  read 2026-07-27.

Index pages only. The per-category sub-pages were NOT read, and every
entry says so in `:transcription-notes` rather than implying a depth
the transcription does not have. None of the three states a version
number for itself, so `:policy-version` records the page identity and
the read date instead of inventing one.

### Decision 2: one shared `category-vocabulary`

`:ad-category` is drawn from a single closed vocabulary that all four
entries index into. Without it, "the platforms disagree" would be
indistinguishable from "the platforms spell things differently", and a
typo in one entry would silently read as a policy difference.
`platform_test.clj` pins that every category named by any entry is a
member.

The vocabulary holds **business categories** — what a campaign
advertises. Content and site-quality failures that the platforms list
alongside their category taxonomies (Microsoft's "Inaccessible site",
"Non-Indexed Sites", "Unmoderated User-Generated Content"; Google's
editorial and technical requirements; Meta's profanity, bullying and
privacy rules) are deliberately excluded: they are properties of a
specific creative or landing page, checked at creative review, not
things a campaign is *for*. Modelling them as categories would have
made the catalog look more complete while making
`category-disposition` mean two different things.

### Decision 3: open vs closed category sets are transcribed, not assumed

ChatGPT states that every category it does not name is disallowed at
launch; the other three enumerate prohibited and restricted content
without closing the set. `:closed-category-set?` records this per
platform, so an unnamed category resolves `:not-permitted` on ChatGPT
and `:permitted` on the other three. `:permitted-categories` is empty
for the three open sets — they publish no allow-list, and inventing one
would be exactly the fabrication ADR-0002 exists to prevent.

### Decision 4: an untranscribed country table is a hold, not a yes

All three new platforms limit restricted categories by country, and
none of those tables is on the index pages. Rather than record a
jurisdiction set that was never read, or omit the key and have the
predicate default to permissive, the entries carry the sentinel
`:restricted-category-jurisdictions :per-category-unenumerated`, and
`restricted-category-allowed-jurisdiction?` returns **false for every
jurisdiction** when it sees it.

The effect is that every restricted-category placement on Google, Meta
and Microsoft HARD-holds today. That is intended. The alternative —
answering `true` because no table was consulted — would let a campaign
run in a country the platform may well forbid it in, on the strength of
a document nobody read. The gap is visible in
`:transcription-notes`, tested in `an-untranscribed-country-table-holds-everywhere`,
and closed by transcribing the table, not by relaxing the predicate.

### Decision 5: jurisdiction-scoped attestations

Meta requires an EU DSA beneficiary/payer disclosure on every ad shown
in the EU. Adding it to `:required-attestations` would have held a
JPN-only campaign for an EU-only requirement; omitting it would have
missed a hard legal obligation. So the entry gained
`:jurisdiction-attestations {iso3 -> #{...}}`, and
`required-attestations` / `missing-attestations` take the campaign's
jurisdiction.

Only `DEU` is enumerated, because `DEU` is the only EU jurisdiction in
`advertising.facts/catalog` — the two catalogs extend together, and the
note says so.

This also keeps `:distinguishable-from-product-ui` where it belongs:
it stays on `chatgpt-ads` alone, because that requirement follows the
generative surface, not the fleet.
`generative-attestation-follows-the-surface-not-the-fleet` pins that
the other three do not inherit it.

### Decision 6: `cross-platform-disposition`

One function answering "how does every catalogued platform treat this
category?" — the question an agency asks before it buys. It is only
answerable because the platforms are modelled separately, which makes
it the cheapest possible demonstration of why they are.

## Alternatives considered

**Keep one platform and call the layer proven.** Rejected: with one
entry the layer's central claim is untested, and the first regression
that assumes ChatGPT's shape would pass CI.

**Transcribe the per-category sub-pages too.** Not rejected —
deferred, and recorded as the extension task. Google's restricted-
category sub-pages alone run to dozens of country tables; transcribing
them from a summary rather than a read would reintroduce exactly the
failure ADR-0002 Decision 2 refused. Holding until they are read is
the safe intermediate state.

**Normalise the four taxonomies into one merged rule set.** Rejected,
emphatically. It would erase the disagreements that are the layer's
entire output, and there is no correct merge: `:political` cannot be
simultaneously prohibited (Microsoft) and restricted (Google) in one
table. `platforms-disagree-and-must-keep-disagreeing` asserts specific
disagreements so a future tidy-up has to delete a test that explains
why it must not.

**Model Meta's special ad categories (housing / employment / credit).**
Rejected for now: they constrain *targeting* rather than admissibility,
and this actor does not model targeting. Recorded in
`:transcription-notes` so the gap is visible rather than silently
absent.

## Consequences

- Coverage goes 1 → 4 platforms. `coverage`'s note now leads with what
  the entries do NOT capture — the open-ended reserve clauses every
  platform keeps — so the bigger number does not read as completeness.
- Every restricted-category placement on the three new platforms holds
  until its country table is transcribed. Operators will meet this
  immediately; `docs/operator-guide.md` explains the fix.
- `missing-attestations` and `required-attestations` gained a
  jurisdiction arity; `compliance-checklist` likewise. The 1-arity
  forms are retained.
- Demo store grew campaign-10..12, each isolating one cross-platform
  effect: an open-set pass on Google, a jurisdiction-scoped attestation
  hold on Meta in DEU, and a restricted-category hold on Microsoft for
  a category ChatGPT permits.
- Tests 57 → 68, assertions 220 → 419.
- **A clean verdict from this catalog is a necessary and not a
  sufficient condition for platform approval.** Four platforms make
  that easier to forget than one did, so it is stated in the README,
  in `coverage`'s note, and here.
