package com.utn.pokemontcg.card.application;

import com.utn.pokemontcg.card.application.dto.CardAbilityResponse;
import com.utn.pokemontcg.card.application.dto.CardAttackResponse;
import com.utn.pokemontcg.card.application.dto.CardSearchResponse;
import com.utn.pokemontcg.card.application.dto.CardSummaryResponse;
import com.utn.pokemontcg.card.application.dto.TypeModifierResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardNormalizerTest {

    private final CardNormalizer normalizer = new CardNormalizer();

    @Test
    void searchResponseMapsCountAndTotalCountDefaults() {
        Map<String, Object> raw = Map.of(
            "count", 0,
            "totalCount", 0,
            "data", List.of()
        );

        CardSearchResponse response = normalizer.searchResponse("pikachu", 20, raw);

        assertEquals("pikachu", response.query());
        assertEquals(20, response.pageSize());
        assertEquals(0, response.count());
        assertEquals(0, response.totalCount());
        assertTrue(response.cards().isEmpty());
    }

    @Test
    void searchResponseConvertsDataListOfMaps() {
        Map<String, Object> raw = Map.of(
            "count", 2,
            "totalCount", 50,
            "data", List.of(cardMap("xy1-1"), cardMap("xy1-2"))
        );

        CardSearchResponse response = normalizer.searchResponse("name:pikachu", 20, raw);

        assertEquals(2, response.count());
        assertEquals(50, response.totalCount());
        assertEquals(2, response.cards().size());
    }

    @Test
    void searchResponseDropsNonMapItemsInData() {
        Map<String, Object> raw = Map.of(
            "count", 1,
            "totalCount", 1,
            "data", List.of("not-a-map", cardMap("xy1-1"), 42)
        );

        CardSearchResponse response = normalizer.searchResponse("q", 10, raw);

        assertEquals(1, response.cards().size());
        assertEquals("xy1-1", response.cards().getFirst().id());
    }

    @Test
    void searchResponseFallsBackToZeroWhenCountIsMissing() {
        Map<String, Object> raw = Map.of("data", List.of());

        CardSearchResponse response = normalizer.searchResponse("q", 10, raw);

        assertEquals(0, response.count());
        assertEquals(0, response.totalCount());
    }

    @Test
    void searchResponseParsesCountFromString() {
        Map<String, Object> raw = Map.of(
            "count", "5",
            "totalCount", "100",
            "data", List.of()
        );

        CardSearchResponse response = normalizer.searchResponse("q", 10, raw);

        assertEquals(5, response.count());
        assertEquals(100, response.totalCount());
    }

    @Test
    void byIdResponseExtractsCardFromDataWrapper() {
        Map<String, Object> raw = Map.of("data", cardMap("xy1-1"));

        CardSummaryResponse card = normalizer.byIdResponse(raw);

        assertEquals("xy1-1", card.id());
    }

    @Test
    void byIdResponseReturnsEmptyCardWhenDataIsMissing() {
        Map<String, Object> raw = Map.of();

        CardSummaryResponse card = normalizer.byIdResponse(raw);

        assertNull(card.id());
        assertNull(card.name());
    }

    @Test
    void cardNormalizesAttacksWeaknessesResistancesAndAbilities() {
        Map<String, Object> raw = Map.ofEntries(
            Map.entry("id", "xy1-1"),
            Map.entry("name", "Venusaur-EX"),
            Map.entry("set", Map.of("id", "xy1")),
            Map.entry("supertype", "Pokemon"),
            Map.entry("subtypes", List.of("Basic", "EX")),
            Map.entry("types", List.of("Grass")),
            Map.entry("hp", "180"),
            Map.entry("attacks", List.of(Map.of(
                "name", "Vine Whip",
                "text", "Does 30 damage.",
                "damage", "30+",
                "cost", List.of("Grass", "Colorless"),
                "convertedEnergyCost", 2
            ))),
            Map.entry("weaknesses", List.of(Map.of("type", "Fire", "value", "x2"))),
            Map.entry("resistances", List.of(Map.of("type", "Water", "value", "-20"))),
            Map.entry("convertedRetreatCost", 2),
            Map.entry("evolvesFrom", "Ivysaur"),
            Map.entry("abilities", List.of(Map.of("name", "Overgrow", "text", "Heal effect"))),
            Map.entry("rules", List.of("Rule one", "Rule two")),
            Map.entry("images", Map.of("small", "https://img.example/xy1-1.png"))
        );

        CardSummaryResponse card = normalizer.searchResponse("q", 10, Map.of("data", List.of(raw))).cards().getFirst();

        assertEquals("Venusaur-EX", card.name());
        assertEquals("xy1", card.setId());
        assertEquals(180, card.hp());
        assertEquals(1, card.attacks().size());
        CardAttackResponse attack = card.attacks().getFirst();
        assertEquals("Vine Whip", attack.name());
        assertEquals("Does 30 damage.", attack.text());
        assertEquals("30+", attack.damage());
        assertEquals(List.of("Grass", "Colorless"), attack.cost());
        assertEquals(2, attack.convertedEnergyCost());
        assertEquals(1, card.weaknesses().size());
        assertEquals("Fire", card.weaknesses().getFirst().type());
        assertEquals("x2", card.weaknesses().getFirst().value());
        assertEquals(1, card.resistances().size());
        assertEquals("Water", card.resistances().getFirst().type());
        assertEquals(-20, Integer.parseInt(card.resistances().getFirst().value()));
        assertEquals(2, card.retreatCost());
        assertEquals("Ivysaur", card.evolvesFrom());
        assertEquals(1, card.abilities().size());
        assertEquals("Overgrow", card.abilities().getFirst().name());
        assertEquals(List.of("Rule one", "Rule two"), card.rules());
        assertEquals("https://img.example/xy1-1.png", card.imageUrl());
    }

    @Test
    void cardFiltersOutAbilitiesWithBlankName() {
        Map<String, Object> raw = Map.of(
            "id", "xy1-1",
            "name", "Test",
            "abilities", List.of(
                Map.of("name", "Real", "text", "Effect"),
                Map.of("name", "", "text", "no name"),
                Map.of("name", "   ", "text", "blank")
            )
        );

        CardSummaryResponse card = normalizer.searchResponse("q", 10, Map.of("data", List.of(raw))).cards().getFirst();

        assertEquals(1, card.abilities().size());
        assertEquals("Real", card.abilities().getFirst().name());
    }

    @Test
    void cardHandlesNonStringHpByReturningNull() {
        Map<String, Object> raw = Map.of(
            "id", "xy1-1",
            "name", "Test",
            "hp", "not-a-number"
        );

        CardSummaryResponse card = normalizer.searchResponse("q", 10, Map.of("data", List.of(raw))).cards().getFirst();

        assertNull(card.hp());
    }

    @Test
    void cardDefaultsHpToNullWhenBlank() {
        Map<String, Object> raw = Map.of(
            "id", "xy1-1",
            "name", "Test",
            "hp", "   "
        );

        CardSummaryResponse card = normalizer.searchResponse("q", 10, Map.of("data", List.of(raw))).cards().getFirst();

        assertNull(card.hp());
    }

    @Test
    void cardDefaultsConvertedRetreatCostToNullWhenBlank() {
        Map<String, Object> raw = Map.of(
            "id", "xy1-1",
            "name", "Test",
            "convertedRetreatCost", "not-a-number"
        );

        CardSummaryResponse card = normalizer.searchResponse("q", 10, Map.of("data", List.of(raw))).cards().getFirst();

        assertNull(card.retreatCost());
    }

    @Test
    void cardLeavesRulesAndImageEmptyWhenMissing() {
        Map<String, Object> raw = Map.of("id", "xy1-1", "name", "Test");

        CardSummaryResponse card = normalizer.searchResponse("q", 10, Map.of("data", List.of(raw))).cards().getFirst();

        assertTrue(card.rules().isEmpty());
        assertNull(card.imageUrl());
    }

    @Test
    void cardConvertsNonListFieldsToEmptyLists() {
        Map<String, Object> raw = Map.of(
            "id", "xy1-1",
            "name", "Test",
            "subtypes", "not-a-list",
            "types", 42
        );

        CardSummaryResponse card = normalizer.searchResponse("q", 10, Map.of("data", List.of(raw))).cards().getFirst();

        assertTrue(card.subtypes().isEmpty());
        assertTrue(card.types().isEmpty());
    }

    @Test
    void cardConvertsListOfNonStringsToStrings() {
        Map<String, Object> raw = Map.of(
            "id", "xy1-1",
            "name", "Test",
            "subtypes", List.of(1, 2, "EX"),
            "types", List.of("Grass", 99)
        );

        CardSummaryResponse card = normalizer.searchResponse("q", 10, Map.of("data", List.of(raw))).cards().getFirst();

        assertEquals(List.of("1", "2", "EX"), card.subtypes());
        assertEquals(List.of("Grass", "99"), card.types());
    }

    @Test
    void cardFallsBackToEmptyWhenDataIsNotAList() {
        Map<String, Object> raw = Map.of("data", "not-a-list");

        CardSearchResponse response = normalizer.searchResponse("q", 10, raw);

        assertTrue(response.cards().isEmpty());
    }

    @Test
    void cardIgnoresNonMapEntriesInAttacksAndModifiers() {
        Map<String, Object> raw = Map.of(
            "id", "xy1-1",
            "name", "Test",
            "attacks", List.of("not-a-map", Map.of("name", "Real", "text", "T", "damage", "10",
                "cost", List.of("Fire"), "convertedEnergyCost", 1)),
            "weaknesses", List.of("not-a-map", Map.of("type", "Fire", "value", "x2")),
            "resistances", List.of("not-a-map", Map.of("type", "Water", "value", "-20"))
        );

        CardSummaryResponse card = normalizer.searchResponse("q", 10, Map.of("data", List.of(raw))).cards().getFirst();

        assertEquals(1, card.attacks().size());
        assertEquals(1, card.weaknesses().size());
        assertEquals(1, card.resistances().size());
    }

    @Test
    void cardParsesNumericConvertedEnergyCost() {
        Map<String, Object> raw = Map.of(
            "id", "xy1-1",
            "name", "Test",
            "attacks", List.of(Map.of(
                "name", "A",
                "text", "T",
                "damage", "0",
                "cost", List.of(),
                "convertedEnergyCost", 5
            ))
        );

        CardSummaryResponse card = normalizer.searchResponse("q", 10, Map.of("data", List.of(raw))).cards().getFirst();

        assertEquals(5, card.attacks().getFirst().convertedEnergyCost());
    }

    private Map<String, Object> cardMap(String id) {
        return Map.ofEntries(
            Map.entry("id", id),
            Map.entry("name", "Card " + id),
            Map.entry("set", Map.of("id", "xy1")),
            Map.entry("supertype", "Pokemon"),
            Map.entry("subtypes", List.of("Basic")),
            Map.entry("types", List.of("Grass")),
            Map.entry("images", Map.of("small", "https://img.example/" + id + ".png"))
        );
    }
}
