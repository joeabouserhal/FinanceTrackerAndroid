-- =============================================================================
-- Finance Tracker (native Android) — Supabase Schema v2
-- Run this entire script in the Supabase SQL Editor (dashboard → SQL → New query).
-- DESTRUCTIVE baseline for NEW / DISPOSABLE projects only. Never rerun on live data.
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
DROP TABLE IF EXISTS goals CASCADE;
DROP TABLE IF EXISTS sync_operations CASCADE;
DROP TABLE IF EXISTS sync_deleted_records CASCADE;
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

CREATE TABLE goals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name VARCHAR(200) NOT NULL,
  target_minor BIGINT NOT NULL CHECK (target_minor > 0),
  currency_id UUID NOT NULL REFERENCES currencies(id) ON DELETE CASCADE,
  account_id UUID REFERENCES accounts(id) ON DELETE CASCADE,
  completed BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  sync_version TEXT NOT NULL DEFAULT '',
  deleted_at TIMESTAMPTZ
);

-- Transactions — the core record
CREATE TABLE transactions (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  type          VARCHAR(10) NOT NULL CHECK (type IN ('income', 'expense', 'goal')),
  amount        BIGINT NOT NULL CHECK (amount > 0),  -- minor units, always positive
  currency_id   UUID NOT NULL REFERENCES currencies(id) ON DELETE RESTRICT,
  category_id   UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
  account_id    UUID REFERENCES accounts(id) ON DELETE SET NULL,
  date          DATE NOT NULL DEFAULT CURRENT_DATE,
  title         TEXT,
  notes         TEXT,
  preset_id     UUID REFERENCES presets(id) ON DELETE SET NULL,
  goal_id       UUID REFERENCES goals(id) ON DELETE SET NULL,
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
CREATE INDEX idx_transactions_goal_id      ON transactions(goal_id);
CREATE INDEX idx_goals_currency_id         ON goals(currency_id);
CREATE INDEX idx_goals_account_id          ON goals(account_id);
CREATE INDEX idx_goals_sync_cursor         ON goals(user_id, updated_at, id);
CREATE INDEX idx_presets_default_currency_id ON presets(default_currency_id);
CREATE INDEX idx_presets_default_category_id ON presets(default_category_id);
CREATE INDEX idx_presets_default_account_id ON presets(default_account_id);
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
ALTER TABLE goals         ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['currencies','categories','accounts','presets','goals','transactions','profiles']
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
GRANT SELECT, INSERT, UPDATE, DELETE ON goals         TO authenticated;

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
CREATE TRIGGER set_goals_updated_at        BEFORE INSERT OR UPDATE ON goals        FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

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
-- BEGIN SYNC REPAIR PROTOCOL
CREATE TABLE IF NOT EXISTS public.sync_operations (
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  operation_id uuid NOT NULL,
  result jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, operation_id)
);
-- A delete that arrives before an insert must still prevent resurrection.
CREATE TABLE IF NOT EXISTS public.sync_deleted_records (
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  table_name text NOT NULL CHECK (table_name IN ('currencies','categories','accounts','presets','goals','transactions')),
  record_id uuid NOT NULL,
  sync_version text NOT NULL,
  deleted_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, table_name, record_id)
);
ALTER TABLE public.sync_operations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sync_deleted_records ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.sync_operations, public.sync_deleted_records FROM anon;
GRANT SELECT, INSERT, UPDATE ON public.sync_operations, public.sync_deleted_records TO authenticated;
DO $policies$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['sync_operations','sync_deleted_records'] LOOP
    EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I',t||'_select',t);
    EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I',t||'_insert',t);
    EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I',t||'_update',t);
    EXECUTE format('CREATE POLICY %I ON public.%I FOR SELECT TO authenticated USING ((select auth.uid())=user_id)',t||'_select',t);
    EXECUTE format('CREATE POLICY %I ON public.%I FOR INSERT TO authenticated WITH CHECK ((select auth.uid())=user_id)',t||'_insert',t);
    EXECUTE format('CREATE POLICY %I ON public.%I FOR UPDATE TO authenticated USING ((select auth.uid())=user_id) WITH CHECK ((select auth.uid())=user_id)',t||'_update',t);
  END LOOP;
END;
$policies$;

CREATE OR REPLACE FUNCTION public.apply_sync_mutation(
  p_table text, p_action text, p_payload jsonb, p_version text, p_operation_id uuid
) RETURNS jsonb
LANGUAGE plpgsql SECURITY INVOKER SET search_path = ''
AS $function$
DECLARE
  v_user uuid := auth.uid();
  v_key text;
  v_id uuid;
  v_version text := p_version;
  v_seen jsonb;
  v_row jsonb;
  v_data jsonb;
  v_result jsonb;
  v_columns text;
  v_count bigint;
  v_status text := 'applied';
  v_deleted public.sync_deleted_records%ROWTYPE;
  v_ref record;
  v_parent jsonb;
  v_other_default text;
