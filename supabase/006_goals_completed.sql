-- 006_goals_completed.sql
-- Marked-complete goals: kept for the "Completed goals" view.
ALTER TABLE goals ADD COLUMN IF NOT EXISTS completed BOOLEAN NOT NULL DEFAULT false;
