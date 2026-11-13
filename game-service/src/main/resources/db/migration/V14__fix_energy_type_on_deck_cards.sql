-- Repara cartas de Energia cuyo energy_type quedo guardado como el supertype
-- literal "Energy" (o vacio/nulo) en lugar del tipo real del elemento.
-- El tipo se deduce del nombre, replicando DeckService.inferEnergyType.
UPDATE deck_cards
SET energy_type = CASE
    WHEN LOWER(name) LIKE '%fire%'      THEN 'Fire'
    WHEN LOWER(name) LIKE '%water%'     THEN 'Water'
    WHEN LOWER(name) LIKE '%grass%'     THEN 'Grass'
    WHEN LOWER(name) LIKE '%lightning%' THEN 'Lightning'
    WHEN LOWER(name) LIKE '%psychic%'   THEN 'Psychic'
    WHEN LOWER(name) LIKE '%fighting%'  THEN 'Fighting'
    WHEN LOWER(name) LIKE '%darkness%'  THEN 'Darkness'
    WHEN LOWER(name) LIKE '%metal%'     THEN 'Metal'
    WHEN LOWER(name) LIKE '%fairy%'     THEN 'Fairy'
    WHEN LOWER(name) LIKE '%dragon%'    THEN 'Dragon'
    ELSE 'Colorless'
END
WHERE LOWER(type) = 'energy'
  AND (energy_type IS NULL OR energy_type = '' OR energy_type = 'Energy');
