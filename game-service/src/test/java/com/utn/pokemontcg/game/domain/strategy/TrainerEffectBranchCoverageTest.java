package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage push for trainer effects.
 * Targets low-branch classes called out in the JaCoCo audit: missing coin-flip
 * branches, manual selection paths, opponent hand/energy side effects, and the
 * Cassius replacement / promotion flows.
 */
class TrainerEffectBranchCoverageTest {

    // ====================================================================
    // RollerSkatesTrainerEffect (50% branch)
    // ====================================================================

    @Test
    void rollerSkatesHeadsDrawsThreeCards() {
        GameAggregate game = gameWithDeck("a", "b", "c", "d");
        GameCard trainer = trainerCard("Roller Skates");
        var ctx = contextWithCoinFlip(game, trainer, null, true);

        var result = new RollerSkatesTrainerEffect().apply(ctx);

        assertEquals("COIN_FLIPPED", result.result());
        assertEquals(true, result.payload().get("heads"));
        assertEquals(3, result.payload().get("cardsDrawn"));
        assertEquals(3, game.hand().get(game.playerOne()).size());
        assertEquals(1, game.deckCardsRemaining().get(game.playerOne()));
    }

    @Test
    void rollerSkatesTailsDrawsZeroCards() {
        GameAggregate game = gameWithDeck("a", "b", "c", "d");
        GameCard trainer = trainerCard("Roller Skates");
        var ctx = contextWithCoinFlip(game, trainer, null, false);

        var result = new RollerSkatesTrainerEffect().apply(ctx);

        assertEquals("COIN_FLIPPED", result.result());
        assertEquals(false, result.payload().get("heads"));
        assertEquals(0, result.payload().get("cardsDrawn"));
        assertTrue(game.hand().get(game.playerOne()).isEmpty());
        assertEquals(4, game.deckCardsRemaining().get(game.playerOne()));
    }

    @Test
    void rollerSkatesTailsWithEmptyDeckStillReportsTails() {
        GameAggregate game = gameWithDeck();
        GameCard trainer = trainerCard("Roller Skates");
        var ctx = contextWithCoinFlip(game, trainer, null, false);

        var result = new RollerSkatesTrainerEffect().apply(ctx);

        assertEquals(false, result.payload().get("heads"));
        assertEquals(0, result.payload().get("cardsDrawn"));
    }

    @Test
    void rollerSkatesCardNameReturnsExpectedValue() {
        // Branch coverage is already 100% for apply(), but the cardName()
        // method body is otherwise unreached (line 11). Cover it explicitly.
        assertEquals("Roller Skates", new RollerSkatesTrainerEffect().cardName());
    }

    // ====================================================================
    // SuperPotionTrainerEffect (50% branch)
    // ====================================================================

    @Test
    void superPotionReturnsNoEnergyToDiscardWhenSlotEmpty() {
        GameAggregate game = gameWithDeck();
        GameCard trainer = trainerCard("Super Potion");
        var ctx = context(game, trainer, "ACTIVE");

        var result = new SuperPotionTrainerEffect().apply(ctx);

        assertEquals("NO_ENERGY_TO_DISCARD", result.result());
    }

    @Test
    void superPotionWithManualSelectionDiscardsChosenEnergy() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        game.attachEnergy(p1, "ACTIVE", new AttachedEnergy("fire-1", "Fire"));
        game.attachEnergy(p1, "ACTIVE", new AttachedEnergy("water-1", "Water"));
        game.addDamageCounters(p1, "ACTIVE", 8);

