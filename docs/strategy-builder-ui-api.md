# Strategy Builder — UI Integration Guide

Everything the frontend needs: endpoints, payloads, status codes, enumerations
and screen flows.

**Source of truth.** Field names below are the serialized JSON the implementation
produces. Design rationale lives in [`architecture.md`](./architecture.md); this
document is the client contract.

---

## 1. The mental model (read this first)

Three objects, and the UI touches two of them.

```
  strategy_templates       the LOGIC          platform-owned. Browse, never edit.
        │
        ▼
  user_strategies          the CONFIG         ← the builder screen lives here
        │                                       one row, everything in it
        ├──────────┬──────────┐
        ▼          ▼          ▼
   subscription  subscription  subscription    ← the deployments screen
     Dhan main    Dhan hedge    Zerodha          who runs it, at what size
```

**A strategy holds its whole configuration.** Symbol, candle, future-or-options,
the CE and PE strikes, the averaging ladder, the stop — all of it on one row, all
of it in one PUT. There is no separate "parameters" call and no key/value map to
assemble.

**A deployment holds none of it.** It points at the strategy. So:

> Editing a strategy changes **every broker it is deployed on**, immediately.

Say that in the UI. It is the behaviour users want ("I changed my strategy") but
it will surprise anyone expecting a per-broker copy. There is no per-broker
override of the configuration — only of size, risk profile and paper/live.

**The dedup is invisible but worth surfacing.** Two users with the same indicator
values on the same symbol and candle share one computation, whatever else differs.
`configHash` and `sharedConfigId` are how you show it.

### What is fixed and what is per-template

| | Where it comes from | Changes per template? |
|---|---|---|
| Symbol, candle, trigger duration | fixed columns | no |
| FUTURES/OPTION, CE & PE strikes | fixed columns | no |
| Lot rule, base lot, averaging count | fixed columns | no |
| SL %, TP % | fixed columns | no |
| **Indicator values (k, d, period…)** | `indicators[].paramSchema` | **yes** |

So the builder form is built **once** by hand for everything except the indicator
block, which is generated from each indicator's `paramSchema`.

**Or generate all of it.** The template and strategy responses also carry the
same content pre-arranged into form sections — `fixedParameters[]` grouped by
`paramGroup` (Market, Instrument, Sizing, Exits), each field with its label,
type, bounds and — on a saved strategy — its current value; and
`indicatorGroups[]` grouped by indicator name. That is an **arrangement of the
same fields**, not a second source of truth: every value in it is also a flat
field, and a PUT still addresses the flat name. Hand-build the form or walk the
groups — both read the same row.

---

## 2. Transport basics

| | |
|---|---|
| Base path | `/api/v1` |
| Content type | `application/json` |
| Auth | `Authorization: Bearer <access token>` — the existing token, unchanged |
| CORS | Credentials allowed; `http://localhost:*`, `http://127.0.0.1:*`, `https://prowfin.proweltconsulting.com`, `https://trade-pnl-analysis.vercel.app` |
| OpenAPI | `/v3/api-docs`, Swagger UI at `/swagger-ui.html` |

The first time an authenticated user touches any endpoint here, their
control-plane profile is provisioned automatically. No signup call.

### Status codes

| Code | When |
|---|---|
| `200` | successful GET / PUT — **and a partially-failed deploy** |
| `201` | successful POST (body = the created resource) |
| `204` | successful DELETE (no body) |
| `400` | validation failure — see `errors[]` |
| `401` | missing / invalid / expired token |
| `404` | not found, **or owned by another user** |
| `409` | conflict: duplicate, locked system row, or blocked by references |

There is no `403`. A resource belonging to another user reports `404` by design.

### Error body

```json
{ "error": "ceStrikeOffset must be 1..15 for OTM, got 16",
  "errors": [
    "ceStrikeOffset must be 1..15 for OTM, got 16",
    "averagingCount must be 0..10, got 25"
  ] }
```

`error` is always present and displayable. `errors` appears **only on 400** and
holds every problem found — render the whole list. Field names in the messages
match the request field names, so matching on them to place an inline error works.

