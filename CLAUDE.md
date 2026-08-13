# CLAUDE.md — working in cloud-itonami-isic-7310

Guidance for agents editing this repo. The README explains what the
business is; this file explains what will break if you edit carelessly.

## The one invariant

An independent governor re-verifies against the actor's own committed
records before any real-world act. Everything else here is in service
of that.

Two layers enforce it for both actuations, and they are deliberately
redundant:

- `advertising.governor/high-stakes` — always escalates
- `advertising.phase/auto-set` — subtracts `actuation-ops` from every
  phase's `:auto` set, structurally

If you find yourself removing one because "the other already covers
it", stop. The redundancy is the design.

## Layout

| Namespace | Role |
|---|---|
| `advertising.facts` | Cited jurisdiction catalog. Two separate bases per entry: advertising-standards and sponsorship-disclosure |
| `advertising.registry` | Pure record construction + pure ceiling predicates. No I/O |
| `advertising.store` | `Store` protocol, `MemStore` ‖ `DatomicStore`, append-only ledger |
| `advertising.governor` | The censor. 7 HARD checks + 2 double-actuation guards + 1 soft gate |
| `advertising.phase` | Rollout gate 0→3 |
| `advertising.advertisingadvisor` | The sealed LLM node. Untrusted |
| `advertising.operation` | The langgraph StateGraph wiring it together |
| `advertising.placer` | The actuation seam. Builds buy requests, holds no credential, opens no socket |
| `advertising.sim` / `advertising.render-html` | `-main` drivers, not library code |
| `advertising.buy` | The operator's `-main`. **The only file that opens a socket** |

## Rules that are not negotiable

**Never fabricate a citation.** A jurisdiction absent from
`advertising.facts/catalog` has no spec-basis, and the governor holds.
Adding one means citing a real official source. The same applies to
`:accepted-disclosure-labels`: those are wordings the authority itself
publishes, and the list is a trust boundary — the governor accepts any
label in it.

**The advisor reports, it does not author.** It must never propose a
sponsorship-disclosure label. Choosing one is a legal act, and an
invented plausible label would defeat the governor check that exists
to catch exactly that. See ADR-0002.

**No network calls in the actor.** `registry` builds the order record;
it does not contact YouTube, Instagram, TikTok or X. Adapters exist in
the workspace (`kotoba-lang/com-youtube`, `com-googleads`, `adnet`) and
this repo depends on none of them, on purpose. Any real-world
integration belongs downstream of a human-approved order — which is
exactly where `advertising.placer` sits (ADR-0006). The rule has not
softened: `placer` is pure, builds requests and opens nothing;
`advertising.buy` is the single file with a socket in it, and a human
runs it. Do not import `buy` from anything, and do not add I/O to
`placer` — the moment either happens, "the actor cannot reach a network"
stops being checkable by reading one file.

**Live and SPENDING are two decisions, and both defaults are off.** A
campaign created PAUSED is real and charges nothing; only ENABLED
charges. `--live` alone must never produce ENABLED, and a spending
placer must always carry `:max-spend-micros` — an operator-set ceiling
independent of the campaign's own authorized budget the governor
recomputes. If you find yourself removing one of those two ceilings
because "the other already covers it", stop; that is the same
redundancy-is-the-design note as the two actuation layers above.

**A receipt is never optional.** Every `:actuation/place-campaign`
commit appends a `:placement-dispatch` fact stating `:mode`, `:sent?`
and the platform. An absent receipt and a dry-run receipt read
identically to an auditor, so "we sent nothing" must be written down
rather than left out. Never let a credential value into one.

**Keep the two actuations independent.** Separate guard booleans
(`:campaign-placed?` / `:tieup-ordered?`), separate sequences, separate
histories. Sharing either would let one act satisfy the other's
double-actuation guard.

**Guard on a dedicated boolean, never on `:status`.** Inherited from
`cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320).

## Adding a governed op

1. `advertising.facts` — the cited basis it checks against, if new
2. `advertising.registry` — pure predicate / record builder
3. `advertising.store` — protocol method + BOTH backends
4. `advertising.governor` — the HARD check, wired into `check`
5. `advertising.phase` — into `write-ops`; into `actuation-ops` too if
   it performs a real-world act
6. `advertising.advertisingadvisor` — the proposal generator + route
7. tests in all five files, plus `advertising.sim` and
   `advertising.render-html` so the console shows it

Steps 3 and 7 are the ones that get skipped. A protocol method added
to only one backend passes every test that uses `seed-db` and fails
only in production.

## Commands

```bash
clojure -M:dev:test      # must be green before any commit
clojure -M:lint          # errors fail CI
clojure -M:dev:run       # both lifecycles + all eight HARD holds
clojure -M:dev:coverage  # cloverage; the README figure comes from here
clojure -M:dev:render-html   # regenerates docs/samples/operator-console.html
clojure -M:buy --campaign <id>              # dry run: builds the buy requests, opens nothing
clojure -M:buy --campaign <id> --live       # real account, campaign PAUSED, zero charge
# --live --spend --max-spend-jpy N          # ENABLED: this charges the client
```

The console must stay byte-identical across reruns — no timestamps, no
randomness, no hand-typed numbers. `regenerate.yml` commits it nightly,
so nondeterminism shows up as a spurious daily diff.
`advertising.drivers-test` asserts this directly; if you add anything
time- or order-dependent to the render path, that is the test that
fails.

Coverage is measured with nothing excluded, and the README quotes the
number `clojure -M:dev:coverage` prints. If you lower it, say so rather
than narrowing the measurement to hide it.

## Docs to update alongside code

`README.md` (scope, layout, coverage tables), `docs/business-model.md`
(Trust Controls), `docs/operator-guide.md` (production controls), and a
new ADR under `docs/adr/` for any decision a future reader would
otherwise have to reverse-engineer.
