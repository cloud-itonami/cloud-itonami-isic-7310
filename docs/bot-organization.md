# Bot Organization: autonomous operation of the ISIC 7310 business

Authoritative design: superproject ADR
`90-docs/adr/2609030930-advertising-7310-autonomous-bot-organization.edn`.
This file is the operator-facing summary; the ADR wins on any conflict.

## The four bots and the one approval surface

| # | bot | form | does | capability ceiling |
|---|---|---|---|---|
| B1 | shinshi-acquisition (sales) | workstation resident (loop-noren pattern, `residency.edn` declaration) | discover prospects → eligibility via this blueprint's governor → ONE contact proposal per tick to the approval queue | public HTTP GET, murakumo-main (extract + verify), ingress key (`202 proposed`). **Never sends.** |
| B2 | campaign-intake (intake) | DO resident (Business Bot `business-7310-assignmentisic73`, live since 2026-09-03) | pre-build client briefs / media plans, run `:campaign/intake` through the Campaign Governor | DO sandbox tools only; actuation ops are structurally unreachable (phase excludes them from every `:auto` set) |
| B3 | ops-metering (measurement) | DO resident (Business Bot, same runtime, new bot_id) | daily funnel/placement metrics into senden vocabulary + BMC datoms; absent data is `:unmeasured`, never invented | `http_get` to metrics endpoints + kotobase x402 storage |
| B4 | kaizen-analyst (improvement) | workstation resident (weekly) | read BMC gate + dogfood-seed hold ledger, propose the next single move in the proposal journal | read-only + murakumo-main summarization; decisions stay human |
| — | approval surface | cockpit + owner | approve placements, budgets (`:authorized-budget`), outreach | **no bot holds an approval key** |

## Invariants (non-negotiable)

1. **No bot actuates.** `:actuation/place-campaign` / `:actuation/order-creator-tieup`
   stay human-gated by phase structure; real spend is the owner's
   `kbb -M:buy --live --spend` run.
2. **Key separation.** B1's ingress key can only propose (`202 proposed`);
   it cannot approve. The Business Bot wallet signs launch messages only
   (`execution_authority: false` — measured).
3. **Zero fabrication.** B3 records `:unmeasured` for anything it could not
   measure; B4 must cite measured datoms (`:cites`) and never multiply an
   unmeasured conversion rate into an expected yield.
4. **One capability plane per bot.** Sales, adjudication, measurement, and
   improvement live in separate bots so the approval queue remains the single
   readable trail of who proposed what, what held, and what a human approved.
5. **Fail-closed status.** Every bot publishes into the bot-slo vocabulary
   (`ok/stale/held/failed/unmeasured`); missing telemetry is never green.

## Rollout order (leverage-first)

1. B2 exists (live 2026-09-03). Next: point its queued prompts at the
   `:campaign/intake` evidence checklist so holds become a machine-enumerated
   list of missing items (dogfood-seed pattern).
2. B3: `POST /v1/grok-bots/bots` with a new `business-7310-*` bot_id, minimum
   credit (1000 micro-USDC), `allowed_hosts` limited to metrics endpoints.
3. B1: clone the loop-noren skeleton; swap domain scoring for this blueprint's
   governor; declare prospect sources + the 特定電子メール法 suppression list in
   `data/` before any contact proposal.
4. B4: weekly cron calling the existing BMC gate evaluator (`70-tools/bmc`
   `gftd.gate`); first proposal target is clearing the dogfood seed's two
   creative-dependent holds.
5. Register B1/B4 in the `cloud.itonami.bots-status.v1` residents block so all
   four bots are visible on `/bots/`.

## Money honesty

Bots advance the business; they are not revenue. The ads business runs in
leverage order 「配線 → 在庫 → 広告主」 (ADR-2608096000) — a running bot fleet
without inventory or an advertiser is not a revenue milestone and must not be
reported as one.