---

## 3. Enumerations

Hardcode these. They are Java enums and database CHECK constraints.

```ts
type Derivative    = 'FUTURES' | 'OPTION';
type Moneyness     = 'ATM' | 'ITM' | 'OTM';
type LotRule       = 'FIXED' | 'DOUBLE' | 'CUMULATIVE';
type TradeMode     = 'paper' | 'live';
type ExecutionMode = 'FIXED_QTY' | 'CAPITAL_PERCENT' | 'RISK_PERCENT';
type InstanceStatus= 'active' | 'retired';
type InstrumentType= 'spot' | 'future' | 'option' | 'index';
type ParamType     = 'int' | 'decimal' | 'bool' | 'enum' | 'text';        // indicator paramSchema
type FixedType     = ParamType | 'timeframe' | 'symbol';                  // fixedParameters[].dataType

const STRIKE_OFFSETS = [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15];   // ITM and OTM
```

Enums are parsed case-insensitively, so `"otm"` is accepted — but send the
canonical form.

**Durations** are validated against `^[0-9]{1,4}[smhdw]$` after lowercasing. Offer
a select:

```
30s · 1m · 3m · 5m · 15m · 30m · 1h · 2h · 4h · 1d · 1w
```

### The strike selector

Moneyness and depth are one control, not two:

```
  ( ) ATM                       → { moneyness: 'ATM' }              (no depth)
  ( ) ITM  [ 1 ▾ ]              → { moneyness: 'ITM', offset: 1..15 }
  (•) OTM  [ 3 ▾ ]              → { moneyness: 'OTM', offset: 1..15 }
```

Disable the depth dropdown when ATM is selected — the API rejects a non-zero
depth on ATM rather than ignoring it, because "OTM0" is not another way of
spelling ATM.

---

## 4. Screen flows

### 4.1 Browse templates

```
GET /api/v1/strategy-templates?active=true
```

Each item carries its `indicators[]` with `paramSchema`, so the builder form can
be rendered with no follow-up call.

Badges: `system: true` → "Built-in", disable edit/delete. `active: false` →
"Inactive", cannot be built on. `unknownIndicators` non-empty → the rule tree
references a missing or disabled indicator; **block the build**, it will fail.

### 4.2 Build a strategy (the main flow)

```
1. GET  /api/v1/strategy-templates/{id}   → indicators + their paramSchema
2. GET  /api/v1/symbols?activeOnly=true   → underlying picker
3. POST /api/v1/my-strategies             → 201
```

Steps 1 and 2 run in parallel. **A strategy needs no trading account** — that is
the deploy step, which keeps the builder unblocked for a user who has not set a
broker up yet.

A body naming only the template is valid and saves a strategy on platform
defaults. Add the market and it becomes deployable.

### 4.3 Deploy to brokers

```
1. GET  /api/v1/my-brokers                        → the setups
2. GET  /api/v1/trading-accounts                  → the accounts under them
3. POST /api/v1/my-strategies/{id}/deploy         → 200 with per-target results
```

If `GET /trading-accounts` returns `[]`, route to broker setup:
`POST /api/v1/my-brokers/setup` does the whole wizard in one transaction.

### 4.4 Retune

```
PUT /api/v1/my-strategies/{id}
{ "indicators": [ {"indicatorName":"EMA Averaging", "params": {"k": 50}} ] }
```

Only what changed. Warn the user that every deployment follows.

Watch `configHash` in the response: when it changes, the strategy moved to a
different shared computation. Nothing broke — do not show a scary message.

---

## 5. Endpoint reference

### 5.1 `GET /api/v1/strategy-templates`

