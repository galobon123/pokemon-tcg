CREATE TABLE IF NOT EXISTS cards (
    id          VARCHAR(50)  PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    set_id      VARCHAR(50)  NOT NULL,
    supertype   VARCHAR(50),
    subtypes    TEXT,
    types       TEXT,
    hp          INTEGER,
    attacks     TEXT,
    weaknesses  TEXT,
    resistances TEXT,
    image_url   TEXT,
    raw_data    TEXT,
    imported_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cards_set_id ON cards(set_id);
CREATE INDEX IF NOT EXISTS idx_cards_name   ON cards(name);
