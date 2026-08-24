-- 004_accounts_is_default.sql
-- Default account per currency (picked by default in add transaction / add preset).
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT false;

-- Promote one existing active account per (user, currency) so current data
-- has exactly one default.
UPDATE accounts
SET is_default = true
WHERE archived = false
  AND id IN (
    SELECT DISTINCT ON (user_id, currency_id) id
    FROM accounts
    WHERE archived = false
    ORDER BY user_id, currency_id, created_at ASC
  );
