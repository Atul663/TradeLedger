# Strategy Builder — UI Integration Guide

Everything the frontend needs to implement the strategy builder module: endpoints,
request payloads, response payloads, status codes, enumerations, dynamic form
rules and screen flows.

**Source of truth.** Field names below are the actual serialized JSON produced by
the implementation, verified against the running Jackson configuration — not a
sketch. Backend design rationale lives in [`strategy-module-api.md`](./strategy-module-api.md);
this document is the client contract.

> **Note on the reference file.** No previous frontend markdown was found at the
> path supplied (`Downloads/trading-platform-database-design.md` is the database
> design document). This guide was written from scratch against the implemented
> API. If a frontend doc turns up, point me at it and I'll merge instead.

---

## 1. The mental model (read this first)

The builder has **two distinct jobs**, and conflating them is the most common way
to get this UI wrong.

**A. Authoring a strategy** — a *template*: a rule tree over indicators plus the
list of knobs users may tune. Platform-level, not owned by anyone. Seeded
strategies are locked (`system: true`).

**B. Subscribing to a strategy** — a *user's configuration*: pick a strategy, a
symbol, a timeframe, fill in the knobs, choose a trading account. This is what a
user owns and what they see on a dashboard.

Between A and B sits a shared object the UI should **display but never create**:

```
Strategy (template)         →  "EMA Crossover", rule tree, knob definitions
   └─ StrategyInstance      →  EMA Crossover + NIFTY + 5m + {fast:9, slow:21}
        └─ Subscription     →  ...on MY account, with MY stop loss, paper mode
```

A `StrategyInstance` is **immutable and shared**. Two users who pick identical
signal parameters land on the *same* instance row and the same `configHash` —
that is deliberate, it is how the platform computes EMA(9) once instead of twice.
Instances appear automatically when someone subscribes. There is no create
endpoint for them.

The consequence that shows up in the UI: **editing a subscription's signal
parameters does not edit anything shared.** It silently repoints the subscription
at a different instance, bumps `version`, and changes `configHash`. The
subscription `id` never changes. Show the user their subscription, not the
instance.

### Which parameters are shared vs private

Every knob carries a `scope`:

| `scope` | Lives on | Hashed | Shown as |
|---|---|---|---|
| `signal` | the shared instance | yes | "Strategy settings" — changing these changes which instance you share |
| `execution` | your subscription | **no** | "Your execution settings" — private, e.g. stop loss / take profit |

Two users can run identical 9×21 math with different stop losses and still share
one computation. Group the form by `scope` and label it; users otherwise
misunderstand why their SL change didn't create a "new" strategy.

---

## 2. Transport basics

| | |
|---|---|
| Base path | `/api/v1` |
| Content type | `application/json` (request and response) |
| Auth | `Authorization: Bearer <access token>` — the existing token, unchanged |
| CORS | Credentials allowed; origins include `http://localhost:*`, `http://127.0.0.1:*`, `https://prowfin.proweltconsulting.com`, `https://trade-pnl-analysis.vercel.app` |
| OpenAPI | `/v3/api-docs`, Swagger UI at `/swagger-ui.html` |

Authentication is untouched by this module — keep using the current login,
refresh and logout flow. The first time an authenticated user touches any
endpoint here, their control-plane profile is provisioned automatically. No
signup call, no extra step.

### Serialization conventions

| Type | JSON | Example |
|---|---|---|
| id | string (UUID v4) | `"9f1c2b4e-..."` — except strategy parameter ids, which are **integers** |
| timestamp | ISO-8601 with offset | `"2026-08-13T15:58:49.123+05:30"` |
| money / quantity | unquoted number, up to 8 decimals | `5000.00000000` |
| absent value | `null` (key is present) | `"maxTotalExposure": null` |
| JSON columns | nested object, never a string | `"ruleTree": { ... }` |

> **Precision warning.** `quantity`, `multiplier`, `lotSize`, `capitalAllocated`
> and every risk limit are `numeric(20,8)` server-side and arrive as JSON
> numbers. `JSON.parse` turns them into IEEE-754 doubles. Don't do arithmetic on
> them for display of exact values — render from the raw string, or parse with a
> decimal library, if you ever show sums.

### Status codes

| Code | When |
|---|---|
| `200` | successful GET / PUT |
| `201` | successful POST (body = the created resource) |
| `204` | successful DELETE (**no body**) |
| `400` | validation failure — see `errors[]` |
| `401` | missing / invalid / expired token |
| `404` | not found, **or owned by another user** |
| `409` | conflict: duplicate, locked system row, or blocked by references |
| `500` | unexpected |

There is no `403`. A resource belonging to another user reports `404` by design —
the ownership filter is part of the query, so the API never reveals that someone
else's row exists.

### Error body

