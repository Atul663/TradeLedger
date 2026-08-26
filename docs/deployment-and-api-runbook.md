# Deployment & API Runbook

Deploy the refactored build, then create everything from scratch — reference
data, brokers, templates, strategies, deployments — with the exact payload and
response for every call.

- [0. Read this before deploying](#0-read-this-before-deploying)
- [1. Deploy](#1-deploy)
- [2. Seed reference data](#2-seed-reference-data)
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
Seeded indicator EMA Crossover id=…
Seeded indicator EMA Averaging id=…
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

## 2. Seed reference data

A strategy cannot pick an underlying that does not exist, so create these first.
Either the API (§2.1) or SQL (§2.2) — the API is easier for a handful of rows,
SQL is better for a bulk load from an instrument feed.

> **These three tables have no owner column.** Anything written here is visible
> to every user, and the writes are not scoped to the caller — there is no role
> model in this API layer to gate them with. The protection is structural
> instead: **a delete is refused while anything references the row**, identity
> columns freeze once they are referenced, and deactivating is always available.
> That makes the worst case additive clutter rather than data loss. It is not a
> substitute for authorization — see gap 9 in the architecture doc.

### 2.1 Through the API

```http
POST {{BASE}}/api/v1/exchanges
Authorization: Bearer {{TOKEN}}

{ "name": "National Stock Exchange of India",
  "code": "NSE",
  "description": "Indian equity and derivatives",
  "status": "active" }
```

**201** — an `ExchangeResponse`. `code` is uppercased on save and is what a
strategy later sends as `exchangeCode`; both `name` and `code` are UNIQUE.

```http
POST {{BASE}}/api/v1/symbols

{ "exchangeCode": "NSE",
  "symbol": "NIFTY",
  "baseAsset": "NIFTY",
  "quoteAsset": "INR",
  "instrumentType": "index",
  "contractSize": 75,
  "tickSize": 0.05,
  "minQty": 75,
  "active": true }
```

**201** — a `SymbolResponse`. `exchangeId` may be sent instead of
`exchangeCode`. The ticker is uppercased and is UNIQUE per exchange.

```http
POST {{BASE}}/api/v1/risk-profiles

{ "name": "Conservative",
  "description": "Tight daily stop, small size",
  "maxDailyLoss": 5000,
  "maxDrawdown": 10000,
  "maxPositionSize": 100000,
  "maxTotalExposure": 500000,
  "maxTradesPerDay": 10,
  "killSwitchEnabled": true }
```

**201** — a `RiskProfileResponse`. Every cap is optional; an absent one means
uncapped.

| Method | Path | Notes |
|---|---|---|
| POST/PUT/DELETE | `/api/v1/exchanges`, `/{id}` | DELETE is 409 while any symbol belongs to it; `code` freezes once symbols exist |
| POST/PUT/DELETE | `/api/v1/symbols`, `/{id}` | DELETE is 409 while any strategy watches it; a symbol cannot change exchange |
| POST/PUT/DELETE | `/api/v1/risk-profiles`, `/{id}` | DELETE is 409 while any deployment runs under it |

PUT is partial on all three. The rules a form should mirror:

| Rule | Message |
|---|---|
| Missing exchange code | `code is required` |
| Bad exchange status | `status must be one of [active, disabled], got paused` |
| Bad instrument type | `instrumentType must be one of [...], got perpetual` |
| Option with no side | `optionType is required when instrumentType is option (CALL or PUT)` |
| Option with no strike | `strikePrice is required when instrumentType is option` |
| Option fields on a non-option | `optionType only applies when instrumentType is option, not index` |
| Non-positive measure | `contractSize must be greater than 0, got 0` |
| Negative cap | `maxDailyLoss must not be negative, got -1` |
| Zero trades per day | `maxTradesPerDay must be at least 1, got 0` |
| Recode a referenced exchange | `Exchange NSE has 3 symbol(s); its code is what clients resolve them by and cannot change underneath them.` |

### 2.2 Through SQL

Better for a bulk load. Note this bypasses the validation above, so the
instrument-type and option-field rules are yours to keep.

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

`accessToken` lives 1 day. Refresh with `POST /api/v1/auth/refresh`
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
    "name": "EMA Averaging",
    "paramSchema": {
      "k": { "type": "int", "min": 1, "max": 300, "default": 21 },
      "d": { "type": "int", "min": 1, "max": 300, "default": 9, "lt": "k" } },
    "active": true,
    "usedByStrategies": [ "EMA Averaging" ],
    "createdAt": "2026-08-23T19:38:11.004+05:30" },
  { "id": "i1000000-0000-0000-0000-000000000002",
    "name": "EMA Crossover",
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

### The fixed half of the same form

`paramSchema` describes the knobs of the one pluggable thing on the platform.
Every *other* setting a strategy has — the candle, the derivative, the strikes,
the ladder, the exits, the deployment sizing — is a **typed column**, and
`/api/v1/fixed-parameters` is where those columns describe themselves to a form:
label, type, default to pre-fill, bounds to enforce, and where the field sits.

**Descriptors, not values.** Nothing here is read to decide what a strategy runs
with — the values are written through `/api/v1/my-strategies` and
`/api/v1/my-subscriptions` as always. Emptying this catalog changes nothing but
how a form renders.

```http
GET {{BASE}}/api/v1/fixed-parameters?group=Exits&active=true
```

**200** — ordered by group, then position within it, then name.

```json
[ { "id": "f1000000-0000-0000-0000-000000000001",
    "name": "slPct",
    "label": "SL %",
    "description": "Percent move against the position that closes it.",
    "dataType": "decimal",
    "scope": "execution",
    "defaultValue": "2.5",
    "validation": { "min": 0, "max": 100 },
    "paramGroup": "Exits",
    "displayOrder": 1,
    "required": false,
    "active": true,
    "createdAt": "2026-08-24T11:02:44.118+05:30",
    "updatedAt": "2026-08-24T11:02:44.118+05:30" } ]
```

Seeded groups: `Market`, `Instrument`, `Sizing`, `Exits`, `Deployment` — one
descriptor per fixed column. Unlike indicator schemas, seeding is **insert-only**:
a row you edit here survives the next deploy.

**The `symbol` knob** (`Market`, position 0) is the one whose options are rows.
Nothing is stored for it; the list is filled from the active `symbols` on every
read:

```json
{ "name": "symbol", "label": "Underlying",
  "dataType": "symbol", "scope": "signal", "required": true,
  "defaultValue": null,
  "validation": { "options": ["BANKNIFTY", "NIFTY"],
                  "optionsSource": "/api/v1/symbols" },
  "paramGroup": "Market", "displayOrder": 0 }
```

Seed `symbols` first (§2) or the list comes back empty. A ticker is unique per
exchange, so one listed on two venues appears once — send `exchangeCode`
alongside it, or `symbolId` instead. Setting `validation.options` on it is a 400.

```http
GET {{BASE}}/api/v1/fixed-parameters/grouped?active=true
```

**200** — the same rows folded into the sections a form renders. Same filters,
same order; `group=` narrows it to that one group.

```json
[ { "paramGroup": "Exits", "count": 2,
    "parameters": [ { "name": "slPct", "label": "SL %", … },
                    { "name": "tpPct", "label": "TP %", … } ] },
  { "paramGroup": "Instrument", "count": 7, "parameters": [ … ] } ]
```

A descriptor with no group collects in a single entry whose `paramGroup` is
`null`.

These descriptors are how a form renders a strategy's non-indicator fields. The
**value** of each one is the flat field of the same `name` on
`GET /api/v1/my-strategies/{id}` — bind the two by string equality (`slPct` the
descriptor to `slPct` the field), and write back through
`PUT /api/v1/my-strategies/{id}` under that same name. The strategy response
carried a pre-joined `fixedParameters[]` until it was removed for going unread;
the join is one line of client code and the descriptors are the same on every
strategy, so they are fetched once per page rather than once per row.

Only the strategy-scope knobs take part — the `Deployment` group describes
subscription columns, and no strategy has them.

```http
GET  {{BASE}}/api/v1/fixed-parameters/by-name/slPct
POST {{BASE}}/api/v1/fixed-parameters
{ "name": "trailStopPct", "label": "Trailing stop %", "dataType": "decimal",
  "scope": "execution", "defaultValue": "1.5",
  "validation": { "min": 0, "max": 100 },
  "paramGroup": "Exits", "displayOrder": 3 }
```

`defaultValue` is text whatever the type is, and is **parsed against `dataType`
and `validation` on save** — an `int` default that is not an integer, a `decimal`
outside its own `min`/`max`, or an `enum` default that is not one of its
`options` are all 400. `options` is required for an `enum` and rejected for
anything else — and are **refused** on a `symbol` knob, whose choices are the
active `symbols` and are filled in from the table on read, never stored;
`min`/`max` apply only to `int` and `decimal`. A `timeframe`
default goes through the same normalizer a strategy's does, so `5M` stores as
`5m`. `name` is UNIQUE case-insensitively.

`PUT /api/v1/fixed-parameters/{id}` is partial, and re-validates the type, the
default and the bounds **together against the resulting row** — retyping a knob
while its stored default no longer fits is 400 rather than half-applied. Send
`""` to clear the description, default or group, and `{}` to clear the bounds.
`DELETE` is always allowed: nothing points at a descriptor, and the column it
describes is untouched. `{"active": false}` is the reversible path.

> A new knob is still a migration and a column. A row here describes it; it does
> not create it.

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
    "ruleTree": { "entry": { "ind": "EMA Averaging",
                             "params": { "k": "$k", "d": "$d" } } },
    "indicators": [
      { "id": "i1000000-0000-0000-0000-000000000001",
        "name": "EMA Averaging",
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
{ "entry": { "and": [ { "ind": "EMA Averaging", "params": {"k":"$k","d":"$d"} },
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
    { "indicatorName": "EMA Averaging", "params": { "k": 21, "d": 9 } } ] }
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
| `derivative` | enum | `OPTION` | `FUTURES` \| `OPTION` |
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

  "strategyId": "t1000000-0000-0000-0000-000000000001",
  "strategyName": "EMA Averaging",

  "name": "NIFTY 21/9 both sides",
  "description": "Sheet block 1",

  "symbol": "NIFTY",
  "exchangeCode": "NSE",
  "candleDuration": "5m",
  "triggerDuration": "5m",

  "derivative": "OPTION",
  "ceEnabled": true,  "ceMoneyness": "OTM", "ceStrikeOffset": 1,
  "peEnabled": true,  "peMoneyness": "OTM", "peStrikeOffset": 1,

  "lotRule": "DOUBLE",
  "baseLot": 65,
  "averagingCount": 2,
  "slPct": 1.50,
  "tpPct": 3.00,

  "indicators": [
    { "id": "ui000000-0000-0000-0000-000000000001",
      "indicatorId": "i1000000-0000-0000-0000-000000000001",
      "indicatorName": "EMA Averaging",
      "slot": null,
      "enabled": true,
      "displayOrder": 0,
      "params": { "d": 9, "k": 21 },
      "paramSchema": {
        "k": { "type": "int", "min": 1, "max": 300, "default": 21 },
        "d": { "type": "int", "min": 1, "max": 300, "default": 9, "lt": "k" } } } ],

  "deployable": true,
  "active": true,
  "createdAt": "2026-08-23T19:45:10.221+05:30",
  "updatedAt": "2026-08-23T19:45:10.221+05:30" }
```

Notes for the UI:

- the `ce*` / `pe*` fields are the **editable** form — same names as the request;
- `params` comes back **sorted** (`d` before `k`) — that is the canonical order
  the hash is computed over, not a display order;
- `deployable` gates the deploy button.

**The shape is flat.** It used to also ship `legs[]` (the CE/PE choice derived for
display), `indicatorGroups[]` (`indicators[]` by name) and `fixedParameters[]`
(descriptor + value by `paramGroup`), plus `userId`, `strategyDescription`,
`strategySystem`, `symbolId`, `instrumentType`, `sharedConfigId`, `configHash` and
`deploymentCount`. All of it was a second view of something already in the row and
nothing read any of it, so a list of N strategies paid for all of it N times.
Where to find what is gone:

| Was | Now |
| --- | --- |
| `legs[]` | derive from `derivative` + the `ce*` / `pe*` fields, or read `/my-strategies/{id}/runtime` |
| `indicatorGroups[]` | group `indicators[]` by `indicatorName` |
| `fixedParameters[]` | `GET /api/v1/fixed-parameters` for the descriptors; the value is the flat field of the same `name` |
| `sharedConfigId`, `configHash` | `/my-strategies/{id}/runtime`, or a deployment on `/my-subscriptions` |
| `deploymentCount` | `GET /api/v1/my-subscriptions` (one call for the whole page) and count by `userStrategyId` |
| `strategyDescription`, `strategySystem` | the group heading on `/grouped`, or `/strategy-templates` |

### 8.3 The FUTURES variant

```http
POST {{BASE}}/api/v1/my-strategies
{ "strategyName": "EMA Averaging",
  "name": "NIFTY futures 50/21",
  "symbol": "NIFTY", "exchangeCode": "NSE",
  "candleDuration": "5m",
  "derivative": "FUTURES",
  "lotRule": "FIXED", "baseLot": 75,
  "indicators": [ { "indicatorName": "EMA Averaging",
                    "params": { "k": 50, "d": 21 } } ] }
```

`ceEnabled` / `peEnabled` must stay off — `FUTURES` has no strike to choose.
`legs` comes back as `[{"side":"FUTURES","label":"FUTURES"}]`.

### 8.4 Read

```http
GET {{BASE}}/api/v1/my-strategies                    # all of the caller's
GET {{BASE}}/api/v1/my-strategies?active=true
GET {{BASE}}/api/v1/my-strategies?strategyId={{templateId}}
GET {{BASE}}/api/v1/my-strategies/{{id}}
```

**Grouped by template** — the same rows, arranged one group per `strategyId` and
tagged with that template's `strategyName`. A user usually builds several
customizations of one template (one per market, one per tuning), so this is the
shape a list screen wants: a heading per template with its rows under it.

```http
GET {{BASE}}/api/v1/my-strategies/grouped
GET {{BASE}}/api/v1/my-strategies/grouped?active=true
GET {{BASE}}/api/v1/my-strategies/grouped?strategyId={{templateId}}   # just that group
```

**200**

```json
[ { "strategyId": "3f1b0c7e-9a41-4c2e-9f11-2b7d5a6e8c01",
    "strategyName": "EMA Averaging",
    "strategyDescription": "EMA of the highs against a shorter signal leg, traded through options or the future, with a configurable averaging ladder.",
    "strategySystem": true,
    "instanceCount": 4,
    "count": 2,
    "strategies": [
      { "id": "us000000-1111-4222-8333-444444444444",
        "name": "NIFTY 21/9 both sides", "symbol": "NIFTY", "candleDuration": "5m", "…": "…" },
      { "id": "us000000-1111-4222-8333-444444444445",
        "name": "BANKNIFTY 21/9", "symbol": "BANKNIFTY", "candleDuration": "5m", "…": "…" } ] },
  { "strategyId": "8c2d1e0f-7b36-4a15-9e42-0d3c6b5a4f92",
    "strategyName": "EMA Crossover",
    "strategyDescription": "Long when the fast leg crosses above the slow leg…",
    "strategySystem": false,
    "instanceCount": 1,
    "count": 1,
    "strategies": [
      { "id": "us000000-1111-4222-8333-444444444446",
        "name": "NIFTY fast cross", "…": "…" } ] } ]
```

Each entry in `strategies[]` is the **same complete `UserStrategyResponse`** the
flat list returns — the configuration fields, `indicators[]` with their schemas,
`deployable`, `active`, all of it. One mapper builds both shapes, so they cannot
drift; grouping changes how the rows are arranged, never what a row carries.

The group header carries the template: `strategyName`, `strategyDescription`,
`strategySystem` (a seeded template is locked, so the logic cannot change under
you) and `instanceCount` — shared computations for that template across **all**
users, which is why it may exceed `count`. `count` is only the caller's rows, and
describes the rows actually in the group *after* the `active` filter.

Groups are ordered by `strategyName` (case-insensitively), rows inside a group
oldest first, and a template the caller has built nothing from produces no group
at all. The name is a **field, not a JSON key**, so rewording a template changes a
value and never the structure you parse.

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
  "ruleTree": "{\"entry\":{\"ind\":\"EMA Averaging\",\"params\":{\"k\":\"$k\",\"d\":\"$d\"}}}",
  "symbolId": "1a2b3c4d-…", "symbol": "NIFTY",
  "candleDuration": "5m", "triggerDuration": "5m",
  "active": true,
  "indicators": [
    { "indicatorId": "i1000000-…", "name": "EMA Averaging",
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
{ "indicators": [ { "indicatorName": "EMA Averaging", "params": { "k": 50 } } ] }
```

`d` keeps its value. **200** with the full editor shape. `k` feeds the hash, so the
strategy has also repointed at a different shared computation - the response does
not show that; read it from `/my-strategies/{id}/runtime`.

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

### 8.8 Delete them all

Same filters as the list — omit both to clear everything the caller owns.

```http
DELETE {{BASE}}/api/v1/my-strategies                          → 200
DELETE {{BASE}}/api/v1/my-strategies?active=false             # only the archived ones
DELETE {{BASE}}/api/v1/my-strategies?strategyId={{templateId}} # only that template's
```

**200, even when nothing could be deleted.** A strategy still deployed on a
broker is *skipped*, not refused, and does not stop the others being cleared:

```json
{
  "requested": 3,
  "deleted": 2,
  "skipped": 1,
  "results": [
    { "id": "us000000-…", "name": "NIFTY calls OTM5", "strategyId": "1a2b3c4d-…",
      "strategyName": "EMA Averaging", "status": "deleted", "deployments": 0, "error": null },
    { "id": "us111111-…", "name": "NIFTY futures 50/21", "strategyId": "1a2b3c4d-…",
      "strategyName": "EMA Averaging", "status": "deleted", "deployments": 0, "error": null },
    { "id": "us222222-…", "name": "NIFTY 21/9 both sides", "strategyId": "1a2b3c4d-…",
      "strategyName": "EMA Averaging", "status": "skipped", "deployments": 3,
      "error": "Strategy NIFTY 21/9 both sides is deployed on 3 account(s). Withdraw those deployments first, or archive it with PUT /api/v1/my-strategies/us222222-… {\"active\":false}." }
  ]
}
```

> Read `results[]`, not the status code — `deleted: 0, skipped: 5` is a 200.
> There is no undo: confirm in the UI before calling it.

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
  "indicators": [ "EMA Averaging(d=9,k=21)" ],

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
    "indicators": [ "EMA Averaging(d=9,k=21)" ],
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
  "indicators": [ "EMA Averaging(d=21,k=50)", "EMA Averaging(d=9,k=21)" ] }
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
| FUTURES, a side still on | `derivative is FUTURES, so no CE or PE side applies - turn them off, or set derivative to OPTION` |
| side on, no moneyness | `ceMoneyness is required while ceEnabled is true (ATM, ITM or OTM)` |
| depth on ATM | `ceStrikeOffset must be 0 for ATM - there is one at-the-money strike; use ITM or OTM to move 3 strike(s) away` |
| depth out of range | `ceStrikeOffset must be 1..15 for OTM, got 16` |
| bad enum | `ceMoneyness must be one of [ATM, ITM, OTM], got 'DEEP'` |
| `baseLot` < 1 | `baseLot must be at least 1, got 0` |
| `averagingCount` out of range | `averagingCount must be 0..10, got 25` |
| ladder with no adds | `lotRule DOUBLE has no effect while averagingCount is 0 - raise averagingCount, or set lotRule to FIXED` |
| bad percentage | `slPct must be greater than 0, got 0` |
| indicator value out of range | `Indicator 'EMA Averaging' parameter 'k' must be <= 300, got 400` |
| indicator cross-field | `Indicator 'EMA Averaging' parameter 'd' must be less than 'k' (21 vs 9)` |
| unknown indicator key | `Indicator 'EMA Averaging' has no parameter 'period' - it declares [k, d]` |
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
  "indicators":[{"indicatorName":"EMA Averaging","params":{"k":21,"d":9}}]}' | jq -r '.id')

# 5. deploy to every account under the setup
curl -s "${H[@]}" -X POST "$BASE/api/v1/my-strategies/$US/deploy" \
  -d "{\"tradeMode\":\"paper\",\"targets\":[{\"userBrokerId\":\"$UB\"}]}" | jq '.deployed,.failed'

# 6. retune — every broker follows
curl -s "${H[@]}" -X PUT "$BASE/api/v1/my-strategies/$US" \
  -d '{"indicators":[{"indicatorName":"EMA Averaging","params":{"k":50,"d":21}}]}' \
  | jq '.name,.indicators'
curl -s "${H[@]}" "$BASE/api/v1/my-strategies/$US/runtime" | jq '.configHash'

# 7. the dedup gate
curl -s "${H[@]}" "$BASE/api/v1/shared-strategy-configs/indicator-plan" | jq
```
