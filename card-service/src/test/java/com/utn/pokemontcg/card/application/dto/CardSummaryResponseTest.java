package com.utn.pokemontcg.card.application.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardSummaryResponseTest {

    @Test
    void compactConstructorWithoutRetreatCostAndAbilitiesDefaultsThem() {
        CardSummaryResponse card = new CardSummaryResponse(
            "xy1-1",
            "Venusaur-EX",
            "xy1",
            "Pokemon",
            List.of("Basic"),
            List.of("Grass"),
            180,
            List.of(),
            List.of(),
            List.of(),
            "https://img.example/xy1-1.png"
        );

        assertEquals("xy1-1", card.id());
        assertEquals("Venusaur-EX", card.name());
        assertEquals("xy1", card.setId());
        assertEquals(180, card.hp());
        assertNull(card.retreatCost(), "retreatCost should default to null");
        assertNull(card.evolvesFrom(), "evolvesFrom should default to null");
        assertTrue(card.abilities().isEmpty(), "abilities should default to empty list");
        assertTrue(card.rules().isEmpty(), "rules should default to empty list");
    }

    @Test
    void compactConstructorWithRetreatCostKeepsAbilitiesAndRulesEmpty() {
        CardSummaryResponse card = new CardSummaryResponse(
            "xy1-1",
            "Venusaur-EX",
            "xy1",
            "Pokemon",
            List.of("Basic"),
            List.of("Grass"),
            180,
            List.of(),
            List.of(),
            List.of(),
            2,
            "https://img.example/xy1-1.png"
        );

        assertEquals(2, card.retreatCost());
        assertNull(card.evolvesFrom());
        assertTrue(card.abilities().isEmpty());
        assertTrue(card.rules().isEmpty());
    }

    @Test
    void fullConstructorPreservesAllFields() {
        CardAbilityResponse ability = new CardAbilityResponse("Overgrow", "Heal effect");

        CardSummaryResponse card = new CardSummaryResponse(
            "xy1-1",
            "Venusaur-EX",
            "xy1",
            "Pokemon",
            List.of("Basic"),
            List.of("Grass"),
            180,
            List.of(),
            List.of(),
            List.of(),
            2,
            "Ivysaur",
            List.of(ability),
            List.of("Rule one"),
            "https://img.example/xy1-1.png"
        );

        assertEquals(2, card.retreatCost());
        assertEquals("Ivysaur", card.evolvesFrom());
        assertEquals(List.of(ability), card.abilities());
        assertEquals(List.of("Rule one"), card.rules());
    }

    @Test
    void recordsAreEqualWhenAllFieldsMatch() {
        CardSummaryResponse a = buildFull();
        CardSummaryResponse b = buildFull();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void recordsDifferWhenAnyFieldChanges() {
        CardSummaryResponse base = buildFull();

        assertNotEquals(base, new CardSummaryResponse(
            "xy1-2", base.name(), base.setId(), base.supertype(),
            base.subtypes(), base.types(), base.hp(), base.attacks(),
            base.weaknesses(), base.resistances(), base.retreatCost(),
            base.evolvesFrom(), base.abilities(), base.rules(), base.imageUrl()
        ));
    }

    @Test
    void toStringContainsFieldNamesAndValues() {
        CardSummaryResponse card = buildFull();
        String rendered = card.toString();

        assertTrue(rendered.contains("id"), "toString should mention field 'id': " + rendered);
        assertTrue(rendered.contains("name"), "toString should mention field 'name': " + rendered);
        assertTrue(rendered.contains("Venusaur-EX"));
        assertTrue(rendered.contains("xy1-1"));
    }

    private CardSummaryResponse buildFull() {
        return new CardSummaryResponse(
            "xy1-1",
            "Venusaur-EX",
            "xy1",
            "Pokemon",
            List.of("Basic"),
            List.of("Grass"),
            180,
            List.of(),
            List.of(),
            List.of(),
            2,
            "Ivysaur",
            List.of(new CardAbilityResponse("Overgrow", "Heal")),
            List.of("Rule one"),
            "https://img.example/xy1-1.png"
        );
    }
}