```json
{ "error": "Parameter 'slow' must be greater than 'fast' (9 vs 21)",
  "errors": [
    "Parameter 'slow' must be greater than 'fast' (9 vs 21)",
    "Parameter 'fast' must be >= 2, got 1"
  ] }
```

`error` is always present and is a displayable sentence. `errors` is present
**only for validation failures (400)** and holds every problem found — render the
whole list, not just the first. `401`, `404`, `409` and `500` return `error` alone.

Suggested handling: `400` → inline field errors (match on the quoted
`'parameterKey'`); `409` → a modal explaining the conflict, since the message
usually tells the user exactly what to do instead; `401` → refresh token, retry
once, then log out.

---

## 3. Enumerations

Hardcode these in the UI; they are CHECK constraints in the database.

```ts
type DataType      = 'int' | 'decimal' | 'bool' | 'enum' | 'timeframe' | 'text';
type ParamScope    = 'signal' | 'execution';
type TradeMode     = 'paper' | 'live';
type ExecutionMode = 'FIXED_QTY' | 'CAPITAL_PERCENT' | 'RISK_PERCENT';
type InstanceStatus= 'active' | 'retired';
type InstrumentType= 'spot' | 'future' | 'option' | 'index';
type OptionType    = 'CALL' | 'PUT';
type ExchangeStatus= 'active' | 'disabled';
```

**Timeframe** is validated against `^[0-9]{1,4}[smhdw]$` after lowercasing.
Offer a select rather than a free-text field:

```
30s · 1m · 3m · 5m · 15m · 30m · 1h · 2h · 4h · 1d · 1w
```

---

## 4. Screen flows

### 4.1 Browse strategies

```
GET /api/v1/strategies?active=true
```

One call returns everything needed to render cards *and* the configuration form —
each item already includes `params[]`, `ruleTree` and `indicators[]`, so there is
no follow-up request per card.

Badges to render per item:
- `system: true` → "Built-in" chip, and **disable edit/delete** (the API returns 409).
- `active: false` → "Inactive"; cannot be subscribed to.
- `unknownIndicators` non-empty → warning: the rule tree references an indicator
  that doesn't exist or is disabled. Subscribing will fail.
- `instanceCount > 0` → delete is blocked; offer "Deactivate" instead.

### 4.2 Subscribe (the main builder flow)

```
1. GET /api/v1/strategies/{id}          → knob definitions + rule tree
2. GET /api/v1/symbols?activeOnly=true  → signal symbol picker
3. GET /api/v1/trading-accounts         → the user's accounts (required)
4. GET /api/v1/risk-profiles            → optional
5. POST /api/v1/subscriptions           → 201
```

Steps 2–4 can run in parallel with 1.

**A subscription requires a trading account.** If `GET /trading-accounts` returns
`[]`, route the user to account creation first — don't let them fill the whole
form and fail at submit.

Render the form from `params[]` (see §6). Submit *all* knob values flat in
`params`, both scopes together; the server splits them by `scope`.

After `201`, surface `indicators[]` and `configHash` — "this configuration needs
EMA(period=9), EMA(period=21)" is the feature that makes dedup legible to users.

### 4.3 Edit a subscription

```
PUT /api/v1/subscriptions/{id}
{ "params": { "fast": 13 } }
```

`params` is **merged** over the current effective configuration, so send only what
changed. Everything else in the body is a partial update too.

Watch for these in the response: `configHash` and `strategyInstanceId` change and
`version` increments when a *signal* parameter changed; they stay put when only
*execution* parameters changed. Nothing changes for the user either way — don't
show a scary "your strategy was recreated" message.

Pause / resume without losing configuration:

```
PUT /api/v1/subscriptions/{id}   { "active": false }
```

### 4.4 Author a new strategy

```
1. GET /api/v1/indicators?active=true   → available primitives + paramSchema
2. build the rule tree in the canvas     (grammar in §7)
3. POST /api/v1/strategies               → 201, is always system:false
```

Send `ruleTree` and `params[]` together on create — the server cross-validates
them and rejects a tree that binds `$fast` when no `fast` parameter exists.

Validation errors here are structural and readable; render them as a list next to
the canvas:

```
"ruleTree references unknown indicator 'EMAA'"
"Indicator 'EMA' has no parameter 'length' - its schema declares [period]"
"ruleTree binds $fast but the strategy defines no parameter 'fast'"
```

---

## 5. Endpoint reference

### 5.1 Strategies — `/api/v1/strategies`

#### `GET /api/v1/strategies`

Query: `active` (boolean, optional), `search` (string, optional — case-insensitive
name fragment). Returns `StrategyDetail[]`.

#### `GET /api/v1/strategies/{id}` → `StrategyDetail` · 404

#### `GET /api/v1/strategies/by-name/{name}` → `StrategyDetail` · 404

