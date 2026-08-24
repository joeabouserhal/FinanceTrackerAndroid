-- =============================================================================
-- Finance Tracker — Schema v2 upgrade for EXISTING deployments
--
-- Run this ONLY if you already ran 001_schema.sql (v1) on your project and
-- have live data you do not want to drop. Fresh projects: just run
-- 001_schema.sql instead.
--
-- Adds what the offline-first sync engine needs:
--   * updated_at on currencies / categories / accounts / presets
--     (transactions already has it)
--   * server-side updated_at maintenance triggers on every table
--   * the profiles table (user display name)
-- =============================================================================

-- 1. updated_at columns ------------------------------------------------

ALTER TABLE currencies  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE categories  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE accounts    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE presets     ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Backfill sensible values for existing rows.
UPDATE currencies SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE categories SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE accounts   SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE presets    SET updated_at = created_at WHERE updated_at IS NULL;

-- 2. profiles table -----------------------------------------------------

CREATE TABLE IF NOT EXISTS profiles (
  user_id     UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  name        VARCHAR(100) NOT NULL,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS profiles_select ON profiles;
DROP POLICY IF EXISTS profiles_insert ON profiles;
DROP POLICY IF EXISTS profiles_update ON profiles;
DROP POLICY IF EXISTS profiles_delete ON profiles;

CREATE POLICY profiles_select ON profiles FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY profiles_insert ON profiles FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY profiles_update ON profiles FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY profiles_delete ON profiles FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON profiles TO authenticated;

-- 3. updated_at maintenance triggers ------------------------------------

CREATE OR REPLACE FUNCTION touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS set_currencies_updated_at   ON currencies;
DROP TRIGGER IF EXISTS set_categories_updated_at   ON categories;
DROP TRIGGER IF EXISTS set_accounts_updated_at     ON accounts;
DROP TRIGGER IF EXISTS set_presets_updated_at      ON presets;
DROP TRIGGER IF EXISTS set_transactions_updated_at ON transactions;
DROP TRIGGER IF EXISTS set_profiles_updated_at     ON profiles;

CREATE TRIGGER set_currencies_updated_at   BEFORE UPDATE ON currencies   FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER set_categories_updated_at   BEFORE UPDATE ON categories   FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER set_accounts_updated_at     BEFORE UPDATE ON accounts     FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER set_presets_updated_at      BEFORE UPDATE ON presets      FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER set_transactions_updated_at BEFORE UPDATE ON transactions FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER set_profiles_updated_at     BEFORE UPDATE ON profiles     FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- Note: the v1 script's touch_transactions_updated_at() function is left in
-- place; it is harmless once its trigger is replaced above.
-- Optionally: DROP FUNCTION IF EXISTS touch_transactions_updated_at();

-- 4. Verification (run after, one at a time) ----------------------------

-- Columns exist (expect updated_at on all 6):
-- SELECT table_name, column_name FROM information_schema.columns
-- WHERE table_schema = 'public' AND column_name = 'updated_at' ORDER BY table_name;

-- Triggers exist (expect 6 rows):
-- SELECT trigger_name FROM information_schema.triggers
-- WHERE trigger_name LIKE 'set_%updated_at' ORDER BY trigger_name;
