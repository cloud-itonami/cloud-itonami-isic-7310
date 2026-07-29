# Operator Guide

## First Deployment

1. Register the operator's license, jurisdiction and responsible principals.
2. Import historical engagements/clients and counterparties.
3. Run read-only validation of existing records against this blueprint's
   contracts.
4. Configure the Campaign Governor's hold/escalation policy.
5. Publish a dry-run operation and audit export.

## Minimum Production Controls

- spec-basis citation required before any customer-facing determination
- placing/publishing a campaign on the client's behalf always requires a human sign-off
- ordering a creator tie-up (a paid post from a named YouTube channel /
  influencer) on the client's behalf always requires a human sign-off
- audit export for every hold, approval and delivery
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
