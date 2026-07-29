# ADR-0004: Creator tie-up (YouTube / influencer) as a second governed actuation

- Status: accepted
- Date: 2026-07-29
- Supersedes: nothing. Extends ADR-0001 (architecture); orthogonal to ADR-0002/0003 (media-platform layer), which govern campaign placement rather than creator engagement.

## Context

The actor shipped in ADR-0001 covers campaign intake, advertising-
standards evidence assessment, misleading-claim-risk screening and
campaign placement. It had no way to express the act an advertising
agency is now asked for constantly: **commissioning a paid post from a
named YouTube channel or influencer on a client's behalf.**

That gap was real, not cosmetic. A survey of this org's 1,349
repositories found no itonami covering it: `isco-2431` (advertising
and marketing professionals) is scoped to physical proof printing and
signage installation, `isic-7320` is market research, `isic-6312`
carries only a `:sponsorship-disclosure` social-impact tag with no
implementation, and `isic-7490` is generic professional services.
Within this repo, the single substantive mention of influencer
marketing anywhere was the FTC Endorsement Guides appearing inside the
USA `:legal-basis` string -- a citation, not a lifecycle.

## Decision

### 1. A second actuation on this actor, not a new repository

Creator tie-up is advertising-agency work under ISIC Rev.5 7310. There
is no separate ISIC code for it, and this repo's own README already
prescribes the extension path: *"add the next gate as its own governed
op with its own HARD checks and tests."* A new repository would have
duplicated `advertising.facts`, the store seam, the governor shape and
the phase table in order to model the same client, the same
jurisdiction catalog and the same authorized budget.

So `:actuation/order-creator-tieup` joins `:actuation/place-campaign`
in `advertising.governor/high-stakes`. The README's previous claim of
a "single-actuation shape" is retired; this actor now has two.

### 2. The two actuations are fully independent

Separate guard booleans (`:campaign-placed?` / `:tieup-ordered?`),
separate jurisdiction-scoped sequences (`JPN-PLC-######` /
`JPN-TIE-######`), separate histories, separate evidence checklists.

A campaign that has been placed has **not** thereby been ordered from
a creator, and a campaign may legitimately do one without the other.
Sharing either guard would have produced a silent, un-auditable
failure: a placed campaign would refuse its own legitimate tie-up
order, or -- far worse -- a placement would satisfy the double-order
guard and let a second real order through. `the-two-actuation-guards-
are-independent` and the store-contract parity block assert both
directions on both backends.

### 3. Structural enforcement of the never-auto invariant

ADR-0001 kept `:actuation/place-campaign` out of every phase's `:auto`
set by convention plus a test. With two actuations, and more plausible
later, `advertising.phase` now derives every `:auto` set through
`auto-set`, which subtracts `actuation-ops` by construction. A future
actuation op added to that set is covered without anyone remembering
to write the test -- and `no-actuation-op-is-ever-auto-eligible`
asserts the property over the whole set rather than op by op.

### 4. Sponsorship disclosure is a GOVERNOR check, not an advisor output

This is the load-bearing decision of this ADR.

A creator tie-up is lawful or unlawful largely on one question: *was
the post identifiable as advertising?* Japan's 2023 designation under
景表法5条3号 (ステマ規制), the FTC Endorsement Guides at 16 CFR Part
255, CAP Code section 2 and UWG § 5a Abs. 4 all turn on it. The answer
is **published jurisdiction-specific fact, not reasoning** -- which is
exactly the class of thing the AdOps-LLM cannot be trusted with, for
the same reason it is not trusted to state a jurisdiction's evidence
requirements.

Three consequences:

- `advertising.governor/sponsorship-disclosure-missing-violations`
  independently recomputes the answer against the campaign's own
  recorded `:disclosure-label`, needing no proposal inspection at all
  -- the same shape as the ceiling checks.
- **Nothing recorded and something-recorded-that-the-authority-does-
  not-publish are the SAME HARD hold.** 「タイアップ」 is a real word
  the industry uses that is not among the 消費者庁's published
  examples (「広告」「宣伝」「プロモーション」「PR」). A governor that
  accepted it because it *sounds* like a disclosure would be worse
  than no governor, because it would launder the failure through an
  audit trail. `campaign-9` in the demo seed exists to hold this case
  down.
- The advisor's `verify-tieup-brief` **reports** the recorded label
  verbatim and never **proposes** one. An advisor that invented a
  plausible-looking label would defeat the check it is supposed to
  feed.

`:accepted-disclosure-labels` is documented as a floor, not an
exhaustive legal whitelist -- no authority publishes one. The check
answers "did the operator record a label the authority has itself
named?", not "is this post compliant."

### 5. The combined-spend ceiling

`creator-tieup-fee-exceeds-authorized-budget?` is the eighth instance
of this fleet's MAXIMUM-ceiling family, and the first to sum two of
the subject's own committed amounts before comparing: a tie-up fee is
spent **on top of** the media plan, not instead of it.

Checking the fee alone against the budget would clear a combined spend
the client never authorized -- `campaign-6` (500,000 media + 400,000
fee against an 800,000 authorization) is seeded precisely so that a
regression to a fee-only comparison fails a test, and
`tieup-fee-is-checked-on-top-of-the-media-plan-not-alone` asserts the
precondition explicitly rather than leaving it implied by the numbers.

### 6. No platform integration, at all

`advertising.registry/register-creator-tieup-order` builds the order
RECORD. It makes no call to YouTube, Instagram, TikTok or X; nothing
is sent to the creator. The workspace does ship adapters an operator
could wire in downstream (`kotoba-lang/com-youtube`,
`com-googleads`, `adnet`) and this repo depends on none of them:
taking such a dependency would put a real network call behind the
governor's actuation gate, collapsing the boundary the whole design
rests on.

`platform` and `creator-handle` are recorded verbatim rather than
validated. This actor cannot check that a handle exists or that
follower counts are genuine, and inventing a validity rule for a
handle would be the same fabrication `advertising.facts` refuses to
commit for jurisdictions.

## Consequences

New ops (7 total, from 4):

| Op | Effect | Auto-eligible |
|---|---|---|
| `:creator/screen` | `:creator-screen/set` | never, at any phase |
| `:tieup/verify` | `:tieup-brief/set` | never, at any phase |
| `:actuation/order-creator-tieup` | `:tieup/mark-ordered` | never, at any phase (structural) |

New HARD checks: `creator-ineligible` (unconditional, so the screening
op HARD-holds on its own finding), `tieup-evidence-incomplete`,
`creator-tieup-fee-exceeds-authorized-budget`,
`sponsorship-disclosure-missing`, plus the `already-ordered` guard.

Test suite: 30 tests / 127 assertions → 58 tests / 322 assertions,
0 failures; `clojure -M:lint` clean.

### Known R0 boundary

Ordering a tie-up requires the tie-up evidence checklist on file, but
that checklist is operator-submitted, so it attests that a
creator-eligibility record *exists* rather than proving
`:creator/screen` actually ran and returned `:eligible`. This is the
same shape the placement lifecycle already shipped in ADR-0001
(`:media-plan/verify` vs `:risk/screen`), and it is left consistent
rather than tightened on one side only. Requiring a committed
`:eligible` verdict before `:actuation/order-creator-tieup` is the
natural next gate, and would be worth applying to both lifecycles at
once.