Names contain spaces — URL-encode: `/by-name/EMA%20Crossover`.

**`StrategyDetail`**

```json
{
  "id": "00000000-0000-0000-0000-00000000e0a1",
  "name": "EMA Crossover",
  "version": 1,
  "description": "Long when fast EMA crosses above slow EMA; exit on reverse cross or SL/TP.",
  "system": true,
  "active": true,
  "ruleTree": {
    "entry": { "cross_above": [ { "ind": "EMA", "params": { "period": "$fast" } },
                                { "ind": "EMA", "params": { "period": "$slow" } } ] },
    "exit":  { "cross_below": [ { "ind": "EMA", "params": { "period": "$fast" } },
                                { "ind": "EMA", "params": { "period": "$slow" } } ] }
  },
  "indicators": ["EMA"],
  "unknownIndicators": [],
  "params": [
    { "id": 1, "parameterKey": "fast", "dataType": "int", "scope": "signal",
      "defaultValue": "9", "validation": { "min": 2, "max": 200 },
      "displayLabel": "Fast EMA period", "displayOrder": 1, "required": true },
    { "id": 2, "parameterKey": "slow", "dataType": "int", "scope": "signal",
      "defaultValue": "21", "validation": { "min": 3, "max": 300, "gt": "fast" },
      "displayLabel": "Slow EMA period", "displayOrder": 2, "required": true },
    { "id": 3, "parameterKey": "sl_pct", "dataType": "decimal", "scope": "execution",
      "defaultValue": "1.5", "validation": { "min": 0.1, "max": 20 },
      "displayLabel": "Stop loss %", "displayOrder": 3, "required": true },
    { "id": 4, "parameterKey": "tp_pct", "dataType": "decimal", "scope": "execution",
      "defaultValue": "3.0", "validation": { "min": 0.1, "max": 50 },
      "displayLabel": "Take profit %", "displayOrder": 4, "required": true }
  ],
  "instanceCount": 3,
  "createdAt": "2026-08-13T15:58:49.123+05:30",
  "updatedAt": "2026-08-13T15:58:49.123+05:30"
}
```

| Field | Notes |
|---|---|
| `system` | `true` = built-in. Edit/delete return **409**. Disable the buttons. |
| `indicators` | indicator names the rule tree resolves successfully |
| `unknownIndicators` | referenced but missing or inactive — show a warning |
| `params[].id` | **integer**, not a UUID |
| `params[].defaultValue` | **always a string**, even for `int` / `decimal` — coerce before use |
| `params[].validation` | always an object; `{}` when no rules |
| `instanceCount` | > 0 means delete is blocked |

#### `POST /api/v1/strategies` → **201** · 400 · 409

```json
{
  "name": "RSI Reversal",
  "description": "Long when RSI leaves oversold.",
  "version": 1,
  "active": true,
  "ruleTree": { "entry": { "cross_above": [ { "ind": "RSI", "params": { "period": "$rsiLen" } },
                                            { "const": "$oversold" } ] } },
  "params": [
    { "parameterKey": "rsiLen", "dataType": "int", "scope": "signal",
      "defaultValue": "14", "validation": { "min": 2, "max": 100 },
      "displayLabel": "RSI length", "displayOrder": 1, "required": true },
    { "parameterKey": "oversold", "dataType": "int", "scope": "signal",
      "defaultValue": "30", "validation": { "min": 1, "max": 50 },
      "displayLabel": "Oversold level", "displayOrder": 2, "required": true },
    { "parameterKey": "sl_pct", "dataType": "decimal", "scope": "execution",
      "defaultValue": "1.5", "validation": { "min": 0.1, "max": 20 },
      "displayLabel": "Stop loss %", "displayOrder": 3, "required": true }
  ]
}
```

Required: `name` (≤100, unique), `ruleTree` (non-empty object).
Optional: `description`, `version` (default 1, ≥1), `active` (default `true`),
`params` (may be added later via the params endpoints).

`system` is **not settable** — API-created strategies are always `system: false`.

`409` when the name is taken.

#### `PUT /api/v1/strategies/{id}` → 200 · 400 · 404 · 409

**Partial**: omitted fields are left unchanged — except `params`, which
**replaces the entire knob set** when present. Omit `params` to leave knobs alone;
send `[]` to delete them all.

Renaming to a taken name → 409. Editing a `system: true` strategy → 409.
Deactivate with `{ "active": false }`.

#### `DELETE /api/v1/strategies/{id}` → **204** · 404 · 409

Hard delete, cascading to the knob definitions. `409` when `instanceCount > 0`
(the message names the count and suggests deactivating) or when `system: true`.

---

### 5.2 Strategy parameters — `/api/v1/strategies/{id}/params`

Indicator parameters, at the level users actually set them. Use these for
incremental editing; use `PUT /strategies/{id}` with `params[]` for bulk replace.

