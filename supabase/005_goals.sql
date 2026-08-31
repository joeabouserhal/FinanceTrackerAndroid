-- 005_goals.sql
-- Monetary goals: target amount per currency (optionally per account).
CREATE TABLE IF NOT EXISTS goals (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name          VARCHAR(100) NOT NULL,
  target_minor  BIGINT NOT NULL CHECK (target_minor > 0),
  currency_id   UUID NOT NULL REFERENCES currencies(id) ON DELETE CASCADE,
  account_id    UUID REFERENCES accounts(id) ON DELETE CASCADE,
  created_at    TIMESTAMPTZ DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_goals_user_id     ON goals(user_id);
CREATE INDEX IF NOT EXISTS idx_goals_currency_id ON goals(currency_id);

ALTER TABLE goals ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "goals_own_read"   ON goals;
DROP POLICY IF EXISTS "goals_own_insert" ON goals;
DROP POLICY IF EXISTS "goals_own_update" ON goals;
DROP POLICY IF EXISTS "goals_own_delete" ON goals;

CREATE POLICY "goals_own_read"   ON goals FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "goals_own_insert" ON goals FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "goals_own_update" ON goals FOR UPDATE TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "goals_own_delete" ON goals FOR DELETE TO authenticated USING (auth.uid() = user_id);

DROP TRIGGER IF EXISTS set_goals_updated_at ON goals;
CREATE TRIGGER set_goals_updated_at
  BEFORE UPDATE ON goals
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

GRANT SELECT, INSERT, UPDATE, DELETE ON goals TO authenticated;
