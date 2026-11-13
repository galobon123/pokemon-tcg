ALTER TABLE deck_cards
    ADD COLUMN IF NOT EXISTS weakness_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS weakness_amount INTEGER,
    ADD COLUMN IF NOT EXISTS resistance_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS resistance_amount INTEGER;