| Method | Path | Status |
|---|---|---|
| GET | `/api/v1/strategies/{id}/params` | 200 → `StrategyParamDef[]` |
| POST | `/api/v1/strategies/{id}/params` | **201** · 400 · 404 · 409 |
| PUT | `/api/v1/strategies/{id}/params/{paramId}` | 200 · 400 · 404 · 409 |
| DELETE | `/api/v1/strategies/{id}/params/{paramId}` | **204** · 404 · 409 |

Request body (POST and PUT):

```json
{ "parameterKey": "fast", "dataType": "int", "scope": "signal",
  "defaultValue": "9", "validation": { "min": 2, "max": 200 },
  "displayLabel": "Fast EMA period", "displayOrder": 1, "required": true }
```

Required on POST: `parameterKey` (≤100), `dataType`, `scope`.
On PUT, `parameterKey` / `dataType` / `scope` may be omitted to keep their current
values. `displayOrder` defaults to `0`, `required` to `true`.

`enum` requires a non-empty `validation.options`. A `gt` / `lt` rule must name an
existing sibling parameter key.

**409 cases:** duplicate `parameterKey` on the strategy; strategy is `system`;
DELETE of a parameter the rule tree still binds (`$fast`) — edit the tree first.

---

### 5.3 Indicators — `/api/v1/indicators`

| Method | Path | Status |
|---|---|---|
| GET | `/api/v1/indicators?active=true` | 200 → `IndicatorDef[]` |
| GET | `/api/v1/indicators/{id}` | 200 · 404 |
| GET | `/api/v1/indicators/by-name/{name}` | 200 · 404 (name is case-insensitive) |
| POST | `/api/v1/indicators` | **201** · 400 · 409 |
| PUT | `/api/v1/indicators/{id}` | 200 · 400 · 404 · 409 |
| DELETE | `/api/v1/indicators/{id}` | **204** · 404 · 409 |

**`IndicatorDef`**

```json
{
  "id": "3f2a...",
  "name": "EMA",
  "paramSchema": { "period": { "type": "int", "min": 2, "max": 300 } },
  "active": true,
  "usedByStrategies": ["EMA Crossover"],
  "createdAt": "2026-08-13T15:58:49.123+05:30"
}
```

`usedByStrategies` drives the UI: non-empty means delete and rename are blocked.
There is no `updatedAt` on this resource.

**Create / update body**

```json
{ "name": "RSI",
  "paramSchema": { "period": { "type": "int", "min": 2, "max": 100 } },
  "active": true }
```

Names are normalized to **UPPERCASE** and must be unique (≤50 chars) — rule trees
match indicators by name, so `"ema"` and `"EMA"` are the same indicator.

`paramSchema` is required and must declare at least one parameter. Each entry:

```
{ "<paramName>": { "type": <DataType>,      // required
                   "min": <number>,          // optional
                   "max": <number>,          // optional
                   "options": [ ... ] } }    // optional, non-empty
```

> **This is where indicator parameters live.** There is no indicator-parameter
> table and no per-parameter endpoint — by design. `paramSchema` declares the
> *shape* of an indicator's inputs; the *values* come from the strategy's
> parameters via `$` bindings in the rule tree. Don't build a CRUD screen for
> indicator parameters; build a schema editor.

PUT is partial. **409 cases:** rename while `usedByStrategies` is non-empty;
delete while referenced; dropping a `paramSchema` key that a live rule tree still
passes. Deactivating (`active: false`) is always allowed and is the escape hatch.

---

### 5.4 Subscriptions — `/api/v1/subscriptions`

All calls are scoped to the authenticated user.

| Method | Path | Status |
|---|---|---|
| GET | `/api/v1/subscriptions` | 200 → `Subscription[]` |
| GET | `/api/v1/subscriptions/{id}` | 200 · 404 |
| POST | `/api/v1/subscriptions` | **201** · 400 · 404 · 409 |
| PUT | `/api/v1/subscriptions/{id}` | 200 · 400 · 404 · 409 |
| DELETE | `/api/v1/subscriptions/{id}` | **204** · 404 |

#### Create

```json
{
  "strategyId": "00000000-0000-0000-0000-00000000e0a1",
  "symbolId": "7c2e...",
  "tradingAccountId": "a41b...",
  "riskProfileId": null,
  "timeframe": "5m",
  "params": { "fast": 9, "slow": 21, "sl_pct": 1.5, "tp_pct": 3.0 },
  "quantity": 50,
  "multiplier": 1,
  "lotSize": null,
  "capitalAllocated": null,
  "executionMode": "FIXED_QTY",
  "tradeMode": "paper"
}
```

