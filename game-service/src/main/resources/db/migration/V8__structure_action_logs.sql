ALTER TABLE game_action_logs
    ADD COLUMN IF NOT EXISTS action VARCHAR(64),
    ADD COLUMN IF NOT EXISTS player_id UUID,
    ADD COLUMN IF NOT EXISTS result TEXT,
    ADD COLUMN IF NOT EXISTS payload JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_game_action_logs_game_action
    ON game_action_logs (game_id, action);
