INSERT INTO decks (id, player_id, name)
VALUES (
    '00000000-0000-0000-0000-000000000101',
    '00000000-0000-0000-0000-000000000001',
    'Seed XY1 Planta'
)
ON CONFLICT (id) DO UPDATE
SET player_id = EXCLUDED.player_id,
    name = EXCLUDED.name;

DELETE FROM deck_cards
WHERE deck_id = '00000000-0000-0000-0000-000000000101';

INSERT INTO deck_cards (
    deck_id,
    card_id,
    name,
    set_id,
    quantity,
    type,
    subtype,
    basic_energy,
    basic_pokemon,
    ace_spec,
    hp,
    attack_damage,
    attack_required_energy,
    energy_type,
    attack_cost,
    retreat_cost,
    evolves_from,
    abilities,
    attack_name,
    attack_text
)
VALUES
(
    '00000000-0000-0000-0000-000000000101',
    'xy1-1',
    'Venusaur-EX',
    'xy1',
    4,
    'Pokémon',
    'Basic,EX',
    false,
    true,
    false,
    180,
    60,
    3,
    null,
    'Grass,Colorless,Colorless',
    4,
    null,
    null,
    'Poison Powder',
    'Your opponent''s Active Pokemon is now Poisoned.'
),
(
    '00000000-0000-0000-0000-000000000101',
    'xy1-132',
    'Grass Energy',
    'xy1',
    56,
    'Energy',
    'Basic',
    true,
    false,
    false,
    null,
    null,
    null,
    'Grass',
    null,
    null,
    null,
    null,
    null,
    null
);