| Field | Required | Default | Notes |
|---|---|---|---|
| `strategyId` | yes\* | | \*or `strategyName` (exact, unique) |
| `symbolId` | yes\* | | \*or `symbol` **+** `exchangeCode` together |
| `tradingAccountId` | **yes** | | must belong to the caller and be active |
| `timeframe` | **yes** | | see §3 |
| `params` | in practice | `{}` | both scopes flat. Omitted keys fall back to their `defaultValue`; a `required` knob with `defaultValue: null` must be supplied or you get a 400 |
| `riskProfileId` | no | `null` | |
| `quantity` | no | `1` | |
| `multiplier` | no | `1` | |
| `lotSize`, `capitalAllocated` | no | `null` | |
| `executionMode` | no | `FIXED_QTY` | |
| `tradeMode` | no | `paper` | |

The strategy must be `active`, the symbol must be `active`, the account must be
`active` — otherwise `400`.

**409:** the caller is already subscribed to this exact configuration on this
account. The message includes the existing `subscriptionId`; offer "open the
existing subscription" rather than a raw error.

#### Update

```json
{ "params": { "fast": 13 },
  "quantity": 75,
  "tradeMode": "live",
  "active": true }
```

Every field optional. `params` merges; everything else replaces. You cannot
change `strategyId`, `symbolId`, `timeframe` or `tradingAccountId` — those define
the instance identity. To move to a different symbol or account, delete and
re-create.

**409:** the repointed configuration would collide with another subscription the
user already has on the same account.

#### `Subscription`

```json
{
  "id": "b8d1...",
  "userId": "11111111-2222-3333-4444-555555555555",
  "strategyId": "00000000-0000-0000-0000-00000000e0a1",
  "strategyName": "EMA Crossover",
  "strategyInstanceId": "6ae0...",
  "configHash": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
  "symbolId": "7c2e...",
  "symbol": "NIFTY",
  "timeframe": "5m",
  "signalParams": { "fast": 9, "slow": 21 },
  "execParams": { "sl_pct": 1.5, "tp_pct": 3.0 },
  "indicators": ["EMA(period=9)", "EMA(period=21)"],
  "tradingAccountId": "a41b...",
  "tradingAccountName": "Primary",
  "riskProfileId": null,
  "riskProfileName": null,
  "quantity": 50.00000000,
  "multiplier": 1.00000000,
  "lotSize": null,
  "capitalAllocated": null,
  "executionMode": "FIXED_QTY",
  "tradeMode": "paper",
  "active": true,
  "version": 1,
  "createdAt": "2026-08-13T15:58:49.123+05:30",
  "updatedAt": "2026-08-13T15:58:49.123+05:30"
}
```

`signalParams` and `execParams` come back **split**. To repopulate the edit form,
merge them: `{ ...signalParams, ...execParams }`.

`indicators` are resolved fingerprints — `EMA(period=9)`, not `EMA`.

---

### 5.5 Strategy instances — `/api/v1/strategy-instances` (read-only)

| Method | Path | Status |
|---|---|---|
| GET | `/api/v1/strategy-instances?status=active` | 200 → `StrategyInstance[]` |
| GET | `/api/v1/strategy-instances/{id}` | 200 · 404 |
| GET | `/api/v1/strategy-instances/indicator-plan` | 200 |

```json
{
  "id": "6ae0...", "strategyId": "0000...", "strategyName": "EMA Crossover",
  "symbolId": "7c2e...", "symbol": "NIFTY", "timeframe": "5m",
  "signalParams": { "fast": 9, "slow": 21 },
  "configHash": "9f86d081...", "supersedesId": null, "status": "active",
  "indicators": ["EMA(period=9)", "EMA(period=21)"],
  "activeSubscribers": 2,
  "createdAt": "2026-08-13T15:58:49.123+05:30"
}
```

These rows carry no user identity, so they are safe to show platform-wide.
`status` flips to `retired` automatically when the last active subscriber leaves.

**Indicator plan** — an optional admin/insight widget:

```json
{ "activeSubscriptions": 3, "distinctInstances": 3,
  "distinctIndicators": 4,
  "indicators": ["EMA(period=13)", "EMA(period=21)", "EMA(period=50)", "EMA(period=9)"] }
```

Three users on 9×21, 9×50 and 13×21 cost four EMA computations, not six. Good
"platform efficiency" card.

---

### 5.6 Trading accounts — `/api/v1/trading-accounts`

Scoped to the caller. Required before any subscription can be created.

| Method | Path | Status |
|---|---|---|
| GET | `/api/v1/trading-accounts` | 200 → `TradingAccount[]` |
| GET | `/api/v1/trading-accounts/{id}` | 200 · 404 |
| POST | `/api/v1/trading-accounts` | **201** · 400 · 404 · 409 |
| PUT | `/api/v1/trading-accounts/{id}` | 200 · 400 · 404 · 409 |
| DELETE | `/api/v1/trading-accounts/{id}` | **204** · 404 · 409 |

