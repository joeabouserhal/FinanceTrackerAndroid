-- Goal-completion transactions link back to their goal so deleting one can
-- reverse the goal (and its sibling withdrawals).
ALTER TABLE transactions ADD COLUMN goal_id UUID REFERENCES goals(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_transactions_goal_id ON transactions(goal_id);
