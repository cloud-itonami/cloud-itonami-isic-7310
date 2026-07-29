# ADR-0005: "We never looked" is not a clean state

- Status: accepted
- Date: 2026-07-29
- Closes: the R0 boundary recorded in ADR-0004
- Extends: ADR-0001 (architecture), ADR-0004 (creator tie-up)

## Context

ADR-0004 recorded a boundary and left it open:

> Ordering a tie-up requires the tie-up evidence checklist on file, but
> that checklist is operator-submitted, so it attests that a
> creator-eligibility record *exists* rather than proving
> `:creator/screen` actually ran and returned `:eligible`. This is the
> same shape the placement lifecycle already shipped in ADR-0001
> (`:media-plan/verify` vs `:risk/screen`), and it is left consistent
> rather than tightened on one side only.

Measuring coverage made the shape of the hole concrete. Both screening
checks were written to fire on a *finding*:

```clj
;; before
hit-on-file? (= :unresolved (:verdict (store/risk-screen-of st campaign-id)))
```

`store/risk-screen-of` returns `nil` when no screening was ever
committed, and `(= :unresolved nil)` is false. So the checks caught
"we looked and found a problem" and missed "we never looked" — and the
second is reachable by simply skipping a step, which is exactly what a
rushed operator does. Four tests in this repo encoded the hole without
anyone noticing: they placed and ordered after verifying evidence but
without ever screening, and passed.

The asymmetry is the wrong way round. A campaign with an unresolved
misleading-claim risk on file is a campaign someone examined. A
campaign with no screening at all carries the same exposure *and* no
record that anyone looked — strictly worse for the agency if the
placement is later disputed, because there is nothing to produce.

## Decision

### 1. Both actuations require a positive screening verdict on file

`risk-screen-missing-violations` (for `:actuation/place-campaign`)
and `creator-screen-missing-violations` (for `:actuation/order-creator-
tieup`) are new HARD checks. Each requires a committed screening record
whose verdict actually clears: `:resolved` and `:eligible` respectively.

Applied to **both lifecycles in the same change**, which is what
ADR-0002 asked for. Tightening only the tie-up side would have left the
older, more-used path weaker than the newer one.

### 2. A non-clearing verdict is not a clearance

`:unknown` — what a typo'd subject, an absent campaign, or a tie-up
with no `:creator-handle` produces — does not satisfy the requirement.
Only the affirmative verdicts do. `a-screening-that-never-cleared-is-
not-a-clearance` holds this down; without it, screening a campaign that
has no creator at all would have produced a record that reads as
"screened" to anything checking for mere presence.

### 3. The specific finding wins over the generic absence

The new checks deliberately exclude `:unresolved`/`:ineligible` from
their trigger sets, because checks 4 and 6 already report those. An
audit ledger listing both `misleading-claim-risk-unresolved` and
`risk-screen-missing` for one campaign would obscure which of two very
different problems the operator actually has. Asserted by
`the-missing-screen-rule-does-not-double-report-a-found-risk`.

### 4. The demo shows the new holds, each in isolation

`advertising.sim` and `advertising.render-html` now attempt each
actuation once *before* screening (demonstrating the new hold) and
again after (demonstrating the hold that scenario was originally built
for). Without the added screening steps, `campaign-3`'s budget hold and
`campaign-6`'s combined-spend hold would each have fired alongside a
screening-missing hold, muddying what the demo claims to show.

## Consequences

- Two new HARD checks; the governor now runs nine, plus two
  double-actuation guards and one soft gate.
- **This is a behaviour change to a shipped actor.** A deployment that
  was placing campaigns without running `:risk/screen` will start
  seeing holds. That is the point, and the hold names the missing step.
- Test helpers renamed to `prepare-placement!` / `prepare-tieup!`, so
  the full precondition set is stated in one place rather than
  re-assembled per test.
- 85 tests / 490 assertions → 89 / 510.

### What is still not closed

The evidence checklists remain operator-submitted. This ADR makes the
*screening verdicts* first-class governor inputs; it does not make the
`:media-plan/verify` and `:tieup/verify` checklists self-proving. An
operator can still submit a checklist asserting documents that do not
exist. Closing that needs per-document attestation records, which is a
larger change and is not attempted here.