```json
{ "exchangeCode": "NSE", "accountName": "Primary", "active": true,
  "vaultRef": "secret/brokers/dhan/acct-123" }
```

`exchangeId` or `exchangeCode` required; `accountName` required (≤100), unique per
user per exchange. PUT is partial; the exchange cannot be changed.

> **`vaultRef` is a pointer, never a secret.** The schema has no api-key or
> secret columns on purpose. Never collect an API key or passphrase in this form —
> the reference identifies a secret already stored in the vault. Setting a
> different value stamps `rotatedAt`.

```json
{ "id": "a41b...", "exchangeId": "d0c1...", "exchangeCode": "NSE",
  "exchangeName": "National Stock Exchange of India",
  "accountName": "Primary", "active": true,
  "vaultRef": "secret/brokers/dhan/acct-123", "rotatedAt": null,
  "activeSubscriptions": 2,
  "createdAt": "...", "updatedAt": "..." }
```

**409 on delete** while `activeSubscriptions > 0` — offer "deactivate" instead.

---

### 5.7 Reference data — read-only

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/exchanges?status=active` | `ExchangeResponse[]` |
| GET | `/api/v1/exchanges/{id}` | |
| GET | `/api/v1/symbols?exchangeId={uuid}&activeOnly=true` | both params optional; `activeOnly` defaults to **true** |
| GET | `/api/v1/symbols/{id}` | |
| GET | `/api/v1/risk-profiles` | |
| GET | `/api/v1/risk-profiles/{id}` | |

```json
// Exchange
{ "id": "d0c1...", "name": "National Stock Exchange of India",
  "code": "NSE", "description": "NSE cash, futures and options", "status": "active" }

// Symbol
{ "id": "7c2e...", "exchangeId": "d0c1...", "exchangeCode": "NSE",
  "symbol": "NIFTY", "baseAsset": null, "quoteAsset": null,
  "instrumentType": "index", "optionType": null, "strikePrice": null,
  "expiryAt": null, "contractSize": null, "tickSize": 0.05000000,
  "minQty": 1.00000000, "active": true }

// Risk profile
{ "id": "5b7d...", "name": "Conservative",
  "description": "Low exposure defaults for paper trading",
  "maxDailyLoss": 5000.00000000, "maxDrawdown": 10000.00000000,
  "maxPositionSize": 50000.00000000, "maxTotalExposure": null,
  "maxTradesPerDay": 10, "killSwitchEnabled": true }
```

There are **no write endpoints** for these three — they are operational master
data (symbols come from the exchange's instrument feed) and the API layer has no
role model to gate writes with. Don't build create/edit screens.

**Symbol picker guidance.** The signal symbol is what indicators run on — by
convention the underlying (`instrumentType` of `index` or `spot`), not an option
contract. Default the picker to those and de-emphasize `option` rows.

### 5.8 The caller's risk limits — `/api/v1/me/risk-limits`

```
GET  /api/v1/me/risk-limits   → 200
PUT  /api/v1/me/risk-limits   → 200 · 400
```

```json
{ "userId": "1111...", "maxDailyLoss": 5000.00000000,
  "maxOpenPositions": 5, "maxTotalExposure": null,
  "updatedAt": "2026-08-13T15:58:49.123+05:30" }
