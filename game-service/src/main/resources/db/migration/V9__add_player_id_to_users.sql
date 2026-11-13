CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS player_id UUID;

UPDATE users
SET player_id = uuid_generate_v4()
WHERE player_id IS NULL;

ALTER TABLE users
    ALTER COLUMN player_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_player_id
    ON users (player_id);
