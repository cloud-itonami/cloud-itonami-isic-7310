# Business Model: Advertising

## Classification

- Repository: `cloud-itonami-isic-7310`
- ISIC Rev.5: `7310`
- Activity: advertising -- creating and placing advertising campaigns for clients across media, including commissioning paid posts from YouTube channels and influencers
- Social impact: professional standards, data sovereignty, transparent audit, sponsorship-disclosure compliance

## Customer

- independent advertising agencies
- cooperative creative collectives
- community media-buying programs
- influencer/creator marketing shops that need an auditable disclosure trail

## Offer

- brief intake
- creative/media-plan proposal
- campaign-placement proposal
- creator-eligibility screening for a named YouTube channel / influencer
- creator-tie-up order proposal, gated on a jurisdiction-published sponsorship-disclosure label
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per agency
- support: monthly retainer with SLA
- migration: import from an incumbent campaign-management system
- per-campaign fee

## Trust Controls

- no campaign is placed/published on a client's behalf without human sign-off
- no creator tie-up is ordered on a client's behalf without human sign-off
- a fabricated media-buy or misleading-claim risk forces a hold, not an override
- every placement and tie-up-order path is auditable
- emergency manual override paths remain outside LLM control
- a fabricated jurisdiction citation, incomplete evidence, or a proposed media
  spend exceeding its own authorized budget -- each forces a hold, not an
  override
- campaign placement is logged and escalated, and cannot be finalized twice
  for the same campaign: a double-placement attempt is held off this actor's
  own campaign facts alone, with no upstream comparison needed
- a creator tie-up whose fee, ADDED TO the campaign's own media spend, exceeds
  the client's own authorized budget forces a hold: the fee is spent on top of
  the media plan, not instead of it
- a creator carrying an unresolved eligibility issue forces a hold, evaluated
  unconditionally so the screening step itself cannot be routed past
- a creator tie-up ordered with NO recorded sponsorship-disclosure label, or
  with a label the jurisdiction's own authority does not publish, forces the
  same hold: recording something must never be mistakable for recording
  something compliant
- creator-tie-up ordering is logged and escalated, and cannot be finalized
  twice for the same campaign, off a guard fully independent of the placement
  guard
- neither a campaign nor a creator tie-up can be actuated with NO screening on
  file at all: a committed verdict that actually clears is required, because
  "we never looked" carries the same exposure as "we looked and found
  something" with no record that anyone looked

## Campaign Governor: decision rule

`blueprint.edn` fixes `:itonami.blueprint/governor` to `:campaign-
governor` -- this is not a generic "review step," it is the gate the
TWO real-world acts this business performs (placing/publishing a
campaign on the client's behalf; ordering a creator tie-up on the
client's behalf) must pass. The governor sits between the AdOps-LLM
and execution, per the README's Core Contract:

```text
AdOps-LLM -> Campaign Governor -> hold, proceed, or human approval
```

**Approves**: routine advertising actions proposed against a campaign
that already has a consented brief on file, a media spend within its
own authorized budget, and no unresolved misleading-claim risk. For a
creator tie-up: a screened-clean creator, a combined media-spend-plus-
fee within the client's own authorization, and a sponsorship-
disclosure label the jurisdiction's own authority publishes. These
proceed straight to the engagement ledger.

**Rejects or escalates**: the governor refuses to let the advisor
place a campaign on its own authority when any of the following hold
-- a fabricated jurisdiction spec-basis; incomplete evidence; a media
spend exceeding its own authorized budget; an unresolved misleading-
claim risk. It refuses a creator tie-up when -- an ineligible creator;
incomplete tie-up evidence; a combined spend exceeding the client's
own authorization; a missing or unpublished disclosure label. A clean
proposal for either act still always routes to a human -- neither
`:actuation/place-campaign` nor `:actuation/order-creator-tieup` is
ever auto-committed, at any rollout phase.

### Why the disclosure gate sits here and not in the LLM

An LLM can draft a brief. It cannot know which disclosure wordings a
regulator has actually published, and a plausible-sounding invented
one -- 「タイアップ」, 「提供」 -- is exactly what produces an
undisclosed-advertising finding against the agency. So the governor
recomputes it against `advertising.facts`, and the advisor is
deliberately built to report the recorded label rather than propose
one. See `docs/adr/0002-creator-tieup.md`.
