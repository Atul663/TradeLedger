# Strategy / Indicator API layer

Backend API layer for managing users' strategies, indicators and indicator
parameters. Built against `trading_platform_schema.sql` (naming authority) and
`trading-platform-database-design.md` (architecture).

Scope: **control plane only**. The execution plane (`signals`, `rejections`,
`positions`, `orders`, `fills`, `pnl_*`) belongs to the trading engine and is not
implemented here, and neither is any bot / signal-evaluation logic.

---

## 1. Entity → table mapping

Every name below is taken verbatim from the SQL file. Entity and table names
move together — see `db/control-plane-rename-migration.sql` for the one-off
migration that renamed the strategy-module tables.

| Java entity | Table | PK | Notes |
|---|---|---|---|
| `User` | `users` | `id` uuid | Control-plane identity. **Not** the auth table. |
| `UserRiskLimit` | `user_risk_limits` | `user_id` uuid | 1:1 with `users`, aggregate caps |
| `Exchange` | `exchanges` | `id` uuid | The venue an instrument trades on |
| `Broker` | `brokers` | `id` uuid | `code` unique; who the order is routed through |
| `Symbol` | `symbols` | `id` uuid | `UNIQUE (exchange_id, symbol)` |
| `UserBroker` | `user_brokers` | `id` uuid | `UNIQUE (user_id, label)`; FK to `brokers` |
| `TradingAccount` | `trading_accounts` | `id` uuid | `UNIQUE (user_broker_id, account_name)`; no exchange column |
| `BrokerCredential` | `broker_credentials` | `id` uuid | Two levels via nullable `trading_account_id`; ciphertext, never plaintext |
| `StrategyTemplate` | `strategy_templates` | `id` uuid | `name` unique; `rule_tree` jsonb |
| `Indicator` | `indicators` | `id` uuid | `param_schema` jsonb; no `updated_at` |
| `StrategyParamDefinition` | `strategy_param_definitions` | `id` bigserial | `UNIQUE (strategy_id, parameter_key)` |
| `SharedStrategyConfig` | `shared_strategy_configs` | `id` uuid | `UNIQUE (strategy_id, symbol_id, timeframe, config_hash)` |
| `RiskProfile` | `risk_profiles` | `id` uuid | |
| `StrategySubscription` | `user_strategy_subscriptions` | `id` uuid | `UNIQUE (shared_config_id, trading_account_id)` |
| `UserStrategy` | `user_strategies` | `id` uuid | `UNIQUE (user_id, name)` — FKs only, no copied master data |
| `UserStrategyIndicator` | `user_strategy_indicators` | `id` uuid | `UNIQUE (user_strategy_id, indicator_id, slot)` |
| `UserStrategyParameter` | `user_strategy_parameters` | `id` uuid | One row per CHANGED value; two partial unique indexes |
| `StrategyIndicatorLink` | `strategy_indicator_links` | `id` uuid | Derived: which indicators a template uses |
| `StrategyParameterLink` | `strategy_parameter_links` | `id` bigserial | Template ↔ `parameters` catalog |
| `IndicatorParameterLink` | `indicator_parameter_links` | `id` bigserial | Indicator ↔ `parameters` catalog |
| `GoogleAuthToken` | `google_auth_tokens` | `id` bigserial | OAuth session store; **not** a profile table |

Column names map 1:1 (`is_active` → `active`, `is_system` → `system`,
`is_required` → `required` on the Java side only; the `@Column(name = ...)` is
always the SQL name). Verified by generating the Hibernate DDL and diffing the
table and column names against the SQL file.

Pre-existing tables — `google_auth_tokens` (was `user_details`)
and `platform_strategy_toggles` (was `strategy_config`) — keep their columns and
data; only their names changed, in the same migration. `dhan_access_tokens` is
dropped by step 7 of that migration: tokens now live per account in
`broker_credentials.access_token`.

---

## 2. Relationships

```
brokers                                     exchanges ──< symbols
   │  catalog, shared by every user                          │
   │                                                         │
users ──< user_brokers ──< trading_accounts                  │
  │            │                   │                         │
  │            └──< broker_credentials                       │
  │                 trading_account_id NULL -> the setup key │
  │                 trading_account_id SET  -> one override  │
  │                                                          │
  └──< user_strategy_subscriptions >── shared_strategy_configs ──┘
                    │                        │      (symbol_id = SIGNAL symbol)
              risk_profiles          strategy_templates ──< strategy_param_definitions
                                             │
                                        rule_tree ──(by name)──> indicators
```

**Strategy → indicator.** There is no join table, by design. A strategy declares
its indicators inside `strategy_templates.rule_tree`, as `{"ind":"EMA","params":{"period":"$fast"}}`
nodes whose `ind` resolves against `indicators.name` and whose `$key`
bindings resolve against `strategy_param_definitions`. A fixed FK could not express one
strategy using the same indicator at two parameterizations, which is exactly what
EMA Crossover does. `IndicatorResolver` is the only class that knows the tree's
shape; `StrategyTemplateValidator` checks the references at save time so a typo
is a 400 rather than a runtime failure in the engine.