```json
[ {
  "id": "9f1c…", "name": "EMA Averaging", "version": 1,
  "description": "EMA of the highs against a shorter signal leg…",
  "system": true, "active": true,
  "ruleTree": { "entry": { "ind": "EMA Averaging", "params": {"k":"$k","d":"$d"} } },
  "indicators": [ {
      "id": "b2e4…", "name": "EMA Averaging", "active": true,
      "paramSchema": {
        "k": {"type":"int","min":1,"max":300,"default":21},
        "d": {"type":"int","min":1,"max":300,"default":9,"lt":"k"}
      } } ],
  "indicatorGroups": [ {
      "indicatorName": "EMA Averaging", "usageCount": 1, "count": 1,
      "indicators": [ { "id": "b2e4…", "name": "EMA Averaging", "active": true,
                        "paramSchema": { "k": {…}, "d": {…} } } ] } ],
  "fixedParameters": [ {
      "paramGroup": "Exits", "count": 2,
      "parameters": [ {
          "id": "…", "name": "slPct", "label": "SL %",
          "description": "Percent move against the position that closes it…",
          "dataType": "decimal", "scope": "execution",
          "defaultValue": "2.5", "validation": {"min":0,"max":100},
          "displayOrder": 1, "required": false, "active": true } ] } ],
  "unknownIndicators": [],
  "instanceCount": 4, "strategyCount": 12,
  "createdAt": "2026-08-23T15:58:49.123+05:30", "updatedAt": "…"
} ]
```

`indicatorGroups[]` is `indicators[]` arranged one group per indicator name.
`usageCount` is how many nodes of the rule tree name that indicator — **that is
how many tuning rows a strategy built from this template will carry**, so a
template naming EMA twice gives `usageCount: 2` and the strategy gets two.

`fixedParameters[]` is the platform's fixed knobs, grouped by `paramGroup` and
ordered the way a form lays them out. Descriptors only here — a template holds no
values. They are the same sections, in the same order, that
`GET /api/v1/my-strategies/{id}` returns with values filled in, so one form draws
a blank template and a saved strategy alike.

#### Rendering an indicator input from `paramSchema`

| Key | Meaning | Use it for |
|---|---|---|
| `type` | `int` \| `decimal` \| `bool` \| `enum` \| `text` | which control |
| `default` | **always present** | the initial value |
| `min` / `max` | numeric bounds | input bounds, client-side check |
| `options` | non-empty list (required for `enum`) | a select |
| `gt` / `lt` | names another key of the same indicator | a cross-field check: `d` must stay under `k` |

Implement `gt`/`lt` client-side too — the server enforces it, but catching it in
the form is a better experience than a round trip.

### 5.2 `POST /api/v1/my-strategies` · `PUT /api/v1/my-strategies/{id}`

One shape for both. **Present is applied, absent is left alone** — which on create
means the column default.

```json
{
  "strategyName": "EMA Averaging",
  "name": "NIFTY 21/9 both sides",
  "description": "…",

  "symbol": "NIFTY", "exchangeCode": "NSE",
  "candleDuration": "5m",
  "triggerDuration": "5m",

  "derivative": "OPTION",
  "ceEnabled": true, "ceMoneyness": "OTM", "ceStrikeOffset": 1,
  "peEnabled": true, "peMoneyness": "ATM",

  "lotRule": "DOUBLE", "baseLot": 65, "averagingCount": 2,
  "slPct": 1.5, "tpPct": 3.0,

  "indicators": [
    { "indicatorName": "EMA Averaging", "params": { "k": 21, "d": 9 } }
  ]
}
```

| Field | Default on create | Notes |
|---|---|---|
| `strategyId` / `strategyName` | — | required on create, ignored on update |
| `name` | the template's name | UNIQUE per user → 409 |
| `symbolId` / `symbol` + `exchangeCode` | none | needed before deploying |
| `candleDuration` | none | needed before deploying; part of the dedup identity |
| `triggerDuration` | none | never hashed |
| `derivative` | `OPTION` | |
| `ceEnabled` / `peEnabled` | `false` | **setting `ceMoneyness` turns the call side on** |
| `ceStrikeOffset` / `peStrikeOffset` | `0` | must be 0 for ATM, 1–15 for ITM/OTM |
| `lotRule` | `FIXED` | non-FIXED needs `averagingCount ≥ 1` |
| `baseLot` | `1` | ≥ 1 |
| `averagingCount` | `0` | 0–10 |
| `slPct` / `tpPct` | `null` | 0 < x ≤ 100 |
| `indicators[].params` | schema defaults | **merged** over what is stored |
| `active` | `true` | archive without deleting |

