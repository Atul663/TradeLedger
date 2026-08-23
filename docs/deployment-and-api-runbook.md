# Deployment & API Runbook

Deploy the refactored build, then create everything from scratch — reference
data, brokers, templates, strategies, deployments — with the exact payload and
response for every call.

- [0. Read this before deploying](#0-read-this-before-deploying)
- [1. Deploy](#1-deploy)
- [2. Seed reference data (SQL — no write API)](#2-seed-reference-data-sql--no-write-api)
- [3. Get a token](#3-get-a-token)
- [4. Broker catalog](#4-broker-catalog)
- [5. Broker setup and trading accounts](#5-broker-setup-and-trading-accounts)
- [6. Indicators](#6-indicators)
- [7. Strategy templates](#7-strategy-templates)
- [8. User strategies](#8-user-strategies)
- [9. Deploy a strategy to brokers](#9-deploy-a-strategy-to-brokers)
- [10. Managing deployments](#10-managing-deployments)
- [11. Verify the dedup](#11-verify-the-dedup)
- [12. Error catalogue](#12-error-catalogue)
- [13. End-to-end script](#13-end-to-end-script)

Conventions: `{{TOKEN}}` is the JWT, `{{BASE}}` is e.g. `http://localhost:8081`.
Every id shown is illustrative. All responses are `application/json`.

---

## 0. Read this before deploying

### ⚠️ The old strategy tables must be dropped

`spring.jpa.hibernate.ddl-auto=update` **only adds**. It never drops a column and
never relaxes a `NOT NULL`. The refactor removed columns that are still `NOT NULL`
in a database created by the previous build, and the new code does not populate
them — so **every insert into `user_strategy_subscriptions` would fail** on
leftovers like `shared_config_id`, `exec_params`, `quantity` and `version`.

Adding the new `NOT NULL` columns to a non-empty `user_strategies` would fail too.

You confirmed there is no real data, so drop the strategy module and let Hibernate
rebuild it. **This does not touch users, brokers, trading accounts, credentials or
auth.**

```sql
-- Run ONCE, before the new build starts. Order does not matter with CASCADE.
DROP TABLE IF EXISTS
    user_strategy_parameters,
    user_strategy_legs,
    user_strategy_indicators,
    user_strategy_subscriptions,
    user_strategies,
    shared_strategy_configs,
    strategy_param_definitions,
    strategy_parameter_links,
    strategy_indicator_links,
    indicator_parameter_links,
    parameters,
    strategy_templates,
    indicators
CASCADE;
```

Preserved: `users`, `exchanges`, `symbols`, `brokers`, `user_brokers`,
`trading_accounts`, `broker_credentials`, `risk_profiles`, `user_risk_limits`,
`google_auth_tokens`, `platform_strategy_toggles`.

Verify afterwards that nothing survived:

```sql
SELECT tablename FROM pg_tables
WHERE schemaname = 'public'
  AND tablename IN ('parameters','indicator_parameter_links','strategy_parameter_links',
                    'strategy_param_definitions','user_strategy_parameters',
                    'strategy_indicator_links','user_strategy_legs');
-- expect 0 rows
```

### Environment variables

| Variable | Required | Effect if missing |
|---|---|---|
| `DB_URL` | **yes** | `Unable to determine Dialect` at boot |
| `DB_USER`, `DB_PASSWORD` | **yes** | connection refused |
| `JWT_SECRET` | **yes** | **NPE during class init of `JwtUtil`** — an unreadable startup crash, not a clear message |
| `TOKEN_SECRET` | **yes** | same class, same failure mode |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | yes for login | OAuth flow fails |
| `CREDENTIAL_ENCRYPTION_KEY` | for broker credentials | app still starts; every credential read/write fails. Generate with `openssl rand -base64 32` |
| `PORT` | no | defaults to `8081` |
| `JPA_SHOW_SQL` | no | defaults to `false`; set `true` to watch the DDL |

---

## 1. Deploy

### Local

```bash
export DB_URL='jdbc:postgresql://<host>/<db>?sslmode=require'
export DB_USER='...' DB_PASSWORD='...'
export JWT_SECRET='...' TOKEN_SECRET='...'
export GOOGLE_CLIENT_ID='...' GOOGLE_CLIENT_SECRET='...'
export CREDENTIAL_ENCRYPTION_KEY="$(openssl rand -base64 32)"

./mvnw clean package -DskipTests
java -jar target/tradeLedger-0.0.1-SNAPSHOT.jar
```

### Docker Compose

`docker-compose.yml` passes all of the above through from your shell or a `.env`
file beside it. Note the Dockerfile says `EXPOSE 8080` while the app listens on
`PORT` (default **8081**) — the compose mapping `8081:8081` is what actually
matters; `EXPOSE` is documentation only.

```bash
docker compose build --no-cache      # --no-cache: the Dockerfile COPYs then builds
docker compose up -d
docker compose logs -f app
```

### Confirm the boot

Look for the seeder lines. On a database where you just dropped the tables:

```
Seeded indicator EMA CROSSOVER id=…
Seeded indicator EMA AVERAGING id=…
Seeded indicator EMA id=…
Seeded indicator RSI id=…
Seeded template EMA Crossover id=…
Seeded template EMA Averaging id=…
```

On later boots those go quiet and you may see `Converged indicator …` if a schema
changed. Then check the schema really landed — this is the thing `ddl-auto`
would otherwise fail at silently:

```sql
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'user_strategies'
ORDER BY ordinal_position;
-- expect: derivative, ce_enabled, ce_moneyness, ce_strike_offset,
--         pe_enabled, pe_moneyness, pe_strike_offset,
--         lot_rule, base_lot, averaging_count, candle_duration,
--         trigger_duration, sl_pct, tp_pct, shared_config_id

SELECT conname FROM pg_constraint
WHERE conrelid = 'user_strategies'::regclass AND contype = 'c';
-- expect: ck_user_strategies_ce_strike, ck_user_strategies_pe_strike,
--         ck_user_strategies_sizing
```

If the CHECK constraints are absent, `ddl-auto=update` skipped them on a
pre-existing table — drop `user_strategies` and restart.

Swagger UI: `{{BASE}}/swagger-ui.html`.

---

## 2. Seed reference data (SQL — no write API)

`exchanges`, `symbols` and `risk_profiles` are **read-only over HTTP**
(`GET` only). A strategy cannot pick an underlying that does not exist, so seed
them first.

> Lot sizes below are placeholders. Set `contract_size` / `min_qty` from the
> current exchange contract specification — they change.

```sql
-- Exchanges
INSERT INTO exchanges (id, name, code, description, status, created_at, updated_at)
VALUES (gen_random_uuid(), 'National Stock Exchange of India', 'NSE',
        'Indian equity and derivatives', 'active', now(), now()),
       (gen_random_uuid(), 'BSE Limited', 'BSE',
        'Indian equity and derivatives', 'active', now(), now())
ON CONFLICT (code) DO NOTHING;

-- Index underlyings. instrument_type 'index' is the sheet's INDEX cell;
-- use 'spot' for a stock underlying.
INSERT INTO symbols (id, exchange_id, symbol, base_asset, quote_asset,
                     instrument_type, contract_size, tick_size, min_qty,
                     is_active, created_at, updated_at)
SELECT gen_random_uuid(), e.id, v.sym, v.sym, 'INR',
       'index', v.lot, 0.05, v.lot, true, now(), now()
FROM exchanges e
CROSS JOIN (VALUES ('NIFTY', 75), ('BANKNIFTY', 35), ('FINNIFTY', 65)) AS v(sym, lot)
WHERE e.code = 'NSE'
ON CONFLICT (exchange_id, symbol) DO NOTHING;

INSERT INTO symbols (id, exchange_id, symbol, base_asset, quote_asset,
                     instrument_type, contract_size, tick_size, min_qty,
                     is_active, created_at, updated_at)
SELECT gen_random_uuid(), e.id, 'SENSEX', 'SENSEX', 'INR',
       'index', 20, 0.05, 20, true, now(), now()
FROM exchanges e WHERE e.code = 'BSE'
ON CONFLICT (exchange_id, symbol) DO NOTHING;

-- Risk profiles (name is NOT unique, so run this once)
INSERT INTO risk_profiles (id, name, description, max_daily_loss, max_drawdown,
                           max_position_size, max_total_exposure, max_trades_per_day,
                           kill_switch_enabled, created_at, updated_at)
VALUES (gen_random_uuid(), 'Conservative', 'Tight daily stop',
        5000, 10000, 100000, 500000, 10, true, now(), now()),
       (gen_random_uuid(), 'Aggressive', 'Wider limits',
        25000, 50000, 500000, 2500000, 40, true, now(), now());
```

### Read them back

```http
GET {{BASE}}/api/v1/exchanges
GET {{BASE}}/api/v1/symbols?activeOnly=true
GET {{BASE}}/api/v1/symbols?exchangeId={{exchangeId}}&activeOnly=true
GET {{BASE}}/api/v1/risk-profiles
Authorization: Bearer {{TOKEN}}
```

**`GET /api/v1/symbols` → 200**

```json
[ { "id": "1a2b3c4d-0000-0000-0000-000000000001",
    "exchangeId": "9e8d7c6b-0000-0000-0000-0000000000a1",
    "exchangeCode": "NSE",
    "symbol": "NIFTY",
    "baseAsset": "NIFTY", "quoteAsset": "INR",
    "instrumentType": "index",
    "optionType": null, "strikePrice": null, "expiryAt": null,
    "contractSize": 75.00000000, "tickSize": 0.05000000, "minQty": 75.00000000,
    "active": true } ]
```

**`GET /api/v1/exchanges` → 200**

```json
[ { "id": "9e8d7c6b-0000-0000-0000-0000000000a1",
    "name": "National Stock Exchange of India", "code": "NSE",
    "description": "Indian equity and derivatives", "status": "active" } ]
```

**`GET /api/v1/risk-profiles` → 200**

```json
[ { "id": "4f5e6d7c-0000-0000-0000-0000000000b1",
    "name": "Conservative", "description": "Tight daily stop",
    "maxDailyLoss": 5000.00000000, "maxDrawdown": 10000.00000000,
    "maxPositionSize": 100000.00000000, "maxTotalExposure": 500000.00000000,
    "maxTradesPerDay": 10, "killSwitchEnabled": true } ]
```

---

## 3. Get a token

Browser: `{{BASE}}/api/v1/auth/google` → Google consent → redirected to the
frontend with an `httpOnly` `refresh_token` cookie set.

```http
GET {{BASE}}/api/v1/auth/me
Cookie: refresh_token=<the cookie>
```

**200**

```json
{ "email": "cam@proweltconsulting.com",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9…",
  "hasPanCard": false }
```

`accessToken` lives 30 minutes. Refresh with `POST /api/v1/auth/refresh`
(same cookie) → `{"accessToken":"…"}`. Every call below sends
`Authorization: Bearer {{TOKEN}}`.

> Your control-plane `users` row is created automatically on the first
> authenticated call to any `/api/v1` endpoint below. There is no signup step.

---

## 4. Broker catalog

Shared master data — who orders route through. **Not** per-user.

```http
POST {{BASE}}/api/v1/brokers
Authorization: Bearer {{TOKEN}}
Content-Type: application/json

{ "code": "DHAN",
  "name": "Dhan",
  "description": "Dhan HQ trading API",
  "apiBaseUrl": "https://api.dhan.co",
  "authType": "api_key",
  "active": true }
```

`authType` ∈ `api_key` · `oauth_redirect` · `totp`. It decides which credential
fields that broker needs.

**201**

```json
{ "id": "b1000000-0000-0000-0000-000000000001",
  "code": "DHAN", "name": "Dhan",
  "description": "Dhan HQ trading API",
  "apiBaseUrl": "https://api.dhan.co",
  "authType": "api_key", "active": true }
```

Several at once:

```http
POST {{BASE}}/api/v1/brokers/bulk
[ { "code": "DHAN",    "name": "Dhan",    "authType": "api_key" },
  { "code": "ZERODHA", "name": "Zerodha", "authType": "oauth_redirect",
    "apiBaseUrl": "https://api.kite.trade" },
  { "code": "ANGELONE","name": "Angel One","authType": "totp" } ]
```

**201** — an array of `BrokerResponse`.

Other calls: `GET /api/v1/brokers?activeOnly=true`, `GET /api/v1/brokers/{id}`,
`GET /api/v1/brokers/by-code/DHAN`, `PUT /api/v1/brokers/{id}`,
`DELETE /api/v1/brokers/{id}` (409 while any user setup points at it).

---

## 5. Broker setup and trading accounts

### 5.1 The one-call wizard (recommended)

Creates the setup, its first account and its API key in **one transaction**.

```http
POST {{BASE}}/api/v1/my-brokers/setup
Authorization: Bearer {{TOKEN}}

{ "brokerCode": "DHAN",
  "label": "My Dhan",
  "active": true,
  "account": { "accountName": "main",
               "brokerAccountId": "1100112233",
               "active": true },
  "credentials": { "apiKey": "dhan-api-key-xxxx",
                   "apiSecret": "dhan-api-secret-yyyy",
                   "clientId": "1100112233" },
  "credentialsScope": "SETUP" }
```

`brokerId` may be sent instead of `brokerCode`. `credentialsScope` is `SETUP`
(the default — the key every account under the setup inherits, so a second
account needs no key at all) or `ACCOUNT` (an override for this account alone).
All `credentials` fields are optional; send what the `authType` needs:

| authType | Typical fields |
|---|---|
| `api_key` | `apiKey`, `apiSecret`, `clientId` |
| `oauth_redirect` | `apiKey`, `apiSecret`, `redirectUrl`, `accessToken`, `refreshToken`, `tokenExpiresAt` |
| `totp` | `apiKey`, `clientId`, `totpSecret` |

Also accepted: `vaultRef` (external secret pointer).

**201**

```json
{ "broker": {
    "id": "ub000000-0000-0000-0000-000000000001",
    "brokerId": "b1000000-0000-0000-0000-000000000001",
    "brokerCode": "DHAN", "brokerName": "Dhan", "authType": "api_key",
    "label": "My Dhan", "active": true,
    "credentialsConfigured": true,
    "tradingAccountCount": 1,
    "accountsWithOwnCredentials": 0,
    "rotatedAt": "2026-08-23T19:41:02.114+05:30",
    "createdAt": "2026-08-23T19:41:02.100+05:30",
    "updatedAt": "2026-08-23T19:41:02.100+05:30" },
  "account": {
    "id": "ta000000-0000-0000-0000-000000000001",
    "userBrokerId": "ub000000-0000-0000-0000-000000000001",
    "userBrokerLabel": "My Dhan",
    "brokerId": "b1000000-0000-0000-0000-000000000001",
    "brokerCode": "DHAN", "brokerName": "Dhan", "brokerAuthType": "api_key",
    "accountName": "main", "brokerAccountId": "1100112233",
    "active": true,
    "credentialsConfigured": true, "credentialsOverridden": false,
    "activeStrategySubscriptions": 0,
    "createdAt": "…", "updatedAt": "…" },
  "credentials": {
    "userBrokerId": "ub000000-0000-0000-0000-000000000001",
    "tradingAccountId": null,
    "brokerCode": "DHAN", "authType": "api_key",
    "apiKeyHint": "dhan…xxxx",
    "hasApiKey": true, "hasApiSecret": true,
    "hasAccessToken": false, "hasRefreshToken": false, "hasTotpSecret": false,
    "redirectUrl": null, "clientId": "1100112233",
    "tokenExpiresAt": null, "tokenExpired": false,
    "overriddenFields": [],
    "vaultRef": null,
    "rotatedAt": "…", "createdAt": "…", "updatedAt": "…" } }
```

**Secrets are never returned.** Only `has*` booleans and `apiKeyHint`.

### 5.2 Adding more accounts under the same setup

This is what makes a `userBrokerId` deploy target fan out.

```http
POST {{BASE}}/api/v1/trading-accounts
{ "userBrokerId": "ub000000-0000-0000-0000-000000000001",
  "accountName": "hedge",
  "brokerAccountId": "1100112244",
  "active": true }
```

**201** — a `TradingAccountResponse`. `accountName` is UNIQUE within the setup →
409 on a repeat.

### 5.3 The rest

| Call | Notes |
|---|---|
| `GET /api/v1/my-brokers?brokerId=&active=` | the caller's setups |
| `GET/PUT/DELETE /api/v1/my-brokers/{id}` | 409 on delete while accounts hang off it |
| `GET/PUT/DELETE /api/v1/my-brokers/{id}/credentials` | the shared key; PUT is partial, `""` clears one field |
| `GET /api/v1/trading-accounts?userBrokerId=` | the caller's accounts |
| `GET/PUT/DELETE /api/v1/trading-accounts/{id}` | an account cannot move between setups |
| `GET/PUT/DELETE /api/v1/trading-accounts/{id}/credentials` | this account's override |
| `GET/PUT /api/v1/me/risk-limits` | `{"maxDailyLoss":…, "maxOpenPositions":…, "maxTotalExposure":…}` |

---

## 6. Indicators

Four are seeded. **An indicator's `paramSchema` is its entire parameter
declaration** — there is no parameter table behind it.

```http
GET {{BASE}}/api/v1/indicators?active=true
```

**200**

```json
[ { "id": "i1000000-0000-0000-0000-000000000001",
    "name": "EMA AVERAGING",
    "paramSchema": {
      "k": { "type": "int", "min": 1, "max": 300, "default": 21 },
      "d": { "type": "int", "min": 1, "max": 300, "default": 9, "lt": "k" } },
    "active": true,
    "usedByStrategies": [ "EMA Averaging" ],
    "createdAt": "2026-08-23T19:38:11.004+05:30" },
  { "id": "i1000000-0000-0000-0000-000000000002",
    "name": "EMA CROSSOVER",
    "paramSchema": {
      "k": { "type": "int", "min": 1, "max": 300, "default": 9 },
      "d": { "type": "int", "min": 1, "max": 300, "default": 21, "gt": "k" } },
    "active": true, "usedByStrategies": [ "EMA Crossover" ], "createdAt": "…" },
  { "id": "…", "name": "EMA",
    "paramSchema": { "period": {"type":"int","min":2,"max":300,"default":9} },
    "active": true, "usedByStrategies": [], "createdAt": "…" },
  { "id": "…", "name": "RSI",
    "paramSchema": { "period": {"type":"int","min":2,"max":100,"default":14} },
    "active": true, "usedByStrategies": [], "createdAt": "…" } ]
```

### Creating your own

```http
POST {{BASE}}/api/v1/indicators
{ "name": "supertrend",
  "paramSchema": {
    "period":     { "type": "int",     "min": 1, "max": 100, "default": 10 },
    "multiplier": { "type": "decimal", "min": 0.5, "max": 10, "default": 3.0 },
    "source":     { "type": "enum",    "options": ["close","hl2","hlc3"],
                    "default": "close" } },
  "active": true }
```

The name is **uppercased on save** → `SUPERTREND`. Rule trees match it by exact
string.

**`paramSchema` grammar**

| Key | Required | Meaning |
|---|---|---|
| `type` | **yes** | `int` · `decimal` · `bool` · `enum` · `text` |
| `default` | **yes** | what applies to a user who never sets the knob |
| `min` / `max` | no | numeric bounds |
| `options` | for `enum` | non-empty list |
| `gt` / `lt` | no | names another key of the *same* indicator |

**201** — an `IndicatorResponse`.

`PUT /api/v1/indicators/{id}` — a rename is 409 while any rule tree names it;
narrowing the schema is 400 if a live tree still passes a dropped key.
`DELETE` is 409 while a rule tree references it **or** any user strategy is tuned
on it.

---

## 7. Strategy templates

A template is **logic only** — a rule tree and nothing a user configures. Two are
seeded: `EMA Crossover` and `EMA Averaging` (both `system: true`, so the API
refuses to edit them).

```http
GET {{BASE}}/api/v1/strategy-templates?active=true
```

**200**

```json
[ { "id": "t1000000-0000-0000-0000-000000000001",
    "name": "EMA Averaging",
    "version": 1,
    "description": "EMA of the highs against a shorter signal leg, traded through options or the future, with a configurable averaging ladder.",
    "system": true,
    "active": true,
    "ruleTree": { "entry": { "ind": "EMA AVERAGING",
                             "params": { "k": "$k", "d": "$d" } } },
    "indicators": [
      { "id": "i1000000-0000-0000-0000-000000000001",
        "name": "EMA AVERAGING",
        "active": true,
        "paramSchema": {
          "k": { "type": "int", "min": 1, "max": 300, "default": 21 },
          "d": { "type": "int", "min": 1, "max": 300, "default": 9, "lt": "k" } } } ],
    "unknownIndicators": [],
    "instanceCount": 0,
    "strategyCount": 0,
    "createdAt": "…", "updatedAt": "…" } ]
```

`indicators[].paramSchema` is the **only** part of the builder form that varies by
template. Everything else — symbol, candle, CE/PE strikes, ladder, exits — is a
fixed column and identical on every template.

`unknownIndicators` non-empty means the rule tree names an indicator that is
missing or disabled: **block building on it**, creation will 400.

Also: `GET /api/v1/strategy-templates/{id}`,
`GET /api/v1/strategy-templates/by-name/EMA%20Averaging`,
`?search=ema`.

### Creating your own

```http
POST {{BASE}}/api/v1/strategy-templates
{ "name": "RSI Reversal",
  "description": "Long when RSI leaves oversold.",
  "ruleTree": { "entry": { "ind": "RSI", "params": { "period": "$period" } } },
  "version": 1,
  "active": true }
```

Rule-tree grammar: an indicator node is `{"ind":"<NAME>","params":{...}}`; a value
of `"$key"` binds to the key of that name in the indicator's own `paramSchema`.
Nodes nest freely under any object or array, so a two-indicator tree is fine:

```json
{ "entry": { "and": [ { "ind": "EMA AVERAGING", "params": {"k":"$k","d":"$d"} },
                      { "ind": "RSI",           "params": {"period":"$period"} } ] } }
```

`is_system` is forced to `false` on anything created through the API. **201** — a
`StrategyTemplateDetailResponse`.

`PUT /api/v1/strategy-templates/{id}` — 409 on system rows, and 409 if you change
`ruleTree` once any user strategy is built on it (their indicator rows were
settled under the old tree). `DELETE` — 409 while any shared computation or user
strategy references it; deactivate instead.

---

## 8. User strategies

**This is the main object.** One row holds the entire configuration.

### 8.1 Create

The spreadsheet, verbatim: EMA High (K) 21, EMA (D) 9, 5 MIN, NIFTY, OPTION,
OTM1, Double LOT from a base of 65, averaging 2 — with **both** a call and a put.

```http
POST {{BASE}}/api/v1/my-strategies
Authorization: Bearer {{TOKEN}}

{ "strategyName": "EMA Averaging",
  "name": "NIFTY 21/9 both sides",
  "description": "Sheet block 1",

  "symbol": "NIFTY",
  "exchangeCode": "NSE",
  "candleDuration": "5m",
  "triggerDuration": "5m",

  "derivative": "OPTION",
  "ceEnabled": true, "ceMoneyness": "OTM", "ceStrikeOffset": 1,
  "peEnabled": true, "peMoneyness": "OTM", "peStrikeOffset": 1,

  "lotRule": "DOUBLE",
  "baseLot": 65,
  "averagingCount": 2,
  "slPct": 1.5,
  "tpPct": 3.0,

  "indicators": [
    { "indicatorName": "EMA AVERAGING", "params": { "k": 21, "d": 9 } } ] }
```

#### Every field

| Field | Type | Default on create | Rule |
|---|---|---|---|
| `strategyId` **or** `strategyName` | uuid / string | — | **required on create**; ignored on update |
| `name` | string(100) | the template's name | UNIQUE per user → 409 |
| `description` | string | `null` | |
| `symbolId` **or** `symbol` + `exchangeCode` | | `null` | needed before deploying |
| `candleDuration` | string | `null` | `^[0-9]{1,4}[smhdw]$`; needed before deploying; **hashed** |
| `triggerDuration` | string | `null` | same format; never hashed |
| `derivative` | enum | `OPTION` | `FUT` \| `OPTION` |
| `ceEnabled` | bool | `false` | **setting `ceMoneyness` turns it on** |
| `ceMoneyness` | enum | `null` | `ATM` \| `ITM` \| `OTM` |
| `ceStrikeOffset` | int | `0` | `0` for ATM; `1..15` for ITM/OTM |
| `peEnabled` / `peMoneyness` / `peStrikeOffset` | | | identical, chosen independently |
| `lotRule` | enum | `FIXED` | `FIXED` \| `DOUBLE` \| `CUMULATIVE` |
| `baseLot` | int | `1` | ≥ 1 |
| `averagingCount` | int | `0` | `0..10`; non-FIXED `lotRule` needs ≥ 1 |
| `slPct` / `tpPct` | decimal(6,2) | `null` | `0 < x ≤ 100` |
| `indicators[]` | array | schema defaults | see below |
| `active` | bool | `true` | archive without deleting |

`indicators[]` entry: `{ "indicatorName" \| "indicatorId" \| "userStrategyIndicatorId", "slot"?, "params"?, "enabled"? }`.
`params` is **merged** over what is stored, so `{"k":50}` changes only `k`.
`slot` is needed only when a template uses one indicator twice.

Enums parse case-insensitively (`"otm"` works), but send the canonical form.

### 8.2 Response — the editor shape

**201** (and the same body from `GET /api/v1/my-strategies/{id}`)

```json
{ "id": "us000000-0000-0000-0000-000000000001",
  "userId": "u0000000-0000-0000-0000-000000000001",

  "strategyId": "t1000000-0000-0000-0000-000000000001",
  "strategyName": "EMA Averaging",
  "strategyDescription": "EMA of the highs against a shorter signal leg…",

  "name": "NIFTY 21/9 both sides",
  "description": "Sheet block 1",

  "symbolId": "1a2b3c4d-0000-0000-0000-000000000001",
  "symbol": "NIFTY",
  "instrumentType": "index",
  "exchangeCode": "NSE",
  "candleDuration": "5m",
  "triggerDuration": "5m",

  "derivative": "OPTION",
  "ceEnabled": true,  "ceMoneyness": "OTM", "ceStrikeOffset": 1,
  "peEnabled": true,  "peMoneyness": "OTM", "peStrikeOffset": 1,
  "legs": [
    { "side": "CE", "moneyness": "OTM", "strikeOffset": 1, "label": "CE OTM1" },
    { "side": "PE", "moneyness": "OTM", "strikeOffset": 1, "label": "PE OTM1" } ],

  "lotRule": "DOUBLE",
  "baseLot": 65,
  "averagingCount": 2,
  "slPct": 1.50,
  "tpPct": 3.00,

  "indicators": [
    { "id": "ui000000-0000-0000-0000-000000000001",
      "indicatorId": "i1000000-0000-0000-0000-000000000001",
      "indicatorName": "EMA AVERAGING",
      "slot": null,
      "enabled": true,
      "displayOrder": 0,
      "params": { "d": 9, "k": 21 },
      "paramSchema": {
        "k": { "type": "int", "min": 1, "max": 300, "default": 21 },
        "d": { "type": "int", "min": 1, "max": 300, "default": 9, "lt": "k" } } } ],

  "sharedConfigId": "sc000000-0000-0000-0000-000000000001",
  "configHash": "6b1f0c9e2a…",
  "deployable": true,
  "deploymentCount": 0,

  "active": true,
  "createdAt": "2026-08-23T19:45:10.221+05:30",
  "updatedAt": "2026-08-23T19:45:10.221+05:30" }
```

Notes for the UI:

- the `ce*` / `pe*` fields are the **editable** form — same names as the request;
- `legs[]` is the same choice **derived** for display (read-only). For a `FUT`
  strategy it is a single `[{"side":"FUT","moneyness":null,"strikeOffset":0,"label":"FUT"}]`;
- `params` comes back **sorted** (`d` before `k`) — that is the canonical order
  the hash is computed over, not a display order;
- `deployable` gates the deploy button; `deploymentCount` tells the user how many
  brokers an edit will move.

### 8.3 The FUT variant

```http
POST {{BASE}}/api/v1/my-strategies
{ "strategyName": "EMA Averaging",
  "name": "NIFTY futures 50/21",
  "symbol": "NIFTY", "exchangeCode": "NSE",
  "candleDuration": "5m",
  "derivative": "FUT",
  "lotRule": "FIXED", "baseLot": 75,
  "indicators": [ { "indicatorName": "EMA AVERAGING",
                    "params": { "k": 50, "d": 21 } } ] }
```

`ceEnabled` / `peEnabled` must stay off — `FUT` has no strike to choose.
`legs` comes back as `[{"side":"FUT","label":"FUT"}]`.

### 8.4 Read

```http
GET {{BASE}}/api/v1/my-strategies                    # all of the caller's
GET {{BASE}}/api/v1/my-strategies?active=true
GET {{BASE}}/api/v1/my-strategies?strategyId={{templateId}}
GET {{BASE}}/api/v1/my-strategies/{{id}}
```

### 8.5 The bot shape

```http
GET {{BASE}}/api/v1/my-strategies/{{id}}/runtime
```

**200**

```json
{ "userStrategyId": "us000000-…",
  "userId": "u0000000-…",
  "strategyId": "t1000000-…",
  "strategyName": "EMA Averaging",
  "ruleTree": "{\"entry\":{\"ind\":\"EMA AVERAGING\",\"params\":{\"k\":\"$k\",\"d\":\"$d\"}}}",
  "symbolId": "1a2b3c4d-…", "symbol": "NIFTY",
  "candleDuration": "5m", "triggerDuration": "5m",
  "active": true,
  "indicators": [
    { "indicatorId": "i1000000-…", "name": "EMA AVERAGING",
      "slot": null, "params": { "d": 9, "k": 21 } } ],
  "derivative": "OPTION",
  "legs": [ { "side":"CE","moneyness":"OTM","strikeOffset":1,"label":"CE OTM1" },
            { "side":"PE","moneyness":"OTM","strikeOffset":1,"label":"PE OTM1" } ],
  "lotRule": "DOUBLE", "baseLot": 65, "averagingCount": 2,
  "slPct": 1.50, "tpPct": 3.00,
  "signalParams": { "d": 9, "k": 21 },
  "sharedConfigId": "sc000000-…",
  "configHash": "6b1f0c9e2a…" }
```

`ruleTree` is a **JSON string** here (it is passed straight through), unlike the
template endpoint where it is a parsed object.

### 8.6 Update

Partial — present is applied, absent is left alone.

```http
PUT {{BASE}}/api/v1/my-strategies/{{id}}
{ "indicators": [ { "indicatorName": "EMA AVERAGING", "params": { "k": 50 } } ] }
```

`d` keeps its value. **200** with the full editor shape — and `configHash` /
`sharedConfigId` will have changed, because `k` feeds the hash.

Other common edits:

```json
{ "ceStrikeOffset": 3 }                       // move the call out to OTM3
{ "peEnabled": false }                        // park the put side, keep its tuning
{ "lotRule": "CUMULATIVE", "averagingCount": 3 }
{ "slPct": 2.0, "tpPct": 5.0 }
{ "active": false }                           // archive
```

> **Every broker this strategy is deployed on follows immediately.** Deployments
> point at this row; they do not copy it. Say so in the UI.

### 8.7 Delete

```http
DELETE {{BASE}}/api/v1/my-strategies/{{id}}      → 204
```

**409 while any deployment exists:**

```json
{ "error": "Strategy NIFTY 21/9 both sides is deployed on 3 account(s). Withdraw those deployments first, or archive it with PUT /api/v1/my-strategies/us000000-… {\"active\":false}." }
```

---

## 9. Deploy a strategy to brokers

```http
POST {{BASE}}/api/v1/my-strategies/{{id}}/deploy
Authorization: Bearer {{TOKEN}}

{ "tradeMode": "paper",
  "multiplier": 1,
  "executionMode": "FIXED_QTY",
  "riskProfileId": "4f5e6d7c-0000-0000-0000-0000000000b1",

  "targets": [
    { "userBrokerId": "ub000000-0000-0000-0000-000000000001" },
    { "tradingAccountId": "ta000000-0000-0000-0000-000000000009",
      "multiplier": 2,
      "tradeMode": "live" } ] }
```

- a target names **one account** (`tradingAccountId`) **or a whole setup**
  (`userBrokerId`, which fans out to every account under it);
- request-level fields are defaults; a target that sets one wins;
- per-target overridable: `riskProfileId`, `multiplier`, `capitalAllocated`,
  `executionMode`, `tradeMode`;
- `executionMode` ∈ `FIXED_QTY` · `CAPITAL_PERCENT` · `RISK_PERCENT`;
  `tradeMode` ∈ `paper` · `live`;
- **no configuration in this body** — it comes from the strategy, which is what
  makes every broker feed off one shared computation.

### Response — always **200** when the request itself was well-formed

```json
{ "userStrategyId": "us000000-0000-0000-0000-000000000001",
  "userStrategyName": "NIFTY 21/9 both sides",
  "symbolId": "1a2b3c4d-…", "symbol": "NIFTY",
  "candleDuration": "5m",
  "sharedConfigId": "sc000000-…",
  "configHash": "6b1f0c9e2a…",

  "requested": 3,
  "deployed": 2,
  "failed": 1,

  "results": [
    { "tradingAccountId": "ta000000-…001",
      "tradingAccountName": "main",
      "userBrokerId": "ub000000-…001",
      "brokerLabel": "My Dhan",
      "status": "deployed",
      "subscription": { "…": "a full StrategySubscriptionResponse, see §10" },
      "error": null },

    { "tradingAccountId": "ta000000-…002",
      "tradingAccountName": "hedge",
      "userBrokerId": "ub000000-…001",
      "brokerLabel": "My Dhan",
      "status": "failed",
      "subscription": null,
      "error": "Strategy NIFTY 21/9 both sides is already deployed on account hedge (subscriptionId=sub00000-…). Change it with PUT /api/v1/my-subscriptions/sub00000-…." },

    { "tradingAccountId": "ta000000-…009",
      "tradingAccountName": "kite-main",
      "userBrokerId": "ub000000-…002",
      "brokerLabel": "My Zerodha",
      "status": "deployed",
      "subscription": { "…": "…" },
      "error": null } ] }
```

**Render `results`, not the status code.** A 200 with `failed: 1` is the normal
outcome of re-deploying. Each account runs in its own transaction, so a failure
does not roll back the successes.

### 400s that reject the whole call, before anything is written

| Body | Message |
|---|---|
| `targets` empty or missing | `targets is required and must name at least one account` |
| a target with neither id | `Every target needs a tradingAccountId or a userBrokerId` |
| the same account twice | `Account main is named twice in targets - a strategy is deployed on an account once` |
| a broker setup with no accounts | `Broker setup My Dhan has no trading accounts to deploy on` |
| archived strategy | `Strategy … is archived; reactivate it before deploying` |
| no market set | `Strategy … has no market yet - set symbol and candleDuration before deploying` |

`404` if the strategy, account or broker setup is not yours.

---

## 10. Managing deployments

### 10.1 Deploy to a single account

```http
POST {{BASE}}/api/v1/my-subscriptions
{ "userStrategyId": "us000000-…",
  "tradingAccountId": "ta000000-…001",
  "riskProfileId": "4f5e6d7c-…",
  "multiplier": 1,
  "capitalAllocated": 200000,
  "executionMode": "FIXED_QTY",
  "tradeMode": "paper" }
```

**201**

```json
{ "id": "sub00000-0000-0000-0000-000000000001",
  "userId": "u0000000-…",

  "userStrategyId": "us000000-…",
  "userStrategyName": "NIFTY 21/9 both sides",
  "strategyId": "t1000000-…",
  "strategyName": "EMA Averaging",

  "symbolId": "1a2b3c4d-…", "symbol": "NIFTY",
  "candleDuration": "5m",

  "derivative": "OPTION",
  "legs": [ { "side":"CE","moneyness":"OTM","strikeOffset":1,"label":"CE OTM1" },
            { "side":"PE","moneyness":"OTM","strikeOffset":1,"label":"PE OTM1" } ],
  "lotRule": "DOUBLE", "baseLot": 65, "averagingCount": 2,
  "slPct": 1.50, "tpPct": 3.00,

  "sharedConfigId": "sc000000-…",
  "configHash": "6b1f0c9e2a…",
  "signalParams": { "d": 9, "k": 21 },
  "indicators": [ "EMA AVERAGING(d=9,k=21)" ],

  "tradingAccountId": "ta000000-…001",
  "tradingAccountName": "main",
  "userBrokerId": "ub000000-…001",
  "brokerLabel": "My Dhan",
  "riskProfileId": "4f5e6d7c-…",
  "riskProfileName": "Conservative",

  "multiplier": 1.00000000,
  "capitalAllocated": 200000.00000000,
  "executionMode": "FIXED_QTY",
  "tradeMode": "paper",

  "active": true,
  "createdAt": "…", "updatedAt": "…" }
```

Everything from `derivative` down to `indicators` is **read through the
strategy**, not stored here — it changes the moment the strategy is retuned.

### 10.2 Update one deployment

```http
PUT {{BASE}}/api/v1/my-subscriptions/{{id}}
{ "tradeMode": "live", "multiplier": 2 }
```

Accepts only: `multiplier`, `capitalAllocated`, `executionMode`, `tradeMode`,
`riskProfileId`, `active`. There is deliberately **no way to fork one broker's
configuration** — retuning is `PUT /api/v1/my-strategies/{id}`.

Pause one broker without losing anything:

```json
{ "active": false }
```

### 10.3 List and withdraw

```http
GET    {{BASE}}/api/v1/my-subscriptions          → all of the caller's
GET    {{BASE}}/api/v1/my-subscriptions/{{id}}
DELETE {{BASE}}/api/v1/my-subscriptions/{{id}}   → 204
```

Withdrawing the last active deployment of a computation retires it (status
`retired`); it is never deleted, so lineage survives.

---

## 11. Verify the dedup

This is the acceptance gate. Build the same strategy for a second user (or a
second strategy of your own with **identical** indicator values, symbol and
candle) and confirm they land on one `configHash`.

```http
GET {{BASE}}/api/v1/shared-strategy-configs?status=active
```

**200**

```json
[ { "id": "sc000000-…",
    "strategyId": "t1000000-…", "strategyName": "EMA Averaging",
    "symbolId": "1a2b3c4d-…", "symbol": "NIFTY",
    "timeframe": "5m",
    "signalParams": { "d": 9, "k": 21 },
    "configHash": "6b1f0c9e2a…",
    "supersedesId": null,
    "status": "active",
    "indicators": [ "EMA AVERAGING(d=9,k=21)" ],
    "activeSubscribers": 3,
    "createdAt": "…" } ]
```

```http
GET {{BASE}}/api/v1/shared-strategy-configs/indicator-plan
```

```json
{ "activeStrategySubscriptions": 5,
  "distinctInstances": 2,
  "distinctIndicators": 2,
  "indicators": [ "EMA AVERAGING(d=21,k=50)", "EMA AVERAGING(d=9,k=21)" ] }
```

**Two strategies with the same `k`/`d` on the same symbol and candle must show one
instance**, however differently they strike, size or exit. If they split, the
instrument fields have leaked into the hash — they should not have.

---

## 12. Error catalogue

Every error body has `error` (a displayable sentence). `400` **also** has
`errors[]` with every problem found — render the whole list.

```json
{ "error": "ceStrikeOffset must be 1..15 for OTM, got 16",
  "errors": [ "ceStrikeOffset must be 1..15 for OTM, got 16",
              "averagingCount must be 0..10, got 25" ] }
```

### Validation (400) — field names match the request

| Cause | Message |
|---|---|
| OPTION, neither side on | `derivative is OPTION but neither side is on - enable ceEnabled, peEnabled, or both` |
| FUT, a side still on | `derivative is FUT, so no CE or PE side applies - turn them off, or set derivative to OPTION` |
| side on, no moneyness | `ceMoneyness is required while ceEnabled is true (ATM, ITM or OTM)` |
| depth on ATM | `ceStrikeOffset must be 0 for ATM - there is one at-the-money strike; use ITM or OTM to move 3 strike(s) away` |
| depth out of range | `ceStrikeOffset must be 1..15 for OTM, got 16` |
| bad enum | `ceMoneyness must be one of [ATM, ITM, OTM], got 'DEEP'` |
| `baseLot` < 1 | `baseLot must be at least 1, got 0` |
| `averagingCount` out of range | `averagingCount must be 0..10, got 25` |
| ladder with no adds | `lotRule DOUBLE has no effect while averagingCount is 0 - raise averagingCount, or set lotRule to FIXED` |
| bad percentage | `slPct must be greater than 0, got 0` |
| indicator value out of range | `Indicator 'EMA AVERAGING' parameter 'k' must be <= 300, got 400` |
| indicator cross-field | `Indicator 'EMA AVERAGING' parameter 'd' must be less than 'k' (21 vs 9)` |
| unknown indicator key | `Indicator 'EMA AVERAGING' has no parameter 'period' - it declares [k, d]` |
| bad duration | `timeframe must look like 30s / 5m / 15m / 1h / 1d / 1w, got '5 MIN'` |
| bad rule tree | `ruleTree references unknown indicator 'EMAA'` |
| dangling binding | `ruleTree binds $foo but no indicator it references declares 'foo' - the declared keys are [k, d]` |
| schema without a default | `paramSchema.period.default is required - it is what applies to every user who never sets this knob` |

### 401 / 404 / 409

| Status | Example |
|---|---|
| `401` | `{"error":"Unauthorized: valid Bearer token required"}` |
| `404` | `{"error":"Strategy not found: us000000-…"}` — **also what another user's row returns**; there is no 403 |
| `409` | duplicate name, already-deployed account, system template edit, delete blocked by references |

---

## 13. End-to-end script

Paste-able smoke test. Set `BASE` and `TOKEN` first.

```bash
BASE=http://localhost:8081
TOKEN='eyJ…'
H=(-H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json')
j() { python3 -c 'import sys,json;print(json.load(sys.stdin)'"$1"')'; }   # or use jq

# 1. broker catalog
curl -s "${H[@]}" -X POST "$BASE/api/v1/brokers" -d '{
  "code":"DHAN","name":"Dhan","authType":"api_key",
  "apiBaseUrl":"https://api.dhan.co"}'

# 2. broker setup + first account + key, one call
UB=$(curl -s "${H[@]}" -X POST "$BASE/api/v1/my-brokers/setup" -d '{
  "brokerCode":"DHAN","label":"My Dhan",
  "account":{"accountName":"main","brokerAccountId":"1100112233"},
  "credentials":{"apiKey":"key","apiSecret":"secret","clientId":"1100112233"},
  "credentialsScope":"SETUP"}' | jq -r '.broker.id')

# 3. a second account under the same setup
curl -s "${H[@]}" -X POST "$BASE/api/v1/trading-accounts" \
  -d "{\"userBrokerId\":\"$UB\",\"accountName\":\"hedge\",\"brokerAccountId\":\"1100112244\"}"

# 4. the strategy — the whole spreadsheet in one body
US=$(curl -s "${H[@]}" -X POST "$BASE/api/v1/my-strategies" -d '{
  "strategyName":"EMA Averaging","name":"NIFTY 21/9 both sides",
  "symbol":"NIFTY","exchangeCode":"NSE",
  "candleDuration":"5m","triggerDuration":"5m",
  "derivative":"OPTION",
  "ceEnabled":true,"ceMoneyness":"OTM","ceStrikeOffset":1,
  "peEnabled":true,"peMoneyness":"OTM","peStrikeOffset":1,
  "lotRule":"DOUBLE","baseLot":65,"averagingCount":2,
  "slPct":1.5,"tpPct":3.0,
  "indicators":[{"indicatorName":"EMA AVERAGING","params":{"k":21,"d":9}}]}' | jq -r '.id')

# 5. deploy to every account under the setup
curl -s "${H[@]}" -X POST "$BASE/api/v1/my-strategies/$US/deploy" \
  -d "{\"tradeMode\":\"paper\",\"targets\":[{\"userBrokerId\":\"$UB\"}]}" | jq '.deployed,.failed'

# 6. retune — every broker follows
curl -s "${H[@]}" -X PUT "$BASE/api/v1/my-strategies/$US" \
  -d '{"indicators":[{"indicatorName":"EMA AVERAGING","params":{"k":50,"d":21}}]}' \
  | jq '.configHash,.deploymentCount'

# 7. the dedup gate
curl -s "${H[@]}" "$BASE/api/v1/shared-strategy-configs/indicator-plan" | jq
```
