-- =============================================================================
-- Finance Tracker (native Android) — Supabase Schema v2
-- Run this entire script in the Supabase SQL Editor (dashboard → SQL → New query).
-- Idempotent: safe to re-run; drops and recreates everything.
--
-- Data model notes (mirrored by the app's local Room DB):
--   * amounts are stored in minor units (INTEGER cents)
--   * accounts belong to exactly one currency (accounts.currency_id)
--   * every transaction requires a category; 'Other' is seeded per type and
--     is the fallback when none is chosen
--   * every table has updated_at (server-authoritative, maintained by
--     triggers) for the app's last-write-wins pull + watermark sync
-- =============================================================================

-- =============================================================================
-- 0. Cleanup (idempotent)
-- =============================================================================

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
DROP FUNCTION IF EXISTS handle_new_user();
-- Note: the table drops below cascade-drop their update triggers, so the
-- generic function must be dropped after the tables.
DROP TABLE IF EXISTS transactions CASCADE;
DROP TABLE IF EXISTS presets CASCADE;
DROP TABLE IF EXISTS accounts CASCADE;
DROP TABLE IF EXISTS categories CASCADE;
DROP TABLE IF EXISTS currencies CASCADE;
DROP TABLE IF EXISTS profiles CASCADE;
DROP FUNCTION IF EXISTS touch_updated_at();
DROP FUNCTION IF EXISTS touch_transactions_updated_at();

-- =============================================================================
-- 1. Tables
-- =============================================================================

-- Currencies the user tracks (USD, LBP, EUR, ...)
CREATE TABLE currencies (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  code        VARCHAR(10) NOT NULL,            -- e.g. 'USD', 'LBP'
  symbol      VARCHAR(10) NOT NULL,            -- e.g. '$', 'LL'
  name        VARCHAR(50) NOT NULL,            -- e.g. 'US Dollar'
  is_default  BOOLEAN DEFAULT false,
  created_at  TIMESTAMPTZ DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  sync_version TEXT NOT NULL DEFAULT '',
  deleted_at  TIMESTAMPTZ,
  UNIQUE(user_id, code)
);

-- Spending / income categories, color-coded
CREATE TABLE categories (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name        VARCHAR(100) NOT NULL,
  type        VARCHAR(10) NOT NULL CHECK (type IN ('income', 'expense')),
  color       VARCHAR(7) NOT NULL,             -- hex e.g. '#4C9A63'
  is_default  BOOLEAN DEFAULT false,           -- seeded at signup vs user-created
  created_at  TIMESTAMPTZ DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  sync_version TEXT NOT NULL DEFAULT '',
  deleted_at TIMESTAMPTZ
);

-- Accounts — each belongs to ONE currency
CREATE TABLE accounts (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  currency_id UUID NOT NULL REFERENCES currencies(id) ON DELETE CASCADE,
  name        VARCHAR(100) NOT NULL,           -- e.g. 'Cash', 'Card'
  archived    BOOLEAN DEFAULT false,
  is_default  BOOLEAN NOT NULL DEFAULT false,
  created_at  TIMESTAMPTZ DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  sync_version TEXT NOT NULL DEFAULT '',
  deleted_at  TIMESTAMPTZ
);

-- Presets — quick-fill templates for the add-transaction form
CREATE TABLE presets (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id               UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name                  VARCHAR(100) NOT NULL,
  type                  VARCHAR(10) NOT NULL CHECK (type IN ('income', 'expense')),
  default_amount        BIGINT,                -- minor units
  default_currency_id   UUID REFERENCES currencies(id) ON DELETE SET NULL,
  default_category_id   UUID REFERENCES categories(id) ON DELETE SET NULL,
  default_account_id    UUID REFERENCES accounts(id) ON DELETE SET NULL,
  archived              BOOLEAN DEFAULT false,
  created_at            TIMESTAMPTZ DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  sync_version          TEXT NOT NULL DEFAULT '',
  deleted_at            TIMESTAMPTZ
);

-- Transactions — the core record
CREATE TABLE transactions (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  type          VARCHAR(10) NOT NULL CHECK (type IN ('income', 'expense')),
  amount        BIGINT NOT NULL CHECK (amount > 0),  -- minor units, always positive
  currency_id   UUID NOT NULL REFERENCES currencies(id) ON DELETE RESTRICT,
  category_id   UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
  account_id    UUID REFERENCES accounts(id) ON DELETE SET NULL,
  date          DATE NOT NULL DEFAULT CURRENT_DATE,
  title         TEXT,
  notes         TEXT,
  preset_id     UUID REFERENCES presets(id) ON DELETE SET NULL,
  created_at    TIMESTAMPTZ DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  sync_version  TEXT NOT NULL DEFAULT '',
  deleted_at    TIMESTAMPTZ
);

-- Display name + account creation date per user (matches the app's local `profiles` table).
CREATE TABLE profiles (
  user_id     UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  name        VARCHAR(100) NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  sync_version TEXT NOT NULL DEFAULT ''
);

-- =============================================================================
-- 2. Indexes
-- =============================================================================

CREATE INDEX idx_currencies_user_id        ON currencies(user_id);
CREATE INDEX idx_categories_user_id        ON categories(user_id);
CREATE INDEX idx_accounts_user_id          ON accounts(user_id);
CREATE INDEX idx_accounts_currency_id      ON accounts(currency_id);
CREATE INDEX idx_presets_user_id           ON presets(user_id);
CREATE INDEX idx_transactions_user_date    ON transactions(user_id, date DESC);
CREATE INDEX idx_transactions_currency_id  ON transactions(currency_id);
CREATE INDEX idx_transactions_category_id  ON transactions(category_id);
CREATE INDEX idx_transactions_account_id   ON transactions(account_id);
CREATE INDEX idx_transactions_preset_id    ON transactions(preset_id);
CREATE INDEX idx_currencies_sync_cursor    ON currencies(user_id, updated_at, id);
CREATE INDEX idx_categories_sync_cursor    ON categories(user_id, updated_at, id);
CREATE INDEX idx_accounts_sync_cursor      ON accounts(user_id, updated_at, id);
CREATE INDEX idx_presets_sync_cursor       ON presets(user_id, updated_at, id);
CREATE INDEX idx_transactions_sync_cursor  ON transactions(user_id, updated_at, id);

-- =============================================================================
-- 3. Row Level Security
-- =============================================================================

ALTER TABLE currencies    ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories    ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts      ENABLE ROW LEVEL SECURITY;
ALTER TABLE presets       ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions  ENABLE ROW LEVEL SECURITY;
ALTER TABLE profiles      ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['currencies','categories','accounts','presets','transactions','profiles']
  LOOP
    EXECUTE format('DROP POLICY IF EXISTS %I_select ON %I', t, t);
    EXECUTE format('DROP POLICY IF EXISTS %I_insert ON %I', t, t);
    EXECUTE format('DROP POLICY IF EXISTS %I_update ON %I', t, t);
    EXECUTE format('DROP POLICY IF EXISTS %I_delete ON %I', t, t);

    EXECUTE format('CREATE POLICY %I_select ON %I FOR SELECT TO authenticated USING ((select auth.uid()) = user_id)', t, t);
    EXECUTE format('CREATE POLICY %I_insert ON %I FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id)', t, t);
    EXECUTE format('CREATE POLICY %I_update ON %I FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id)', t, t);
    EXECUTE format('CREATE POLICY %I_delete ON %I FOR DELETE TO authenticated USING ((select auth.uid()) = user_id)', t, t);
  END LOOP;
END $$;

-- =============================================================================
-- 4. Explicit grants (in case API auto-exposure is off)
-- =============================================================================

GRANT SELECT, INSERT, UPDATE, DELETE ON currencies    TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON categories    TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON accounts      TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON presets       TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON transactions  TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON profiles      TO authenticated;

-- =============================================================================
-- 5. updated_at maintenance (server is the timestamp authority for LWW sync)
-- =============================================================================

CREATE OR REPLACE FUNCTION touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

CREATE TRIGGER set_currencies_updated_at   BEFORE INSERT OR UPDATE ON currencies   FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER set_categories_updated_at   BEFORE INSERT OR UPDATE ON categories   FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER set_accounts_updated_at     BEFORE INSERT OR UPDATE ON accounts     FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER set_presets_updated_at      BEFORE INSERT OR UPDATE ON presets      FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER set_transactions_updated_at BEFORE INSERT OR UPDATE ON transactions FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER set_profiles_updated_at     BEFORE INSERT OR UPDATE ON profiles     FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- =============================================================================
-- 6. Signup trigger — seed default data
-- =============================================================================

CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
  v_currency_id UUID;
BEGIN
  -- Default currency: USD + a Cash account under it
  INSERT INTO public.currencies (user_id, code, symbol, name, is_default)
  VALUES (NEW.id, 'USD', '$', 'US Dollar', true)
  RETURNING id INTO v_currency_id;

  INSERT INTO public.accounts (user_id, currency_id, name, is_default)
  VALUES (NEW.id, v_currency_id, 'Cash', true);

  INSERT INTO public.profiles (user_id, name)
  VALUES (NEW.id, 'You');
  -- Default expense categories (incl. the mandatory 'Other')
  INSERT INTO public.categories (user_id, name, type, color, is_default) VALUES
    (NEW.id, 'Groceries',     'expense', '#4C9A63', true),
    (NEW.id, 'Rent',          'expense', '#E8432E', true),
    (NEW.id, 'Utilities',     'expense', '#F4C430', true),
    (NEW.id, 'Transport',     'expense', '#77746C', true),
    (NEW.id, 'Dining Out',    'expense', '#E8432E', true),
    (NEW.id, 'Entertainment', 'expense', '#4C9A63', true),
    (NEW.id, 'Health',        'expense', '#E8432E', true),
    (NEW.id, 'Shopping',      'expense', '#F4C430', true),
    (NEW.id, 'Other',         'expense', '#77746C', true);

  -- Default income categories (incl. 'Other' so the fallback works both ways)
  INSERT INTO public.categories (user_id, name, type, color, is_default) VALUES
    (NEW.id, 'Salary',   'income', '#4C9A63', true),
    (NEW.id, 'Freelance','income', '#4C9A63', true),
    (NEW.id, 'Other',    'income', '#77746C', true);

  RETURN NEW;
END;
$$;

CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION handle_new_user();

-- =============================================================================
-- 7. Verification queries (run after, one at a time)
-- =============================================================================

-- Tables exist:
-- SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;

-- RLS is on:
-- SELECT tablename, rowsecurity FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename;

-- Trigger exists:
-- SELECT trigger_name, event_object_table FROM information_schema.triggers
-- WHERE event_object_schema = 'auth' AND trigger_name = 'on_auth_user_created';

-- updated_at triggers exist (expect 6 rows):
-- SELECT trigger_name FROM information_schema.triggers
-- WHERE trigger_name LIKE 'set_%updated_at' ORDER BY trigger_name;
