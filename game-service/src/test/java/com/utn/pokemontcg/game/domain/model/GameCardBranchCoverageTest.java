package com.utn.pokemontcg.game.domain.model;

import com.utn.pokemontcg.game.infrastructure.client.CardServiceClient.AttackData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage push for {@link GameCard} (72.5% branch).
 * Targets the null-handling branches in isBasicEnergy, isSpecialEnergy,
 * hasSubtype (with diacritics), and hasRuleContaining.
 */
class GameCardBranchCoverageTest {

    @Test
    void isBasicEnergyRequiresEnergySupertype() {
        GameCard card = new GameCard("e", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0);
        assertTrue(card.isBasicEnergy());
    }

    @Test
    void isBasicEnergyRejectsNonEnergyCards() {
        GameCard card = new GameCard("p", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1);
        assertFalse(card.isBasicEnergy(),
            "Una carta Pokemon no es Basic Energy aunque tenga subtype 'Basic'");
    }

    @Test
    void isBasicEnergyRejectsSpecialEnergy() {
        GameCard card = new GameCard("s", "Special Energy", "Energy", Set.of("Special"), 0, 0, 0, "Fire", List.of(), 0);
        assertFalse(card.isBasicEnergy());
    }

    @Test
    void isSpecialEnergyRequiresEnergySupertype() {
        GameCard card = new GameCard("s", "Special Energy", "Energy", Set.of("Special"), 0, 0, 0, "Fire", List.of(), 0);
        assertTrue(card.isSpecialEnergy());
    }

    @Test
    void isSpecialEnergyRejectsNonEnergyCards() {
        GameCard card = new GameCard("p", "Pikachu", "Pokemon", Set.of("Special"), 60, 10, 1);
        assertFalse(card.isSpecialEnergy());
    }

    @Test
    void isEnergyMatchesAnyEnergySupertype() {
        GameCard card = new GameCard("e", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0);
        assertTrue(card.isEnergy());
    }

    @Test
    void isEnergyRejectsNonEnergyCards() {
        GameCard card = new GameCard("p", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1);
        assertFalse(card.isEnergy());
    }

    @Test
    void isPokemonSupportsAccentedPokémon() {
        GameCard card = new GameCard("p", "Pikachu", "Pokémon", Set.of("Basic"), 60, 10, 1);
        assertTrue(card.isPokemon());
    }

    @Test
    void isPokemonExChecksNameSuffix() {
        GameCard card = new GameCard("p", "Mewtwo-EX", "Pokemon", Set.of("Basic"), 120, 20, 2);
        assertTrue(card.isPokemonEx(),
            "Una carta con sufijo '-EX' debe reconocerse como Pokemon-EX");
    }

    @Test
    void isPokemonExChecksSubtype() {
        GameCard card = new GameCard("p", "Mewtwo", "Pokemon", Set.of("EX"), 120, 20, 2);
        assertTrue(card.isPokemonEx());
    }

    @Test
    void isBasicPokemonRequiresBasicSubtype() {
        GameCard card = new GameCard("p", "Pikachu", "Pokemon", Set.of("Stage 1"), 60, 10, 1);
        assertFalse(card.isBasicPokemon());
    }

    @Test
    void isBasicPokemonAcceptsBasicSubtype() {
        GameCard card = new GameCard("p", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1);
        assertTrue(card.isBasicPokemon());
    }

    @Test
    void isTrainerRequiresTrainerSupertype() {
        GameCard card = new GameCard("p", "Potion", "Trainer", Set.of("Item"), 0, 0, 0);
        assertTrue(card.isTrainer());
    }

    @Test
    void isTrainerRejectsNonTrainerCards() {
        GameCard card = new GameCard("p", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1);
        assertFalse(card.isTrainer());
    }

    @Test
    void hasSubtypeMatchesIgnoringCase() {
        GameCard card = new GameCard("p", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1);
        assertTrue(card.hasSubtype("basic"));
        assertTrue(card.hasSubtype("BASIC"));
    }

    @Test
    void hasSubtypeMatchesIgnoringDiacritics() {
        GameCard card = new GameCard("p", "Pikachu", "Pokemon", Set.of("Básic"), 60, 10, 1);
        assertTrue(card.hasSubtype("Basic"),
            "hasSubtype debe ignorar diacríticos en subtypes");
    }

    @Test
    void hasSubtypeReturnsFalseForMissingSubtype() {
        GameCard card = new GameCard("p", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1);
        assertFalse(card.hasSubtype("Stage 1"));
    }

    @Test
    void hasRuleContainingMatchesNameCaseInsensitive() {
        GameCard card = new GameCard("p", "Fur Coat", "Pokemon", Set.of("Basic"), 60, 10, 1);
        assertTrue(card.hasRuleContaining("fur"));
    }

    @Test
    void hasRuleContainingMatchesSubtype() {
        GameCard card = new GameCard("p", "Tool", "Trainer", Set.of("Pokemon Tool"), 0, 0, 0);
        assertTrue(card.hasRuleContaining("pokemon tool"));
    }

    @Test
    void hasRuleContainingIgnoresDiacritics() {
        GameCard card = new GameCard("p", "Pokémon", "Pokemon", Set.of("Basic"), 60, 10, 1);
        assertTrue(card.hasRuleContaining("Pokemon"));
    }

    @Test
    void hasRuleContainingReturnsFalseForNullOrBlank() {
        GameCard card = new GameCard("p", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1);
        assertFalse(card.hasRuleContaining(null));
        assertFalse(card.hasRuleContaining(""));
        assertFalse(card.hasRuleContaining("   "));
    }

    @Test
    void hasRuleContainingReturnsFalseWhenNotMatched() {
        GameCard card = new GameCard("p", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1);
        assertFalse(card.hasRuleContaining("flying"));
    }

    @Test
    void compactConstructorNormalizesNullCollections() {
        GameCard card = new GameCard("p", "Pikachu", "Pokemon", null, 60, 10, 1,
            "Thunder Shock", "Flip a coin.", "Lightning",
            null, 1, "Pichu", null, null, null, null, null, null);
        assertTrue(card.subtypes().isEmpty());
        assertTrue(card.attackCost().isEmpty());
        assertTrue(card.abilities().isEmpty());
        assertTrue(card.attacks().isEmpty());
    }

    @Test
    void compactConstructorCopiesCollections() {
        java.util.ArrayList<String> cost = new java.util.ArrayList<>(List.of("Lightning"));
        java.util.ArrayList<String> abilities = new java.util.ArrayList<>(List.of("Static"));
        java.util.ArrayList<AttackData> attacks = new java.util.ArrayList<>(List.of(
            new AttackData("Thunder Shock", "Flip a coin.", "20", List.of("Lightning"), 1)
        ));
        GameCard card = new GameCard("p", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1,
            "Thunder Shock", "Flip a coin.", "Lightning",
            cost, 1, "Pichu", abilities, null, null, null, null, attacks);
        cost.add("Colorless");
        abilities.add("New");
        attacks.add(new AttackData("X", "", "0", List.of(), 0));

        assertEquals(1, card.attackCost().size(),
            "La lista de cost debe estar copiada, no ser la original mutable");
        assertEquals(1, card.abilities().size());
        assertEquals(1, card.attacks().size());
    }
}
