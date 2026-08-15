-- ============================================================================
-- OPTIONAL sample reference data.
--
-- Not part of trading_platform_schema.sql and NOT run automatically. It exists
-- because exchanges / symbols / risk_profiles are operational master data with
-- no owner column and no write API (see ReferenceDataService for why they are
-- read-only), which means a fresh database has nothing for a subscription's
-- exchange_id / symbol_id / risk_profile_id to point at.
--
-- In production these rows come from the venue's instrument feed and from
-- whoever governs platform risk. Run this only to get a working environment for
-- development or a demo:
--
--   psql "$DB_URL" -f src/main/resources/db/control-plane-schema.sql
--   psql "$DB_URL" -f src/main/resources/db/control-plane-sample-data.sql
--
-- Idempotent: safe to run more than once.
-- ============================================================================

INSERT INTO exchanges (name, code, description) VALUES
  ('National Stock Exchange of India', 'NSE', 'NSE cash, futures and options')
ON CONFLICT (code) DO NOTHING;

-- Indicators run on the UNDERLYING; an index row is the usual signal symbol.
INSERT INTO symbols (exchange_id, symbol, instrument_type, tick_size, min_qty)
SELECT e.id, 'NIFTY', 'index', 0.05, 1
FROM exchanges e
WHERE e.code = 'NSE'
ON CONFLICT (exchange_id, symbol) DO NOTHING;

INSERT INTO symbols (exchange_id, symbol, base_asset, quote_asset, instrument_type,
                     tick_size, min_qty)
SELECT e.id, 'RELIANCE', 'RELIANCE', 'INR', 'spot', 0.05, 1
FROM exchanges e
WHERE e.code = 'NSE'
ON CONFLICT (exchange_id, symbol) DO NOTHING;

-- risk_profiles.name is not unique in the schema, so this guards on absence
-- rather than ON CONFLICT.
INSERT INTO risk_profiles (name, description, max_daily_loss, max_drawdown,
                           max_position_size, max_trades_per_day)
SELECT 'Conservative', 'Low exposure defaults for paper trading',
       5000, 10000, 50000, 10
WHERE NOT EXISTS (SELECT 1 FROM risk_profiles WHERE name = 'Conservative');