An indicator entry is addressed by `indicatorName`, `indicatorId`, or
`userStrategyIndicatorId` — plus `slot` only when a template uses one indicator
twice.

### 5.3 `GET /api/v1/my-strategies/{id}` — the editor shape

```json
{
  "id": "…", "userId": "…",
  "strategyId": "…", "strategyName": "EMA Averaging", "strategyDescription": "…",
  "strategySystem": true,
  "name": "NIFTY 21/9 both sides", "description": null,

  "symbolId": "…", "symbol": "NIFTY",
  "instrumentType": "index", "exchangeCode": "NSE",
  "candleDuration": "5m", "triggerDuration": "5m",

  "derivative": "OPTION",
  "ceEnabled": true, "ceMoneyness": "OTM", "ceStrikeOffset": 1,
  "peEnabled": true, "peMoneyness": "ATM", "peStrikeOffset": 0,
  "legs": [ { "side":"CE", "moneyness":"OTM", "strikeOffset":1, "label":"CE OTM1" },
            { "side":"PE", "moneyness":"ATM", "strikeOffset":0, "label":"PE ATM" } ],

  "lotRule": "DOUBLE", "baseLot": 65, "averagingCount": 2,
  "slPct": 1.50, "tpPct": 3.00,

  "indicators": [ {
      "id": "…", "indicatorId": "…", "indicatorName": "EMA Averaging",
      "slot": null, "enabled": true, "displayOrder": 0,
      "params": { "d": 9, "k": 21 },
      "paramSchema": { "k": {"type":"int","min":1,"max":300,"default":21},
                       "d": {"type":"int","min":1,"max":300,"default":9,"lt":"k"} } } ],

  "indicatorGroups": [ {
      "indicatorId": "…", "indicatorName": "EMA Averaging",
      "count": 1, "enabled": true,
      "schema": { "k": {…}, "d": {…} },
      "indicators": [ { …the same row as above… } ] } ],

  "fixedParameters": [ {
      "paramGroup": "Exits", "count": 2,
      "parameters": [
        { "id": "…", "name": "slPct", "label": "SL %",
          "dataType": "decimal", "scope": "execution",
          "value": 1.50,
          "defaultValue": "2.5", "validation": {"min":0,"max":100},
          "displayOrder": 1, "required": false },
        { "name": "tpPct", "label": "TP %", "value": 3.00, … } ] },
    { "paramGroup": "Instrument", "count": 7, "parameters": [ … ] },
    { "paramGroup": "Market",     "count": 2, "parameters": [ … ] },
    { "paramGroup": "Sizing",     "count": 3, "parameters": [ … ] } ],

  "sharedConfigId": "…", "configHash": "a3f1…",
  "deployable": true, "deploymentCount": 3,
  "active": true, "createdAt": "…", "updatedAt": "…"
}
```

Two views of the same choice, on purpose:

- the `ce*` / `pe*` fields are the **editable** form — same names as the request,
  so a round trip is edit-one-field-and-PUT-it-back;
- `legs[]` is the same thing **derived for display** — iterate it for a summary
  line without reimplementing "CE OTM1" from three fields. It is read-only, and
  for a `FUTURES` strategy it is a single `{"side":"FUTURES","label":"FUTURES"}`.

`deployable` is false until symbol and candle are set — use it to gate the deploy
button. `deploymentCount` tells the user how many brokers an edit will move.

**Three arrangements, one row.** `indicatorGroups[]` and `fixedParameters[]` are
`indicators[]` and the flat `ce*`/`pe*`/`lotRule`/`slPct`… fields, rearranged for
a form:

- `indicatorGroups[]` — one group per indicator name. It only differs from the
  flat list when a template names the same indicator twice, which is exactly when
  the flat list reads as repeating look-alike rows; `slot` tells the usages apart,
  `enabled` on the group is true while **any** usage is. `schema` is hoisted to
  the group because it belongs to the indicator, and is still on every row.
