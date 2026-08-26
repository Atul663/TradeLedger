-- ============================================================================
-- ONE-OFF MIGRATION: an index the case-insensitive indicator lookup can use.
--
-- A rule tree names an indicator by string and the platform matches it WITHOUT
-- regard to case, so every resolution runs
--
--   upper(name) = upper(?)
--
-- which the UNIQUE index on indicators.name cannot serve - a functional
-- predicate needs a functional index. Without this every lookup is a sequential
-- scan: cheap on a nine-row catalogue, and needless either way.
--
-- ddl-auto=update creates neither functional nor partial indexes, so this has to
-- be run by hand. It is in control-plane-schema.sql for databases created from
-- scratch; this file is for one that already exists.
--
--   psql "$DB_URL" -f src/main/resources/db/indicators-name-index-migration.sql
--
-- Not itself UNIQUE, deliberately: indicators.name already is, and a database
-- predating the casing fix may still hold two rows differing only by case (an
-- 'EMA AVERAGING' beside an 'EMA Averaging') which ControlPlaneSeeder converges
-- on the next boot. A unique index would refuse to be created until it had, and
-- turn a performance fix into a failed migration.
--
-- Safe at any time, on any size of table, and a no-op the second time.
-- ============================================================================

DO $$
BEGIN
  IF to_regclass('public.indicators') IS NULL THEN
    RAISE NOTICE 'indicators does not exist yet - nothing to migrate';
    RETURN;
  END IF;

  CREATE INDEX IF NOT EXISTS idx_indicators_name_ci ON indicators (upper(name));
  RAISE NOTICE 'idx_indicators_name_ci is in place';
END
$$;

-- Verify:
--   SELECT indexname, indexdef FROM pg_indexes
--   WHERE tablename = 'indicators';
--   -- expect idx_indicators_name_ci on upper(name::text)
--
--   EXPLAIN SELECT * FROM indicators WHERE upper(name) = upper('EMA Averaging');
--   -- expect an Index Scan once the catalogue is large enough for the planner
--   -- to prefer one; on a handful of rows a Seq Scan is still the right plan.
