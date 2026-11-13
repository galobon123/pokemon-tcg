package com.utn.pokemontcg.game.deck;

import com.utn.pokemontcg.game.application.service.deck.DeckValidationService;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckCardInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeckValidationServiceTest {

    private final DeckValidationService validator = new DeckValidationService();

    @Test
    void shouldRejectDeckWithout60Cards() {
        var result = validator.validate(List.of(new DeckCardInput("xy1-1", "Card 1", "xy1", 10, "Pokemon", "Basic", false, true, false, 120, 30, 1)));
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("60")));
    }

    @Test
    void shouldRejectMoreThanOneAceSpec() {
        var cards = List.of(
            new DeckCardInput("xy1-1", "Ace 1", "xy1", 1, "Trainer", "ACE SPEC", false, false, true, null, null, null),
            new DeckCardInput("xy1-2", "Ace 2", "xy1", 1, "Trainer", "ACE SPEC", false, false, true, null, null, null),
            new DeckCardInput("xy1-3", "Basic", "xy1", 58, "Pokemon", "Basic", false, true, false, 120, 30, 1)
        );
        var result = validator.validate(cards);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("AS TÁCTICO")));
    }

    @Test
    void shouldRejectCardsOutsideXy1() {
        var cards = List.of(
            new DeckCardInput("bw1-1", "Wrong Set", "bw1", 4, "Pokemon", "Basic", false, true, false, 120, 30, 1),
            new DeckCardInput("xy1-2", "Basic", "xy1", 56, "Pokemon", "Basic", false, true, false, 120, 30, 1)
        );

        var result = validator.validate(cards);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("xy1")));
    }

    @Test
    void shouldAcceptValidDeck() {
        var cards = List.of(
            new DeckCardInput("xy1-1", "Basic Mon", "xy1", 4, "Pokemon", "Basic", false, true, false, 120, 30, 1),
            new DeckCardInput("xy1-2", "Energy", "xy1", 56, "Energy", "Basic", true, false, false, 0, 0, 0)
        );
        var result = validator.validate(cards);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void shouldRejectDeckWithoutBasicPokemon() {
        var cards = List.of(
            new DeckCardInput("xy1-2", "Energy", "xy1", 60, "Energy", "Basic", true, false, false, 0, 0, 0)
        );
        var result = validator.validate(cards);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Básico")));
    }

    @Test
    void shouldRejectMoreThanFourCopies() {
        var cards = List.of(
            new DeckCardInput("xy1-1", "Many Copies", "xy1", 5, "Trainer", "Item", false, false, false, null, null, null),
            new DeckCardInput("xy1-2", "Basic", "xy1", 55, "Pokemon", "Basic", false, true, false, 120, 30, 1)
        );
        var result = validator.validate(cards);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("excede")));
    }

    @Test
    void shouldAllowFourCopiesMax() {
        var cards = new java.util.ArrayList<>(List.of(
            new DeckCardInput("xy1-1", "Four Copies", "xy1", 4, "Trainer", "Item", false, false, false, null, null, null)
        ));
        for (int i = 0; i < 56; i++) {
            cards.add(new DeckCardInput("xy1-b" + i, "Basic " + i, "xy1", 1, "Pokemon", "Basic", false, true, false, 120, 30, 1));
        }
        var result = validator.validate(cards);
        assertTrue(result.valid());
    }

    @Test
    void setIdOfUsesCardIdPrefixWhenSetIdIsNull() {
        var cards = new java.util.ArrayList<>(List.of(
            new DeckCardInput("xy1-1", "Card", null, 4, "Pokemon", "Basic", false, true, false, 120, 30, 1)
        ));
        for (int i = 0; i < 56; i++) {
            cards.add(new DeckCardInput("xy1-b" + i, "Basic " + i, "xy1", 1, "Pokemon", "Basic", false, true, false, 120, 30, 1));
        }
        var result = validator.validate(cards);
        assertTrue(result.valid());
    }

    @Test
    void setIdOfUsesCardIdPrefixWhenSetIdIsBlank() {
        var cards = new java.util.ArrayList<>(List.of(
            new DeckCardInput("xy1-1", "Card", "  ", 4, "Pokemon", "Basic", false, true, false, 120, 30, 1)
        ));
        for (int i = 0; i < 56; i++) {
            cards.add(new DeckCardInput("xy1-b" + i, "Basic " + i, "xy1", 1, "Pokemon", "Basic", false, true, false, 120, 30, 1));
        }
        var result = validator.validate(cards);
        assertTrue(result.valid());
    }

    @Test
    void setIdOfReturnsEmptyForCardIdWithoutDash() {
        var cards = new java.util.ArrayList<>(List.of(
            new DeckCardInput("NoDash", "Weird", null, 4, "Pokemon", "Basic", false, true, false, 120, 30, 1)
        ));
        for (int i = 0; i < 56; i++) {
            cards.add(new DeckCardInput("xy1-b" + i, "Basic " + i, "xy1", 1, "Pokemon", "Basic", false, true, false, 120, 30, 1));
        }
        var result = validator.validate(cards);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("xy1")));
    }

    @Test
    void basicEnergyCardsAreNotSubjectToCopyLimit() {
        var cards = new java.util.ArrayList<>(List.of(
            new DeckCardInput("xy1-1", "Fire Energy", "xy1", 30, "Energy", "Basic", true, false, false, 0, 0, 0)
        ));
        for (int i = 0; i < 30; i++) {
            cards.add(new DeckCardInput("xy1-b" + i, "Basic " + i, "xy1", 1, "Pokemon", "Basic", false, true, false, 120, 30, 1));
        }
        var result = validator.validate(cards);
        assertTrue(result.valid());
    }
}
