package com.utn.pokemontcg.game.deck;

import com.utn.pokemontcg.game.application.service.deck.DeckValidationService;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckCardInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedStarterDeckTest {

    @Test
    void seedXy1GrassDeckIsValid() {
        DeckValidationService validator = new DeckValidationService();

        var result = validator.validate(List.of(
            new DeckCardInput(
                "xy1-1",
                "Venusaur-EX",
                "xy1",
                4,
                "Pokémon",
                "Basic,EX",
                false,
                true,
                false,
                180,
                60,
                3,
                "Poison Powder",
                "Your opponent's Active Pokemon is now Poisoned.",
                null,
                List.of("Grass", "Colorless", "Colorless"),
                4,
                null,
                List.of()
            ),
            new DeckCardInput(
                "xy1-132",
                "Grass Energy",
                "xy1",
                56,
                "Energy",
                "Basic",
                true,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                "Grass",
                List.of(),
                null,
                null,
                List.of()
            )
        ));

        assertTrue(result.valid(), () -> String.join(" ", result.errors()));
    }
}
