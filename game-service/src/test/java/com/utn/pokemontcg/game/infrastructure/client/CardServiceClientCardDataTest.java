package com.utn.pokemontcg.game.infrastructure.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CardServiceClientCardDataTest {

    @Test
    void fromMapParsesBasicFields() {
        Map<String, Object> map = Map.of(
            "id", "xy1-1",
            "name", "Venusaur",
            "supertype", "Pokemon",
            "subtypes", List.of("Stage 2"),
            "hp", 160,
            "retreatCost", 3
        );
        var data = CardServiceClient.CardData.fromMap(map);
        assertEquals("xy1-1", data.id());
        assertEquals("Venusaur", data.name());
        assertEquals("Pokemon", data.supertype());
        assertEquals("Stage 2", data.subtypes());
        assertEquals(160, data.hp());
        assertEquals(3, data.retreatCost());
    }

    @Test
    void fromMapParsesTypesAsFirstOfList() {
        Map<String, Object> map = Map.of("types", List.of("Grass", "Colorless"));
        var data = CardServiceClient.CardData.fromMap(map);
        assertEquals("Grass", data.energyType());
    }

    @Test
    void fromMapParsesWeaknessAndResistance() {
        Map<String, Object> map = Map.of(
            "weaknesses", List.of(Map.of("type", "Fire", "value", "×2")),
            "resistances", List.of(Map.of("type", "Water", "value", "-20"))
        );
        var data = CardServiceClient.CardData.fromMap(map);
        assertEquals("Fire", data.weaknessType());
        assertEquals(Integer.valueOf(2), data.weaknessAmount());
        assertEquals("Water", data.resistanceType());
        assertEquals(Integer.valueOf(-20), data.resistanceAmount());
    }

    @Test
    void fromMapParsesAttacks() {
        Map<String, Object> map = Map.of(
            "attacks", List.of(Map.of(
                "name", "Vine Whip",
                "text", "Deal 30 damage.",
                "damage", "30",
                "cost", List.of("Grass", "Colorless"),
                "convertedEnergyCost", 2
            ))
        );
        var data = CardServiceClient.CardData.fromMap(map);
        assertNotNull(data.attacks());
        assertEquals(1, data.attacks().size());
        assertEquals("Vine Whip", data.attacks().get(0).name());
        assertEquals("30", data.attacks().get(0).damage());
        assertEquals(2, data.attacks().get(0).convertedEnergyCost());
    }

    @Test
    void fromMapParsesAbilities() {
        Map<String, Object> map = Map.of(
            "abilities", List.of(Map.of("name", "Overgrow"))
        );
        var data = CardServiceClient.CardData.fromMap(map);
        assertEquals(1, data.abilities().size());
        assertEquals("Overgrow", data.abilities().get(0));
    }

    @Test
    void fromMapReturnsNullForMissingFields() {
        Map<String, Object> map = Map.of("id", "xy1-1");
        var data = CardServiceClient.CardData.fromMap(map);
        assertNull(data.name());
        assertNull(data.hp());
        assertNull(data.energyType());
        assertNull(data.weaknessType());
    }

    @Test
    void fromMapHandlesEmptyWeaknessesAndResistances() {
        Map<String, Object> map = Map.of("weaknesses", List.of(), "resistances", List.of());
        var data = CardServiceClient.CardData.fromMap(map);
        assertNull(data.weaknessType());
        assertNull(data.resistanceType());
    }

    @Test
    void fromMapSkipsNonMapAttackItems() {
        Map<String, Object> map = Map.of("attacks", List.of("invalid"));
        var data = CardServiceClient.CardData.fromMap(map);
        assertNotNull(data.attacks());
        assertTrue(data.attacks().isEmpty());
    }
}
