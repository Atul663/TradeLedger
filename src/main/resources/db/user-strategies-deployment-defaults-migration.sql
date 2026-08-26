-- ============================================================================
-- ONE-OFF MIGRATION: the deployment defaults on user_strategies.
--
-- Adds the four Deployment-group fixed parameters as columns on the strategy:
-- execution_mode, multiplier, capital_allocated, trade_mode. A deploy that does
-- not name one of these now inherits the strategy's value instead of a hardcoded
-- default.
--
-- Run this ONCE against any database that already holds strategies, BEFORE
-- booting the new build:
--
--   psql "$DB_URL" -f src/main/resources/db/user-strategies-deployment-defaults-migration.sql
--
-- WHY IT CANNOT BE LEFT TO HIBERNATE. Three of the four are NOT NULL, and
-- spring.jpa.hibernate.ddl-auto=update issues a bare
-- `ALTER TABLE ... ADD COLUMN ... not null` with no default - which PostgreSQL
-- refuses on a table that already has rows. The app would fail to start. Adding
-- the columns WITH their defaults first, as below, leaves ddl-auto with nothing
-- to do and it starts cleanly.
--
-- On an empty or brand-new database this script is unnecessary but harmless:
-- every step is guarded, so running it twice, or before the table exists, is a
-- no-op rather than an error.
--
-- The values chosen here are the same ones the entity defaults to, so every
-- existing strategy comes out of this migration deploying exactly the way it
-- deployed before it: FIXED_QTY, 1x, no earmarked capital, and PAPER. Nothing
-- goes live because a column appeared.
-- ============================================================================

DO $$
BEGIN
  IF to_regclass('public.user_strategies') IS NULL THEN
    RAISE NOTICE 'user_strategies does not exist yet - nothing to migrate';
    RETURN;
  END IF;

  ALTER TABLE user_strategies
    ADD COLUMN IF NOT EXISTS execution_mode    varchar(20)   NOT NULL DEFAULT 'FIXED_QTY',
    ADD COLUMN IF NOT EXISTS multiplier        numeric(20,8) NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS capital_allocated numeric(20,8),
    ADD COLUMN IF NOT EXISTS trade_mode        varchar(10)   NOT NULL DEFAULT 'paper';

  -- The same CHECKs user_strategy_subscriptions carries, so the strategy cannot
  -- hold a default the deployment it seeds would refuse.
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
                 WHERE conrelid = 'user_strategies'::regclass
                   AND conname = 'ck_user_strategies_execution_mode') THEN
    ALTER TABLE user_strategies
      ADD CONSTRAINT ck_user_strategies_execution_mode
      CHECK (execution_mode IN ('FIXED_QTY','CAPITAL_PERCENT','RISK_PERCENT'));
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint
                 WHERE conrelid = 'user_strategies'::regclass
                   AND conname = 'ck_user_strategies_trade_mode') THEN
    ALTER TABLE user_strategies
      ADD CONSTRAINT ck_user_strategies_trade_mode
      CHECK (trade_mode IN ('paper','live'));
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint
                 WHERE conrelid = 'user_strategies'::regclass
                   AND conname = 'ck_user_strategies_multiplier') THEN
    ALTER TABLE user_strategies
      ADD CONSTRAINT ck_user_strategies_multiplier CHECK (multiplier >= 0);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint
                 WHERE conrelid = 'user_strategies'::regclass
                   AND conname = 'ck_user_strategies_capital_allocated') THEN
    ALTER TABLE user_strategies
      ADD CONSTRAINT ck_user_strategies_capital_allocated
      CHECK (capital_allocated IS NULL OR capital_allocated >= 0);
  END IF;
END
$$;

-- Verify:
--   SELECT column_name, data_type, is_nullable, column_default
--   FROM information_schema.columns
--   WHERE table_name = 'user_strategies'
--     AND column_name IN ('execution_mode','multiplier','capital_allocated','trade_mode');
--   -- expect 4 rows; trade_mode defaults to 'paper'