- `fixedParameters[]` — one group per `paramGroup`, each field carrying its
  descriptor (from `/api/v1/fixed-parameters`) **and** its `value` on this
  strategy, already typed. The `Deployment` group is absent: those knobs are
  columns on a subscription, not on a strategy, and they come back on
  `/api/v1/my-subscriptions`.

**The `symbol` knob renders itself.** It sits first in the `Market` group with
`dataType: "symbol"`, and its `validation.options` is the live list of active
tickers — filled in from the `symbols` table on every read, never stored, so a
newly listed instrument shows up without a deploy. Render it as a select like any
`enum`; `optionsSource` (`/api/v1/symbols`) is where to refresh it from.

```json
{ "name": "symbol", "label": "Underlying", "dataType": "symbol",
  "scope": "signal", "required": true, "value": "NIFTY",
  "validation": { "options": ["BANKNIFTY", "NIFTY"],
                  "optionsSource": "/api/v1/symbols" },
  "displayOrder": 0 }
```

Tickers, not ids — because `symbol` is what a PUT carries. **A ticker is unique
per exchange, not globally**, so one listed on two venues appears in the list
once; send `exchangeCode` with it, or send `symbolId` instead. `value` is `null`
until a market is chosen, which is the state `deployable: false` describes.

**Write with the flat names.** These are read shapes. `PUT` takes `slPct`, not a
group entry — see 5.2. And if the descriptor catalog is empty or a knob has been
deactivated, `fixedParameters[]` shrinks or empties while the flat fields stay
exactly as they were.

### 5.4 `GET /api/v1/my-strategies/{id}/runtime` — the bot shape

Same rows, resolved for something that has to place an order: `legs` resolved,
indicator values coerced to their declared types, and `signalParams` exactly as
hashed. Also carries `ruleTree`, `derivative`, `lotRule`, `baseLot`,
`averagingCount`, `slPct`, `tpPct`, `sharedConfigId`, `configHash`.

### 5.5 `POST /api/v1/my-strategies/{id}/deploy`

```json
{ "tradeMode": "paper", "multiplier": 1,
  "targets": [ { "tradingAccountId": "acc-1" },
               { "tradingAccountId": "acc-2", "multiplier": 2, "tradeMode": "live" },
               { "userBrokerId": "brk-9" } ] }
```

A target names **one account** or **a whole broker setup** (`userBrokerId` fans
out to every account under it). Request-level fields are defaults; a target that
sets one wins. Each target may override `riskProfileId`, `multiplier`,
`capitalAllocated`, `executionMode`, `tradeMode`.

The configuration is not in this body — it comes from the strategy, which is what
makes every broker run identical maths off one shared computation.

**Response — always 200 when the request itself was well-formed:**

```json
{ "userStrategyId": "…", "userStrategyName": "NIFTY 21/9 both sides",
  "symbolId": "…", "symbol": "NIFTY", "candleDuration": "5m",
  "sharedConfigId": "…", "configHash": "a3f1…",
  "requested": 3, "deployed": 2, "failed": 1,
  "results": [
    { "tradingAccountId":"acc-1", "tradingAccountName":"main",
      "userBrokerId":"brk-1", "brokerLabel":"My Dhan",
      "status":"deployed", "subscription": { … }, "error": null },
    { "tradingAccountId":"acc-2", "tradingAccountName":"hedge",
      "userBrokerId":"brk-1", "brokerLabel":"My Dhan",
      "status":"failed", "subscription": null,
      "error":"Strategy NIFTY 21/9 both sides is already deployed on account hedge (subscriptionId=…)." }
  ] }
```

**Render `results`, not the status code.** A 200 with `failed: 1` is the normal
case for a re-deploy. Group by `brokerLabel`; show each failure's `error` inline
on its row.

400s that reject the whole call, before anything is written: empty `targets`, a
target with neither id, an account named twice, an archived strategy, a strategy
with no market yet.

### 5.6 Deployments — `/api/v1/my-subscriptions`

`POST` deploys to one account: `{ "userStrategyId", "tradingAccountId",
"riskProfileId?", "multiplier?", "capitalAllocated?", "executionMode?",
"tradeMode?" }`.

