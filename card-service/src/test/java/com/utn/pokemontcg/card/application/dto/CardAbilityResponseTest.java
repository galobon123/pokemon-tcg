package com.utn.pokemontcg.card.application.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardAbilityResponseTest {

    @Test
    void exposesNameAndTextThroughRecordAccessors() {
        CardAbilityResponse ability = new CardAbilityResponse("Overgrow", "Once during your turn, you may heal 10 damage from this Pokemon.");

        assertEquals("Overgrow", ability.name());
        assertEquals("Once during your turn, you may heal 10 damage from this Pokemon.", ability.text());
    }

    @Test
    void recordsAreEqualWhenAllComponentsMatch() {
        CardAbilityResponse a = new CardAbilityResponse("Overgrow", "Heal effect");
        CardAbilityResponse b = new CardAbilityResponse("Overgrow", "Heal effect");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void recordsDifferWhenNameChanges() {
        CardAbilityResponse base = new CardAbilityResponse("Overgrow", "Heal effect");

        assertNotEquals(base, new CardAbilityResponse("Blaze", "Heal effect"));
    }

    @Test
    void recordsDifferWhenTextChanges() {
        CardAbilityResponse base = new CardAbilityResponse("Overgrow", "Heal effect");

        assertNotEquals(base, new CardAbilityResponse("Overgrow", "Different effect"));
    }

    @Test
    void toStringContainsComponentNamesAndValues() {
        CardAbilityResponse ability = new CardAbilityResponse("Overgrow", "Boost damage");

        String rendered = ability.toString();

        assertTrue(rendered.contains("name"), "toString should mention field 'name': " + rendered);
        assertTrue(rendered.contains("text"), "toString should mention field 'text': " + rendered);
        assertTrue(rendered.contains("Overgrow"), "toString should contain name value: " + rendered);
        assertTrue(rendered.contains("Boost damage"), "toString should contain text value: " + rendered);
    }
}
