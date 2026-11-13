package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectContext;
import com.utn.pokemontcg.game.domain.strategy.WaterShurikenAbilityEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage for {@link WaterShurikenAbilityEffectStrategy}.
 * Focuses on validation paths and the UnsupportedOperationException fallback.
 */
class WaterShurikenAbilityEffectStrategyCoverageTest {

    private final WaterShurikenAbilityEffectStrategy strategy = new WaterShurikenAbilityEffectStrategy();

    @Test
    void targetIdWithThreePartsIsInvalidFormat() {
        GameAggregate game = game();
        // "ACTIVE:uuid:extra" splits into 3 parts
        String target = "ACTIVE:" + game.playerTwo() + ":extra";

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(context(game, target)));
        assertTrue(ex.getMessage().contains("Formato de objetivo invalido"));
    }

    @Test
    void targetIdWithInvalidUuidIsRejected() {
        GameAggregate game = game();
        // "ACTIVE:not-a-uuid" -> UUID.fromString throws -> wrapped
        String target = "ACTIVE:not-a-uuid";

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(context(game, target)));
        assertTrue(ex.getMessage().contains("Identificador de jugador objetivo invalido"));
    }

    @Test
    void targetIdWithInvalidUuidButValidFormatIsRejected() {
        GameAggregate game = game();
        // "ACTIVE:garbage" splits into 2 parts, but UUID.fromString fails
        String target = "ACTIVE:garbage";

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(context(game, target)));
        assertTrue(ex.getMessage().contains("Identificador de jugador objetivo invalido"));
    }

    @Test
    void targetSlotNotOccupiedIsRejected() {
        GameAggregate game = game();
        // Opponent has an ACTIVE, but no bench pokemon
        String target = "BENCH_0:" + game.playerTwo();

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(context(game, target)));
        assertTrue(ex.getMessage().contains("El slot objetivo no contiene un Pokemon"));
    }

    @Test
    void handNullIsRejected() {
        GameAggregate game = game();
        // Remove the hand entry to simulate null
        game.hand().remove(game.playerOne());

        String target = "ACTIVE:" + game.playerTwo();

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(context(game, target)));
        assertTrue(ex.getMessage().contains("requiere descartar una Energia Water"));
    }

    @Test
    void handEmptyIsRejected() {
        GameAggregate game = game();
        // Hand is already an empty list (initializeZonesFor created one)
        assertTrue(game.hand().get(game.playerOne()).isEmpty());

        String target = "ACTIVE:" + game.playerTwo();

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(context(game, target)));
        assertTrue(ex.getMessage().contains("requiere descartar una Energia Water"));
    }

    @Test
    void handWithoutWaterEnergyIsRejected() {
        GameAggregate game = game();
        // Add only a non-water energy card to the hand
        GameCard fireEnergy = new GameCard(
            "fire-1", "Fire Energy", "Energy", Set.of("Basic"),
            0, 0, 0, "Fire", List.of(), 0);
        game.registerCard(fireEnergy);
        game.hand().get(game.playerOne()).add("fire-1");

        String target = "ACTIVE:" + game.playerTwo();

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(context(game, target)));
        assertTrue(ex.getMessage().contains("requiere descartar una Energia Water"));
    }

    @Test
    void immutableHandTriggersMutableCopyFallback() {
        GameAggregate game = game();
        GameCard waterEnergy = new GameCard(
            "water-1", "Water Energy", "Energy", Set.of("Basic"),
            0, 0, 0, "Water", List.of(), 0);
        game.registerCard(waterEnergy);

        // Replace the hand with an IMMUTABLE list containing the water energy
        // This forces currentHand.remove(...) to throw UnsupportedOperationException
        // which the strategy catches and falls back to a mutable copy.
        game.hand().put(game.playerOne(), List.of("water-1"));

        String target = "ACTIVE:" + game.playerTwo();

        var result = strategy.apply(context(game, target));

        assertEquals("WATER_SHURIKEN_ACTIVATED", result.result());
        // The immutable list was replaced with a mutable one
        assertFalse(game.hand().get(game.playerOne()).contains("water-1"),
            "Water energy must be removed from hand after the strategy runs");
        // Discard pile must contain the water energy id
        assertTrue(game.discardPile().get(game.playerOne()).contains("water-1"),
            "Water energy must be in discard pile after the strategy runs");
        // Damage counters should be applied to opponent's ACTIVE
        assertEquals(3, game.damageCountersAtSlot(game.playerTwo(), "ACTIVE"));
    }

    @Test
    void happyPathWithMutableHandWorks() {
        GameAggregate game = game();
        GameCard waterEnergy = new GameCard(
            "water-1", "Water Energy", "Energy", Set.of("Basic"),
            0, 0, 0, "Water", List.of(), 0);
        game.registerCard(waterEnergy);
        game.hand().get(game.playerOne()).add("water-1");

        String target = "ACTIVE:" + game.playerTwo();

        var result = strategy.apply(context(game, target));

        assertEquals("WATER_SHURIKEN_ACTIVATED", result.result());
        assertEquals(3, game.damageCountersAtSlot(game.playerTwo(), "ACTIVE"));
    }

    @Test
    void benchIndex0OccupiedIsValidTarget() {
        GameAggregate game = game();
        // Put a pokemon in opponent's BENCH_0
        GameCard opponentBench = new GameCard(
            "bench-poke", "BenchPoke", "Pokemon", Set.of("Basic"),
            60, 10, 1);
        game.registerCard(opponentBench);
        // bench() was initialized as an empty list, so .add puts it at index 0
        game.bench().get(game.playerTwo()).add(0, "bench-poke");

        String target = "BENCH_0:" + game.playerTwo();

        // Should not throw "slot not occupied" — the rest of validation still fails
        // because hand is empty, which proves the slot check passed
        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(context(game, target)));
        assertTrue(ex.getMessage().contains("requiere descartar una Energia Water"),
            "Slot check must pass; hand check should be the failing one");
    }

    @Test
    void targetIsOwnPlayerIsRejected() {
        GameAggregate game = game();
        // Target player == self
        String target = "ACTIVE:" + game.playerOne();

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(context(game, target)));
        assertTrue(ex.getMessage().contains("solo puede seleccionar Pokemon del rival"));
    }

    @Test
    void nullTargetIdIsRejected() {
        GameAggregate game = game();

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(context(game, null)));
        assertTrue(ex.getMessage().contains("requiere seleccionar"));
    }

    @Test
    void blankTargetIdIsRejected() {
        GameAggregate game = game();

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(context(game, "   ")));
        assertTrue(ex.getMessage().contains("requiere seleccionar"));
    }

    // ---------- helpers ----------

    private AbilityEffectContext context(GameAggregate game, String targetId) {
        return new AbilityEffectContext(game, game.playerOne(), null, "Water Shuriken", 0, targetId);
    }

    private GameAggregate game() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(p1);
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());
        game.activePokemon().put(p1, "active-1");
        game.activePokemon().put(p2, "active-2");
        game.registerCard(new GameCard("active-1", "Active", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard("active-2", "Defender", "Pokemon", Set.of("Basic"), 60, 10, 1));
        // Initialize hand with a mutable ArrayList (default behaviour)
        game.hand().put(p1, new ArrayList<>());
        return game;
    }
}