`PUT /{id}` changes **only how this account runs it** — `multiplier`,
`capitalAllocated`, `executionMode`, `tradeMode`, `riskProfileId`, `active`.
Retuning the strategy is `PUT /api/v1/my-strategies/{id}`; there is deliberately
no way to fork one broker's configuration.

`GET` returns the deployment with the configuration read through the strategy —
`derivative`, `legs`, `lotRule`, `baseLot`, `averagingCount`, `slPct`, `tpPct`,
plus `signalParams`, `indicators[]` (resolved fingerprints), `configHash`, and
`userBrokerId` / `brokerLabel` for grouping.

`DELETE /{id}` withdraws from that broker. `DELETE /api/v1/my-strategies/{id}` is
**409 while any deployment exists** — withdraw them first, or archive with
`{"active": false}`.

> **Precision.** `multiplier`, `capitalAllocated` and the risk limits are
> `numeric(20,8)` and arrive as JSON numbers. Don't do arithmetic on them for
> exact display.

---

## 6. Form validation to mirror client-side

The server enforces all of these and returns every failure at once. Mirroring
them saves a round trip.

| Rule | Message shape |
|---|---|
| OPTION with neither side on | `derivative is OPTION but neither side is on…` |
| FUTURES with a side still on | `derivative is FUTURES, so no CE or PE side applies…` |
| Side on with no moneyness | `ceMoneyness is required while ceEnabled is true…` |
| Depth on ATM | `ceStrikeOffset must be 0 for ATM…` |
| Depth out of range | `ceStrikeOffset must be 1..15 for OTM, got 16` |
| `baseLot < 1` | `baseLot must be at least 1…` |
| `averagingCount` outside 0–10 | `averagingCount must be 0..10…` |
| Non-FIXED ladder with 0 adds | `lotRule DOUBLE has no effect while averagingCount is 0…` |
| `slPct` / `tpPct` outside (0, 100] | `slPct must be greater than 0…` |
| Indicator value out of range | `Indicator 'EMA Averaging' parameter 'k' must be <= 300, got 400` |
| Indicator cross-field | `…parameter 'd' must be less than 'k' (21 vs 9)` |
| Unknown indicator key | `Indicator 'EMA Averaging' has no parameter 'period' - it declares [k, d]` |

---

## 7. Worked example — the spreadsheet, end to end

The sheet describes: EMA High (K) 21, EMA (D) 9, 5 MIN, NIFTY, OPTION, OTM1,
Double LOT from a base of 65, averaging 2. Both a call and a put.

```http
POST /api/v1/my-strategies
{ "strategyName": "EMA Averaging",
  "name": "NIFTY 21/9",
  "symbol": "NIFTY", "exchangeCode": "NSE",
  "candleDuration": "5m", "triggerDuration": "5m",
  "derivative": "OPTION",
  "ceEnabled": true, "ceMoneyness": "OTM", "ceStrikeOffset": 1,
  "peEnabled": true, "peMoneyness": "OTM", "peStrikeOffset": 1,
  "lotRule": "DOUBLE", "baseLot": 65, "averagingCount": 2,
  "indicators": [ { "indicatorName": "EMA Averaging", "params": { "k": 21, "d": 9 } } ] }
→ 201, deployable: true

POST /api/v1/my-strategies/{id}/deploy
{ "tradeMode": "paper",
  "targets": [ {"userBrokerId": "<my Dhan>"}, {"tradingAccountId": "<Zerodha main>"} ] }
→ 200, deployed: 3, failed: 0
```

The sheet's second block — EMA High 50, EMA 21 — is the **same template** with
different values, so it is a second strategy, not a second template:

```http
POST /api/v1/my-strategies
{ "strategyName": "EMA Averaging", "name": "NIFTY 50/21", … ,
  "indicators": [ { "indicatorName": "EMA Averaging", "params": { "k": 50, "d": 21 } } ] }
```

Both are valid because the `EMA Averaging` schema declares `d` with `lt: k`.
`{"k": 9, "d": 21}` would be rejected — that is the `EMA Crossover` template's
shape, which declares the constraint the other way round.
