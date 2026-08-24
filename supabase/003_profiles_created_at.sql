-- =============================================================================
-- Finance Tracker — Schema v3: profiles.created_at
--
-- Adds the account-creation date to profiles (shown as
-- "Been a user since {date}" on the Options page) and backfills it from
-- auth.users. Also creates a profile row for any user who signed up before
-- the profiles table existed (v2).
-- =============================================================================

-- 1. Column + backfill from the auth timestamp ---------------------------
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

UPDATE public.profiles p
SET created_at = u.created_at
FROM auth.users u
WHERE p.user_id = u.id AND p.created_at = now();

-- 2. Profile rows for legacy users ----------------------------------------
INSERT INTO public.profiles (user_id, name, created_at)
SELECT u.id, 'You', u.created_at
FROM auth.users u
WHERE u.id NOT IN (SELECT user_id FROM public.profiles);

-- 3. Verification ----------------------------------------------------------
-- SELECT p.user_id, p.name, p.created_at, u.created_at
-- FROM public.profiles p JOIN auth.users u ON u.id = p.user_id
-- WHERE p.created_at = u.created_at LIMIT 5;
