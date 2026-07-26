# Business Model: Advertising

## Classification

- Repository: `cloud-itonami-isic-7310`
- ISIC Rev.5: `7310`
- Activity: advertising -- creating and placing advertising campaigns for clients across media
- Social impact: professional standards, data sovereignty, transparent audit

## Customer

- independent advertising agencies
- cooperative creative collectives
- community media-buying programs

## Offer

- brief intake
- creative/media-plan proposal
- media-platform ad-policy conformance assessment (per-platform category
  taxonomy, restricted-category approval + jurisdiction limits, excluded
  placement contexts, required attestations)
- campaign-placement proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per agency
- support: monthly retainer with SLA
- migration: import from an incumbent campaign-management system
- per-campaign fee

### Price list (2026-07-25)

The customer is the **agency**, not the advertiser: this is B2B software an
advertising operator runs to serve its own client roster.

| Tier | Scope | Price |
|---|---|---|
| Self-host | fork and run it yourself under AGPL-3.0 | ¥0 |
| Setup / implementation | one-time: jurisdiction spec-basis config, governor budget ceilings, ledger bootstrap | ¥300,000 |
| Managed Starter | 1 agency, ≤10 active campaigns, ≤¥5M/月 managed media spend | **¥80,000/月** |
| Managed Growth | ≤50 active campaigns, ≤¥50M/月 spend, multi-client separation + audit package | ¥200,000/月 |
| Support retainer (SLA) | 4h business-hours response; add-on to either managed tier | +¥50,000/月 |
| Per-campaign | for agencies preferring variable over flat | ¥10,000/campaign |
| Migration | import from an incumbent campaign-management system, scoped per source | quoted |

**Market-anchored (2026-07-25)**: the bands above were checked against public
2026 pricing for the two adjacent categories this sits between.

- Campaign-automation SaaS sold to operators: Revealbot from $99/月 (up to
  $10k managed spend) rising to $199/月 at $50k spend; AdStellar Hobby $49 /
  Pro $129 / Ultra $499; WordStream from ~$49/月; HubSpot Marketing Hub
  Professional $800–890/月. At ~¥150/$ that is ¥7k–¥134k/月.
- Managed Google Ads retainers charged *by* agencies: ~$500/月 for small
  business, $1,500–5,000/月 mid-market, $15,000+/月 enterprise — ¥75k–750k+/月
  at the same rate.

Managed Starter at ¥80,000/月 therefore lands just above the top self-serve
SaaS tier (HubSpot Pro ¥120k/月 is the nearest comparable, and it carries no
governor or audit ledger) and well under one mid-market retainer the agency
itself bills (¥225k–750k/月) — i.e. the platform costs the operator a fraction
of a single client engagement. Starter is set to ¥80,000/月 flat deliberately
to match the house Managed Starter tier already live on
`cloud-itonami-isic-6399`, so the portfolio presents one price ladder rather
than one per blueprint.

**Deliberately not priced as a share of ad spend.** The industry norm is 2–5%
of managed media spend. This blueprint refuses that model: it pays the
operator more precisely when the client spends more, which sits in direct
tension with the one thing the Campaign Governor exists to enforce — a media
spend exceeding its own authorized budget forces a hold, not an override. A
flat fee keeps the governor's budget ceiling and the operator's incentive
pointing the same way.

**Unit economics (estimate, not measured)** — assumptions stated so they can be
falsified once a paying operator exists:

- infrastructure: static UI + actor runtime ≈ ¥5k–15k/月
- LLM: proposals only at brief-intake / assess / place — not a per-impression cost
- operator sign-off is the real cost driver, because
  `:actuation/place-campaign` is never auto-committed at any rollout phase: at
  ~2 min per placement, Starter (10 campaigns × ~4 placements/月 ≈ 40) ≈ 1.3
  h/月; Growth (50 campaigns × ~4 ≈ 200) ≈ 6.7 h/月
- support + incident: budget 5 h/月 until a first operator's media mix stabilises

At ¥80,000/月 Starter the gross margin before operator labour is >80%; the
binding constraint is human sign-off time, not infrastructure. That is a
consequence of the trust model, not an inefficiency to optimise away.

**Not yet purchasable.** Unlike `cloud-itonami-isic-6399` (live Stripe Payment
Link for its ¥80,000/月 Starter), no Stripe object exists for any tier above.
The prices are ratified as a published price list only; creating the Stripe
product/price and the Payment Link is a payment-rail action reserved for the
owner. Until that happens this blueprint has a price but no checkout, and
`externalPaid` stays 0.

## Trust Controls

- no campaign is placed/published on a client's behalf without human sign-off
- a fabricated media-buy or misleading-claim risk forces a hold, not an override
- every placement path is auditable
- emergency manual override paths remain outside LLM control
- a fabricated jurisdiction citation, incomplete evidence, or a proposed media
  spend exceeding its own authorized budget -- each forces a hold, not an
  override
- a media platform whose own published ad policy has not been read and
  transcribed cannot be placed on at all: an unknown platform holds, it does
  not default to permissive (ADR-0002)
- an ad category the target platform itself prohibits, a restricted category
  without both advertiser approval and an allowed jurisdiction, a placement
  requested against a context the platform refuses to serve ads near, or a
  generative surface where ad/answer distinguishability was never attested --
  each forces a hold, not an override
- lawfulness and platform-policy conformance are checked independently:
  clearing the jurisdiction side clears nothing about the platform, and vice
  versa
- campaign placement is logged and escalated, and cannot be finalized twice
  for the same campaign: a double-placement attempt is held off this actor's
  own campaign facts alone, with no upstream comparison needed

## Campaign Governor: decision rule

`blueprint.edn` fixes `:itonami.blueprint/governor` to `:campaign-
governor` -- this is not a generic "review step," it is the one gate
the ONE real-world act this business performs (placing/publishing a
campaign on the client's behalf) must pass. The governor sits between
the AdOps-LLM and execution, per the README's Core Contract:

```text
AdOps-LLM -> Campaign Governor -> hold, proceed, or human approval
```

**Approves**: routine advertising actions proposed against a campaign
that already has a consented brief on file, a media spend within its
own authorized budget, no unresolved misleading-claim risk, and a
target media platform whose own published ad policy has been read,
transcribed and satisfied. These proceed straight to the engagement
ledger.

**Rejects or escalates**: the governor refuses to let the advisor
place a campaign on its own authority when any of the following hold
-- a fabricated jurisdiction spec-basis; incomplete evidence; a media
spend exceeding its own authorized budget; an unresolved misleading-
claim risk; a target media platform with no transcribed ad policy; a
missing platform-conformance assessment; an ad category the platform
itself prohibits; a restricted category without both advertiser
approval and an allowed jurisdiction; an unmade required attestation;
or a placement requested against a context the platform refuses to
serve ads near. A clean placement proposal still always routes to a
human -- `:actuation/place-campaign` is never auto-committed, at any
rollout phase.

**Two independent authorities.** The jurisdiction checks answer "is
this campaign lawful where it runs?"; the media-platform checks answer
"does the platform it was bought on actually allow it?" (ADR-0002).
Neither subsumes the other, so both run on every placement.
