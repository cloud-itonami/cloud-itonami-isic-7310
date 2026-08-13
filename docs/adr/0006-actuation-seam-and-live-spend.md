# ADR-0006: The actuation seam, and what it takes to spend a client's money

Date: 2026-08-13
Status: accepted
Supersedes nothing. Extends ADR-0002 (media-platform layer) and
ADR-0003 (multi-platform catalog).

## Context

Two gaps, and they were connected.

**The catalog could rule on four platforms.** An agency asking "can we
run this on X / LINE / Telegram / YouTube?" got the honest but useless
answer that those platforms had no transcribed policy, so every
placement targeting them HELD.

**Nothing downstream could act.** `:actuation/place-campaign` committed
`:campaign/mark-placed` and drafted a placement record, and the audit
ledger said nothing about whether an ad had actually been bought.
Bookkeeping and spending a client's money are different events, and a
ledger that cannot tell them apart has the same defect as a gate
reporting a pass it never measured.

## Decision

### 1. Four more platforms, and two states the model could not express

`x-ads`, `telegram-ads`, `youtube-ads` and `line-yahoo-ads` were
transcribed from a direct read on 2026-08-13. Two of them did not fit
the existing model, and the model was wrong rather than the platforms.

**`:policy-read :partial`.** LINEヤフー's enumerated 掲載基準 renders via
JavaScript and served a loading error to every fetch; only the
operator's published change document could be read. Under the open-set
rule every unread category would have resolved `:permitted` — the entry
would have waved through precisely what nobody read. A third
disposition, `:not-transcribed`, now says *we did not read this* and the
governor holds on it, with a detail distinct from *the platform refused
it*. Reading the rest is the extension task; relaxing the flag is not.

**`:categories-incorporated-from`.** YouTube's own overview states that
the Google Ads policies apply to ads on YouTube, so `youtube-ads`
resolves its categories *and its restricted-category country rule*
through `google-ads` rather than copying them into a second table that
drifts. Without the country hop the incorporating surface would have
been freer about where a restricted category may run than the policy it
inherited it from. This key is only ever set from a transcribed
sentence; two surfaces looking similar is not a reason.

### 2. The seam is one namespace, and its default is to do nothing

`advertising.placer` is the only thing in the actor that could act. It
is called from `advertising.operation`'s commit node — downstream of the
governor and the human approval, so a held campaign builds no request at
all — and it is called on **every** `:actuation/place-campaign` commit,
writing a receipt to the ledger unconditionally. A dispatch that sends
nothing writes `:sent? false` and says why. The receipt is never
omitted, because an absent receipt and a dry-run receipt read
identically to whoever audits this later.

`advertising.placer` itself stays pure and portable: it builds requests
and holds no credential and opens no socket. `advertising.buy` is the
only file in the repository that opens one, and a human runs it.

### 3. Live and spending are two decisions

A Google Ads campaign created via the API with `status: PAUSED` is a
real campaign object on a real account and charges nothing. Only
`ENABLED` charges. Therefore:

- `--live` exercises the credentials, the two-step budget-then-campaign
  creation and the governor path end to end, and creates the campaign
  PAUSED. Everything real except the charge.
- `--spend` is required on top of it to create the campaign ENABLED, and
  it in turn requires `--max-spend-jpy` — an **operator-set** ceiling,
  independent of the campaign's own client-authorized budget that
  `advertising.registry` already recomputes, and checked in the last
  function before the network.

Two ceilings from two sources, because the failure being guarded against
is a campaign record that is internally consistent and wrong.

### 4. Credentials are named, never held

Four environment variables, read by `credentials-from-env`, which
returns a complete set or the list of **missing names** — never a
partial map, because a request built from three of four fails at the API
with an error the operator has to decode. No credential value reaches a
receipt or the ledger; the requests that go out carry them, the audit
trail does not.

## Consequences

**Six of the eight catalogued platforms are policy-only.** This actor
can rule on them and cannot buy them, and the receipt says
`:unsupported` rather than looking like a successful placement. That is
not a gap to be closed for symmetry: each adapter is a real integration
with a real credential and a real invoice behind it.

**Modelling a Google Ads campaign as one request was wrong, not
simplified.** `campaigns:mutate` rejects an inline budget. The live path
threads the `resourceName` the API actually returns and refuses to guess
one if the budget step comes back without it.

**Running it found what the unit tests could not.** Every receipt
shipped `:placement-number nil` because `commit-record!` returns the
store, not the result, on both backends — invisible to tests that passed
the placement record in by hand. The commit node now reads it back from
`placement-history`.

**The remaining blocker is not technical.** Creating the Google Ads
account, applying for the developer token, completing OAuth and
attaching a payment method require a human with the company's identity
and payment details. Nothing in this repository can stand in for that,
and nothing in it should try.
