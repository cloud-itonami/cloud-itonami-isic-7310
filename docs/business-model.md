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
- campaign-placement proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per agency
- support: monthly retainer with SLA
- migration: import from an incumbent campaign-management system
- per-campaign fee

## Trust Controls

- no campaign is placed/published on a client's behalf without human sign-off
- a fabricated media-buy or misleading-claim risk forces a hold, not an override
- every placement path is auditable
- emergency manual override paths remain outside LLM control
- a fabricated jurisdiction citation, incomplete evidence, or a proposed media
  spend exceeding its own authorized budget -- each forces a hold, not an
  override
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
own authorized budget, and no unresolved misleading-claim risk. These
proceed straight to the engagement ledger.

**Rejects or escalates**: the governor refuses to let the advisor
place a campaign on its own authority when any of the following hold
-- a fabricated jurisdiction spec-basis; incomplete evidence; a media
spend exceeding its own authorized budget; an unresolved misleading-
claim risk. A clean placement proposal still always routes to a human
-- `:actuation/place-campaign` is never auto-committed, at any rollout
phase.