**Indicator → parameters.** Stored as the `param_schema` JSON on the indicator
row, not as a parameter table — the design's changelog records EAV as
deliberately removed (gap #7). So indicator-parameter CRUD is create/update of
`paramSchema`; the *values* a user picks live in `strategy_param_definitions` (per
strategy) and in `shared_strategy_configs.signal_params` / `user_strategy_subscriptions.exec_params`
(per configuration).

**User → strategy.** Through `user_strategy_subscriptions`. `strategy_templates` and
`shared_strategy_configs` have no owner column and are shared on purpose — that
sharing is what dedup is. Ownership is enforced on `user_strategy_subscriptions`,
`trading_accounts` and `user_risk_limits`, and it is part of the query
(`findByIdAndUser_Id`), so another user's row reports 404, not 403.

---

## 3. Endpoints

All require the existing `Authorization: Bearer <access token>`. Errors are
`{"error": "...", "errors": [...]}`; `errors` is present for validation failures.

### Strategies — `/api/v1/strategy-templates`

| Method | Path | Status | Notes |
|---|---|---|---|
| GET | `/` | 200 | `?active=`, `?search=` |
| GET | `/{id}` | 200 / 404 | |
| GET | `/by-name/{name}` | 200 / 404 | |
| POST | `/` | 201 / 400 / 409 | optional inline `params[]` |
| PUT | `/{id}` | 200 / 400 / 404 / 409 | `params[]` replaces the knob set |
| DELETE | `/{id}` | 204 / 404 / 409 | 409 while instances reference it |
| GET | `/{id}/params` | 200 | |
| POST | `/{id}/params` | 201 / 400 / 409 | |
| PUT | `/{id}/params/{paramId}` | 200 / 400 / 404 / 409 | |
| DELETE | `/{id}/params/{paramId}` | 204 / 404 / 409 | 409 while the rule tree binds it |

Seeded (`is_system = true`) strategies are read-only: edit or delete returns 409.
Deactivate with `PUT {"active": false}` instead of deleting when instances exist.

### Indicators — `/api/v1/indicators`

`GET /`, `GET /{id}`, `GET /by-name/{name}`, `POST /` (201),
`PUT /{id}`, `DELETE /{id}` (204).

Delete and rename are refused with 409 while a strategy rule tree references the
indicator by name; so is dropping a `paramSchema` key a live rule tree passes.

### Subscriptions — `/api/v1/my-subscriptions`

`GET /`, `GET /{id}`, `POST /` (201), `PUT /{id}`, `DELETE /{id}` (204) — all
scoped to the caller.

`PUT` merges `params` over the current configuration, so `{"params":{"fast":13}}`
is valid. A signal-scope change repoints the subscription at the instance for the
resulting config (creating it only if nobody already runs that math), bumps
`version`, records `supersedes_id`, and retires the old instance once its last
active subscriber leaves. Execution-scope changes stay on the row.

### My strategies — `/api/v1/my-strategies`

A user strategy is a customization of a global template. It stores foreign keys
and **one row per value the user actually changed** — no default, label, data
type or validation rule is copied down, so an admin retuning a global default
moves every user who left that knob alone and moves nobody who overrode it.

```
user_strategies             -> strategy_templates          which global strategy
  user_strategy_indicators  -> indicators                  which indicator usages
    user_strategy_parameters -> indicator_parameter_links  changed indicator values
  user_strategy_parameters  -> parameters                  changed strategy values
```

`GET /` (`?active=`, `?strategyId=`), `GET /{id}`, `POST /` (201), `PUT /{id}`,
`DELETE /{id}` (204) — all scoped to the caller. `UNIQUE (user_id, name)`, so one
user may customize a template many times and two users may both name one
"My EMA".

**Creating.** The indicator rows are created from the template's own indicators,
so a body with no `overrides` saves a faithful copy sitting entirely on global
defaults. Only the knobs listed get a row:

```json
{ "strategyName": "EMA Crossover",
  "name": "My fast EMA",
  "timeframe": "5m",
  "overrides": [ {"indicatorId": "...", "parameterId": 1, "value": "13"},
                 {"parameterId": 4, "value": "2.5"} ] }
```

Overrides address knobs by **id, never by name**. `indicatorId` absent means a
strategy-level knob (`sl`, `tp`, `quantity`, the durations); `slot` is only
needed once a template uses one indicator more than once. `PUT` applies them
entry by entry, and an entry with `"value": null` **deletes** that override so
the knob returns to the global default — the only way to unset without knowing
what the default is.

**Effective value** resolves in three levels, at read time:

```
user_strategy_parameters.custom_value
  -> indicator_parameter_links.default_value  (or strategy_parameter_links)
    -> parameters.default_value
```

**Two reads, one set of rows.** `GET /{id}` is the UI shape: indicators and
knobs, each carrying `defaultValue`, `customValue`, `effectiveValue` and
`overridden`, so the whole form renders from one call. `GET /{id}/runtime` is the
bot shape: the same knobs collapsed to the values in force, coerced by the
engine's own validator and already split into signal and execution scope. The
`user_strategy_effective_params` view in `control-plane-schema.sql` answers the
same question straight from SQL for anything that does not speak HTTP.

**Validation** runs the whole effective set through `StrategyParamValidator`
against the template's `strategy_param_definitions` — which
`StrategyParameterLinkSync` derives from the same catalog — so type, range and
cross-field rules like `d > k` are all enforced, and an override that only makes
sense next to another value is still caught.

Deleting a template is refused with 409 while any user has customized it.

`POST /{id}/subscribe` (201) puts one to work on a trading account, returning the
same `StrategySubscriptionResponse` as `/api/v1/my-subscriptions`. The effective
values are projected into the flat map the execution path already consumes, so
the config hash and the dedup keep working untouched — two users on the same
signal values still share one indicator computation. `symbolId` / `timeframe` in
the body override the saved market and are required when the user strategy has
none. The row is not consumed: subscribing it to a second account is a second
subscription.

### Shared strategy configs — `/api/v1/shared-strategy-configs` (read-only)

`GET /` (`?status=active|retired`), `GET /{id}`, and
`GET /indicator-plan` — the dedup report: active subscriptions vs distinct
indicator computations. It carries counts and fingerprints only, no identifiers.

### Broker setups — `/api/v1/my-brokers`

`GET /`, `GET /{id}`, `POST /` (201), `PUT /{id}`, `DELETE /{id}` (204),
plus `GET|PUT|DELETE /{id}/credentials`. Step one: the setup holds the login
and the API key every account under it inherits. 409 on delete while accounts
still hang off it, and a setup cannot change broker.

### Trading accounts — `/api/v1/trading-accounts`

`GET /` (`?userBrokerId`), `GET /{id}`, `POST /` (201), `PUT /{id}`,
`DELETE /{id}` (204), plus `GET|PUT|DELETE /{id}/credentials`.
An account belongs to a setup and inherits its credentials; the credentials
sub-resource here writes an override, resolved per field, and `rotated_at` is
stamped whenever a secret changes.

### Reference data — `/api/v1`

`GET /exchanges`, `/exchanges/{id}`, `/symbols` (`?exchangeId=`, `?activeOnly=`),
`/symbols/{id}`, `/risk-profiles`, `/risk-profiles/{id}` — read-only.
`GET|PUT /me/risk-limits` — the caller's aggregate caps.

---

## 4. Authentication

Unchanged. `JwtFilter`, `SecurityConfig`, `GoogleAuthService`, `JwtUtil` and
`google_auth_tokens` were not modified. The new controllers read the principal the
existing filter already puts in the security context (the email) through
`SecuredController.currentEmail()`, matching what the existing controllers do
inline.

`CurrentUserService` maps that email to a `users` row, provisioning it on first
use, because `trading_accounts`, `user_strategy_subscriptions` and `user_risk_limits` all key
off `users(id)` — a uuid the auth tables do not carry. `users.password_hash` is
NOT NULL in the schema and Google-authenticated callers have no local credential,
so it stores the non-secret sentinel `EXTERNAL_AUTH:GOOGLE`, which the auth flow
never reads; `username` is derived from the email local part and disambiguated on
collision.

---

## 5. Dedup and the config hash

`shared_strategy_configs.config_hash` is
`sha256(strategy_id | symbol_id | timeframe | canonical_jsonb(signal_params))`.
The `trg_shared_configs_hash` trigger computes it server-side — **the database is the
referee** — and `ConfigHashUtil` / `CanonicalJson` reproduce it byte for byte so
an existing instance can be found before an insert is attempted.

Matching it exactly means matching jsonb's *text rendering*, not just its
ordering: `{"fast": 9, "slow": 21}` with a space after each `:` and `,`, keys
ordered by length then bytewise, numbers passed through `trim_scale()`. Those
rules are asserted in `StrategyDedupTest`; if they drift, nothing throws — the
same 9×21 config just lands on two instances and the platform pays for the same
EMA twice.

---

## 6. Database setup

`spring.jpa.hibernate.ddl-auto=update` creates the tables and columns, but cannot
create the `pgcrypto` extension, the `canonical_jsonb` / `compute_config_hash` /
`set_config_hash` functions, the `touch_updated_at` triggers, the CHECK
constraints or the partial and GIN indexes. Run the script first on a fresh
database:

```
psql "$DB_URL" -f src/main/resources/db/control-plane-schema.sql
```

On a database created before the strategy-module rename, run the migration
FIRST — `ddl-auto=update` does not rename anything, it would create a second,
empty set of tables beside the populated old ones:

```
psql "$DB_URL" -f src/main/resources/db/control-plane-rename-migration.sql
psql "$DB_URL" -f src/main/resources/db/control-plane-schema.sql
```

`db/control-plane-sample-data.sql` is optional and separate: exchanges, symbols
and risk profiles are operational master data with no write API, so a fresh
database has nothing for a subscription to point at. Load it for development, or
seed those rows from the venue's instrument feed in production.
