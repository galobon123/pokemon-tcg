CREATE TABLE IF NOT EXISTS card_sets (
    id              VARCHAR(50) PRIMARY KEY,
    name            VARCHAR(200),
    api_updated_at  VARCHAR(50),
    total_cards_api INTEGER,
    last_sync_at    TIMESTAMPTZ
);