```

PUT body accepts `maxDailyLoss`, `maxOpenPositions`, `maxTotalExposure`; all
optional, none may be negative.

> **PUT here is a full replace, not a patch.** An omitted field is set to `null`
> (cleared). Always send all three fields from the loaded form state.

GET returns all-null values with a `null` `updatedAt` when the user has never set
limits — that is a valid empty state, not an error.

These are **aggregate** caps across every subscription. A `risk_profile` caps a
single subscription. Label them distinctly or users will conflate them.

---

## 6. Rendering the configuration form dynamically

Never hardcode `fast` / `slow` / `sl_pct`. A new strategy ships its own form.

Sort by `displayOrder`, then group by `scope`.

| `dataType` | Control | Constraints from `validation` |
|---|---|---|
| `int` | number input, `step=1` | `min`, `max` |
| `decimal` | number input, `step=any` | `min`, `max` |
| `bool` | checkbox / toggle | — |
| `enum` | select | `options[]` (always present) |
| `timeframe` | select from the timeframe list (§3) | `options[]` if present |
| `text` | text input | — |

Additional `validation` keys:

| Key | Meaning | Client rule |
|---|---|---|
| `min` / `max` | inclusive numeric bounds | standard |
| `options` | allowed values | populate the select |
| `gt` | **must exceed another parameter** — value is a `parameterKey` | cross-field: `value > form[validation.gt]` |
| `lt` | must be below another parameter | `value < form[validation.lt]` |

`{"min":3,"max":300,"gt":"fast"}` on `slow` means: between 3 and 300, **and**
strictly greater than whatever `fast` currently holds. Re-validate `slow` when
`fast` changes.

Initial values: `defaultValue` — remembering it is **always a string**. Coerce by
`dataType` (`parseInt`, `parseFloat`, `=== 'true'`) before putting it in state, or
numeric comparisons in the `gt` rule will compare strings.

`required: true` with a non-null `defaultValue` means the field is pre-filled and
may be left as-is; `required: true` with `defaultValue: null` means the user must
supply it.

Client-side validation is a UX nicety — **the server re-validates everything** and
returns the full `errors[]`. Always render server errors even if your client
thought the form was valid.

---

## 7. Rule tree grammar (for the builder canvas)

`ruleTree` is a free-form JSON object. Two things inside it are meaningful to this
API:

**Indicator node**

```json
{ "ind": "EMA", "params": { "period": "$fast" } }
```

- `ind` — must match an `indicator_defs.name`, active, case-sensitive (uppercase).
- `params` keys — must exist in that indicator's `paramSchema`.
- values — either a `"$binding"` referencing a strategy `parameterKey`, or a literal.

**Binding**

Any string starting with `$` anywhere in the tree is a placeholder resolved
against the strategy's **signal-scope** parameters at subscribe time.

Everything else — `entry`, `exit`, `cross_above`, `cross_below`, `const`, and any
operator you invent — is passed through untouched.

> **The API does not validate the operator vocabulary.** It validates indicator
> names, indicator parameter keys, and `$` bindings only. A tree with a typo in
> `cross_above` will save successfully and fail later in the engine. If the
> builder offers an operator palette, that palette is the frontend's contract to
> enforce.

What the server rejects at save time:

| Error | Cause |
|---|---|
| `ruleTree is required and must be a non-empty JSON object` | empty tree |
| `ruleTree references no indicators - expected at least one {"ind":"..."} node` | no indicator node |
| `ruleTree references unknown indicator 'X'` | no such `indicator_defs.name` |
| `ruleTree references inactive indicator 'X'` | exists but `active: false` |
| `Indicator 'EMA' has no parameter 'length' - its schema declares [period]` | key not in `paramSchema` |
| `ruleTree binds $fast but the strategy defines no parameter 'fast'` | missing knob |

The same indicator may appear many times at different parameterizations — that is
exactly what EMA Crossover does with `$fast` and `$slow`, and it is why there is
no strategy-to-indicator join table to query.

---

## 8. Gotchas

1. **`204` responses have no body.** Don't call `response.json()` on a DELETE.
2. **`404` can mean "someone else's".** Never render "this was deleted" with
   certainty; "not found or not available" is the honest message.
3. **Strategy parameter ids are integers**; every other id is a UUID string.
4. **`defaultValue` is a string** even for numeric types.
5. **Subscription `PUT` merges `params`, replaces everything else.**
6. **Risk-limits `PUT` clears omitted fields.** Send all three.
7. **`configHash` changing is normal** after a signal-parameter edit. The
   subscription `id` is stable — key your list on it, not on the hash or instance id.
8. **A `409` message is usually actionable** — it names the blocking count, the
   conflicting id, or the "deactivate instead" alternative. Show the message text.
9. **`system: true` strategies are read-only.** Disable the controls rather than
   letting the user discover it via a 409.
10. **Empty reference data is possible on a fresh environment.** Exchanges,
    symbols and risk profiles are seeded by ops, not by the app. Handle `[]`
    gracefully instead of rendering an empty picker with no explanation.

---

## 9. Suggested TypeScript types

```ts
export interface ApiError { error: string; errors?: string[] }

export interface StrategyParamDef {
  id: number;
  parameterKey: string;
  dataType: DataType;
  scope: ParamScope;
  defaultValue: string | null;
  validation: Record<string, unknown>;   // {} when none
  displayLabel: string | null;
  displayOrder: number;
  required: boolean;
}

export interface StrategyDetail {
  id: string; name: string; version: number; description: string | null;
  system: boolean; active: boolean;
  ruleTree: Record<string, unknown>;
  indicators: string[]; unknownIndicators: string[];
  params: StrategyParamDef[];
  instanceCount: number;
  createdAt: string; updatedAt: string;
}

export interface IndicatorDef {
  id: string; name: string;
  paramSchema: Record<string, { type: DataType; min?: number; max?: number; options?: unknown[] }>;
  active: boolean; usedByStrategies: string[]; createdAt: string;
}

