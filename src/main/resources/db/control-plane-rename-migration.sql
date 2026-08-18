-- ============================================================================
-- ONE-OFF MIGRATION: table renames for the strategy module and the auth tables.
--
-- Run this ONCE against any database created before the rename, BEFORE booting
-- the new build. Order matters: spring.jpa.hibernate.ddl-auto=update does not
-- rename anything - it would see the new names as missing and create a second,
-- empty set of tables alongside the populated old ones.
--
--   psql "$DB_URL" -f src/main/resources/db/control-plane-rename-migration.sql
--   psql "$DB_URL" -f src/main/resources/db/control-plane-schema.sql
--
-- The second command is not optional: it recreates the triggers under their new
-- names and creates the user_strategies tables.
--
-- Idempotent, and safe from any starting point. Each step is guarded on the old
-- name still existing AND the new name not existing, so running it twice, or
-- against a database that only got part way through an earlier revision of this
-- script, or against a fresh database that never had the old names, is a no-op
-- rather than an error.
--
--   OLD                      ->  NEW
--   strategies               ->  strategy_templates
--   strategy_param_defs      ->  strategy_param_definitions
--   strategy_indicators      ->  strategy_indicator_links
--   strategy_parameters      ->  strategy_parameter_links
--   strategy_instances       ->  shared_strategy_configs
--   subscriptions            ->  user_strategy_subscriptions
--   strategy_config          ->  platform_strategy_toggles
--   indicator_defs           ->  indicators
--   indicator_parameters     ->  indicator_parameter_links
--   user_details             ->  google_auth_tokens
--   dhan_access_token        ->  dhan_access_tokens
--   subscriptions.strategy_instance_id
--                            ->  user_strategy_subscriptions.shared_config_id
--
-- NEW TABLES (created by control-plane-schema.sql, not here):
--   user_strategies / user_strategy_indicators / user_strategy_parameters
-- ============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Tables.
--
-- The strategy_template_* entries are intermediate names from an earlier
-- revision of this script. They are listed so a database that already ran that
-- revision lands on the same final names; on any other database they simply do
-- not match and are skipped.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
  r record;
BEGIN
  FOR r IN SELECT * FROM (VALUES
      ('strategies',                   'strategy_templates'),
      ('strategy_param_defs',          'strategy_param_definitions'),
      ('strategy_template_params',     'strategy_param_definitions'),
      ('strategy_indicators',          'strategy_indicator_links'),
      ('strategy_template_indicators', 'strategy_indicator_links'),
      ('strategy_parameters',          'strategy_parameter_links'),
      ('strategy_template_parameters', 'strategy_parameter_links'),
      ('strategy_instances',           'shared_strategy_configs'),
      ('subscriptions',                'user_strategy_subscriptions'),
      ('strategy_config',              'platform_strategy_toggles'),
      ('indicator_defs',               'indicators'),
      ('indicator_parameters',         'indicator_parameter_links'),
      ('user_details',                 'google_auth_tokens'),
      ('dhan_access_token',            'dhan_access_tokens')
    ) AS v(old_name, new_name)
  LOOP
    IF to_regclass(r.old_name) IS NOT NULL AND to_regclass(r.new_name) IS NULL THEN
      EXECUTE format('ALTER TABLE %I RENAME TO %I', r.old_name, r.new_name);
      RAISE NOTICE 'renamed table % -> %', r.old_name, r.new_name;
    END IF;
  END LOOP;
END $$;

-- ---------------------------------------------------------------------------
-- 2. The one column rename: "instance" read as per-user, which it never was.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
              WHERE table_name = 'user_strategy_subscriptions'
                AND column_name = 'strategy_instance_id') THEN
    ALTER TABLE user_strategy_subscriptions
      RENAME COLUMN strategy_instance_id TO shared_config_id;
    RAISE NOTICE 'renamed column strategy_instance_id -> shared_config_id';
  END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 3. Indexes. Renaming a table leaves its indexes under their old names.
