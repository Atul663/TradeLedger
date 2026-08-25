# TradeLedger — Code Architecture

A complete map of the backend: what every layer does, what every table and column
stores, and how a request flows end to end.

- [1. The big idea](#1-the-big-idea)
- [2. Two parallel subsystems](#2-two-parallel-subsystems)
- [3. Layer map](#3-layer-map)
- [4. Data model — column by column](#4-data-model--column-by-column)
- [5. Why the parameter catalog is gone](#5-why-the-parameter-catalog-is-gone)
- [6. Flows](#6-flows)
- [7. Cross-cutting machinery](#7-cross-cutting-machinery)
- [8. Endpoint reference](#8-endpoint-reference)
- [9. Known gaps](#9-known-gaps)

---

## 1. The big idea

A user builds a **strategy** — one complete, runnable configuration — and
**deploys** it to as many brokers as they like. The platform computes the
indicators behind it **once**, however many users ask for the same numbers.

```
  strategy_templates        the LOGIC        "EMA Averaging", a rule tree
        │                                    platform-owned, shared by everyone
        ▼
  user_strategies           the CONFIG       NIFTY · 5m · CE OTM1 + PE ATM
        │                                    · doubling ladder from 65 · k=21 d=9
        │                                    one row, one owner, typed columns
        ├──────────────┬──────────────┐
        ▼              ▼              ▼
  subscription    subscription   subscription   the DEPLOYMENTS
   Dhan main       Dhan hedge      Zerodha       who runs it, at what size
```

Two things make that work.

**Everything the platform defines is a column.** Which underlying, which candle,
future or options, CE at OTM3 and PE at ATM, the averaging ladder, the stop —
these are fixed concepts on an Indian F&O desk. They are typed columns on
`user_strategies` with CHECK constraints behind them, so a strike depth that
disagrees with its moneyness is impossible rather than merely refused, and "every
strategy trading OTM calls on a doubling ladder" is a `WHERE` clause.

**Only indicators are pluggable, so only they are schemaless.** EMA takes k and
d, RSI takes period, the next one takes whatever its author says. An indicator
declares the shape of its knobs in `indicators.param_schema`; a user's values for
them live in `user_strategy_indicators.params` as jsonb and are validated against
that schema on every write.

That split is the axis the whole design turns on:

```
        FIXED — the platform's vocabulary        PLUGGABLE — the indicator's

  derivative, ce_moneyness, base_lot,      k, d, period, whatever comes next
  lot_rule, candle_duration, sl_pct        declared in indicators.param_schema
  typed columns + CHECK constraints        jsonb, validated on write
  the same on every template               different per indicator
  NEVER hashed                             the WHOLE of the config hash
```

And the second half of that line is what makes dedup work. The indicators run on
the **underlying**, so the maths is identical whether the signal is traded through
a call, a put or a future. Two users on NIFTY 5m with `k=21, d=9` share one
computation even if one buys OTM3 calls with a doubling ladder and the other sells
ATM puts at a fixed lot.

---

## 2. Two parallel subsystems

The codebase contains two distinct worlds that share a database and a JWT.

### A. Legacy / auth subsystem (pre-existing)

| Piece | Purpose |
|---|---|
| `GoogleAuthToken` → `google_auth_tokens` | **The authentication table.** Google tokens, PAN card, revoked flag |
| `GoogleAuthController` `/api/v1/auth` | OAuth login, refresh, logout |
| `JwtUtil`, `JwtFilter` | Issues and validates the access/refresh JWTs |
| `PlatformStrategyToggle` → `platform_strategy_toggles` | Platform-wide on/off switches, keyed by an uppercased name string with no FK to `strategy_templates` |
| `PlatformStrategyToggleController` `/api/v1/strategy-toggles` | CRUD over those switches. `PUT /{name}/toggle` is **mutually exclusive** — enabling one disables every other |

### B. Control plane / strategy module

| Piece | Purpose |
|---|---|
| `User` → `users` | Control-plane identity, **not** the login table |
| `Exchange`, `Symbol` | Venue and contract master |
| `Indicator` → `indicators` | Compute primitives, each declaring its own `param_schema` |
| `StrategyTemplate` → `strategy_templates` | The logic — a rule tree, and nothing a user configures |
| `UserStrategy` → `user_strategies` | **One user's complete configuration**, all of it in typed columns |
| `UserStrategyIndicator` → `user_strategy_indicators` | One row per indicator usage, carrying its values as jsonb |
| `SharedStrategyConfig` → `shared_strategy_configs` | The dedup unit — content-addressed, immutable |
| `StrategySubscription` → `user_strategy_subscriptions` | One deployment: this strategy, on this account |
| `Broker` | Who the order is routed through (DHAN, ZERODHA) — distinct from the venue |
| `UserBroker` | One user's setup with a broker, and the key its accounts share |
| `TradingAccount`, `BrokerCredential` | The accounts under a setup + their encrypted credentials |
| `RiskProfile`, `UserRiskLimit` | Per-deployment and per-user caps |

### The bridge between them

`google_auth_tokens` (auth) and `users` (control plane) are **different tables with no
FK between them**. The only link is the email string:

```
JWT subject (email)
   │
   ├─ JwtFilter puts it in the SecurityContext as the principal
   │
   ├─ SecuredController.currentEmail() reads it
   │
   └─ CurrentUserService.require(email) → users row (created on first touch)
```

`CurrentUserServiceImpl.require()` lowercases the email, looks it up, and if
absent **provisions a row lazily** — username derived from the email local part
(disambiguated with `-2`, `-3`… on collision), `password_hash` set to the
sentinel `EXTERNAL_AUTH:GOOGLE` because the column is NOT NULL but Google users
have no local credential. A non-`active` status raises 409.

---

## 3. Layer map

```
HTTP request
   │
   ▼
JwtFilter ─────────────── reads "Authorization: Bearer …"
   │                      requires claim type == "access"
   │                      sets principal = email  (no token → passes through)
   ▼
SecurityConfig ────────── /v3/api-docs, /swagger-ui**  → permitAll
   │                      /api/v1/**                   → permitAll (!)
   │                      anything else                → authenticated
   ▼
Controller ────────────── extends SecuredController
   │                      currentEmail() → 401 if principal missing/anonymous
   ▼
Service interface ─────── IndicatorCatalogService, StrategyTemplateService,
   │                      SharedStrategyConfigService, StrategySubscriptionService,
   │                      UserStrategyService, TradingAccountService,
   │                      ReferenceDataService, CurrentUserService
   ▼
ServiceImpl ───────────── business rules, @Transactional boundary
   │
   ├── StrategyTemplateValidator ── rule trees and indicator schemas (author-time)
   ├── UserStrategyValidator ────── the typed columns: legs, ladder, exits
   ├── IndicatorParams ──────────── values against indicators.param_schema
   ├── SubscriptionFanOut ───────── one deployment per transaction
   ├── RuleTrees / IndicatorResolver ─ reads the rule tree
   ├── CanonicalJson / ConfigHashUtil ─ content addressing
   └── JsonSupport ──────────────── jsonb text ⇄ Map
   ▼
Repository (Spring Data JPA)
   ▼
Entity  ──────────────────  Postgres (Neon)
```

Errors are mapped by `StrategyApiExceptionHandler`, a `@RestControllerAdvice`
**scoped by `assignableTypes` to the strategy-module controllers only**, so the
authentication controllers keep their own response shapes.

---

## 4. Data model — column by column

### `users` — control-plane identity (`User.java`)

| Column | Type | Stores |
|---|---|---|
| `id` | UUID | PK. Every strategy-side FK points here |
| `username` | String(50) | Derived from the email local part; NOT NULL UNIQUE |
| `email` | String(100) | The JWT subject. The lookup key |
| `password_hash` | String(255) | Sentinel `EXTERNAL_AUTH:GOOGLE` — never verified |
| `status` | String(20) | `active` / `suspended` / `closed` |

### `symbols` — contract master (`Symbol.java`)

UNIQUE `(exchange_id, symbol)`. The **underlying** a strategy watches.

| Column | Stores |
|---|---|
| `exchange_id` | FK → exchanges |
| `symbol` | Ticker, uppercased on lookup |
| `instrument_type` | `spot` / `future` / `option` / `index` — the sheet's INDEX-vs-STOCK cell, so no column of its own is needed |
| `option_type`, `strike_price`, `expiry_at` | Options fields, for a fully-specified contract row |
| `contract_size`, `tick_size`, `min_qty` | Trading increments |
| `is_active` | Inactive symbols are refused at configure time |

> Indicators run on the **underlying**. Which contract the order lands on is the
> strategy's `derivative` and CE/PE columns, resolved at entry time.

### `indicators` — compute primitives (`Indicator.java`)

**The only dynamic schema left on the platform**, and the only table that declares
a type at all — every other value is a typed column, and its type is the column's.

| Column | Stores |
|---|---|
| `id` | UUID PK |
| `name` | `EMA Averaging`, `RSI`. UNIQUE, **uppercased on save**. Matched by exact string against rule trees |
| `param_schema` | jsonb. **The declaration**: `{"k":{"type":"int","min":1,"max":300,"default":21}}` |
| `is_active` | Inactive indicators fail rule-tree validation |

`param_schema` entry grammar — `type` is required and one of
`int · decimal · bool · enum · text`; `default` is **required** (with no parameter
catalog behind it, this is the only thing that can say what applies to a user who
never touches a knob); optional `min` / `max` (numeric), `options` (non-empty
list, required for `enum`), and `gt` / `lt` naming a sibling key for a cross-field
rule.

### `fixed_parameters` — how the fixed knobs are described (`FixedParameter.java`)

**A descriptor catalog, not a value store.** `indicators.param_schema` says what
the pluggable half of a strategy takes; this says the same about the fixed half —
the label, type, suggested default and bounds of each knob that is a **typed
column** on `user_strategies` or `user_strategy_subscriptions`.

| Column | Stores |
|---|---|
| `id` | UUID PK |
| `name` | `slPct`, `baseLot`, `candleDuration`. UNIQUE case-insensitively. By convention **the API field name of the column it describes**, so a form binds the two by string equality |
| `label` | `SL %` |
| `description` | Help text |
| `data_type` | `int · decimal · bool · enum · timeframe · text · symbol · exchange` |
| `scope` | `signal` / `execution` — whether the knob is part of a strategy's shared identity |
| `default_value` | text, coerced by `data_type`. What a form **pre-fills**, not what the engine applies |
| `validation` | jsonb. `{"min":0,"max":100}`, or `{"options":[...]}` which an `enum` requires. Mirrors the CHECK constraint on the column. **Empty for a `symbol` / `exchange` knob** — see below |
| `param_group`, `display_order` | Which section of the form, and where in it |
| `is_required`, `is_active` | Whether a form must demand it; whether it is shown at all |

Nothing reads this table to decide what a strategy runs with. The value of every
knob it describes is the column, with the column's own default and CHECK
constraint behind it — emptying `fixed_parameters` changes nothing but how a form
renders. `SchemaMappingTest` pins that: a `user_id`, a `custom_value` or a
`parameter_id` here would be the catalog of §5 again under a new name, and the
absence is asserted rather than left to review.

`default_value` is validated against `data_type` and `validation` on write, so an
`int` knob cannot pre-fill `none`, a `decimal` cannot sit outside its own
`min`/`max`, and an `enum` default has to be one of its options. A `timeframe`
default goes through the same `Timeframes` normalizer a strategy's does, so `5M`
stores as `5m`.

#### `symbol` and `exchange` — the knobs whose choices are rows

`symbol` is a choice like `enum`, but its options are the active `symbols`, not a
vocabulary anyone authored. That is why it is a **type of its own** rather than an
`enum` with a list in it: a list copied into `validation` would be stale the next
time an instrument is listed or retired, and it would be an admin's to edit when
it is really the reference data's. So nothing is stored — a `symbol` knob
**refuses** `validation.options` on write (400) — and `FixedParameterOptions`
fills them in on every read path, from the table:

```json
"validation": { "options": ["BANKNIFTY", "FINNIFTY", "NIFTY"],
                "optionsSource": "/api/v1/symbols" }
```

Tickers rather than ids, because `symbol` is the field a PUT carries. A ticker is
unique **per exchange**, not globally, so one listed on two venues is offered once
and `exchangeCode` alongside it picks the venue — the same pair `SymbolResolver`
has always required. `symbolId` remains the unambiguous alternative.

`exchange` is the same mechanism over the active `exchanges`, and it is what makes
the symbol knob's output submittable — a form can offer the venue beside the
ticker instead of the client knowing to append one:

```json
"validation": { "options": ["BSE", "NSE"],
                "optionsSource": "/api/v1/exchanges" }
```

Codes rather than names or ids, for the same reason: `exchangeCode` is the field a
PUT carries. A `disabled` exchange is left out — `SymbolResolver` would refuse a
symbol on one anyway, so offering it would be offering a choice that cannot be
saved. The knob sits at `displayOrder 0`, **before** `symbol`, because the venue
narrows the instrument.

`FixedParameter.REFERENCE_TYPES` is the set both belong to; the write rule that
refuses authored options and the read path that fills them both key off it, so a
third reference knob is one entry plus one lookup.

### `strategy_templates` — the logic (`StrategyTemplate.java`)

| Column | Stores |
|---|---|
| `id` | UUID PK |
| `name` | UNIQUE, max 100. The business key |
| `version` | Caller-bumped when the rule tree changes meaning |
| `is_system` | `true` = seeded → **immutable through the API** (409) |
| `is_active` | Inactive templates cannot be built on |
| `rule_tree` | jsonb. **The only declaration of which indicators are used** |

A template holds no defaults, no strikes, no sizing and no exits — those belong to
a user's configuration, not to the logic.

### `user_strategies` — one complete configuration (`UserStrategy.java`)

UNIQUE `(user_id, name)`. **This is the centre of the model.**

| Column | Stores |
|---|---|
| `user_id` | FK → users. Every read and write is filtered by this |
| `strategy_id` | FK → strategy_templates — which logic |
| `symbol_id` | FK → symbols — the **underlying** the indicators run on |
| `shared_config_id` | FK → shared_strategy_configs — the dedup unit this resolved to |
| `name`, `description` | The user's own label and note |
| `candle_duration` | `5m` — the candle evaluated on. Part of the shared config's identity |
| `trigger_duration` | How often to re-check inside that candle. Never hashed |
| `derivative` | `FUTURES` \| `OPTION` |
| `ce_enabled`, `ce_moneyness`, `ce_strike_offset` | The call side: on/off, `ATM`/`ITM`/`OTM`, depth 0–15 |
| `pe_enabled`, `pe_moneyness`, `pe_strike_offset` | The put side, chosen **independently** of the call |
| `lot_rule` | `FIXED` \| `DOUBLE` \| `CUMULATIVE` |
| `base_lot` | The first entry's size, in contracts |
| `averaging_count` | How many times it may add to a losing position |
| `sl_pct`, `tp_pct` | numeric(6,2) exits |
| `is_active` | A shelf flag — deployments decide what runs |

Three CHECK constraints ride on the table:

```sql
ck_user_strategies_ce_strike   ce_moneyness IS NULL
                            OR (ce_moneyness = 'ATM' AND ce_strike_offset = 0)
                            OR (ce_moneyness IN ('ITM','OTM')
                                AND ce_strike_offset BETWEEN 1 AND 15)
ck_user_strategies_pe_strike   … the same for the put side
ck_user_strategies_sizing      base_lot > 0
                            AND averaging_count BETWEEN 0 AND 10
```

**Why CE and PE are columns and not rows.** They are not a list — they are two
named sides, fixed at two. Columns make "one CE and one PE" structurally true
instead of something a unique constraint has to enforce, and they turn a join into
a field access. (The one thing that would change this is multi-leg spreads — an
iron condor needs four legs plus a buy/sell direction. That is not in scope.)

### `user_strategy_indicators` — the tuning (`UserStrategyIndicator.java`)

UNIQUE `(user_strategy_id, indicator_id, slot)`.

| Column | Stores |
|---|---|
| `user_strategy_id` | FK → user_strategies |
| `indicator_id` | FK → indicators — by id, never a name |
| `slot` | `fast` / `slow` when one template uses an indicator twice; null otherwise |
| `params` | jsonb — `{"k":21,"d":9}`, validated against `indicators.param_schema` on write |
| `is_enabled` | A disabled row contributes nothing to the config hash |

A child table rather than more columns because the **set** of indicators is open —
one strategy uses EMA, the next uses EMA and RSI — and unlike CE/PE they are not
named slots.

### `shared_strategy_configs` — the dedup unit (`SharedStrategyConfig.java`)

UNIQUE `(strategy_id, symbol_id, timeframe, config_hash)`.

| Column | Stores |
|---|---|
| `strategy_id`, `symbol_id`, `timeframe` | Three quarters of the identity |
| `signal_params` | jsonb, canonicalized — the union of every enabled indicator's values |
| `config_hash` | sha256 of those four. The content address |
| `supersedes` | FK → the instance this one replaced (lineage across a retune) |
| `status` | `active` / `retired` |

**Never mutated.** A retune inserts a new instance, repoints the strategy,
records `supersedes`, and retires the orphan once its last active deployment
leaves.

### `user_strategy_subscriptions` — one deployment (`StrategySubscription.java`)

UNIQUE `(user_strategy_id, trading_account_id)` — a strategy is deployed on an
account once; deploying it again is an edit.

| Column | Stores |
|---|---|
| `user_id` | FK → users. Denormalized so the ownership filter stays a one-table query |
| `user_strategy_id` | FK → user_strategies. **The whole of "what it runs"** |
| `trading_account_id` | FK → trading_accounts. Where it runs |
| `risk_profile_id` | FK → risk_profiles, nullable |
| `multiplier` | Scales the strategy's `base_lot` on this account alone |
| `capital_allocated` | For the percent-based execution modes |
| `execution_mode` | `FIXED_QTY` / `CAPITAL_PERCENT` / `RISK_PERCENT` |
| `trade_mode` | `paper` / `live` — one broker can go live while the rest do not |
| `is_active` | Pause one broker without touching the strategy or the others |

**The configuration is not copied here.** A deployment reaches its instrument,
strikes, ladder, exits and indicator values through `user_strategy_id`, so
retuning the strategy moves every broker at once and a running deployment can
never drift from the strategy it claims to run.

### Supporting tables

| Entity → table | Stores |
|---|---|
| `UserBroker` → `user_brokers` | user + catalog broker + `label`, UNIQUE together |
| `TradingAccount` → `trading_accounts` | Belongs to a `UserBroker`; `account_name` UNIQUE within it. **No exchange** — the symbol decides the venue |
| `Broker` → `brokers` | `code` unique. `auth_type` says which credential fields that broker needs |
| `BrokerCredential` → `broker_credentials` | Two levels: `trading_account_id` NULL is the setup's key, set is one account's override. Resolution is **per field**. AES-GCM ciphertext via `SecretCipher` |
| `RiskProfile` → `risk_profiles` | Reusable per-deployment caps |
| `UserRiskLimit` → `user_risk_limits` | **PK is `user_id`** — one row per user, aggregate caps |

---

## 5. Why the parameter catalog is gone

An earlier model stored every knob as a row in a `parameters` catalog, joined to
indicators and templates through link tables, projected into a derived
`strategy_param_definitions` table, and overridden per user in
`user_strategy_parameters` — seven tables, values as `text` with a `data_type`
column, and a sync class keeping the derived table in step.

It was replaced because the model had already told us it was wrong:

| Concept | Was a catalog row | **And** a typed column |
|---|---|---|
| quantity | `parameters.code = 'quantity'` | `user_strategy_subscriptions.quantity` |
| candle duration | `parameters.code = 'candle_duration'` | `user_strategies.timeframe` |
| lot size | — | `user_strategy_subscriptions.lot_size` |

When the same fact lives in a key/value row *and* a column, the flexibility is
being paid for and not used. Worse, a flat `code → text` map physically cannot
express a repeating group, which is exactly what "a call at OTM3 **and** a put at
ATM" is.

Seven tables were dropped: `parameters`, `indicator_parameter_links`,
`strategy_parameter_links`, `strategy_param_definitions`,
`user_strategy_parameters`, `strategy_indicator_links`, and a short-lived
`user_strategy_legs`. `SchemaMappingTest` asserts they stay gone.

What was given up, and what replaced it:

| Lost | Replacement |
|---|---|
| Admin retunes a global default, every non-overriding user moves | `indicators.param_schema` defaults do exactly this for indicator values; the seeder converges them on boot |
| Forms rendering themselves from `params[]` | `GET /strategy-templates/{id}` carries each indicator's `paramSchema`; `fixed_parameters` describes the fixed fields, which are the same on every template |
| A new execution knob without a migration | A migration. It is a column now — which is the point |

`strategy_indicator_links` went too, and it was not a trade-off: it was a *derived
cache* of what the rule tree already said. Reading the tree directly is the only
way the two can never disagree.

### What `fixed_parameters` is, and why it is not that catalog coming back

The one thing genuinely lost above was a form that renders itself: the fixed
fields had labels, types and defaults hardcoded in whatever client drew them, and
rewording a label or retuning a suggested default meant a frontend deploy.
`fixed_parameters` gives those back — and stops there.

| | `parameters` (dropped) | `fixed_parameters` |
|---|---|---|
| Holds | user **values**, as `text` rows | **descriptors** — label, type, default, bounds |
| Reached from a user row | yes, via `user_strategy_parameters` and two link tables | never — nothing has a foreign key to it |
| Read to decide what runs | yes, through a COALESCE chain | no |
| Effect of deleting every row | users lose their configuration | forms lose their labels |

The failure of the old model was the same fact living in a key/value row *and* a
column. Here the fact lives in the column, and this table holds only what the
column could never say about itself. Which is also its limit: it cannot express a
repeating group either, and it is not where a new knob goes — a new knob is still
a migration and a column, plus a row here to describe it.

---

## 6. Flows

### 6.1 Authentication

```
GET  /api/v1/auth/google            → redirect to Google consent
GET  /api/v1/auth/callback?code=…   → exchange, fetch email, set refresh cookie,
                                      redirect to <frontend>/create-plan
GET  /api/v1/auth/me                → {email, accessToken, hasPanCard}
POST /api/v1/auth/refresh           → {accessToken}
POST /api/v1/auth/logout            → revoke + clear cookie
```

Every other endpoint expects `Authorization: Bearer <accessToken>`.

### 6.2 Build a strategy — `POST /api/v1/my-strategies`

```
 email ─→ CurrentUserService.require()  ──────────→ users row (lazily provisioned)

 resolveTemplate()   strategyId, or strategyName   → 404 / must be active
 normalizeName()     defaults to the template name → 409 if you already have one
        │
        ▼
 seedIndicatorRows()
        │  IndicatorResolver.indicatorNames(rule_tree)  → ["EMA Averaging"]
        │  each name → indicators row (by name, FK stored)
        │  params = IndicatorParams.defaults(indicator)   {"d":9,"k":21}
        ▼
 applyRequest()
        │  present field applied, absent field left alone
        │  enums parsed case-insensitively, with the alternatives in the message
        │  naming ceMoneyness turns the call side on
        │  indicator params MERGED over what is stored, then validated
        ▼
 UserStrategyValidator.validate()
        │  FUTURES  → neither option side may be on
        │  OPTION → at least one side, each with a moneyness
        │  ATM → offset 0;  ITM/OTM → 1..15
        │  base_lot ≥ 1;  averaging_count 0..10
        │  a non-FIXED ladder needs somewhere to climb
        ▼
 resolveSharedConfig()      (skipped until symbol + candle_duration are set)
        │  signal = union of every ENABLED indicator's params, sorted
        │  ruleTrees.assertResolves()  ← catches a $key with no value behind it
        │  canonical = CanonicalJson(signal)
        │  hash = sha256(strategyId|symbolId|candleDuration|canonical)
        │  lookup by (strategy, symbol, timeframe, hash)
        │      found  → reuse            ← SHARING HAPPENS HERE
        │      absent → INSERT new instance
        │  changed?  → repoint, record supersedes, retire the orphan
        ▼
   user_strategies row, deployable
```

### 6.3 Deploy to many brokers — `POST /api/v1/my-strategies/{id}/deploy`

```
 { "tradeMode":"paper",
   "targets":[ {"tradingAccountId":"…"},
               {"tradingAccountId":"…", "multiplier":2, "tradeMode":"live"},
               {"userBrokerId":"…"} ] }          ← fans out to all its accounts

 expand()   target → account(s), ownership-checked
            per-target value wins, else the request-level default
            the same account twice → 400 before anything is written
        │
        ▼
 for each account:
        SubscriptionFanOut.deployOne()      @Transactional(REQUIRES_NEW)
        │
        ├─ succeeded → {"status":"deployed", "subscription":{…}}
        └─ threw     → {"status":"failed",   "error":"<the same sentence
                                                      the single-account
                                                      endpoint would give>"}
        ▼
 { "requested":3, "deployed":2, "failed":1, "results":[…] }      HTTP 200
```

**Why `REQUIRES_NEW`.** One account that already runs this strategy is a 409 for
that account and nothing at all for the other four. But a failed flush inside a
shared transaction marks the whole transaction rollback-only, so catching the
exception in a loop would only move the failure to commit time and take the
successes down with it. Each account gets a clean transaction; when it fails, only
that one rolls back. It lives in its own bean because Spring's transaction advice
is a proxy — a self-call would silently bypass the annotation.

### 6.4 Retune — `PUT /api/v1/my-strategies/{id}`

Partial: a present field is applied, an absent one left alone. Send
`{"indicators":[{"indicatorName":"EMA Averaging","params":{"k":50}}]}` and `d`
keeps its value.

If the change moves the signal params, the strategy repoints at a different
shared computation — `sharedConfigId` and `configHash` change, the old instance is
retired once its last active deployment leaves, and **every broker running this
strategy follows**, because they point at this row rather than copying it.

### 6.5 Withdraw — `DELETE /api/v1/my-subscriptions/{id}`

Delete the row, `flush()`, then `retireIfOrphaned()`. An instance is retired —
never deleted — when its last active deployment goes, so lineage survives.

`DELETE /api/v1/my-strategies/{id}` is **refused** while any deployment still
points at it (409, with the count). Cascading would silently stop trading on every
broker.

### 6.6 The dedup report — `GET /api/v1/shared-strategy-configs/indicator-plan`

Walks every active instance, sums active deployments, and unions their resolved
fingerprints into a `TreeSet`.

```json
{ "activeStrategySubscriptions": 3, "distinctInstances": 3, "distinctIndicators": 4,
  "indicators": ["EMA(period=13)","EMA(period=21)","EMA(period=50)","EMA(period=9)"] }
```

---

## 7. Cross-cutting machinery

### `IndicatorParams` — the one place unchecked data could hide

Everything else on a strategy is a typed column with a constraint behind it.
Indicator values are jsonb, so the check a column would have given for free is
done here on every write: unknown key, wrong type, out of range, and the
cross-field `gt` / `lt` rules. Missing keys fall through to the schema's
`default`. The result is a `TreeMap` — it feeds the config hash directly, and an
unordered map would split the dedup silently.

### `UserStrategyValidator` — the cross-column rules

The CHECK constraints already make an impossible strike impossible. This layer
catches the same mistakes one request earlier with a message worth reading, and
enforces what a CHECK cannot state readably: a FUTURES strategy with an option side
still on, an OPTION strategy with neither. It collects every error rather than
throwing on the first.

### `CanonicalJson` — byte-exact serialization

Object keys sorted **by length first, then byte value** (jsonb's own ordering,
*not* plain lexicographic); array order preserved; numeric scale trimmed so `9`,
`9.0` and `"9"` all render `9`; jsonb spacing — `{"d": 9, "k": 21}`, with the
spaces.

### `ConfigHashUtil` — content addressing

```
sha256( strategy_id | symbol_id | candle_duration | canonical_jsonb(signal_params) )
```

Instrument, strikes, ladder and exits are deliberately excluded — that exclusion
is exactly what lets two users trading opposite sides of the same signal share one
computation.

### Two validators, two jobs

| Class | Validates | When |
|---|---|---|
| `StrategyTemplateValidator` | **Structure** — rule trees and indicator schemas | Author-time (create/update a template or indicator) |
| `UserStrategyValidator` + `IndicatorParams` | **Values** — the typed columns and the jsonb | Every write of a user strategy |

Nothing in either is EMA-specific.

### Error mapping (`StrategyApiExceptionHandler`)

| Exception | Status | Body |
|---|---|---|
| `UnauthenticatedException` | 401 | `{"error":"Unauthorized: valid Bearer token required"}` |
| `StrategyValidationException` | 400 | `{"error":"<first>","errors":[…]}` |
| `ResourceNotFoundException` | 404 | `{"error":"Strategy not found: <id>"}` |
| `ResourceConflictException` | 409 | `{"error":"…"}` |
| `DataIntegrityViolationException` | 409 | `{"error":"Request conflicts with existing data"}` |
| parse / type-mismatch | 400 | first line of the parser message |
| anything else | 500 | `{"error":"Unexpected error processing the request"}` |

### Concurrency stance

Two races are handled by **letting the database referee and not catching the
violation**: first-ever user provisioning (`users.email` UNIQUE) and concurrent
instance creation (the dedup UNIQUE). Catching would not help — a failed flush has
already marked the transaction rollback-only. The caller gets a 409 and the retry
succeeds.

### Seeding (`ControlPlaneSeeder`)

An `ApplicationRunner`, idempotent on `indicators.name`,
`strategy_templates.name` and `fixed_parameters.name`:

| Indicator | Schema |
|---|---|
| `EMA Crossover` | `k` default 9, `d` default 21 with `gt: k` — the classic fast/slow pair |
| `EMA Averaging` | `k` default 21, `d` default 9 with `lt: k` — the EMA of the highs against a shorter signal leg, so 21/9 and 50/21 are both valid and 9/21 is not |
| `EMA`, `RSI` | `period`, for templates that use them singly |

| Template | Rule tree |
|---|---|
| `EMA Crossover` | `{"entry":{"ind":"EMA Crossover","params":{"k":"$k","d":"$d"}}}` |
| `EMA Averaging` | `{"entry":{"ind":"EMA Averaging","params":{"k":"$k","d":"$d"}}}` |

| Fixed-parameter group | Descriptors |
|---|---|
| `Market` | `candleDuration` (signal scope), `triggerDuration` |
| `Instrument` | `derivative`, `ceEnabled` / `ceMoneyness` / `ceStrikeOffset`, and the `pe` three |
| `Sizing` | `lotRule`, `baseLot`, `averagingCount` |
| `Exits` | `slPct`, `tpPct` |
| `Deployment` | `executionMode`, `multiplier`, `capitalAllocated`, `tradeMode` |

Schemas **converge** on every boot — they are the only declaration of what applies
by default, so a platform retune has to be able to land. Rule trees converge only
while no user strategy exists on that template; otherwise it logs a WARN and
leaves the row alone, because rewriting the tree would strand indicator rows that
were settled under the old one.

Fixed parameters are the exception: **insert-only, never converged**. Nothing
reads them to decide what anyone runs, and `/api/v1/fixed-parameters` is the
intended way to reword a label or retune a suggested default — so converging on
boot would silently undo an admin's edit on the next deploy.

---

## 8. Endpoint reference

All strategy-module endpoints require `Authorization: Bearer <accessToken>`.

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/indicators?active=` | List, each with its `paramSchema` |
| GET | `/api/v1/indicators/{id}`, `/by-name/{name}` | Name is uppercased before lookup |
| POST/PUT/DELETE | `/api/v1/indicators`, `/{id}` | Rename and delete blocked while referenced |
| GET | `/api/v1/fixed-parameters?group=&scope=&active=` | The fixed knobs' labels, types, defaults and bounds, ordered as a form lays them out |
| GET | `/api/v1/fixed-parameters/grouped?group=&scope=&active=` | The same rows, one group per `paramGroup` — the sections a form renders |
| GET | `/api/v1/fixed-parameters/{id}`, `/by-name/{name}` | Name matched case-insensitively |
| POST/PUT/DELETE | `/api/v1/fixed-parameters`, `/{id}` | **Descriptors, not values** — nothing here changes what a strategy runs with. `defaultValue` is parsed against `dataType` and `validation` on write |
| GET | `/api/v1/strategy-templates?active=&search=` | The template + its indicators' schemas, also grouped by indicator name (`indicatorGroups[]`) and the fixed-knob descriptors grouped by section (`fixedParameters[]`) |
| GET | `/api/v1/strategy-templates/{id}`, `/by-name/{name}` | |
| POST | `/api/v1/strategy-templates` | 201, `is_system` forced false |
| PUT / DELETE | `/api/v1/strategy-templates/{id}` | 409 on system rows; rule tree frozen once strategies exist |
| GET/POST | `/api/v1/my-strategies` | The caller's strategies, ownership-filtered |
| GET | `/api/v1/my-strategies/grouped?active=&strategyId=` | The same rows, one group per template, each tagged with its `strategyName` |
| GET/PUT/DELETE | `/api/v1/my-strategies/{id}` | Editor shape: the flat fields, plus the same content arranged as `indicatorGroups[]` (by indicator name) and `fixedParameters[]` (by `paramGroup`, descriptor + value). Writes address the flat names. Another user's row reports **404, not 403** |
| GET | `/api/v1/my-strategies/{id}/runtime` | Bot shape: legs resolved, values coerced |
| POST | `/api/v1/my-strategies/{id}/deploy` | **Fan out to many brokers**, per-target outcome |
| GET/POST | `/api/v1/my-subscriptions` | Deployments, ownership-filtered |
| GET/PUT/DELETE | `/api/v1/my-subscriptions/{id}` | Sizing, mode and pause only |
| GET | `/api/v1/shared-strategy-configs?status=`, `/{id}` | |
| GET | `/api/v1/shared-strategy-configs/indicator-plan` | Dedup report |
| GET/POST | `/api/v1/trading-accounts`, `/my-brokers` | |
| POST | `/api/v1/my-brokers/setup` | Wizard: setup + first account + key in one transaction |
| GET/PUT/DELETE | `/api/v1/my-brokers/{id}`, `/{id}/credentials` | |
| GET/PUT/DELETE | `/api/v1/trading-accounts/{id}`, `/{id}/credentials` | Masked on read; PUT is partial; `""` clears |
| GET/POST/PUT/DELETE | `/api/v1/exchanges`, `/symbols`, `/risk-profiles` | Reference data. **Shared master data** — writes are not role-gated; deletes are refused while referenced |
| GET/POST/PUT/DELETE | `/api/v1/brokers` | The broker catalog. **Shared master data** — writes are not role-gated yet |
| GET/PUT | `/api/v1/me/risk-limits` | Aggregate caps |
| GET/POST/PUT/DELETE | `/api/v1/strategy-toggles/...` | Legacy platform switches, unrelated to templates |

Ownership is enforced *inside the query* (`findByIdAndUser_Id`), which is why a
foreign resource is a 404 rather than a 403 — the API does not confirm it exists.

---

## 9. Known gaps

1. **`users.id` type collision.** A deployed database whose `users.id` is a
   `bigint` identity column cannot be converted by `ddl-auto=update`; the ALTER
   fails at boot and the FKs are never created.

2. **`ddl-auto=update` hides failed migrations.** Schema drift surfaces as a WARN
   in the startup log, never an error. `SchemaMappingTest` catches mapping
   mistakes offline, but not a database that has drifted from the mapping. Worth
   replacing with Flyway/Liquibase.

3. **The `config_hash` trigger does not exist under `ddl-auto`.** The
   application-computed hash is the only one in play, which makes
   `CanonicalJson`'s byte-exactness untested rather than enforced.

4. **`JwtUtil.SECRET` is a static field read from `System.getenv("JWT_SECRET")`.**
   Missing env var → NPE during class initialization, not a readable startup
   error.

5. **`/api/v1/**` is `permitAll` in `SecurityConfig`.** Authentication is enforced
   by `SecuredController.currentEmail()` in the controller layer instead, so a new
   controller that forgets to extend it is silently public.

6. **The averaging ladder is stored, not computed.** `lot_rule`, `base_lot` and
   `averaging_count` record the user's choice; turning them into per-entry sizes
   belongs to the execution engine, which is not in this repository.

7. **Multi-leg spreads are out of reach.** CE and PE as columns fix the leg count
   at two. An iron condor would need four legs plus a buy/sell direction, which is
   a child table again.

8. **Lazy user provisioning inside a read-only transaction.** *(pre-existing)*
   `CurrentUserService.require()` is `@Transactional` with the default
   propagation, so it joins whatever transaction is already running — including
   the `readOnly = true` ones on every list/get/deploy. A brand-new user whose
   very first request is a read would need an INSERT on a read-only connection.
   Unreachable in practice (a read of your own rows implies you have some), but
   the safe fix is `REQUIRES_NEW` on `require()`.

9. **Shared master data has write endpoints but no authorization.** `brokers`,
   `exchanges`, `symbols` and `risk_profiles` have no owner column, so any
   authenticated caller can create and edit rows every other user depends on.
   There is no role model in this API layer to gate that with. What stands in for
   it is structural, and it bounds the damage rather than preventing the write:

   - a delete is **refused while anything references the row** (409 with the
     count), so no write here can break a saved configuration;
   - identity columns freeze once referenced — an exchange's `code` cannot change
     while it has symbols, and a symbol cannot move between exchanges;
   - deactivating is always available and is the documented alternative to
     deleting.

   The worst case is therefore additive clutter, not data loss. A real fix is a
   role claim in the JWT and an admin check on these six controllers.