export interface Subscription {
  id: string; userId: string;
  strategyId: string; strategyName: string;
  strategyInstanceId: string; configHash: string;
  symbolId: string; symbol: string; timeframe: string;
  signalParams: Record<string, unknown>;
  execParams: Record<string, unknown>;
  indicators: string[];
  tradingAccountId: string; tradingAccountName: string;
  riskProfileId: string | null; riskProfileName: string | null;
  quantity: number; multiplier: number;
  lotSize: number | null; capitalAllocated: number | null;
  executionMode: ExecutionMode; tradeMode: TradeMode;
  active: boolean; version: number;
  createdAt: string; updatedAt: string;
}

export interface StrategyInstance {
  id: string; strategyId: string; strategyName: string;
  symbolId: string; symbol: string; timeframe: string;
  signalParams: Record<string, unknown>;
  configHash: string; supersedesId: string | null;
  status: InstanceStatus; indicators: string[];
  activeSubscribers: number; createdAt: string;
}

export interface TradingAccount {
  id: string; exchangeId: string; exchangeCode: string; exchangeName: string;
  accountName: string; active: boolean;
  vaultRef: string | null; rotatedAt: string | null;
  activeSubscriptions: number; createdAt: string; updatedAt: string;
}

export interface Exchange {
  id: string; name: string; code: string;
  description: string | null; status: ExchangeStatus;
}

export interface SymbolRef {
  id: string; exchangeId: string; exchangeCode: string; symbol: string;
  baseAsset: string | null; quoteAsset: string | null;
  instrumentType: InstrumentType; optionType: OptionType | null;
  strikePrice: number | null; expiryAt: string | null;
  contractSize: number | null; tickSize: number | null; minQty: number | null;
  active: boolean;
}

export interface RiskProfile {
  id: string; name: string; description: string | null;
  maxDailyLoss: number | null; maxDrawdown: number | null;
  maxPositionSize: number | null; maxTotalExposure: number | null;
  maxTradesPerDay: number | null; killSwitchEnabled: boolean;
}

export interface UserRiskLimits {
  userId: string;
  maxDailyLoss: number | null; maxOpenPositions: number | null;
  maxTotalExposure: number | null; updatedAt: string | null;
}
```

---

## 10. Endpoint index

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/strategies` | list (`?active`, `?search`) |
| GET | `/api/v1/strategies/{id}` | detail |
| GET | `/api/v1/strategies/by-name/{name}` | detail by unique name |
| POST | `/api/v1/strategies` | create |
| PUT | `/api/v1/strategies/{id}` | update / deactivate |
| DELETE | `/api/v1/strategies/{id}` | delete |
| GET | `/api/v1/strategies/{id}/params` | list knobs |
| POST | `/api/v1/strategies/{id}/params` | add knob |
| PUT | `/api/v1/strategies/{id}/params/{paramId}` | update knob |
| DELETE | `/api/v1/strategies/{id}/params/{paramId}` | delete knob |
| GET | `/api/v1/indicators` | list (`?active`) |
| GET | `/api/v1/indicators/{id}` | detail |
| GET | `/api/v1/indicators/by-name/{name}` | detail by name |
| POST | `/api/v1/indicators` | create |
| PUT | `/api/v1/indicators/{id}` | update schema / deactivate |
| DELETE | `/api/v1/indicators/{id}` | delete |
| GET | `/api/v1/subscriptions` | the caller's configurations |
| GET | `/api/v1/subscriptions/{id}` | detail |
| POST | `/api/v1/subscriptions` | subscribe |
| PUT | `/api/v1/subscriptions/{id}` | edit / pause / resume |
| DELETE | `/api/v1/subscriptions/{id}` | unsubscribe |
| GET | `/api/v1/strategy-instances` | shared configs (`?status`) |
| GET | `/api/v1/strategy-instances/{id}` | detail |
| GET | `/api/v1/strategy-instances/indicator-plan` | dedup report |
| GET | `/api/v1/trading-accounts` | the caller's accounts |
| GET | `/api/v1/trading-accounts/{id}` | detail |
| POST | `/api/v1/trading-accounts` | create |
| PUT | `/api/v1/trading-accounts/{id}` | update / rotate vault ref |
| DELETE | `/api/v1/trading-accounts/{id}` | delete |
| GET | `/api/v1/exchanges` | reference (`?status`) |
| GET | `/api/v1/exchanges/{id}` | reference |
| GET | `/api/v1/symbols` | reference (`?exchangeId`, `?activeOnly`) |
| GET | `/api/v1/symbols/{id}` | reference |
| GET | `/api/v1/risk-profiles` | reference |
| GET | `/api/v1/risk-profiles/{id}` | reference |
| GET | `/api/v1/me/risk-limits` | caller's aggregate caps |
| PUT | `/api/v1/me/risk-limits` | set caps (full replace) |

Not part of this module and unchanged: `/api/v1/auth/**` (login, refresh,
logout), `/api/v1/strategy/**` (the legacy platform-wide on/off switches), and
the Dhan token endpoints.
