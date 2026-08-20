# TradeLedger — Code Architecture

A complete map of the backend: what every layer does, what every table and field
stores, and how a request flows end to end.

- [1. The big idea](#1-the-big-idea)
- [2. Two parallel subsystems](#2-two-parallel-subsystems)
- [3. Layer map](#3-layer-map)
- [4. Data model — field by field](#4-data-model--field-by-field)
- [5. The strategy ↔ indicator ↔ parameter triangle](#5-the-strategy--indicator--parameter-triangle)
- [6. Flows](#6-flows)
- [7. Cross-cutting machinery](#7-cross-cutting-machinery)
- [8. Endpoint reference](#8-endpoint-reference)
- [9. Known gaps](#9-known-gaps)

---

## 1. The big idea

The system lets many users run the same trading strategy with different settings
**without recomputing the same indicator many times**.

Three users subscribe to EMA Crossover on BTCUSDT/5m:

| User | fast | slow | stop loss |
|---|---|---|---|
| A | 9 | 21 | 1.5% |
| B | 9 | 21 | 4.0% |
| C | 13 | 21 | 1.5% |

A and B want *identical math* and differ only in a personal exit rule. So the
platform stores **one** shared configuration row for `{fast:9, slow:21}` that A
and B both point at, and a second one for `{fast:13, slow:21}` for C. The stop
losses live on each user's own row and never affect sharing.

That split is the axis the entire design turns on:

```
                  a parameter is either...

  SIGNAL scope                          EXECUTION scope
  ------------                          ---------------
  changes the math                      changes only your exit
  fast, slow, rsiLen                    sl_pct, tp_pct
  stored on shared_strategy_configs          stored on subscriptions
  INSIDE the config hash                NEVER hashed
  shared between users                  personal
```

Which side a parameter falls on is not hardcoded anywhere — it is the `scope`
column of a `strategy_param_definitions` row. A new strategy is an INSERT, never a
schema change.

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

### B. Control plane / strategy module (the new work)

| Piece | Purpose |
|---|---|
| `User` → `users` | Control-plane identity, **not** the login table |
| `Exchange`, `Symbol` | Venue and contract master |
| `Indicator` → `indicators` | Compute primitives (EMA, RSI) |
| `StrategyTemplate` → `strategy_templates` | Strategy template — the rule tree |
| `Parameter` → `parameters` | The parameter catalog — one row per distinct knob |
| `IndicatorParameterLink` → `indicator_parameter_links` | Indicator ↔ parameter, by id |
| `StrategyParameterLink` → `strategy_parameter_links` | Strategy ↔ parameter, by id |
| `StrategyParamDefinition` → `strategy_param_definitions` | Derived flat knob set the engine validates against |
| `StrategyIndicatorLink` → `strategy_indicator_links` | Derived index: which indicators a strategy uses, as FKs |
| `SharedStrategyConfig` → `shared_strategy_configs` | Immutable shared config (the dedup unit) |
| `StrategySubscription` → `user_strategy_subscriptions` | A user's personal leg |
| `UserStrategy` → `user_strategies` | A user's customization of a template — FKs only, no copied master data |
| `UserStrategyIndicator` → `user_strategy_indicators` | Which indicator usages that customization carries |
| `UserStrategyParameter` → `user_strategy_parameters` | ONE ROW PER CHANGED VALUE; a knob left at its default has none |
| `Broker` | Who the order is routed through (DHAN, ZERODHA) — distinct from the venue |
| `UserBroker` | One user's setup with a broker, and the key its accounts share |
| `TradingAccount`, `BrokerCredential` | The accounts under a setup + their encrypted credentials |
| `RiskProfile`, `UserRiskLimit` | Per-subscription and per-user caps |

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
   │                      UserStrategyService,
   │                      TradingAccountService, ReferenceDataService,
   │                      CurrentUserService
   ▼
ServiceImpl ───────────── business rules, @Transactional boundary
   │
   ├── StrategyTemplateValidator ── rule tree + knob-definition structure
   ├── StrategyParamValidator ─────── submitted values: coerce, constrain, split
   ├── IndicatorResolver ──────────── reads the rule tree
   ├── CanonicalJson / ConfigHashUtil ─ content addressing
   └── JsonSupport ────────────────── jsonb text ⇄ Map
   ▼
Repository (Spring Data JPA)
   ▼
Entity  ──────────────────  Postgres (Neon)
```

Errors are mapped by `StrategyApiExceptionHandler`, a `@RestControllerAdvice`
**scoped to the six strategy-module controllers only** (via `assignableTypes`),
so the authentication controllers keep their own response shapes.

---

## 4. Data model — field by field

### `users` — control-plane identity (`User.java`)

| Field | Type | Stores |
|---|---|---|
| `id` | UUID | PK. Every strategy-side FK points here |
| `username` | String(50) | Derived from the email local part; NOT NULL UNIQUE |
| `email` | String(100) | The JWT subject. The lookup key |
| `passwordHash` | String(255) | Sentinel `EXTERNAL_AUTH:GOOGLE` — never verified |
| `status` | String(20) | `active` / `suspended` / `closed` |
| `createdAt`, `updatedAt` | OffsetDateTime | Set by `@PrePersist` / `@PreUpdate` |

### `exchanges` — venue master (`Exchange.java`)

| Field | Stores |
|---|---|
| `id` | UUID PK |
| `name`, `code` | Both UNIQUE. `code` is what the API accepts (`NSE`) |
| `status` | `active` / `disabled` |

### `symbols` — contract master (`Symbol.java`)

UNIQUE `(exchange_id, symbol)`.

| Field | Stores |
|---|---|
| `exchange` | FK → exchanges |
| `symbol` | Ticker, uppercased on lookup |
| `baseAsset`, `quoteAsset` | Leg names |
| `instrumentType` | `spot` / `future` / `option` / `index` |
| `optionType` | `CALL` / `PUT` — only when `instrumentType = option` |
| `strikePrice`, `expiryAt` | Options fields. `expiryAt` NULL = perpetual |
| `contractSize`, `tickSize`, `minQty` | Trading increments |
| `active` | Inactive symbols are refused at subscribe time |

> Convention: indicators run on the **underlying** (spot/index).
> `SharedStrategyConfig.symbol` is the *signal* symbol, not the traded contract.

### `indicators` — compute primitives (`Indicator.java`)

| Field | Stores |
|---|---|
| `id` | UUID PK |
| `name` | `EMA`, `RSI`. UNIQUE, max 50, **uppercased on save**. Matched by exact string against rule trees |
| `paramSchema` | jsonb. **The indicator's parameter definition** — `{"period":{"type":"int","min":2,"max":300}}` |
| `active` | Inactive indicators fail rule-tree validation |
| `createdAt` | No `updatedAt` — it is a catalog, deactivated rather than deleted |

`paramSchema` entry grammar: `type` is required and must be one of
`int | decimal | bool | enum | timeframe | text`; optional `min` / `max`
(numeric) and `options` (non-empty list). `default` is accepted and stored but
not validated.

### `strategy_templates` — the template (`StrategyTemplate.java`)

| Field | Stores |
|---|---|
| `id` | UUID PK |
| `name` | UNIQUE, max 100. The business key |
| `version` | Caller-bumped when the rule tree changes meaning |
| `description` | Free text |
| `system` | `true` = seeded platform strategy → **immutable through the API** (409) |
| `active` | Inactive strategies cannot be subscribed to |
| `ruleTree` | jsonb. **Where the strategy↔indicator link lives** |
| `createdAt`, `updatedAt` | Timestamps |

### `strategy_param_definitions` — the derived knob set (`StrategyParamDefinition.java`)

> **Derived, not authored.** Since the parameter catalog landed, these rows are
> generated by `StrategyParameterLinkSync` from `indicator_parameter_links` +
> `strategy_parameter_links` on every strategy save. They remain the engine's input —
> `StrategyParamValidator` validates against them and the config hash is computed
> from them — but the catalog is where parameters are authored.


UNIQUE `(strategy_id, parameter_key)`. PK is `bigserial`, not uuid — it is off
the hot path.

| Field | Stores |
|---|---|
| `strategy` | FK → strategy_templates |
| `parameterKey` | Strategy-local name: `fast`, `slow`, `sl_pct`. `fast` in two strategies does not collide |
| `dataType` | `int` / `decimal` / `bool` / `enum` / `timeframe` / `text` |
| `scope` | **`signal`** (hashed, shared) or **`execution`** (personal, never hashed) |
| `defaultValue` | Text; applied when the user omits the key |
| `validation` | jsonb — `{"min":2,"max":200}`, `{"options":[…]}`, `{"gt":"fast"}` |
| `displayLabel`, `displayOrder` | Form rendering hints |
| `required` | Missing + no default → 400 |

### `parameters` — the catalog (`Parameter.java`)

One row per distinct knob the platform knows about, with a stable id.

| Field | Stores |
|---|---|
| `id` | bigserial PK — the same value wherever the parameter appears |
| `code` | UNIQUE business key and wire name: `k`, `sl`, `candle_duration` |
| `name` | Display label: `K`, `SL`, `Candle Duration` |
| `dataType` | `int` / `decimal` / `bool` / `enum` / `timeframe` / `text` |
| `scope` | `signal` or `execution` — a property of the parameter, not of a usage |
| `defaultValue`, `validation` | Canonical values, narrowable per usage |
| `description` | Free text for the form |
| `universal` | Auto-attached to every strategy (SL, TP, quantity, the durations) |
| `system` | Platform-supplied, protected from edit |

### `indicator_parameter_links` — indicator ↔ parameter (`IndicatorParameterLink.java`)

UNIQUE `(indicator_id, parameter_id)`. Carries optional `default_value` /
`validation` overrides, which is what lets EMA and RSI share one `period` catalog
row while declaring different maxima. Plus `display_order` and `is_required`.

### `strategy_parameter_links` — strategy ↔ parameter (`StrategyParameterLink.java`)

UNIQUE `(strategy_id, parameter_id)`, same override columns. Rows for
`universal` parameters are written automatically by `StrategyParameterLinkSync`, so
every strategy carries SL/TP/quantity without anyone linking them by hand — and
they are still real rows, so the hierarchy is uniform.

### `shared_strategy_configs` — the dedup unit (`SharedStrategyConfig.java`)

UNIQUE `(strategy_id, symbol_id, timeframe, config_hash)`.

| Field | Stores |
|---|---|
| `id` | UUID PK |
| `strategy` | FK → strategy_templates |
| `symbol` | FK → symbols — the **signal** symbol |
| `timeframe` | `5m`, `15m`, `1h` … |
| `signalParams` | jsonb, canonicalized, **signal scope only**: `{"fast": 9, "slow": 21}` |
| `configHash` | sha256 of the four identity fields. The content address |
| `supersedes` | FK → the instance this one replaced (version lineage) |
| `status` | `active` / `retired` |
| `createdAt` | Immutable row — a param change never updates it |

**This row is never mutated on a parameter change.** A new instance is inserted,
the subscription is repointed, `supersedes` records the lineage, and the orphan
is retired once its last active subscriber leaves.

### `user_strategy_subscriptions` — the personal leg (`StrategySubscription.java`)

UNIQUE `(shared_config_id, trading_account_id)` — the same math on the same
account is one leg, so a repeat is an update, not a second row.

| Field | Stores |
|---|---|
| `id` | UUID PK |
| `user` | FK → users. **Every read and write is filtered by this** |
| `strategyInstance` | FK → the shared config |
| `tradingAccount` | FK → trading_accounts |
| `riskProfile` | FK → risk_profiles, nullable |
| `quantity` | Order size. Default 1 |
| `multiplier` | Size scaler. Default 1 |
| `lotSize`, `capitalAllocated` | Optional sizing inputs |
| `executionMode` | `FIXED_QTY` / `CAPITAL_PERCENT` / `RISK_PERCENT` |
| `execParams` | jsonb — **execution-scope params**, e.g. `{"sl_pct":1.5,"tp_pct":3.0}`. Never hashed |
| `tradeMode` | `paper` / `live` |
| `active` | Pause without losing configuration |
| `version` | Bumped on every repoint. Plain data, **not** a JPA optimistic lock |
| `createdAt`, `updatedAt` | Timestamps |

### Supporting tables

| Entity → table | Stores |
|---|---|
| `UserBroker` → `user_brokers` | user + catalog broker + `label`, UNIQUE together. The parent of every account reached through it |
| `TradingAccount` → `trading_accounts` | Belongs to a `UserBroker`; `accountName` UNIQUE within it. `brokerAccountId` is the broker's own id. **No exchange** — the symbol decides the venue |
| `Broker` → `brokers` | `code` unique. `authType` says which credential fields that broker needs |
| `BrokerCredential` → `broker_credentials` | Two levels: `trading_account_id` NULL is the setup's key, set is one account's override. Resolution is **per field**. Secrets are AES-GCM ciphertext via `SecretCipher` |
| `RiskProfile` → `risk_profiles` | Reusable per-subscription caps: `maxDailyLoss`, `maxDrawdown`, `maxPositionSize`, `maxTotalExposure`, `maxTradesPerDay`, `killSwitchEnabled` |
| `UserRiskLimit` → `user_risk_limits` | **PK is `user_id`** — one row per user. Aggregate caps: `maxDailyLoss`, `maxOpenPositions`, `maxTotalExposure`. Exists because ten subscriptions would otherwise mean ten independent daily-loss limits and no total |
| `GoogleAuthToken` → `google_auth_tokens` | Auth only: `email`, `accessToken`, `refreshToken`, `panCard`, `revoked`, `createdAt` |
| `StrategyConfig` → `platform_strategy_toggles` | Legacy: `strategyName` (uppercased, UNIQUE), `enabled`, `configJson`, `updatedAt` |

---

## 5. The strategy ↔ indicator ↔ parameter hierarchy

Every edge is a foreign key. Nothing in the hierarchy is a name string.

```
                        ┌─────────────────────┐
                        │  strategy_templates │
                        └──────────┬──────────┘
                                   │
            ┌──────────────────────┴──────────────────────┐
            │                                             │
┌───────────▼────────────────┐          ┌─────────────────▼────────────┐
│ strategy_template_         │          │ strategy_template_           │
│            indicators      │          │            parameters        │
│ strategy_id ─┐             │          │ strategy_id ─┐               │
│ indicator_id─┼───┐         │          │ parameter_id─┼───┐           │
└──────────────┘   │         │          └──────────────┘   │           │
                   │         │                             │           │
        ┌──────────▼───┴──┐  │                             │           │
        │ indicators  │  │                             │           │
        └────────┬────────┘  │                             │           │
                 │           │                             │           │
   ┌─────────────▼──────────┐│                             │           │
   │ indicator_parameter_links   ││                             │           │
   │ indicator_id ─┘        ││                             │           │
   │ parameter_id ──────────┼┼──────────────────────┐      │           │
   └────────────────────────┘│                      │      │           │
                             │                 ┌────▼──────▼───────────▼─┐
                             └────────────────►│      parameters         │
                                               │      (catalog)          │
                                               └─────────────────────────┘
```

Seeded shape:

```
EMA Crossover (strategy)
│
├── EMA CROSSOVER (indicator)      via strategy_indicator_links
│     ├── K                        via indicator_parameter_links
│     └── D
│
├── SL              universal      via strategy_parameter_links
├── TP              universal
├── Quantity        universal
├── Candle Duration universal
└── Trigger Duration universal
```

**Ownership is explicit.** A parameter reached through `indicator_parameter_links`
configures a computation and is signal scope. One reached through
`strategy_parameter_links` configures execution. That distinction is a property of the
catalog row (`parameters.scope`), so it holds wherever the parameter appears.

The `rule_tree` still exists and still binds `$code` placeholders onto indicator
inputs — it is *how* the strategy wires its indicator up, and it is what
`strategy_indicator_links` is derived from. But it is no longer how anything is
*discovered*: the frontend reads the link tables.

```
strategy_templates.rule_tree
{"entry":{"ind":"EMA CROSSOVER","params":{"k":"$k","d":"$d"}}}
             │                              │
   indexed into                  resolved against the knob set
   strategy_indicator_links           derived from the catalog
```

### Three things `IndicatorResolver` derives

| Method | Returns | Used for |
|---|---|---|
| `indicatorNames(tree)` | `["EMA"]` — distinct `"ind"` values | Validating a rule tree at save time; the `indicators` response field |
| `bindings(tree)` | `["fast","slow"]` — `$key` placeholders, `$` stripped | Checking the knob set covers the tree |
| `resolve(tree, params)` | `["EMA(period=9)","EMA(period=21)"]` | The actual computations — **what dedup counts** |

`resolve` substitutes `$fast` → `9` and hands each node to
`IndicatorFingerprint.of()`, which sorts keys and normalizes numbers so `9`,
`9.0` and `"9"` all render as `9`. Because fingerprints come from **resolved**
params, dedup works *across* strategies: EMA(9) requested by two different
strategies is one computation.

### Where the missing FK is compensated for

| When | Check | Failure |
|---|---|---|
| Strategy create/update | Every `"ind"` name exists and is `active` | 400 `references unknown indicator 'EMAA'` |
| Strategy create/update | Every param an `ind` node passes exists in that indicator's `paramSchema` | 400 |
| Strategy create/update | Every `$binding` has a matching knob | 400 `binds $foo but the strategy defines no parameter 'foo'` |
| Indicator rename | Refused while any rule tree mentions the old name | 409 |
| Indicator schema narrowing | Refused if a live tree still passes the dropped key | 400 |
| Indicator delete | Refused while referenced | 409 → deactivate instead |
| Knob delete | Refused while the tree binds `$key` | 409 |

### 5.1 The derived index — `strategy_indicator_links`

A join table with real FKs, **rebuilt from the rule tree on every strategy save**
by `StrategyIndicatorLinkSync`, and rebuilt for every strategy at startup by
`ControlPlaneSeeder` so seeded rows and direct SQL inserts are covered.

| Field | Stores |
|---|---|
| `id` | UUID PK |
| `strategy` | FK → strategy_templates |
| `indicator` | FK → indicators |
| `createdAt` | Timestamp |

UNIQUE `(strategy_id, indicator_id)`. It records **which** indicators a strategy
depends on, never at which parameterization — EMA(9) and EMA(21) in one tree are
two computations but **one** dependency row. The concrete computations stay where
they belong, resolved per instance from the tree's `$bindings`.

Rows are reconciled key by key (delete what left, insert what arrived) rather
than deleted and re-inserted, so a strategy that keeps using EMA keeps the same
mapping row and no delete/insert pair can trip the unique constraint inside one
transaction. An indicator name the catalog does not have cannot be mapped — there
is no row to point a FK at — so it is skipped and surfaces as
`unknownIndicators` instead of failing the save.

Link rows are addressed by the pair they join — `(strategy_id, indicator_id)`,
`(indicator_id, parameter_id)`, `(strategy_id, parameter_id)` — each of which is
UNIQUE. The link row's own id is never exposed in a response: it would be a second
way to name something the pair already names unambiguously.

Guards
(`usedByStrategies`, the delete and rename refusals in
`IndicatorCatalogServiceImpl`) still scan rule trees directly — unchanged, and
correct even if the index were ever stale.

---

## 6. Flows

### 6.1 Authentication

```
GET /api/v1/auth/google
      → redirect to Google consent (scope: gmail.readonly + userinfo.email)

GET /api/v1/auth/callback?code=…&state=…
      → exchange code for Google tokens
      → fetch email from googleapis userinfo
      → GoogleAuthTokenService.saveOrUpdateToken(email, access, refresh)
      → set cookie refresh_token  (httpOnly, Secure, SameSite=None, 7 days)
      → redirect to <frontend>/create-plan

GET /api/v1/auth/me           reads the cookie
      → {email, accessToken, hasPanCard}      accessToken lives 30 minutes

POST /api/v1/auth/refresh     reads the cookie → {accessToken}
POST /api/v1/auth/logout      sets google_auth_tokens.revoked = true, clears cookie
```

Every other endpoint expects `Authorization: Bearer <accessToken>`.
`JwtFilter` rejects a refresh token used as a Bearer with
`Wrong token type: refresh`.

### 6.2 Create an indicator — `POST /api/v1/indicators`

```
1. name → trim + UPPERCASE          "EMA Crossover" becomes "EMA CROSSOVER"
2. name required, ≤ 50 chars
3. validateParamSchema: ≥ 1 entry; each entry an object with a legal `type`;
   min/max numeric and min ≤ max; options a non-empty list
4. existsByName → 409
5. INSERT; active defaults to true
6. response includes usedByStrategies (computed by scanning strategies)
```

### 6.3 Create a strategy — `POST /api/v1/strategy-templates`

```
1. name required, ≤ 100 chars; version ≥ 1 if supplied
2. validateParamSet — duplicate parameterKey → 400;
   each knob: legal dataType, legal scope, enum needs options,
   gt/lt must reference a sibling key
3. validateRuleTree against the indicator catalog
   (bindings checked only if params were supplied — a strategy may be
    created before its knobs)
4. existsByName → 409
5. INSERT strategy with is_system = FALSE  ← API-authored rows are never system
6. INSERT each strategy_param_def
```

`PUT` additionally calls `requireEditable()`, which rejects `system = true` rows
with 409. Supplying `params` **replaces** the knob set — key by key, so ids stay
stable and a partial failure cannot leave a strategy with zero parameters.

`DELETE` is refused while any `shared_strategy_configs` row references the strategy.

### 6.4 Subscribe — `POST /api/v1/my-subscriptions` (the main flow)

```
 email ─→ CurrentUserService.require()  ──────────→ users row (lazily provisioned)

 resolveStrategy()     strategyId, or strategyName          → 404 / must be active
 resolveSymbol()       symbolId, or symbol + exchangeCode   → 404 / must be active
 requireOwnedAccount() findByIdAndUser_Id                   → 404 if not yours
 resolveRiskProfile()  optional

 normalizeTimeframe()  must match ^[0-9]{1,4}[smhdw]$  → "5m"
 normalizeTradeMode()  paper | live          (default paper)
 normalizeExecutionMode() FIXED_QTY | CAPITAL_PERCENT | RISK_PERCENT

        │
        ▼
 StrategyParamValidator.validate(defs, submittedParams)
        │  pass 1 ─ unknown key → error
        │           missing + default → fill;  missing + required → error
        │           coerce by dataType (int → longValueExact, decimal → BigDecimal…)
        │  pass 2 ─ options / min / max / gt / lt   (cross-field needs pass 1 done)
        │  split  ─ by strategy_param_definitions.scope, into two sorted TreeMaps
        ▼
   signal {fast:9, slow:21}          execution {sl_pct:1.5, tp_pct:3.0}
        │                                        │
        │  assertRuleTreeResolves()              │
        │  (catches a $key with no signal param) │
        ▼                                        │
 SharedStrategyConfigService.resolveOrCreate()   │
        │                                        │
        │  canonical = CanonicalJson(signal)     │
        │  hash = sha256(strategyId|symbolId|timeframe|canonical)
        │  lookup by (strategy, symbol, timeframe, hash)
        │      found  → revive if retired, reuse   ← SHARING HAPPENS HERE
        │      absent → INSERT new instance
        ▼                                        │
   shared_strategy_configs row  ◄─────────────────────┘
        │
        ▼
 UNIQUE(instance, account) check → already active? 409 with the existing id
        │
        ▼
 INSERT subscriptions row  (quantity, multiplier, execParams, tradeMode, …)
```

### 6.5 Update a subscription — `PUT /api/v1/my-subscriptions/{id}`

Partial. Submitted keys are **merged over the current effective config**, so
`{"params":{"fast":13}}` is valid and leaves every other knob untouched.

```
merged = instance.signalParams + subscription.execParams + request.params
       ↓ validate + split again
       ↓ resolveOrCreate  → target instance

target == current ?
   yes → execution-only change. Just save. No repoint.
   no  → REPOINT:
           • conflict check: is another subscription already on
             (target, thisAccount)?  → 409
           • if the target was newly created, set supersedes = current
           • subscription.strategyInstance = target
           • subscription.version += 1
           • save, then retireIfOrphaned(current)

active flag flipped ?
   true  → reviveIfRetired(instance)
   false → retireIfOrphaned(instance)
```

### 6.6 Unsubscribe — `DELETE /api/v1/my-subscriptions/{id}`

Delete the row, `flush()`, then `retireIfOrphaned(instanceId)`. An instance is
retired — never deleted — when `countBySharedStrategyConfig_IdAndActiveTrue` hits 0,
so lineage and history survive.

### 6.7 The dedup report — `GET /api/v1/shared-strategy-configs/indicator-plan`

Walks every active instance, sums active subscribers, and unions their resolved
fingerprints into a `TreeSet`. The design's acceptance gate: three users on 9x21,
9x50 and 13x21 must report **3 subscriptions, 3 instances, 4 indicators** — not 6.

```json
{ "activeStrategySubscriptions": 3, "distinctInstances": 3, "distinctIndicators": 4,
  "indicators": ["EMA(period=13)","EMA(period=21)","EMA(period=50)","EMA(period=9)"] }
```

---

## 7. Cross-cutting machinery

### `JsonSupport` — jsonb ⇄ Map

Five jsonb columns are held as `String` on the entity side (`rule_tree`,
`param_schema`, `validation`, `signal_params`, `exec_params`) and handed to
callers as `Map`. `toMap()` and `readTree()` return an **empty map / null on
malformed input rather than throwing**, so bad stored data degrades a GET instead
of failing it.

### `CanonicalJson` — byte-exact serialization

Must reproduce what Postgres produces for `canonical_jsonb(params)::text`,
because the schema's `compute_config_hash()` hashes exactly that string.

- object keys sorted **by length first, then byte value** (jsonb's own ordering,
  *not* plain lexicographic)
- array order preserved
- numeric scale trimmed — `9`, `9.0`, `9.00` all render `9`
- **jsonb spacing**: `{"fast": 9, "slow": 21}` — with the spaces, not compact

If this drifts from the database function, dedup splits silently and two users
pay for the same EMA twice.

### `ConfigHashUtil` — content addressing

```
sha256( strategy_id | symbol_id | timeframe | canonical_jsonb(signal_params) )
```

Execution params are deliberately excluded — that exclusion is exactly what lets
two users with different stop losses share one computation.

### Two validators, two jobs

| Class | Validates | When |
|---|---|---|
| `StrategyTemplateValidator` | **Structure** — rule trees, knob *definitions*, indicator *schemas* | Author-time (create/update a strategy or indicator) |
| `StrategyParamValidator` | **Values** — a submitted param map against the knob definitions; coerces, defaults, splits by scope | Subscribe-time |

Nothing in either is EMA-specific. An RSI strategy validates through the same
code path the day its rows are inserted.

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
instance creation (the dedup UNIQUE). Catching would not help — a failed flush
has already marked the transaction rollback-only, so "recovering" would produce a
confusing failure at commit. The caller gets a 409 and the retry succeeds.

### Seeding (`ControlPlaneSeeder`)

An `ApplicationRunner` that runs on every boot and is idempotent — it keys on
unique business columns (`parameters.code`, `indicators.name`,
`strategy_templates.name`, and the two link-table unique constraints):

1. **Catalog** — `k`, `d`, `period` (signal); `sl`, `tp`, `quantity`,
   `candle_duration`, `trigger_duration` (execution, all `universal`)
2. **Indicators** — `EMA CROSSOVER` (→ K, D), `EMA` (→ period, 2–300),
   `RSI` (→ period, 2–100)
3. **Strategy** — `EMA Crossover`, `system = true`, rule tree
   `{"entry":{"ind":"EMA CROSSOVER","params":{"k":"$k","d":"$d"}}}`
4. `indicatorSync.syncAll()` then `parameterSync.syncAll()` — index the rule
   trees, attach universal parameters, derive `strategy_param_definitions`

The seeder also **converges** a pre-catalog EMA Crossover onto the new rule tree —
but only when the strategy has zero instances, since rewriting the tree under
existing instances would strand signal params hashed against the old knob set. If
instances exist it logs a WARN and leaves the row alone.

---

## 8. Endpoint reference

All strategy-module endpoints require `Authorization: Bearer <accessToken>`.

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/indicators?active=` | List |
| GET | `/api/v1/indicators/{id}` | |
| GET | `/api/v1/indicators/by-name/{name}` | Name is uppercased before lookup |
| POST | `/api/v1/indicators` | 201 |
| PUT | `/api/v1/indicators/{id}` | Rename blocked while referenced |
| DELETE | `/api/v1/indicators/{id}` | 409 while referenced |
| GET | `/api/v1/parameters?scope=` | The catalog |
| GET | `/api/v1/parameters/{id}`, `/by-code/{code}` | One parameter |
| GET | `/api/v1/parameters/{id}/usage` | Every indicator and strategy using it |
| GET | `/api/v1/indicators/{id}/parameters` | An indicator's parameters, by id |
| GET | `/api/v1/strategy-templates/{id}/parameters` | A strategy's own parameters, by id |
| GET | `/api/v1/strategy-templates?active=&search=` | Returns `StrategyTemplateDetailResponse` — the full hierarchy |
| GET | `/api/v1/strategy-templates/{id}`, `/by-name/{name}` | |
| POST | `/api/v1/strategy-templates` | 201, `is_system` forced false |
| PUT / DELETE | `/api/v1/strategy-templates/{id}` | 409 on system rows |
| GET/POST | `/api/v1/strategy-templates/{id}/params` | Knob CRUD |
| PUT/DELETE | `/api/v1/strategy-templates/{id}/params/{paramId}` | |
| GET | `/api/v1/shared-strategy-configs?status=` | |
| GET | `/api/v1/shared-strategy-configs/{id}` | |
| GET | `/api/v1/shared-strategy-configs/indicator-plan` | Dedup report |
| GET/POST | `/api/v1/my-strategies` | A user's customized strategies, ownership-filtered |
| GET/PUT/DELETE | `/api/v1/my-strategies/{id}` | UI shape: defaults and overrides side by side. Another user's row reports **404, not 403** |
| GET | `/api/v1/my-strategies/{id}/runtime` | Bot shape: effective values, signal/execution split |
| POST | `/api/v1/my-strategies/{id}/subscribe` | 201, projects the effective values onto a subscription |
| GET/POST | `/api/v1/my-subscriptions` | Ownership-filtered |
| GET/POST/PUT/DELETE | `/api/v1/strategy-toggles/...` | Legacy platform switches, unrelated to templates |
| GET/PUT/DELETE | `/api/v1/my-subscriptions/{id}` | Another user's row reports **404, not 403** |
| GET/POST | `/api/v1/trading-accounts` | |
| GET/POST | `/api/v1/my-brokers` | The caller's broker setups — step one |
| POST | `/api/v1/my-brokers/setup` | Wizard: setup + first account + key in one transaction |
| GET/PUT/DELETE | `/api/v1/my-brokers/{id}` | 409 on delete while accounts hang off it |
| GET/PUT/DELETE | `/api/v1/my-brokers/{id}/credentials` | The key every account under it inherits |
| GET/PUT/DELETE | `/api/v1/trading-accounts/{id}` | An account cannot move between setups |
| GET/PUT/DELETE | `/api/v1/trading-accounts/{id}/credentials` | One account's override; masked on read, PUT is partial, `""` clears a field |
| GET | `/api/v1/exchanges`, `/symbols`, `/risk-profiles` | Reference data, read-only |
| GET/POST/PUT/DELETE | `/api/v1/brokers` | The broker catalog. **Shared master data** — writes are not role-gated yet |
| GET/PUT | `/api/v1/me/risk-limits` | Aggregate caps |

Ownership is enforced *inside the query* (`findByIdAndUser_Id`), which is why a
foreign resource is a 404 rather than a 403 — the API does not confirm it exists.

---

## 9. Known gaps

1. **`users.id` type collision.** The deployed database has a `users` table whose
   `id` is a `bigint` identity column, but `User.java` declares `UUID`.
   `ddl-auto=update` cannot convert it, so the ALTER fails at boot and the FKs
   `user_strategy_subscriptions.user_id` and `trading_accounts.user_id` are never created.
   Writes that provision a user row will fail until the table is rebuilt.

2. **`ddl-auto=update` hides failed migrations.** Schema drift surfaces as a WARN
   in the startup log, never an error. Worth replacing with Flyway/Liquibase.

3. **The `trg_shared_configs_hash` trigger does not exist under `ddl-auto`.** The code
   documents Postgres as "the referee" for `config_hash`, but the schema SQL that
   creates `canonical_jsonb()`, `compute_config_hash()` and the trigger is not in
   this repository. In practice the application-computed hash is the only one in
   play — which makes `CanonicalJson`'s byte-exactness untested rather than
   enforced.

4. **`JwtUtil.SECRET` is a static field read from `System.getenv("JWT_SECRET")`.**
   Missing env var → NPE during class initialization, not a readable startup
   error.

5. **`/api/v1/**` is `permitAll` in `SecurityConfig`.** Authentication for those
   routes is enforced by `SecuredController.currentEmail()` in the controller
   layer instead. It works, but a new controller under `/api/v1` that forgets to
   extend `SecuredController` is silently public.

6. **Two DTOs named alike.** `StrategyTemplateDetailResponse` (used by `/api/v1/strategy-templates`,
   plural) versus `StrategyResponse` (used by `/api/v1/strategy`, singular, the
   legacy `platform_strategy_toggles` controller). Only the former has `indicators`.