        GameCard trainer = trainerCard("Super Potion");
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), "ACTIVE", null,
            null, List.of(), List.of("water-1"), null, null, null);
        var result = new SuperPotionTrainerEffect().apply(ctx);

        assertEquals("DAMAGE_HEALED_AND_ENERGY_DISCARDED", result.result());
        assertEquals("ACTIVE", result.payload().get("targetSlot"));
        assertEquals(6, result.payload().get("healedCounters"));
        assertEquals(1, game.attachedEnergyCount(p1, "ACTIVE"));
        assertTrue(game.discardPile().get(p1).contains("water-1"));
    }

    @Test
    void superPotionFallsBackToFirstEnergyWhenSelectionEmpty() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        game.attachEnergy(p1, "ACTIVE", new AttachedEnergy("fire-1", "Fire"));
        game.attachEnergy(p1, "ACTIVE", new AttachedEnergy("water-1", "Water"));
        game.addDamageCounters(p1, "ACTIVE", 3);

        GameCard trainer = trainerCard("Super Potion");
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), "ACTIVE", null,
            null, List.of(), List.of("unknown-id"), null, null, null);
        var result = new SuperPotionTrainerEffect().apply(ctx);

        assertEquals("DAMAGE_HEALED_AND_ENERGY_DISCARDED", result.result());
        assertEquals(3, result.payload().get("healedCounters"));
        // one energy still removed (fallback to first)
        assertEquals(1, game.attachedEnergyCount(p1, "ACTIVE"));
    }

    @Test
    void superPotionHealsAtMostSixCounters() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        game.attachEnergy(p1, "ACTIVE", new AttachedEnergy("fire-1", "Fire"));
        game.addDamageCounters(p1, "ACTIVE", 10);

        GameCard trainer = trainerCard("Super Potion");
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), "ACTIVE", null,
            null, List.of(), List.of("fire-1"), null, null, null);
        var result = new SuperPotionTrainerEffect().apply(ctx);

        assertEquals(6, result.payload().get("healedCounters"));
        // 10 - 6 = 4 counters remain
        assertEquals(4, game.damageCountersAtSlot(p1, "ACTIVE"));
    }

    // ====================================================================
    // MaxReviveTrainerEffect (70% branch)
    // ====================================================================

    @Test
    void maxRevivePicksFirstPokemonFromDiscardWithoutSelection() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        game.registerCard(basic("mon-1"));
        game.registerCard(basic("mon-2"));
        game.discardPile().get(p1).addAll(List.of("mon-1", "mon-2"));

        var result = new MaxReviveTrainerEffect().apply(context(game, trainerCard("Max Revive"), null));

        assertEquals("POKEMON_TO_TOP_OF_DECK", result.result());
        assertEquals("mon-1", result.payload().get("cardId"));
        assertEquals("mon-1", game.deck().get(p1).getFirst());
        assertFalse(game.discardPile().get(p1).contains("mon-1"));
    }

    @Test
    void maxReviveSkipsNonPokemonInDiscard() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        game.registerCard(basic("mon-1"));
        game.discardPile().get(p1).addAll(List.of("trainer-x", "mon-1"));

        var result = new MaxReviveTrainerEffect().apply(context(game, trainerCard("Max Revive"), null));

        assertEquals("POKEMON_TO_TOP_OF_DECK", result.result());
        assertEquals("mon-1", result.payload().get("cardId"));
    }

    @Test
    void maxReviveReturnsNoPokemonWhenDiscardHasOnlyNonPokemon() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        GameCard trainer = new GameCard("trainer-x", "Switch", "Trainer", Set.of("Item"), 0, 0, 0);
        game.registerCard(trainer);
        game.discardPile().get(p1).add("trainer-x");

        var result = new MaxReviveTrainerEffect().apply(context(game, trainerCard("Max Revive"), null));

        assertEquals("NO_POKEMON_IN_DISCARD", result.result());
    }

    @Test
    void maxReviveSkipsSelectionIdsNotPresentInDiscard() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        game.registerCard(basic("mon-1"));
        game.discardPile().get(p1).add("mon-1");

        GameCard trainer = trainerCard("Max Revive");
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), null, null,
            null, List.of("missing-id", "mon-1"), List.of(), null, null, null);
        var result = new MaxReviveTrainerEffect().apply(ctx);

        assertEquals("POKEMON_TO_TOP_OF_DECK", result.result());
        assertEquals("mon-1", result.payload().get("cardId"));
    }

    // ====================================================================
    // CassiusTrainerEffect (70% branch)
    // ====================================================================

    @Test
    void cassiusThrowsWhenActiveAndNoBench() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        game.activePokemon().put(p1, "active-mon");

        var trainer = trainerCard("Cassius");
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), "ACTIVE", null);

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> new CassiusTrainerEffect().apply(ctx));
        assertTrue(ex.getMessage().toLowerCase().contains("banca"));
    }

    @Test
    void cassiusReturnsPokemonWithAttachedEnergyAndToolToDeck() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        game.activePokemon().put(p1, "active-mon");
        GameCard active = basic("active-mon");
        game.registerCard(active);
        game.benchPokemon(p1, "bench-mon", "BENCH_0");
        GameCard bench = basic("bench-mon");
        game.registerCard(bench);
        game.attachEnergy(p1, "ACTIVE", new AttachedEnergy("fire-1", "Fire"));
        game.equipTool(p1, "ACTIVE", "tool-card");

        GameCard tool = new GameCard("tool-card", "Tool", "Trainer", Set.of("Pokemon Tool"), 0, 0, 0);
        game.registerCard(tool);

        var result = new CassiusTrainerEffect().apply(context(game, trainerCard("Cassius"), "ACTIVE"));

        assertEquals("POKEMON_RETURNED_TO_DECK", result.result());
        assertEquals("ACTIVE", result.payload().get("targetSlot"));
        assertEquals("active-mon", result.payload().get("cardId"));
        // pokemon, energy and tool all end up in the deck
        List<String> deck = game.deck().get(p1);
        assertTrue(deck.contains("active-mon"));
        assertTrue(deck.contains("fire-1"));
        assertTrue(deck.contains("tool-card"),
            "El tool del Active debe ser devuelto al mazo por Cassius");
    }

    @Test
    void cassiusOnBenchClearsSlotAndEnergies() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        GameCard bench = basic("bench-mon");
        game.registerCard(bench);
        game.benchPokemon(p1, "bench-mon", "BENCH_0");
        game.attachEnergy(p1, "BENCH_0", new AttachedEnergy("fire-1", "Fire"));

        new CassiusTrainerEffect().apply(context(game, trainerCard("Cassius"), "BENCH_0"));

        assertNull(game.cardAtSlot(p1, "BENCH_0"));
        assertEquals(0, game.attachedEnergyCount(p1, "BENCH_0"));
    }

    // ====================================================================
    // EscapeRopeTrainerEffect (50% branch)
    // ====================================================================

    @Test
    void escapeRopeSwapsPlayerAndOpponent() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        UUID p2 = game.playerTwo();
        game.activePokemon().put(p1, "p1-active");
        game.activePokemon().put(p2, "p2-active");
        game.benchPokemon(p1, "p1-bench-0", "BENCH_0");
        game.benchPokemon(p2, "p2-bench-0", "BENCH_0");
        GameCard basic1 = basic("p1-bench-0");
        GameCard basic2 = basic("p2-bench-0");
        game.registerCard(basic1);
        game.registerCard(basic2);

        var result = new EscapeRopeTrainerEffect().apply(
            context(game, trainerCard("Escape Rope"), "BENCH_0"));

        assertEquals("ESCAPE_ROPE_RESOLVED", result.result());
        assertEquals(true, result.payload().get("opponentSwapped"));
        assertEquals("p1-bench-0", game.activePokemon().get(p1));
        assertEquals("p2-bench-0", game.activePokemon().get(p2));
    }

    @Test
    void escapeRopeDoesNotSwapOpponentWhenOpponentBenchIsEmpty() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        UUID p2 = game.playerTwo();
        game.activePokemon().put(p1, "p1-active");
        game.activePokemon().put(p2, "p2-active");
        game.benchPokemon(p1, "p1-bench-0", "BENCH_0");
        GameCard basic1 = basic("p1-bench-0");
        game.registerCard(basic1);

        var result = new EscapeRopeTrainerEffect().apply(
            context(game, trainerCard("Escape Rope"), "BENCH_0"));

        assertEquals("ESCAPE_ROPE_RESOLVED", result.result());
        assertEquals(false, result.payload().get("opponentSwapped"));
        assertEquals("p2-active", game.activePokemon().get(p2));
    }

    @Test
    void escapeRopeSwapsOpponentFromNonZeroBenchIndex() {
        // Opponent bench layout: [null, "p2-bench-1"] — the first non-null is at index 1.
        // Exercises the for loop's null-skip branch (i=0 is null) and the non-null branch at i=1.
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        UUID p2 = game.playerTwo();
        game.activePokemon().put(p1, "p1-active");
        game.activePokemon().put(p2, "p2-active");
        game.benchPokemon(p1, "p1-bench-0", "BENCH_0");
        game.registerCard(basic("p1-bench-0"));

        List<String> p2Bench = game.bench().get(p2);
        p2Bench.add(null);
        p2Bench.add("p2-bench-1");
        game.registerCard(basic("p2-bench-1"));

        var result = new EscapeRopeTrainerEffect().apply(
            context(game, trainerCard("Escape Rope"), "BENCH_0"));

        assertEquals("ESCAPE_ROPE_RESOLVED", result.result());
        assertEquals(true, result.payload().get("opponentSwapped"));
        assertEquals("p1-bench-0", game.activePokemon().get(p1));
        assertEquals("p2-bench-1", game.activePokemon().get(p2));
        // The original active ended up in the slot the bench entry came from
        assertEquals("p2-active", game.cardAtSlot(p2, "BENCH_1"));
    }

    @Test
    void escapeRopeSwapsOpponentSkippingLeadingNulls() {
        // Opponent bench layout: [null, null, "p2-bench-2", "p2-bench-3"]
        // Exercises the loop skipping two nulls before finding the first non-null at i=2.
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        UUID p2 = game.playerTwo();
        game.activePokemon().put(p1, "p1-active");
        game.activePokemon().put(p2, "p2-active");
        game.benchPokemon(p1, "p1-bench-0", "BENCH_0");
        game.registerCard(basic("p1-bench-0"));

        List<String> p2Bench = game.bench().get(p2);
        p2Bench.add(null);
        p2Bench.add(null);
        p2Bench.add("p2-bench-2");
        p2Bench.add("p2-bench-3");
        game.registerCard(basic("p2-bench-2"));
        game.registerCard(basic("p2-bench-3"));

        var result = new EscapeRopeTrainerEffect().apply(
            context(game, trainerCard("Escape Rope"), "BENCH_0"));

        assertEquals("ESCAPE_ROPE_RESOLVED", result.result());
        assertEquals(true, result.payload().get("opponentSwapped"));
        assertEquals("p2-bench-2", game.activePokemon().get(p2));
        assertEquals("p2-active", game.cardAtSlot(p2, "BENCH_2"));
    }

    // ====================================================================
    // EvosodaTrainerEffect (75% branch)
    // ====================================================================

    @Test
    void evosodaNoOpWhenBaseIsNull() {
        GameAggregate game = gameWithDeck("charmeleon-1");
        UUID p1 = game.playerOne();
        GameCard evolution = new GameCard("charmeleon-1", "Charmeleon", "Pokemon",
            Set.of("Stage 1"), 80, 20, 1, "Fire", List.of(), 1, "Charmander", List.of());
        game.registerCard(evolution);
        // activePokemon not set for player; base lookup will return null

        var result = new EvosodaTrainerEffect().apply(context(game, trainerCard("Evosoda"), "ACTIVE"));

        assertEquals("NO_EVOLUTION_FOUND", result.result());
    }

    @Test
    void evosodaWithManualSelectionFindsEvolutionInDeck() {
        GameAggregate game = gameWithDeck("other-1", "charmeleon-1");
        UUID p1 = game.playerOne();
        GameCard base = new GameCard("charmander-1", "Charmander", "Pokemon",
            Set.of("Basic"), 60, 10, 1, "Fire", List.of(), 0, null, List.of());
        GameCard other = new GameCard("other-1", "Other", "Pokemon",
            Set.of("Basic"), 50, 10, 1, "Water", List.of(), 0, null, List.of());
        GameCard evolution = new GameCard("charmeleon-1", "Charmeleon", "Pokemon",
            Set.of("Stage 1"), 80, 20, 1, "Fire", List.of(), 1, "Charmander", List.of());
        game.registerCard(base);
        game.registerCard(other);
        game.registerCard(evolution);
        game.activePokemon().put(p1, "charmander-1");

        GameCard trainer = trainerCard("Evosoda");
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), "ACTIVE", null,
            null, List.of("charmeleon-1"), List.of(), null, null, null);
        var result = new EvosodaTrainerEffect().apply(ctx);

        assertEquals("POKEMON_EVOLVED_FROM_DECK", result.result());
        assertEquals("charmeleon-1", game.cardAtSlot(p1, "ACTIVE"));
    }

    @Test
    void evosodaManualSelectionSkipsMissingIds() {
        GameAggregate game = gameWithDeck("charmeleon-1");
        UUID p1 = game.playerOne();
        GameCard base = new GameCard("charmander-1", "Charmander", "Pokemon",
            Set.of("Basic"), 60, 10, 1, "Fire", List.of(), 0, null, List.of());
        GameCard evolution = new GameCard("charmeleon-1", "Charmeleon", "Pokemon",
            Set.of("Stage 1"), 80, 20, 1, "Fire", List.of(), 1, "Charmander", List.of());
        game.registerCard(base);
        game.registerCard(evolution);
        game.activePokemon().put(p1, "charmander-1");

        GameCard trainer = trainerCard("Evosoda");
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), "ACTIVE", null,
            null, List.of("unknown", "charmeleon-1"), List.of(), null, null, null);
        var result = new EvosodaTrainerEffect().apply(ctx);

        assertEquals("POKEMON_EVOLVED_FROM_DECK", result.result());
    }

    // ====================================================================
    // GreatBallTrainerEffect (80% branch)
    // ====================================================================

    @Test
    void greatBallReturnsNoPokemonFoundWhenDeckHasNoPokemon() {
        GameAggregate game = gameWithDeck("energy-1", "energy-2");
        UUID p1 = game.playerOne();
        game.registerCard(new GameCard("energy-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.registerCard(new GameCard("energy-2", "Water Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Water", List.of(), 0));

        var result = new GreatBallTrainerEffect().apply(context(game, trainerCard("Great Ball"), null));

        assertEquals("NO_POKEMON_FOUND", result.result());
    }

    @Test
    void greatBallManualSelectionIdNotInTopCardsFallsThrough() {
        GameAggregate game = gameWithDeck("basic-1", "basic-2", "basic-3");
        UUID p1 = game.playerOne();
        game.registerCard(basic("basic-1"));
        game.registerCard(basic("basic-2"));
        game.registerCard(basic("basic-3"));

        GameCard trainer = trainerCard("Great Ball");
        // "not-in-top" is not in the top 7 cards (only 3 cards in deck)
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), null, null,
            null, List.of("not-in-top"), List.of(), null, null, null);
        var result = new GreatBallTrainerEffect().apply(ctx);

        assertEquals("NO_POKEMON_FOUND", result.result());
    }

    @Test
    void greatBallPicksFirstPokemonFromTopCards() {
        GameAggregate game = gameWithDeck("basic-1", "energy-1", "basic-2");
        UUID p1 = game.playerOne();
        game.registerCard(basic("basic-1"));
        game.registerCard(basic("basic-2"));
        game.registerCard(new GameCard("energy-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));

        var result = new GreatBallTrainerEffect().apply(context(game, trainerCard("Great Ball"), null));

        assertEquals("POKEMON_FOUND", result.result());
        assertEquals("basic-1", result.payload().get("cardId"));
        assertTrue(game.hand().get(p1).contains("basic-1"));
    }

    // ====================================================================
    // ProfessorsLetterTrainerEffect (55.6% branch)
    // ====================================================================

    @Test
    void professorsLetterManualSelectionStopsAtTwoCards() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        List<String> deck = game.deck().computeIfAbsent(p1, k -> new ArrayList<>());
        deck.add("fire-1:1");
        deck.add("water-1:1");
        deck.add("grass-1:1");
        GameCard fire = new GameCard("fire-1:1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0);
        GameCard water = new GameCard("water-1:1", "Water Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Water", List.of(), 0);
        GameCard grass = new GameCard("grass-1:1", "Grass Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Grass", List.of(), 0);
        game.registerCard(fire);
        game.registerCard(water);
        game.registerCard(grass);
        game.deckCardsRemaining().put(p1, 3);

        GameCard trainer = trainerCard("Professor's Letter");
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), null, null,
            null, List.of("fire-1", "water-1", "grass-1"), List.of(), null, null, null);
        var result = new ProfessorsLetterTrainerEffect().apply(ctx);

        assertEquals("BASIC_ENERGY_SEARCHED", result.result());
        assertEquals(2, result.payload().get("cardsFound"));
        assertTrue(game.hand().get(p1).contains("fire-1:1"));
        assertTrue(game.hand().get(p1).contains("water-1:1"));
    }

    @Test
    void professorsLetterManualSelectionRejectsNonBasicEnergy() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        List<String> deck = game.deck().computeIfAbsent(p1, k -> new ArrayList<>());
        deck.add("special-1:1");
        GameCard special = new GameCard("special-1:1", "Special Energy", "Energy",
            Set.of("Special"), 0, 0, 0, "Fire", List.of(), 0);
        game.registerCard(special);
        game.deckCardsRemaining().put(p1, 1);

        GameCard trainer = trainerCard("Professor's Letter");
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), null, null,
            null, List.of("special-1"), List.of(), null, null, null);
        var result = new ProfessorsLetterTrainerEffect().apply(ctx);

        assertEquals("BASIC_ENERGY_SEARCHED", result.result());
        assertEquals(0, result.payload().get("cardsFound"));
    }

    // ====================================================================
    // LysandreTrainerEffect (100% branch)
    // ====================================================================

    @Test
    void lysandreSwapsOpponentActiveWithBench() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        UUID p2 = game.playerTwo();
        game.activePokemon().put(p1, "p1-active");
        game.activePokemon().put(p2, "p2-active");
        game.benchPokemon(p2, "p2-bench-0", "BENCH_0");
        GameCard basic = basic("p2-bench-0");
        game.registerCard(basic);

        var result = new LysandreTrainerEffect().apply(
            context(game, trainerCard("Lysandre"), "BENCH_0"));

        assertEquals("OPPONENT_ACTIVE_SWITCHED", result.result());
        assertEquals("p2-bench-0", game.activePokemon().get(p2));
        assertEquals("p2-active", game.cardAtSlot(p2, "BENCH_0"));
    }

    // ====================================================================
    // SwitchTrainerEffect (100% branch)
    // ====================================================================

    @Test
    void switchSwapsPlayerActiveWithBench() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        game.activePokemon().put(p1, "p1-active");
        game.benchPokemon(p1, "p1-bench-0", "BENCH_0");
        GameCard basic = basic("p1-bench-0");
        game.registerCard(basic);

        var result = new SwitchTrainerEffect().apply(
            context(game, trainerCard("Switch"), "BENCH_0"));

        assertEquals("ACTIVE_SWITCHED", result.result());
        assertEquals("p1-bench-0", game.activePokemon().get(p1));
        assertEquals("p1-active", game.cardAtSlot(p1, "BENCH_0"));
    }

    // ====================================================================
    // ShaunaTrainerEffect (100% branch)
    // ====================================================================

    @Test
    void shaunaShufflesEmptyHandAndDraws() {
        GameAggregate game = gameWithDeck("d1", "d2", "d3", "d4", "d5", "d6");
        UUID p1 = game.playerOne();
        game.hand().get(p1).clear();

        var result = new ShaunaTrainerEffect().apply(context(game, trainerCard("Shauna"), null));

        assertEquals("HAND_SHUFFLED_AND_DRAWN", result.result());
        assertEquals(5, result.payload().get("cardsDrawn"));
        assertEquals(5, game.hand().get(p1).size());
    }

    // ====================================================================
    // TiernoTrainerEffect (100% branch)
    // ====================================================================

    @Test
    void tiernoShufflesEmptyHandAndDraws() {
        GameAggregate game = gameWithDeck("d1", "d2", "d3", "d4", "d5");
        UUID p1 = game.playerOne();
        game.hand().get(p1).clear();

        var result = new TiernoTrainerEffect().apply(context(game, trainerCard("Tierno"), null));

        assertEquals("HAND_SHUFFLED_AND_DRAWN", result.result());
        assertEquals(5, result.payload().get("cardsDrawn"));
    }

    // ====================================================================
    // ProfessorSycamoreTrainerEffect (100% branch)
    // ====================================================================

    @Test
    void professorSycamoreDiscardsHandAndDrawsSeven() {
        GameAggregate game = gameWithDeck("d1", "d2", "d3", "d4", "d5", "d6", "d7");
        UUID p1 = game.playerOne();
        game.hand().get(p1).addAll(List.of("h1", "h2", "h3"));

        var result = new ProfessorSycamoreTrainerEffect().apply(
            context(game, trainerCard("Professor Sycamore"), null));

        assertEquals("HAND_DISCARDED_AND_DRAWN", result.result());
        assertEquals(7, result.payload().get("cardsDrawn"));
        assertEquals(7, game.hand().get(p1).size());
        assertTrue(game.discardPile().get(p1).containsAll(List.of("h1", "h2", "h3")));
    }

    // ====================================================================
    // TeamFlareGruntTrainerEffect (91.7% line)
    // ====================================================================

    @Test
    void teamFlareGruntManualSelectionDiscardsChosenEnergy() {
        GameAggregate game = gameWithDeck();
        UUID p2 = game.playerTwo();
        game.activePokemon().put(p2, "p2-active");
        game.attachEnergy(p2, "ACTIVE", new AttachedEnergy("opp-fire", "Fire"));
        game.attachEnergy(p2, "ACTIVE", new AttachedEnergy("opp-water", "Water"));

        GameCard trainer = trainerCard("Team Flare Grunt");
        var ctx = new TrainerEffectContext(game, game.playerOne(), trainer, trainer.id(),
            null, null, null, List.of(), List.of("opp-water"), null, null, null);
        var result = new TeamFlareGruntTrainerEffect().apply(ctx);

        assertEquals("OPPONENT_ENERGY_DISCARDED", result.result());
        assertEquals("opp-water", result.payload().get("energyCardId"));
        assertEquals(1, game.attachedEnergyCount(p2, "ACTIVE"));
        assertTrue(game.discardPile().get(p2).contains("opp-water"));
    }

    // ====================================================================
    // RedCardTrainerEffect (90% line)
    // ====================================================================

    @Test
    void redCardShufflesOpponentHandIntoDeckAndDraws() {
        GameAggregate game = gameWithDeck();
        UUID p2 = game.playerTwo();
        game.hand().get(p2).addAll(List.of("h1", "h2", "h3"));
        game.deck().put(p2, new ArrayList<>(List.of("d1", "d2", "d3", "d4", "d5", "d6")));

        var result = new RedCardTrainerEffect().apply(context(game, trainerCard("Red Card"), null));

        assertEquals("OPPONENT_HAND_SHUFFLED_AND_DRAWN", result.result());
        assertEquals(4, result.payload().get("cardsDrawn"));
        assertEquals(4, game.hand().get(p2).size());
    }

    // ====================================================================
    // EnergySearchTrainerEffect (72.2% branch)
    // ====================================================================

    @Test
    void energySearchWithoutSelectionFindsFirstBasicEnergy() {
        GameAggregate game = gameWithDeck("fire-1:1", "pokemon-1");
        UUID p1 = game.playerOne();
        GameCard fire = new GameCard("fire-1:1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0);
        game.registerCard(fire);

        var result = new EnergySearchTrainerEffect().apply(
            context(game, trainerCard("Energy Search"), null));

        assertEquals("BASIC_ENERGY_SEARCHED", result.result());
        assertEquals(1, result.payload().get("cardsFound"));
        assertTrue(game.hand().get(p1).contains("fire-1:1"));
    }

    @Test
    void energySearchWithSelectionSkipsNonBasicEnergy() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        List<String> deck = game.deck().computeIfAbsent(p1, k -> new ArrayList<>());
        deck.add("special-1:1");
        GameCard special = new GameCard("special-1:1", "Special Energy", "Energy",
            Set.of("Special"), 0, 0, 0, "Fire", List.of(), 0);
        game.registerCard(special);
        game.deckCardsRemaining().put(p1, 1);

        GameCard trainer = trainerCard("Energy Search");
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), null, null,
            null, List.of("special-1"), List.of(), null, null, null);
        var result = new EnergySearchTrainerEffect().apply(ctx);

        assertEquals("BASIC_ENERGY_SEARCHED", result.result());
        assertEquals(0, result.payload().get("cardsFound"));
    }

    // ====================================================================
    // ProfessorsLetterTrainerEffect — remaining branches (|| equals, null
    // instanceId, card missing from catalog, cardName() return).
    // ====================================================================

    @Test
    void professorsLetterManualSelectionMatchesInstanceIdWithoutColonSuffix() {
        // The deck contains "fire-1" (no ":N" suffix). The filter uses the
        // `id.equals(rawId)` branch because `id.startsWith(rawId + ":")` is
        // false for that entry.
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        List<String> deck = game.deck().computeIfAbsent(p1, k -> new ArrayList<>());
        deck.add("fire-1");
        game.registerCard(new GameCard("fire-1", "Fire Energy", "Energy",
            Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.deckCardsRemaining().put(p1, 1);

        GameCard trainer = trainerCard("Professor's Letter");
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), null, null,
            null, List.of("fire-1"), List.of(), null, null, () -> true);
        var result = new ProfessorsLetterTrainerEffect().apply(ctx);

        assertEquals("BASIC_ENERGY_SEARCHED", result.result());
        assertEquals(1, result.payload().get("cardsFound"));
        assertTrue(game.hand().get(p1).contains("fire-1"));
        assertTrue(game.deck().get(p1).isEmpty());
    }

    @Test
    void professorsLetterManualSelectionSkipsRawIdNotInDeck() {
        // rawId "ghost-id" doesn't match any instanceId in the deck → the
        // stream's findFirst() returns null → the `instanceId == null` branch.
        GameAggregate game = gameWithDeck("fire-1:1");
        UUID p1 = game.playerOne();
        game.registerCard(new GameCard("fire-1:1", "Fire Energy", "Energy",
            Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.deckCardsRemaining().put(p1, 1);

        GameCard trainer = trainerCard("Professor's Letter");
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), null, null,
            null, List.of("ghost-id"), List.of(), null, null, () -> true);
        var result = new ProfessorsLetterTrainerEffect().apply(ctx);

        assertEquals("BASIC_ENERGY_SEARCHED", result.result());
        assertEquals(0, result.payload().get("cardsFound"));
        // Original deck entry is untouched.
        assertTrue(game.deck().get(p1).contains("fire-1:1"));
    }

    @Test
    void professorsLetterManualSelectionSkipsInstanceIdMissingFromCatalog() {
        // The instanceId is present in the deck but not registered in the
        // cardCatalog → `card == null` short-circuits the && chain.
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        List<String> deck = game.deck().computeIfAbsent(p1, k -> new ArrayList<>());
        deck.add("ghost-1:1");
        game.deckCardsRemaining().put(p1, 1);
        // No registerCard for "ghost-1:1" → cardCatalog().get("ghost-1:1") is null.

        GameCard trainer = trainerCard("Professor's Letter");
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), null, null,
            null, List.of("ghost-1"), List.of(), null, null, () -> true);
        var result = new ProfessorsLetterTrainerEffect().apply(ctx);

        assertEquals("BASIC_ENERGY_SEARCHED", result.result());
        assertEquals(0, result.payload().get("cardsFound"));
        assertTrue(game.hand().get(p1).isEmpty());
    }

    @Test
    void professorsLetterCardNameReturnsExpectedValue() {
        // Covers the `cardName()` return that is otherwise only reached
        // through the registry constructor path.
        assertEquals("Professor's Letter", new ProfessorsLetterTrainerEffect().cardName());
    }

    // ====================================================================
    // TrainerEffectRegistry — exercises the `value == null` branch of
    // normalize() via a strategy whose cardName() returns null.
    // ====================================================================

    @Test
    void registryNormalizesStrategyWithNullCardNameWithoutCrashing() {
        TrainerEffectStrategy nullNamed = new TrainerEffectStrategy() {
            @Override
            public String cardName() {
                return null;
            }

            @Override
            public TrainerEffectResult apply(TrainerEffectContext ctx) {
                return TrainerEffectResult.noop("NULL_NAMED");
            }
        };
        // A strategy with a null name must not poison the registry for the
        // other strategies that have valid names.
        TrainerEffectRegistry registry = new TrainerEffectRegistry(
            List.of(new EnergySearchTrainerEffect(), nullNamed));

        assertTrue(registry.find(
            new GameCard("c1", "Energy Search", "Trainer", Set.of("Item"), 0, 0, 0)).isPresent(),
            "Other strategies must still be findable when one strategy has a null cardName");
        assertTrue(registry.find(
            new GameCard("c2", "  energy search  ", "Trainer", Set.of("Item"), 0, 0, 0)).isPresent(),
            "Whitespace + lowercase lookup must still match via the normalized registry key");
    }

    // ====================================================================
    // helpers
    // ====================================================================

    private GameAggregate gameWithDeck(String... playerOneDeck) {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.initializeZonesFor(p1, new ArrayList<>(List.of(playerOneDeck)));
        game.initializeZonesFor(p2, List.of());
        return game;
    }

    private GameCard basic(String id) {
        return new GameCard(id, "Basic Pokemon", "Pokemon", Set.of("Basic"), 60, 10, 1);
    }

    private GameCard trainerCard(String name) {
        return new GameCard("trainer-" + name.toLowerCase().replace(' ', '-'),
            name, "Trainer", Set.of("Item"), 0, 0, 0);
    }

    private TrainerEffectContext context(GameAggregate game, GameCard trainer, String targetSlot) {
        return new TrainerEffectContext(game, game.playerOne(), trainer, trainer.id(),
            targetSlot, null);
    }

    private TrainerEffectContext contextWithCoinFlip(GameAggregate game, GameCard trainer,
                                                     String targetSlot, boolean heads) {
        return new TrainerEffectContext(game, game.playerOne(), trainer, trainer.id(),
            targetSlot, null, null, List.of(), List.of(), null, null, () -> heads);
    }
}
