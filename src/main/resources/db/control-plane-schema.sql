-- ============================================================================
-- Control-plane schema for the strategy / indicator API layer.
--
-- Extracted verbatim (names, columns, constraints) from
-- trading_platform_schema.sql v1.0. Only the CONTROL PLANE is included: the
-- execution plane (signals, rejections, positions, orders, fills, pnl_*) belongs
-- to the trading engine and is deliberately out of scope for this API layer.
--
-- The SQL file is the naming authority: every JPA entity in
-- com.example.tradeLedger.entity maps to a table and column defined here.
--
-- Safe to run repeatedly, and safe to run against a database that
-- spring.jpa.hibernate.ddl-auto=update has already touched - everything is
-- "if not exists". Run this FIRST on a fresh database: it installs the pieces
-- Hibernate cannot generate (pgcrypto, the canonical_jsonb/config_hash referee
-- functions, the updated_at triggers, CHECK constraints and partial indexes).
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;      -- gen_random_uuid()

-- ---------------------------------------------------------------------------
-- Helper: generic updated_at maintenance
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION touch_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END $$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------------
-- Helper: canonical JSONB  (edge case #22 - {"fast":9} must equal {"fast":9.0})
-- jsonb already sorts keys and deduplicates; this additionally normalizes
-- numeric scale via trim_scale() so 9, 9.0, 9.00 hash identically.
--
-- The app-side canonicalizer (utils/CanonicalJson.java) MUST implement the same
-- rules INCLUDING jsonb's text rendering ("k": v, separated by ", "); this
-- function is the referee used by the config_hash trigger.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION canonical_jsonb(j jsonb) RETURNS jsonb AS $$
DECLARE
  t text := jsonb_typeof(j);
BEGIN
  IF t = 'object' THEN
    RETURN COALESCE(
      (SELECT jsonb_object_agg(k, canonical_jsonb(v) ORDER BY k)
         FROM jsonb_each(j) AS e(k, v)),
      '{}'::jsonb);
  ELSIF t = 'array' THEN
    RETURN COALESCE(
      (SELECT jsonb_agg(canonical_jsonb(v) ORDER BY ord)
         FROM jsonb_array_elements(j) WITH ORDINALITY AS e(v, ord)),
      '[]'::jsonb);
  ELSIF t = 'number' THEN
    RETURN to_jsonb(trim_scale((j #>> '{}')::numeric));
  ELSE
    RETURN j;
  END IF;
END $$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION compute_config_hash(
  p_strategy_id uuid, p_symbol_id uuid, p_timeframe text, p_params jsonb
) RETURNS text AS $$
  SELECT encode(sha256(convert_to(
           p_strategy_id::text || '|' || p_symbol_id::text || '|' ||
           p_timeframe || '|' || canonical_jsonb(p_params)::text, 'UTF8')),
         'hex');
$$ LANGUAGE sql IMMUTABLE;

-- ============================================================================
-- CONTROL PLANE
-- ============================================================================

CREATE TABLE IF NOT EXISTS users (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  username      varchar(50)  NOT NULL UNIQUE,
  email         varchar(100) NOT NULL UNIQUE,
  password_hash varchar(255) NOT NULL,
  status        varchar(20)  NOT NULL DEFAULT 'active'
                CHECK (status IN ('active','suspended','closed')),
  created_at    timestamptz  NOT NULL DEFAULT now(),
  updated_at    timestamptz  NOT NULL DEFAULT now()
);
DROP TRIGGER IF EXISTS trg_users_touch ON users;
CREATE TRIGGER trg_users_touch BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- User-level AGGREGATE caps (risk gate check #4). Per-subscription limits
-- live in risk_profiles; without this table, a user with 10 subscriptions
-- would have 10 independent daily-loss limits and no total.
CREATE TABLE IF NOT EXISTS user_risk_limits (
  user_id            uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  max_daily_loss     numeric(20,8),
  max_open_positions int,
  max_total_exposure numeric(20,8),
  updated_at         timestamptz NOT NULL DEFAULT now()
);
DROP TRIGGER IF EXISTS trg_url_touch ON user_risk_limits;
CREATE TRIGGER trg_url_touch BEFORE UPDATE ON user_risk_limits
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TABLE IF NOT EXISTS exchanges (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name        varchar(50) NOT NULL UNIQUE,
  code        varchar(20) NOT NULL UNIQUE,
  description text,
  status      varchar(20) NOT NULL DEFAULT 'active'
              CHECK (status IN ('active','disabled')),
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);
DROP TRIGGER IF EXISTS trg_exchanges_touch ON exchanges;
CREATE TRIGGER trg_exchanges_touch BEFORE UPDATE ON exchanges
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- Options-aware symbol master. Indicators usually run on the UNDERLYING
-- (instrument_type spot/index); orders target option contracts.
CREATE TABLE IF NOT EXISTS symbols (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  exchange_id     uuid NOT NULL REFERENCES exchanges(id),
  symbol          varchar(50) NOT NULL,
  base_asset      varchar(20),
  quote_asset     varchar(20),
  instrument_type varchar(20) NOT NULL
                  CHECK (instrument_type IN ('spot','future','option','index')),
  option_type     varchar(4)  CHECK (option_type IN ('CALL','PUT')),
  strike_price    numeric(20,8),
  expiry_at       timestamptz,              -- NULL = perpetual / non-expiring
  contract_size   numeric(20,8),
  tick_size       numeric(20,8),
  min_qty         numeric(20,8),
  is_active       boolean NOT NULL DEFAULT true,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (exchange_id, symbol),
  CHECK (instrument_type <> 'option'
         OR (option_type IS NOT NULL AND strike_price IS NOT NULL
             AND expiry_at IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_symbols_active_expiry ON symbols (expiry_at)
  WHERE is_active = true;
DROP TRIGGER IF EXISTS trg_symbols_touch ON symbols;
CREATE TRIGGER trg_symbols_touch BEFORE UPDATE ON symbols
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ---------------------------------------------------------------------------
-- BROKERS - who the order is placed through.
--
-- Deliberately not rows in `exchanges`. An exchange is the venue an instrument
-- trades on and is what `symbols` has a foreign key to; a broker is the API the
-- platform authenticates against to reach it. One account trades NSE through
-- Dhan, so folding the two together would mix venue rows and broker rows in the
-- table every symbol depends on.
--
-- auth_type tells a credential form which fields on broker_credentials this
-- broker actually needs, so one form serves all of them:
--   api_key        key + secret, no browser step
--   oauth_redirect key + secret + redirect URL, exchanged for a daily token
--   totp           key + secret + client id + TOTP seed
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS brokers (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code         varchar(30) NOT NULL UNIQUE,      -- DHAN, ZERODHA, UPSTOX
  name         varchar(100) NOT NULL,
  description  text,
  api_base_url text,
  auth_type    varchar(30) NOT NULL DEFAULT 'api_key'
               CHECK (auth_type IN ('api_key','oauth_redirect','totp')),
  is_active    boolean NOT NULL DEFAULT true,
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now()
);
DROP TRIGGER IF EXISTS trg_brokers_touch ON brokers;
CREATE TRIGGER trg_brokers_touch BEFORE UPDATE ON brokers
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

INSERT INTO brokers (code, name, description, api_base_url, auth_type) VALUES
  ('DELTA',   'Delta Exchange', 'Delta India: broker and venue in one',
                                     'https://api.india.delta.exchange', 'api_key'),
  ('DHAN',    'Dhan',      'Dhan partner API',     'https://api.dhan.co',        'api_key'),
  ('ZERODHA', 'Zerodha',   'Kite Connect',         'https://api.kite.trade',     'oauth_redirect'),
  ('UPSTOX',  'Upstox',    'Upstox API v2',        'https://api.upstox.com',     'oauth_redirect'),
  ('ANGELONE','Angel One', 'SmartAPI',             'https://apiconnect.angelone.in', 'totp')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- USER BROKERS - one user's authenticated setup with one broker, and the parent
-- of every trading account reached through it.
--
--   brokers            DELTA, DHAN, ZERODHA        shared catalog
--     user_brokers     "My Delta"                  one user's setup + its API key
--       trading_accounts  main, hedge, algo-1      the accounts it reaches
--
-- The same shape as user_strategies: `brokers` is the catalog every user shares,
-- this is one user's instance of a row in it.
--
-- Unique on (user_id, label) rather than (user_id, broker_id), so two separate
-- Delta logins are two setups rather than a conflict.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_brokers (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  broker_id  uuid NOT NULL REFERENCES brokers(id),
  label      varchar(100) NOT NULL,      -- the user's own name for this setup
  is_active  boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_user_brokers_user_label UNIQUE (user_id, label)
);
CREATE INDEX IF NOT EXISTS idx_user_brokers_user   ON user_brokers (user_id);
CREATE INDEX IF NOT EXISTS idx_user_brokers_broker ON user_brokers (broker_id);
DROP TRIGGER IF EXISTS trg_user_brokers_touch ON user_brokers;
CREATE TRIGGER trg_user_brokers_touch BEFORE UPDATE ON user_brokers
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ---------------------------------------------------------------------------
-- TRADING ACCOUNTS - one account under a setup; what a strategy subscribes to.
--
-- NO EXCHANGE COLUMN. It used to carry one, which forced a Dhan login that
-- reaches NSE and BSE into two rows and made a venue-less broker like Delta pick
-- a meaningless value. Where an order goes is decided by the symbol, which
-- already knows its exchange.
--
-- user_id is kept even though user_brokers already knows the owner: ownership
-- filtering happens on every read and joining through the parent to do it would
-- turn the cheapest, most frequent check in the module into a two-table query.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trading_accounts (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id           uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  user_broker_id    uuid REFERENCES user_brokers(id),   -- NOT NULL once migrated
  account_name      varchar(100) NOT NULL,
  broker_account_id varchar(100),   -- the broker's own id: Delta sub-account, Dhan client id
  is_active         boolean NOT NULL DEFAULT true,
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_taccounts_user ON trading_accounts (user_id) WHERE is_active;
CREATE INDEX IF NOT EXISTS idx_taccounts_setup ON trading_accounts (user_broker_id);
DROP TRIGGER IF EXISTS trg_taccounts_touch ON trading_accounts;
CREATE TRIGGER trg_taccounts_touch BEFORE UPDATE ON trading_accounts
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- Columns and constraints for a database that predates the setup layer. Each is
-- guarded, so this is a no-op on a fresh database and a migration on an old one.
ALTER TABLE trading_accounts ADD COLUMN IF NOT EXISTS user_broker_id    uuid REFERENCES user_brokers(id);
ALTER TABLE trading_accounts ADD COLUMN IF NOT EXISTS broker_account_id varchar(100);

-- The account name is unique within its SETUP. A Delta "main" and a Dhan "main"
-- are different accounts that happen to share a label, not a conflict. Every
-- earlier key is dropped by name; ddl-auto=update never alters one in place.
DO $$
DECLARE
  legacy text;
BEGIN
  FOREACH legacy IN ARRAY ARRAY['trading_accounts_user_id_exchange_id_account_name_key',
                                'uq_taccounts_user_exchange_name',
                                'uq_taccounts_user_broker_account'] LOOP
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = legacy) THEN
      EXECUTE format('ALTER TABLE trading_accounts DROP CONSTRAINT %I', legacy);
      RAISE NOTICE 'dropped legacy unique constraint %', legacy;
    END IF;
  END LOOP;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_taccounts_broker_account') THEN
    ALTER TABLE trading_accounts
      ADD CONSTRAINT uq_taccounts_broker_account UNIQUE (user_broker_id, account_name);
    RAISE NOTICE 'added uq_taccounts_broker_account';
  END IF;
END $$;

-- ---------------------------------------------------------------------------
-- BROKER CREDENTIALS - what authenticates to a broker, at either of two levels.
--
--   trading_account_id IS NULL  -> the setup's own key, used by all its accounts
--   trading_account_id IS SET   -> one account's override of it
--
-- Two levels because that is how brokers differ. A Dhan login issues one key for
-- everything under it; a Delta sub-account can be issued its own.
--
-- RESOLUTION IS PER FIELD, not per row: an override holding only an access token
-- still uses the setup's api_key and api_secret. Same rule as
-- user_strategy_parameters, and the reason "this sub-account has its own session
-- token" is a one-field write instead of a copy that then drifts.
--
-- The secret columns hold AES-GCM ciphertext written by SecretCipher (key:
-- CREDENTIAL_ENCRYPTION_KEY), stored as 'v1:base64(iv||ciphertext||tag)'. A dump
-- without that key yields nothing usable.
--
-- redirect_url and client_id are plaintext on purpose: a callback URL is
-- registered publicly with the broker and a client id is an account number.
-- Encrypting them would cost searchability and protect nothing.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS broker_credentials (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_broker_id     uuid NOT NULL REFERENCES user_brokers(id) ON DELETE CASCADE,
  trading_account_id uuid REFERENCES trading_accounts(id) ON DELETE CASCADE,
  api_key            text,          -- ciphertext
  api_secret         text,          -- ciphertext
  access_token       text,          -- ciphertext
  refresh_token      text,          -- ciphertext
  totp_secret        text,          -- ciphertext
  redirect_url       text,          -- plaintext: public OAuth callback
  client_id          varchar(100),  -- plaintext: the broker's login identifier
  token_expires_at   timestamptz,
  vault_ref          text,          -- optional pointer, for Vault installations
  rotated_at         timestamptz,
  created_at         timestamptz NOT NULL DEFAULT now(),
  updated_at         timestamptz NOT NULL DEFAULT now()
);

-- One row per level. Two partial indexes rather than one UNIQUE: Postgres treats
-- NULLs as distinct, so a plain UNIQUE over the nullable column would happily
-- let a setup hold two sets of credentials.
CREATE UNIQUE INDEX IF NOT EXISTS uq_broker_creds_setup
  ON broker_credentials (user_broker_id)
  WHERE trading_account_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_broker_creds_account
  ON broker_credentials (trading_account_id)
  WHERE trading_account_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_broker_creds_setup ON broker_credentials (user_broker_id);

DROP TRIGGER IF EXISTS trg_broker_creds_touch ON broker_credentials;
CREATE TRIGGER trg_broker_creds_touch BEFORE UPDATE ON broker_credentials
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- The effective-credential read, for anything talking to the database directly.
--
-- The REST API is authoritative - GET /api/v1/trading-accounts/{id}/credentials
-- returns the same resolution, masked. This view exists so a report can answer
-- "which accounts can actually authenticate" in one query. It deliberately
-- exposes NO secret: only whether each field resolves to something.
CREATE OR REPLACE VIEW trading_account_credential_status AS
SELECT ta.id                AS trading_account_id,
       ta.user_id,
       ta.account_name,
       ta.broker_account_id,
       ub.id                AS user_broker_id,
       ub.label             AS setup_label,
       b.code               AS broker_code,
       b.auth_type,
       COALESCE(own.api_key,       setup.api_key)       IS NOT NULL AS has_api_key,
       COALESCE(own.api_secret,    setup.api_secret)    IS NOT NULL AS has_api_secret,
       COALESCE(own.access_token,  setup.access_token)  IS NOT NULL AS has_access_token,
       COALESCE(own.totp_secret,   setup.totp_secret)   IS NOT NULL AS has_totp_secret,
       COALESCE(own.client_id,     setup.client_id)                 AS client_id,
       COALESCE(own.redirect_url,  setup.redirect_url)              AS redirect_url,
       COALESCE(own.token_expires_at, setup.token_expires_at)       AS token_expires_at,
       (own.id IS NOT NULL)                                         AS has_own_credentials
FROM trading_accounts ta
JOIN user_brokers ub ON ub.id = ta.user_broker_id
JOIN brokers      b  ON b.id  = ub.broker_id
LEFT JOIN broker_credentials setup
       ON setup.user_broker_id = ub.id AND setup.trading_account_id IS NULL
LEFT JOIN broker_credentials own
       ON own.trading_account_id = ta.id;

-- ---------------------------------------------------------------------------
-- Strategy templates and their tunable knobs
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS strategy_templates (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name        varchar(100) NOT NULL UNIQUE,
  version     int NOT NULL DEFAULT 1,
  description text,
  is_system   boolean NOT NULL DEFAULT true,
  is_active   boolean NOT NULL DEFAULT true,
  rule_tree   jsonb NOT NULL,   -- conditions over indicators with $key bindings
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);
DROP TRIGGER IF EXISTS trg_strategy_templates_touch ON strategy_templates;
CREATE TRIGGER trg_strategy_templates_touch BEFORE UPDATE ON strategy_templates
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- Compute primitives (EMA, RSI, ...). One row + one O(1) engine class each.
CREATE TABLE IF NOT EXISTS indicators (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name         varchar(50) NOT NULL UNIQUE,      -- 'EMA', 'RSI'
  param_schema jsonb NOT NULL,                   -- {"period":{"type":"int",...}}
  is_active    boolean NOT NULL DEFAULT true,
  created_at   timestamptz NOT NULL DEFAULT now()
);

-- The FIXED knobs: what each one is called, what type it takes, what a form
-- pre-fills and what bounds it should enforce.
--
-- A DESCRIPTOR CATALOG, NOT A VALUE STORE - and the distinction is the whole
-- reason it is allowed to exist. The value of every knob described here is a
-- typed column on user_strategies or user_strategy_subscriptions, with its own
-- default and its own CHECK constraint; nothing joins to this table, no user row
-- hangs off it, and emptying it changes nothing except how a form renders.
--
-- It is NOT the old `parameters` catalog, which held user VALUES as text rows
-- reached through link tables. That one was dropped and SchemaMappingTest
-- asserts it stays dropped.
--
-- The dynamic counterpart is indicators.param_schema, which does the same job
-- for the one pluggable thing on the platform. Bootstrapped by
-- ControlPlaneSeeder with a row per fixed column; retuned through
-- /api/v1/fixed-parameters.
CREATE TABLE IF NOT EXISTS fixed_parameters (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name          varchar(100) NOT NULL UNIQUE,    -- 'slPct' - the API field name
  label         varchar(100) NOT NULL,           -- 'Stop loss %'
  description   text,
  data_type     varchar(20) NOT NULL
                CHECK (data_type IN ('int','decimal','bool','enum','timeframe','text')),
  scope         varchar(20) NOT NULL DEFAULT 'execution'
                CHECK (scope IN ('signal','execution')),
  default_value text,                            -- text for six types; data_type coerces it
  validation    jsonb,                           -- {"min":0,"max":100} / {"options":[...]}
  param_group   varchar(50),                     -- 'market','instrument','sizing','exits'
  display_order int NOT NULL DEFAULT 0,
  is_required   boolean NOT NULL DEFAULT false,
  is_active     boolean NOT NULL DEFAULT true,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_fixed_parameters_display_order CHECK (display_order >= 0)
);
-- Case-insensitive uniqueness: 'slPct' and 'slpct' are one knob, and the API
-- resolves a descriptor by name without caring which spelling a caller sent.
CREATE UNIQUE INDEX IF NOT EXISTS uq_fixed_parameters_name_lower
  ON fixed_parameters (lower(name));
-- The read a form makes: one section, in order.
CREATE INDEX IF NOT EXISTS idx_fixed_parameters_group
  ON fixed_parameters (param_group, display_order, name);
DROP TRIGGER IF EXISTS trg_fixed_parameters_touch ON fixed_parameters;
CREATE TRIGGER trg_fixed_parameters_touch BEFORE UPDATE ON fixed_parameters
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- Long-narrow: ONE ROW PER KNOB. RSI params, EMA params, SuperTrend params
-- all live here identically - new strategies are INSERTs, never ALTER TABLE.
-- scope drives the gap-#4 split: 'signal' -> instance (hashed),
-- 'execution' -> subscription (personal).
CREATE TABLE IF NOT EXISTS strategy_param_definitions (
  id            bigserial PRIMARY KEY,
  strategy_id   uuid NOT NULL REFERENCES strategy_templates(id) ON DELETE CASCADE,
  parameter_key varchar(100) NOT NULL,
  data_type     varchar(20) NOT NULL
                CHECK (data_type IN ('int','decimal','bool','enum','timeframe','text')),
  scope         varchar(20) NOT NULL
                CHECK (scope IN ('signal','execution')),
  default_value text,
  validation    jsonb,                 -- {"min":2,"max":200} / {"options":[...]} / {"gt":"fast"}
  display_label varchar(100),
  display_order int NOT NULL DEFAULT 0,
  is_required   boolean NOT NULL DEFAULT true,
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (strategy_id, parameter_key)
);

-- ---------------------------------------------------------------------------
-- STRATEGY_INSTANCES - immutable, content-addressed (the dedup unit)
-- A param change NEVER updates a row: insert new instance, repoint
-- subscription, supersedes_id records lineage, refcount retires the old one.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS shared_strategy_configs (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  strategy_id   uuid NOT NULL REFERENCES strategy_templates(id),
  symbol_id     uuid NOT NULL REFERENCES symbols(id),   -- SIGNAL symbol
  timeframe     varchar(20) NOT NULL,                   -- '5m','15m','1h',...
  signal_params jsonb NOT NULL,          -- canonicalized signal-scope params only
  config_hash   varchar(64) NOT NULL,
  supersedes_id uuid REFERENCES shared_strategy_configs(id),
  status        varchar(20) NOT NULL DEFAULT 'active'
                CHECK (status IN ('active','retired')),
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (strategy_id, symbol_id, timeframe, config_hash)
);
CREATE INDEX IF NOT EXISTS idx_shared_configs_params ON shared_strategy_configs USING GIN (signal_params);
CREATE INDEX IF NOT EXISTS idx_shared_configs_active ON shared_strategy_configs (strategy_id)
  WHERE status = 'active';

-- Hash is set server-side from canonicalized params: the DB is the referee,
-- the app must agree byte-for-byte (see StrategyDedupTest).
CREATE OR REPLACE FUNCTION set_config_hash() RETURNS trigger AS $$
BEGIN
  NEW.signal_params := canonical_jsonb(NEW.signal_params);
  NEW.config_hash   := compute_config_hash(
      NEW.strategy_id, NEW.symbol_id, NEW.timeframe, NEW.signal_params);
  RETURN NEW;
END $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_shared_configs_hash ON shared_strategy_configs;
CREATE TRIGGER trg_shared_configs_hash BEFORE INSERT ON shared_strategy_configs
  FOR EACH ROW EXECUTE FUNCTION set_config_hash();

-- ---------------------------------------------------------------------------
-- USER STRATEGIES - a user's customization of a global strategy template.
--
--   user_strategies  --> strategy_templates            which global strategy
--        |
--        +- user_strategy_indicators --> indicators    which indicator usages
--        |        |
--        |        +- user_strategy_parameters --> indicator_parameter_links
--        |                                        the changed indicator values
--        +- user_strategy_parameters --> parameters
--                                        the changed strategy values (sl, tp...)
--
-- NO GLOBAL ROW IS EVER WRITTEN. Templates, indicators and the parameter catalog
-- are shared by every user; these three tables hold foreign keys and the values
-- the user actually changed. No default, label, data type or validation rule is
-- copied down, so an admin retuning a global default moves every user who left
-- that knob alone and moves nobody who overrode it.
--
-- A knob at its default has NO ROW in user_strategy_parameters. The effective
-- value is resolved at read time:
--     custom_value -> link default_value -> parameters.default_value
-- which is what the user_strategy_effective_params view below does in one
-- COALESCE.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_strategies (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  strategy_id  uuid NOT NULL REFERENCES strategy_templates(id),  -- no cascade: template in use
  name         varchar(100) NOT NULL,        -- the user's own label
  description  text,
  symbol_id    uuid REFERENCES symbols(id),  -- nullable: pick the market at subscribe time
  timeframe    varchar(20),
  is_active    boolean NOT NULL DEFAULT true,
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, name)          -- many customizations of one template, per user
);
CREATE INDEX IF NOT EXISTS idx_user_strategies_user     ON user_strategies (user_id);
CREATE INDEX IF NOT EXISTS idx_user_strategies_template ON user_strategies (strategy_id);
DROP TRIGGER IF EXISTS trg_user_strategies_touch ON user_strategies;
CREATE TRIGGER trg_user_strategies_touch BEFORE UPDATE ON user_strategies
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- One row per indicator USAGE. The FK is to indicators - authored master data -
-- and not to strategy_indicator_links, which StrategyIndicatorLinkSync rebuilds
-- from the rule tree on every template save; user rows must not hang off a table
-- that regenerates. Membership of the template is checked when the row is written.
--
-- slot is null for every template that uses each indicator once, which is all of
-- them today. It is in the unique key so a future strategy running two plain EMAs
-- keeps them as distinct rows without a schema change.
CREATE TABLE IF NOT EXISTS user_strategy_indicators (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_strategy_id uuid NOT NULL REFERENCES user_strategies(id) ON DELETE CASCADE,
  indicator_id     uuid NOT NULL REFERENCES indicators(id),
  slot             varchar(50),               -- 'fast' / 'slow', or NULL
  is_enabled       boolean NOT NULL DEFAULT true,
  display_order    int NOT NULL DEFAULT 0,
  created_at       timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_strategy_id, indicator_id, slot)
);
CREATE INDEX IF NOT EXISTS idx_user_strategy_indicators_parent
  ON user_strategy_indicators (user_strategy_id);

-- ONE ROW PER VALUE THE USER CHANGED - nothing else lives here.
--
-- Two levels, told apart by user_strategy_indicator_id:
--   set  -> an indicator knob (k, d); indicator_parameter_link_id names the
--           default it displaces
--   NULL -> a strategy knob (sl, tp, quantity, the durations)
--
-- custom_value is text for the same reason parameters.default_value is: one
-- table holds int, decimal, bool, enum, timeframe and text knobs, and
-- parameters.data_type is what coerces it. It is validated against that type and
-- the validation rules in force before it is ever written.
CREATE TABLE IF NOT EXISTS user_strategy_parameters (
  id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_strategy_id            uuid NOT NULL REFERENCES user_strategies(id) ON DELETE CASCADE,
  user_strategy_indicator_id  uuid REFERENCES user_strategy_indicators(id) ON DELETE CASCADE,
  parameter_id                bigint NOT NULL REFERENCES parameters(id),
  indicator_parameter_link_id bigint REFERENCES indicator_parameter_links(id),
  custom_value                text NOT NULL,
  created_at                  timestamptz NOT NULL DEFAULT now(),
  updated_at                  timestamptz NOT NULL DEFAULT now()
);

-- One override per knob per level. Two partial indexes rather than one UNIQUE:
-- Postgres treats NULLs as distinct, so a plain UNIQUE over a nullable column
-- would happily let the same strategy knob be overridden twice.
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_strategy_params_indicator
  ON user_strategy_parameters (user_strategy_indicator_id, parameter_id)
  WHERE user_strategy_indicator_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_strategy_params_strategy
  ON user_strategy_parameters (user_strategy_id, parameter_id)
  WHERE user_strategy_indicator_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_user_strategy_params_parent
  ON user_strategy_parameters (user_strategy_id);

DROP TRIGGER IF EXISTS trg_user_strategy_params_touch ON user_strategy_parameters;
CREATE TRIGGER trg_user_strategy_params_touch BEFORE UPDATE ON user_strategy_parameters
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ---------------------------------------------------------------------------
-- The effective-value read, for anything talking to the database directly.
--
-- The REST API is authoritative - GET /api/v1/my-strategies/{id}/runtime returns
-- the same resolution with the engine's type coercion applied. This view exists
-- so a bot or a report can answer "what does this user strategy actually run
-- with" in one query, and it must be kept in step with
-- UserStrategyServiceImpl.toKnob() if the fallback order ever changes.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW user_strategy_effective_params AS
SELECT us.id                          AS user_strategy_id,
       us.user_id,
       us.strategy_id,
       usi.id                         AS user_strategy_indicator_id,
       i.id                           AS indicator_id,
       i.name                         AS indicator_name,
       usi.slot,
       p.id                           AS parameter_id,
       p.code                         AS parameter_code,
       p.name                         AS parameter_label,
       p.data_type,
       p.scope,
       COALESCE(usp.custom_value, ipl.default_value, p.default_value) AS effective_value,
       COALESCE(ipl.default_value, p.default_value)                   AS default_value,
       usp.custom_value,
       (usp.id IS NOT NULL)                                           AS is_overridden,
       ipl.display_order
FROM user_strategies us
JOIN user_strategy_indicators  usi ON usi.user_strategy_id = us.id AND usi.is_enabled
JOIN indicators                i   ON i.id   = usi.indicator_id
JOIN indicator_parameter_links ipl ON ipl.indicator_id = i.id
JOIN parameters                p   ON p.id   = ipl.parameter_id
LEFT JOIN user_strategy_parameters usp
       ON usp.user_strategy_indicator_id = usi.id
      AND usp.parameter_id = p.id

UNION ALL

-- Strategy-level knobs: no indicator, defaults from strategy_parameter_links.
SELECT us.id, us.user_id, us.strategy_id,
       NULL::uuid, NULL::uuid, NULL::varchar, NULL::varchar,
       p.id, p.code, p.name, p.data_type, p.scope,
       COALESCE(usp.custom_value, spl.default_value, p.default_value),
       COALESCE(spl.default_value, p.default_value),
       usp.custom_value,
       (usp.id IS NOT NULL),
       spl.display_order
FROM user_strategies us
JOIN strategy_parameter_links spl ON spl.strategy_id = us.strategy_id
JOIN parameters               p   ON p.id = spl.parameter_id
LEFT JOIN user_strategy_parameters usp
       ON usp.user_strategy_id = us.id
      AND usp.parameter_id = p.id
      AND usp.user_strategy_indicator_id IS NULL;

CREATE TABLE IF NOT EXISTS risk_profiles (
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name                varchar(100) NOT NULL,
  description         text,
  max_daily_loss      numeric(20,8),
  max_drawdown        numeric(20,8),
  max_position_size   numeric(20,8),
  max_total_exposure  numeric(20,8),
  max_trades_per_day  int,
  kill_switch_enabled boolean NOT NULL DEFAULT true,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now()
);
DROP TRIGGER IF EXISTS trg_riskprofiles_touch ON risk_profiles;
CREATE TRIGGER trg_riskprofiles_touch BEFORE UPDATE ON risk_profiles
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ---------------------------------------------------------------------------
-- SUBSCRIPTIONS - the fan-out edge; personal execution knobs live HERE
-- UNIQUE(instance, account) => one config on 2 accounts = 2 subscriptions
-- = 2 independent positions => per-leg tracking by construction (edge #7).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_strategy_subscriptions (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id              uuid NOT NULL REFERENCES users(id),
  shared_config_id uuid NOT NULL REFERENCES shared_strategy_configs(id),
  trading_account_id   uuid NOT NULL REFERENCES trading_accounts(id),
  risk_profile_id      uuid REFERENCES risk_profiles(id),
  quantity             numeric(20,8) NOT NULL DEFAULT 1,
  multiplier           numeric(20,8) NOT NULL DEFAULT 1,
  lot_size             numeric(20,8),
  capital_allocated    numeric(20,8),
  execution_mode       varchar(20) NOT NULL DEFAULT 'FIXED_QTY'
                       CHECK (execution_mode IN
                              ('FIXED_QTY','CAPITAL_PERCENT','RISK_PERCENT')),
  exec_params          jsonb NOT NULL DEFAULT '{}'::jsonb,
                       -- {"sl_pct":1.5,"tp_pct":3.0,"trailing":...} - personal,
                       -- execution-scope keys only, NEVER part of any hash
  trade_mode           varchar(10) NOT NULL DEFAULT 'paper'
                       CHECK (trade_mode IN ('paper','live')),
  is_active            boolean NOT NULL DEFAULT true,
  version              int NOT NULL DEFAULT 1,
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now(),
  UNIQUE (shared_config_id, trading_account_id)
);
CREATE INDEX IF NOT EXISTS idx_user_strategy_subs_user ON user_strategy_subscriptions (user_id) WHERE is_active;
CREATE INDEX IF NOT EXISTS idx_user_strategy_subs_config ON user_strategy_subscriptions (shared_config_id)
  WHERE is_active;
DROP TRIGGER IF EXISTS trg_user_strategy_subs_touch ON user_strategy_subscriptions;
CREATE TRIGGER trg_user_strategy_subs_touch BEFORE UPDATE ON user_strategy_subscriptions
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ============================================================================
-- SEED - EMA Crossover as the worked example (U1/U2/U3 scenario).
-- ControlPlaneSeeder performs the same inserts on startup and both are
-- idempotent, so running either (or both) is fine.
-- ============================================================================

INSERT INTO indicators (name, param_schema) VALUES
  ('EMA', '{"period":{"type":"int","min":2,"max":300}}'),
  ('RSI', '{"period":{"type":"int","min":2,"max":100}}')
ON CONFLICT (name) DO NOTHING;

INSERT INTO strategy_templates (id, name, description, rule_tree) VALUES
  ('00000000-0000-0000-0000-00000000e0a1', 'EMA Crossover',
   'Long when fast EMA crosses above slow EMA; exit on reverse cross or SL/TP.',
   '{"entry":{"cross_above":[{"ind":"EMA","params":{"period":"$fast"}},
                             {"ind":"EMA","params":{"period":"$slow"}}]},
     "exit":{"cross_below":[{"ind":"EMA","params":{"period":"$fast"}},
                            {"ind":"EMA","params":{"period":"$slow"}}]}}')
ON CONFLICT (name) DO NOTHING;

-- The strategy id is looked up by name rather than hard-coded: if
-- ControlPlaneSeeder already created the row on a previous boot it carries a
-- generated uuid, and hard-coding the literal above would make these inserts
-- fail on a foreign key. Either seeder can run first.
INSERT INTO strategy_param_definitions
  (strategy_id, parameter_key, data_type, scope, default_value, validation,
   display_label, display_order)
SELECT s.id, v.parameter_key, v.data_type, v.scope, v.default_value,
       v.validation::jsonb, v.display_label, v.display_order
FROM strategy_templates s
CROSS JOIN (VALUES
  ('fast',  'int',     'signal',    '9',   '{"min":2,"max":200}',             'Fast EMA period', 1),
  ('slow',  'int',     'signal',    '21',  '{"min":3,"max":300,"gt":"fast"}', 'Slow EMA period', 2),
  ('sl_pct','decimal', 'execution', '1.5', '{"min":0.1,"max":20}',            'Stop loss %',     3),
  ('tp_pct','decimal', 'execution', '3.0', '{"min":0.1,"max":50}',            'Take profit %',   4)
) AS v(parameter_key, data_type, scope, default_value, validation,
       display_label, display_order)
WHERE s.name = 'EMA Crossover'
ON CONFLICT (strategy_id, parameter_key) DO NOTHING;

-- ============================================================================
-- END OF CONTROL-PLANE SCHEMA
-- ============================================================================
