-- ============================================================================
-- ONE-OFF MIGRATION: trade_mode is stored 'Paper' / 'Live'.
--
-- It used to be stored lower case - 'paper' / 'live' - so a caller sending
-- "Paper" got "paper" back, and a select offering Paper could not display the
-- value its own field reported. The stored form is now the displayed form.
--
-- THREE PLACES HOLD IT, and all three move together:
--   user_strategies.trade_mode             the author's deployment default
--   user_strategy_subscriptions.trade_mode what an account actually runs
--   fixed_parameters (name='tradeMode')    the options a form builds its select from
--
-- Each column carries a CHECK naming the old spelling, and ddl-auto=update never
-- revisits a constraint that already exists, so the checks are dropped and
-- rebuilt here rather than left to the application - the same trap that broke
-- FUTURES, see user-strategies-derivative-rename-migration.sql.
--
-- Run this ONCE against any database holding the old spelling, BEFORE booting
-- the new build - the application writes 'Paper' from the moment it starts, and
-- the old CHECK would refuse it:
--
--   psql "$DB_URL" -f src/main/resources/db/trade-mode-casing-migration.sql
--
-- NOTHING GOES LIVE BECAUSE OF THIS. 'paper' becomes 'Paper' and 'live' becomes
-- 'Live'; no row changes which of the two it holds. Guarded and idempotent, so
-- running it twice, or on a database already in the new casing, is a no-op.
-- ============================================================================

DO $$
DECLARE
  target text;
  stale  record;
  moved  integer;
BEGIN
  FOREACH target IN ARRAY ARRAY['user_strategies', 'user_strategy_subscriptions']
  LOOP
    IF to_regclass('public.' || target) IS NULL THEN
      RAISE NOTICE '% does not exist yet - skipping', target;
      CONTINUE;
    END IF;

    -- Dropped FIRST: the old constraint forbids 'Paper', so nothing below can
    -- run while it stands. Found by definition rather than by name - Hibernate
    -- generates <table>_<column>_check, the schema file names its own, and a
    -- database may carry either.
    FOR stale IN
      SELECT conname, pg_get_constraintdef(oid) AS def
        FROM pg_constraint
       WHERE conrelid = ('public.' || target)::regclass
         AND contype = 'c'
         AND pg_get_constraintdef(oid) ILIKE '%trade_mode%'
    LOOP
      EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', target, stale.conname);
      RAISE NOTICE 'dropped %.% - was %', target, stale.conname, stale.def;
    END LOOP;

    -- The default is a lower-case literal too, and a column default is not
    -- rewritten by an UPDATE. Dropped before the data moves, restored after.
    EXECUTE format('ALTER TABLE %I ALTER COLUMN trade_mode DROP DEFAULT', target);

    -- initcap(), not a CASE over the two values: it is the same rule the new
    -- spelling follows, so a third mode added later needs nothing changed here.
    EXECUTE format('UPDATE %I SET trade_mode = initcap(trade_mode) '
                   || 'WHERE trade_mode <> initcap(trade_mode)', target);
    GET DIAGNOSTICS moved = ROW_COUNT;
    RAISE NOTICE 'recased % row(s) in %', moved, target;

    EXECUTE format('ALTER TABLE %I ALTER COLUMN trade_mode SET DEFAULT ''Paper''', target);

    EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I CHECK (trade_mode IN (''Paper'',''Live''))',
                   target, 'ck_' || target || '_trade_mode');
    RAISE NOTICE 'added ck_%_trade_mode', target;
  END LOOP;

  -- The select a form builds from. ControlPlaneSeeder writes these in the new
  -- casing now, but seedFixedParameter is INSERT-ONLY by design - it will not
  -- correct a row that already exists, so an existing catalogue would go on
  -- offering the old spelling forever.
  IF to_regclass('public.fixed_parameters') IS NOT NULL THEN
    UPDATE fixed_parameters
       SET default_value = 'Paper',
           validation    = '{"options":["Paper","Live"]}'
     WHERE lower(name) = 'trademode';
    GET DIAGNOSTICS moved = ROW_COUNT;
    RAISE NOTICE 'updated % fixed_parameters row(s) for tradeMode', moved;
  END IF;
END
$$;

-- Verify:
--   SELECT conrelid::regclass AS tbl, conname, pg_get_constraintdef(oid)
--   FROM pg_constraint
--   WHERE contype = 'c' AND pg_get_constraintdef(oid) ILIKE '%trade_mode%';
--   -- expect one row per table, each naming 'Paper' and 'Live'
--
--   SELECT trade_mode, count(*) FROM user_strategies GROUP BY trade_mode
--   UNION ALL
--   SELECT trade_mode, count(*) FROM user_strategy_subscriptions GROUP BY trade_mode;
--   -- expect only Paper / Live
--
--   SELECT default_value, validation FROM fixed_parameters WHERE name = 'tradeMode';
--   -- expect Paper and {"options": ["Paper", "Live"]}
