# Strategy / Indicator API layer

Backend API layer for managing users' strategies, indicators and indicator
parameters. Built against `trading_platform_schema.sql` (naming authority) and
`trading-platform-database-design.md` (architecture).

Scope: **control plane only**. The execution plane (`signals`, `rejections`,
`positions`, `orders`, `fills`, `pnl_*`) belongs to the trading engine and is not
implemented here, and neither is any bot / signal-evaluation logic.

---

## 1. Entity → table mapping

Every name below is taken verbatim from the SQL file. Nothing was renamed,
pluralized or abbreviated.

| Java entity | Table | PK | Notes |
|---|---|---|---|
| `User` | `users` | `id` uuid | Control-plane identity. **Not** the auth table. |
| `UserRiskLimit` | `user_risk_limits` | `user_id` uuid | 1:1 with `users`, aggregate caps |
| `Exchange` | `exchanges` | `id` uuid | |
| `Symbol` | `symbols` | `id` uuid | `UNIQUE (exchange_id, symbol)` |
| `TradingAccount` | `trading_accounts` | `id` uuid | `UNIQUE (user_id, exchange_id, account_name)` |
| `AccountCredential` | `account_credentials` | `id` uuid | 1:1, `vault_ref` only — no secrets |
| `Strategy` | `strategies` | `id` uuid | `name` unique; `rule_tree` jsonb |
| `IndicatorDef` | `indicator_defs` | `id` uuid | `param_schema` jsonb; no `updated_at` |
| `StrategyParamDef` | `strategy_param_defs` | `id` bigserial | `UNIQUE (strategy_id, parameter_key)` |
| `StrategyInstance` | `strategy_instances` | `id` uuid | `UNIQUE (strategy_id, symbol_id, timeframe, config_hash)` |
| `RiskProfile` | `risk_profiles` | `id` uuid | |
| `Subscription` | `subscriptions` | `id` uuid | `UNIQUE (strategy_instance_id, trading_account_id)` |

Column names map 1:1 (`is_active` → `active`, `is_system` → `system`,
`is_required` → `required` on the Java side only; the `@Column(name = ...)` is
always the SQL name). Verified by generating the Hibernate DDL and diffing the
table and column names against the SQL file.

Pre-existing tables — `user_details`, `dhan_access_token`, `strategy_config` —
are untouched.

---

## 2. Relationships

```
users ──< trading_accounts >── exchanges ──< symbols
  │              │                              │
  │              └──── 1:1 ── account_credentials
  │                                             │
  └──< subscriptions >── strategy_instances ────┘   (symbol_id = SIGNAL symbol)
             │                    │
       risk_profiles          strategies ──< strategy_param_defs
                                   │
                              rule_tree  ──(by name)──> indicator_defs
```

**Strategy → indicator.** There is no join table, by design. A strategy declares
its indicators inside `strategies.rule_tree`, as `{"ind":"EMA","params":{"period":"$fast"}}`
nodes whose `ind` resolves against `indicator_defs.name` and whose `$key`
bindings resolve against `strategy_param_defs`. A fixed FK could not express one
strategy using the same indicator at two parameterizations, which is exactly what
EMA Crossover does. `IndicatorResolver` is the only class that knows the tree's
shape; `StrategyDefinitionValidator` checks the references at save time so a typo
is a 400 rather than a runtime failure in the engine.

**Indicator → parameters.** Stored as the `param_schema` JSON on the indicator
row, not as a parameter table — the design's changelog records EAV as
deliberately removed (gap #7). So indicator-parameter CRUD is create/update of
`paramSchema`; the *values* a user picks live in `strategy_param_defs` (per
strategy) and in `strategy_instances.signal_params` / `subscriptions.exec_params`
(per configuration).

**User → strategy.** Through `subscriptions`. `strategies` and
`strategy_instances` have no owner column and are shared on purpose — that
sharing is what dedup is. Ownership is enforced on `subscriptions`,
`trading_accounts` and `user_risk_limits`, and it is part of the query
(`findByIdAndUser_Id`), so another user's row reports 404, not 403.

---

## 3. Endpoints

All require the existing `Authorization: Bearer <access token>`. Errors are
`{"error": "...", "errors": [...]}`; `errors` is present for validation failures.

### Strategies — `/api/v1/strategies`

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

### Subscriptions — `/api/v1/subscriptions`

`GET /`, `GET /{id}`, `POST /` (201), `PUT /{id}`, `DELETE /{id}` (204) — all
scoped to the caller.

`PUT` merges `params` over the current configuration, so `{"params":{"fast":13}}`
is valid. A signal-scope change repoints the subscription at the instance for the
resulting config (creating it only if nobody already runs that math), bumps
`version`, records `supersedes_id`, and retires the old instance once its last
active subscriber leaves. Execution-scope changes stay on the row.

### Strategy instances — `/api/v1/strategy-instances` (read-only)

`GET /` (`?status=active|retired`), `GET /{id}`, and
`GET /indicator-plan` — the dedup report: active subscriptions vs distinct
indicator computations. It carries counts and fingerprints only, no identifiers.

### Trading accounts — `/api/v1/trading-accounts`

`GET /`, `GET /{id}`, `POST /` (201), `PUT /{id}`, `DELETE /{id}` (204).
`vaultRef` writes `account_credentials` and stamps `rotated_at` on change.

### Reference data — `/api/v1`

`GET /exchanges`, `/exchanges/{id}`, `/symbols` (`?exchangeId=`, `?activeOnly=`),
`/symbols/{id}`, `/risk-profiles`, `/risk-profiles/{id}` — read-only.
`GET|PUT /me/risk-limits` — the caller's aggregate caps.

---

## 4. Authentication

Unchanged. `JwtFilter`, `SecurityConfig`, `GoogleAuthService`, `JwtUtil` and
`user_details` were not modified. The new controllers read the principal the
existing filter already puts in the security context (the email) through
`SecuredController.currentEmail()`, matching what the existing controllers do
inline.

`CurrentUserService` maps that email to a `users` row, provisioning it on first
use, because `trading_accounts`, `subscriptions` and `user_risk_limits` all key
off `users(id)` — a uuid the auth tables do not carry. `users.password_hash` is
NOT NULL in the schema and Google-authenticated callers have no local credential,
so it stores the non-secret sentinel `EXTERNAL_AUTH:GOOGLE`, which the auth flow
never reads; `username` is derived from the email local part and disambiguated on
collision.

---

## 5. Dedup and the config hash

`strategy_instances.config_hash` is
`sha256(strategy_id | symbol_id | timeframe | canonical_jsonb(signal_params))`.
The `trg_instances_hash` trigger computes it server-side — **the database is the
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

`db/control-plane-sample-data.sql` is optional and separate: exchanges, symbols
and risk profiles are operational master data with no write API, so a fresh
database has nothing for a subscription to point at. Load it for development, or
seed those rows from the venue's instrument feed in production.