BEGIN
  IF v_user IS NULL THEN RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='Sign in to sync'; END IF;
  IF p_table IS NULL OR p_table NOT IN ('currencies','categories','accounts','presets','goals','transactions','profiles')
     OR p_action IS NULL OR p_action NOT IN ('insert','update','delete')
     OR jsonb_typeof(p_payload) IS DISTINCT FROM 'object' OR p_operation_id IS NULL THEN
    RAISE EXCEPTION USING ERRCODE='22023', MESSAGE='Invalid sync mutation';
  END IF;
  v_key := CASE WHEN p_table='profiles' THEN 'user_id' ELSE 'id' END;
  v_id := nullif(p_payload->>v_key,'')::uuid;
  IF v_id IS NULL OR (p_table='profiles' AND v_id<>v_user)
     OR (p_payload ? 'user_id' AND (p_payload->>'user_id')::uuid IS DISTINCT FROM v_user) THEN
    RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='Sync owner mismatch';
  END IF;
  IF v_version IS NULL OR v_version !~ '^[0-9]{19}-[0-9]{6}-[A-Za-z0-9-]+$' THEN
    RAISE EXCEPTION USING ERRCODE='22023', MESSAGE='Invalid sync version';
  END IF;

  -- Serializes retries, defaults, and concurrent devices for this owner only.
  PERFORM pg_advisory_xact_lock(hashtextextended(v_user::text,0));
  EXECUTE format('SELECT to_jsonb(t) FROM public.%I t WHERE %I=$1 AND user_id=$2 FOR UPDATE',p_table,v_key)
    INTO v_row USING v_id,v_user;
  SELECT result INTO v_seen FROM public.sync_operations WHERE user_id=v_user AND operation_id=p_operation_id;
  -- Never trust legacy acknowledgments: the old EXECUTE/FOUND bug could report
  -- an insert as applied without inserting a row. Protocol 2 verifies effects.
  IF v_seen->>'protocol'='2' THEN
    IF v_seen->>'table'<>p_table OR v_seen->>'id'<>v_id::text OR v_seen->>'version'<>v_version THEN
      RAISE EXCEPTION USING ERRCODE='22023', MESSAGE='Operation id reused for different mutation';
    END IF;
    RETURN v_seen || jsonb_build_object('row',v_row);
  END IF;
  SELECT * INTO v_deleted FROM public.sync_deleted_records
    WHERE user_id=v_user AND table_name=p_table AND record_id=v_id;

  IF p_action='delete' THEN
    IF p_table='profiles' THEN RAISE EXCEPTION USING ERRCODE='22023', MESSAGE='Profiles cannot be deleted through sync'; END IF;
    IF v_row IS NOT NULL AND coalesce(v_row->>'sync_version','')>v_version AND v_row->>'deleted_at' IS NULL THEN
      v_status := 'stale';
    ELSE
      INSERT INTO public.sync_deleted_records(user_id,table_name,record_id,sync_version)
        VALUES(v_user,p_table,v_id,v_version)
        ON CONFLICT(user_id,table_name,record_id) DO UPDATE
        SET sync_version=greatest(public.sync_deleted_records.sync_version,excluded.sync_version);
      IF v_row IS NOT NULL THEN
        EXECUTE format('UPDATE public.%I SET deleted_at=coalesce(deleted_at,clock_timestamp()), sync_version=greatest(sync_version,$1) WHERE id=$2 AND user_id=$3',p_table)
          USING v_version,v_id,v_user;
        GET DIAGNOSTICS v_count = ROW_COUNT;
        IF v_count<>1 THEN RAISE EXCEPTION USING ERRCODE='40001', MESSAGE='Delete did not affect expected row'; END IF;
      END IF;
      v_status := 'deleted';
    END IF;
  ELSIF v_deleted.record_id IS NOT NULL OR v_row->>'deleted_at' IS NOT NULL THEN
    v_status := 'deleted';
  ELSIF v_row IS NOT NULL AND coalesce(v_row->>'sync_version','')>=v_version THEN
    -- Resolve stale mutations before checking obsolete references.
    v_status := 'stale';
  ELSE
    v_data := jsonb_build_object(
      'created_at',clock_timestamp(),'updated_at',clock_timestamp(),
      'is_default',false,'archived',false,'completed',false
    ) || coalesce(v_row,'{}'::jsonb) || p_payload ||
      jsonb_build_object(v_key,v_id,'user_id',v_user,'sync_version',v_version);
    IF p_table<>'profiles' THEN v_data := v_data || jsonb_build_object('deleted_at',null); END IF;

    FOR v_ref IN SELECT * FROM (VALUES
      ('currency_id','currencies'),('default_currency_id','currencies'),
      ('category_id','categories'),('default_category_id','categories'),
      ('account_id','accounts'),('default_account_id','accounts'),
      ('preset_id','presets'),('goal_id','goals')
    ) AS refs(col,parent_table) LOOP
      IF nullif(v_data->>v_ref.col,'') IS NOT NULL THEN
        EXECUTE format('SELECT to_jsonb(t) FROM public.%I t WHERE id=$1 AND user_id=$2',v_ref.parent_table)
          INTO v_parent USING (v_data->>v_ref.col)::uuid,v_user;
        IF v_parent IS NULL OR
           (v_parent->>'deleted_at' IS NOT NULL AND (v_row->>v_ref.col) IS DISTINCT FROM (v_data->>v_ref.col)) THEN
          RAISE EXCEPTION USING ERRCODE='23503', MESSAGE='Missing or unavailable sync reference: '||v_ref.col;
        END IF;
        IF v_ref.col IN ('account_id','default_account_id') AND
           coalesce(v_data->>'currency_id',v_data->>'default_currency_id') IS NOT NULL AND
           v_parent->>'currency_id'<>coalesce(v_data->>'currency_id',v_data->>'default_currency_id') THEN
          RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='Account currency does not match';
        END IF;
      END IF;
    END LOOP;

    IF p_table IN ('currencies','accounts') AND coalesce((v_data->>'is_default')::boolean,false) THEN
      IF p_table='currencies' THEN
        SELECT max(sync_version) INTO v_other_default FROM public.currencies
          WHERE user_id=v_user AND id<>v_id AND is_default AND deleted_at IS NULL;
      ELSE
        SELECT max(sync_version) INTO v_other_default FROM public.accounts
          WHERE user_id=v_user AND id<>v_id AND currency_id=(v_data->>'currency_id')::uuid AND is_default AND deleted_at IS NULL;
      END IF;
      IF v_other_default>v_version THEN
        v_data := v_data || jsonb_build_object('is_default',false);
      ELSIF p_table='currencies' THEN
        UPDATE public.currencies SET is_default=false,sync_version=v_version
          WHERE user_id=v_user AND id<>v_id AND is_default AND deleted_at IS NULL;
      ELSE
        UPDATE public.accounts SET is_default=false,sync_version=v_version
          WHERE user_id=v_user AND id<>v_id AND currency_id=(v_data->>'currency_id')::uuid AND is_default AND deleted_at IS NULL;
      END IF;
    END IF;

    IF v_row IS NULL THEN
      EXECUTE format('INSERT INTO public.%1$I SELECT * FROM jsonb_populate_record(NULL::public.%1$I,$1)',p_table) USING v_data;
    ELSE
      SELECT string_agg(quote_ident(column_name),',' ORDER BY ordinal_position) INTO v_columns
        FROM information_schema.columns WHERE table_schema='public' AND table_name=p_table
        AND column_name NOT IN ('id','user_id','created_at','updated_at');
      EXECUTE format('UPDATE public.%1$I AS t SET (%2$s)=(SELECT %2$s FROM jsonb_populate_record(t,$1)) WHERE t.%3$I=$2 AND t.user_id=$3',p_table,v_columns,v_key)
        USING v_data,v_id,v_user;
    END IF;
    GET DIAGNOSTICS v_count = ROW_COUNT;
    IF v_count<>1 THEN RAISE EXCEPTION USING ERRCODE='40001', MESSAGE='Mutation did not affect expected row'; END IF;
  END IF;

  EXECUTE format('SELECT to_jsonb(t) FROM public.%I t WHERE %I=$1 AND user_id=$2',p_table,v_key)
    INTO v_row USING v_id,v_user;
  v_result := jsonb_build_object('protocol',2,'status',v_status,'table',p_table,'id',v_id::text,'version',v_version,'row',v_row);
  INSERT INTO public.sync_operations(user_id,operation_id,result) VALUES(v_user,p_operation_id,v_result)
    ON CONFLICT(user_id,operation_id) DO UPDATE SET result=excluded.result;
  RETURN v_result;
END;
$function$;
REVOKE ALL ON FUNCTION public.apply_sync_mutation(text,text,jsonb,text,uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.apply_sync_mutation(text,text,jsonb,text,uuid) TO authenticated;
-- END SYNC REPAIR PROTOCOL