-- ---------------------------------------------------------------------------
ALTER INDEX IF EXISTS idx_instances_params RENAME TO idx_shared_configs_params;
ALTER INDEX IF EXISTS idx_instances_active RENAME TO idx_shared_configs_active;
ALTER INDEX IF EXISTS idx_subs_user        RENAME TO idx_user_strategy_subs_user;
ALTER INDEX IF EXISTS idx_subs_instance    RENAME TO idx_user_strategy_subs_config;

-- ---------------------------------------------------------------------------
-- 4. Named constraints. Only the ones the JPA entities declare by name are
--    listed - Postgres-generated names (strategies_name_key and friends) are
--    invisible to the application and renaming them buys nothing.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
  r record;
BEGIN
  FOR r IN SELECT * FROM (VALUES
      ('shared_strategy_configs',     'uq_instances_dedup',
                                      'uq_shared_configs_dedup'),
      ('user_strategy_subscriptions', 'uq_subs_instance_account',
                                      'uq_user_strategy_subs_config_account'),
      ('strategy_param_definitions',  'uq_param_defs_strategy_key',
                                      'uq_param_definitions_strategy_key'),
      ('strategy_param_definitions',  'uq_template_params_strategy_key',
                                      'uq_param_definitions_strategy_key'),
      ('strategy_indicator_links',    'uq_strategy_indicators_strategy_indicator',
                                      'uq_strategy_indicator_links'),
      ('strategy_indicator_links',    'uq_template_indicators_strategy_indicator',
                                      'uq_strategy_indicator_links'),
      ('strategy_parameter_links',    'uq_strategy_parameters',
                                      'uq_strategy_parameter_links'),
      ('strategy_parameter_links',    'uq_template_parameters',
                                      'uq_strategy_parameter_links'),
      ('indicator_parameter_links',   'uq_indicator_parameters',
                                      'uq_indicator_parameter_links')
    ) AS v(table_name, old_name, new_name)
  LOOP
    IF to_regclass(r.table_name) IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname = r.old_name)
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = r.new_name) THEN
      EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I',
                     r.table_name, r.old_name, r.new_name);
      RAISE NOTICE 'renamed constraint % -> %', r.old_name, r.new_name;
    END IF;
  END LOOP;
END $$;

-- ---------------------------------------------------------------------------
-- 5. Triggers. Dropped rather than renamed - control-plane-schema.sql recreates
--    them under the new names on its next run, and leaving the old ones in
--    place would fire two identical touch_updated_at()/set_config_hash() calls
--    per row.
-- ---------------------------------------------------------------------------
-- ---------------------------------------------------------------------------
-- 6. Retire user_saved_strategies.
--
-- A short-lived earlier shape that stored a user's values as two jsonb blobs.
-- It is replaced by user_strategies + user_strategy_indicators +
-- user_strategy_parameters, which hold the same information relationally.
--
-- Dropped ONLY when empty. If it has rows, this leaves it alone and says so:
-- the values inside those blobs have to be turned into parameter rows, and
-- guessing at that silently would be worse than stopping.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
  rows_left bigint;
BEGIN
  IF to_regclass('user_saved_strategies') IS NOT NULL THEN
    EXECUTE 'SELECT count(*) FROM user_saved_strategies' INTO rows_left;
    IF rows_left = 0 THEN
      DROP TABLE user_saved_strategies;
      RAISE NOTICE 'dropped empty user_saved_strategies (replaced by user_strategies)';
    ELSE
      RAISE WARNING 'user_saved_strategies still holds % row(s) and was NOT dropped. '
                    'Convert its signal_params/exec_params jsonb into '
                    'user_strategy_parameters rows, then drop it by hand.', rows_left;
    END IF;
  END IF;
END $$;

DROP TRIGGER IF EXISTS trg_strategies_touch ON strategy_templates;
DROP TRIGGER IF EXISTS trg_instances_hash   ON shared_strategy_configs;
DROP TRIGGER IF EXISTS trg_subs_touch       ON user_strategy_subscriptions;

COMMIT;

-- ============================================================================
-- Verify:
--   \dt
--   \d user_strategy_subscriptions
--   SELECT count(*) FROM user_strategy_subscriptions;   -- row counts unchanged
-- Then run control-plane-schema.sql to restore the triggers and create
-- the user_strategies tables.
-- ============================================================================
