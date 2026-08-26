-- ============================================================================
-- ONE-OFF MIGRATION: Derivative.FUT was renamed to Derivative.FUTURES.
--
-- Hibernate 6.2+ emits a CHECK constraint for every @Enumerated(EnumType.STRING)
-- column, auto-named <table>_<column>_check and listing the constant names AS
-- THEY WERE WHEN THE COLUMN WAS CREATED:
--
--   ALTER TABLE user_strategies
--     ADD CONSTRAINT user_strategies_derivative_check
--     CHECK (derivative IN ('FUT','OPTION'));
--
-- spring.jpa.hibernate.ddl-auto=update adds missing tables and columns and
-- NOTHING ELSE - it never revisits a constraint that already exists. So the
-- database went on enforcing the old spelling after the enum was renamed, and
-- every write of a futures strategy failed:
--
--   new row for relation "user_strategies" violates check constraint
--   "user_strategies_derivative_check"
--
-- OPTION was unaffected, which is why this only ever showed up on FUTURES.
--
-- Rows written before the rename still hold 'FUT', which no longer maps to any
-- enum constant - reading one throws. They are migrated here too.
--
-- Run this ONCE against any database that predates the rename, BEFORE booting:
--
--   psql "$DB_URL" -f src/main/resources/db/user-strategies-derivative-rename-migration.sql
--
-- On a database created after the rename this is a no-op: the constraint it
-- drops already spells FUTURES, no row holds 'FUT', and the constraint it adds
-- is guarded. Running it twice is harmless.
-- ============================================================================

DO $$
DECLARE
  stale record;
  moved integer;
BEGIN
  IF to_regclass('public.user_strategies') IS NULL THEN
    RAISE NOTICE 'user_strategies does not exist yet - nothing to migrate';
    RETURN;
  END IF;

  -- Dropped BEFORE the data is moved: the old constraint forbids 'FUTURES', so
  -- the UPDATE below cannot run while it stands.
  --
  -- Found by definition rather than by name. The name Hibernate generates is an
  -- implementation detail and a hand-rolled one may be sitting here instead, but
  -- either way it is the only CHECK on this table that mentions the column.
  FOR stale IN
    SELECT conname, pg_get_constraintdef(oid) AS def
      FROM pg_constraint
     WHERE conrelid = 'public.user_strategies'::regclass
       AND contype = 'c'
       AND pg_get_constraintdef(oid) ILIKE '%derivative%'
  LOOP
    EXECUTE format('ALTER TABLE user_strategies DROP CONSTRAINT %I', stale.conname);
    RAISE NOTICE 'dropped % - was %', stale.conname, stale.def;
  END LOOP;

  UPDATE user_strategies SET derivative = 'FUTURES' WHERE derivative = 'FUT';
  GET DIAGNOSTICS moved = ROW_COUNT;
  IF moved > 0 THEN
    RAISE NOTICE 'moved % row(s) from FUT to FUTURES', moved;
  END IF;

  -- Added under a name of our own. Hibernate only generates its version when it
  -- creates the column, so on every database that already has one this is the
  -- constraint doing the work - and a name we chose is one a future rename can
  -- be written against.
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
                 WHERE conrelid = 'public.user_strategies'::regclass
                   AND conname = 'ck_user_strategies_derivative') THEN
    ALTER TABLE user_strategies
      ADD CONSTRAINT ck_user_strategies_derivative
      CHECK (derivative IN ('FUTURES','OPTION'));
    RAISE NOTICE 'added ck_user_strategies_derivative';
  END IF;
END
$$;

-- Verify:
--   SELECT conname, pg_get_constraintdef(oid)
--   FROM pg_constraint
--   WHERE conrelid = 'public.user_strategies'::regclass AND contype = 'c'
--     AND pg_get_constraintdef(oid) ILIKE '%derivative%';
--   -- expect exactly one row: ck_user_strategies_derivative
--   --   CHECK (derivative::text = ANY (ARRAY['FUTURES','OPTION']))
--
--   SELECT derivative, count(*) FROM user_strategies GROUP BY derivative;
--   -- expect no 'FUT'
