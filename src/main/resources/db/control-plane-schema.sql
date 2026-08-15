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

CREATE TABLE IF NOT EXISTS trading_accounts (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  exchange_id  uuid NOT NULL REFERENCES exchanges(id),
  account_name varchar(100) NOT NULL,
  is_active    boolean NOT NULL DEFAULT true,
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, exchange_id, account_name)
);
CREATE INDEX IF NOT EXISTS idx_taccounts_user ON trading_accounts (user_id) WHERE is_active;
DROP TRIGGER IF EXISTS trg_taccounts_touch ON trading_accounts;
CREATE TRIGGER trg_taccounts_touch BEFORE UPDATE ON trading_accounts
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- Vault references ONLY. No api_key/api_secret/passphrase columns: secrets
-- live in Vault; the adapter resolves vault_ref at startup.
CREATE TABLE IF NOT EXISTS account_credentials (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  trading_account_id uuid NOT NULL UNIQUE
                     REFERENCES trading_accounts(id) ON DELETE CASCADE,
  vault_ref          text NOT NULL,     -- e.g. secret/brokers/deribit/acct-123
  rotated_at         timestamptz,
  created_at         timestamptz NOT NULL DEFAULT now(),
  updated_at         timestamptz NOT NULL DEFAULT now()
);
DROP TRIGGER IF EXISTS trg_creds_touch ON account_credentials;
CREATE TRIGGER trg_creds_touch BEFORE UPDATE ON account_credentials
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ---------------------------------------------------------------------------
-- Strategy templates and their tunable knobs
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS strategies (
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
DROP TRIGGER IF EXISTS trg_strategies_touch ON strategies;
CREATE TRIGGER trg_strategies_touch BEFORE UPDATE ON strategies
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- Compute primitives (EMA, RSI, ...). One row + one O(1) engine class each.
CREATE TABLE IF NOT EXISTS indicator_defs (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name         varchar(50) NOT NULL UNIQUE,      -- 'EMA', 'RSI'
  param_schema jsonb NOT NULL,                   -- {"period":{"type":"int",...}}
  is_active    boolean NOT NULL DEFAULT true,
  created_at   timestamptz NOT NULL DEFAULT now()
);

-- Long-narrow: ONE ROW PER KNOB. RSI params, EMA params, SuperTrend params
-- all live here identically - new strategies are INSERTs, never ALTER TABLE.
-- scope drives the gap-#4 split: 'signal' -> instance (hashed),
-- 'execution' -> subscription (personal).
CREATE TABLE IF NOT EXISTS strategy_param_defs (
  id            bigserial PRIMARY KEY,
  strategy_id   uuid NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
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
CREATE TABLE IF NOT EXISTS strategy_instances (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  strategy_id   uuid NOT NULL REFERENCES strategies(id),
  symbol_id     uuid NOT NULL REFERENCES symbols(id),   -- SIGNAL symbol
  timeframe     varchar(20) NOT NULL,                   -- '5m','15m','1h',...
  signal_params jsonb NOT NULL,          -- canonicalized signal-scope params only
  config_hash   varchar(64) NOT NULL,
  supersedes_id uuid REFERENCES strategy_instances(id),
  status        varchar(20) NOT NULL DEFAULT 'active'
                CHECK (status IN ('active','retired')),
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (strategy_id, symbol_id, timeframe, config_hash)
);
CREATE INDEX IF NOT EXISTS idx_instances_params ON strategy_instances USING GIN (signal_params);
CREATE INDEX IF NOT EXISTS idx_instances_active ON strategy_instances (strategy_id)
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
DROP TRIGGER IF EXISTS trg_instances_hash ON strategy_instances;
CREATE TRIGGER trg_instances_hash BEFORE INSERT ON strategy_instances
  FOR EACH ROW EXECUTE FUNCTION set_config_hash();

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
CREATE TABLE IF NOT EXISTS subscriptions (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id              uuid NOT NULL REFERENCES users(id),
  strategy_instance_id uuid NOT NULL REFERENCES strategy_instances(id),
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
  UNIQUE (strategy_instance_id, trading_account_id)
);
CREATE INDEX IF NOT EXISTS idx_subs_user     ON subscriptions (user_id) WHERE is_active;
CREATE INDEX IF NOT EXISTS idx_subs_instance ON subscriptions (strategy_instance_id)
  WHERE is_active;
DROP TRIGGER IF EXISTS trg_subs_touch ON subscriptions;
CREATE TRIGGER trg_subs_touch BEFORE UPDATE ON subscriptions
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ============================================================================
-- SEED - EMA Crossover as the worked example (U1/U2/U3 scenario).
-- ControlPlaneSeeder performs the same inserts on startup and both are
-- idempotent, so running either (or both) is fine.
-- ============================================================================

INSERT INTO indicator_defs (name, param_schema) VALUES
  ('EMA', '{"period":{"type":"int","min":2,"max":300}}'),
  ('RSI', '{"period":{"type":"int","min":2,"max":100}}')
ON CONFLICT (name) DO NOTHING;

INSERT INTO strategies (id, name, description, rule_tree) VALUES
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
INSERT INTO strategy_param_defs
  (strategy_id, parameter_key, data_type, scope, default_value, validation,
   display_label, display_order)
SELECT s.id, v.parameter_key, v.data_type, v.scope, v.default_value,
       v.validation::jsonb, v.display_label, v.display_order
FROM strategies s
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
