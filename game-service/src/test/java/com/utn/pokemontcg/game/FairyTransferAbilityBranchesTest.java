package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectContext;
import com.utn.pokemontcg.game.domain.strategy.FairyTransferAbilityEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage push for {@link FairyTransferAbilityEffectStrategy}.
 * The existing {@link FairyTransferAbilityTest} covers happy paths, the
 * "benched" rejection, the cross-player and non-Fairy cases. This file
 * targets the four remaining branches:
 *   - targetId without pipe separator
 *   - parts.length != 3 after split
 *   - source slot not occupied by the player
 *   - destination slot not occupied by the player
 */
class FairyTransferAbilityBranchesTest {

    private final FairyTransferAbilityEffectStrategy strategy = new FairyTransferAbilityEffectStrategy();

    @Test
    void targetIdWithoutPipeSeparatorIsRejected() {
        GameAggregate game = gameWithOccupiedActiveAndBench("fairy-energy");
        UUID player = game.currentTurnPlayer();

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(ctx(game, player, "ACTIVE", "ACTIVE_BENCH_0_fairy-energy")));
        assertTrue(ex.getMessage().contains("formato"),
            "Sin '|' en targetId el mensaje debe pedir el formato srcSlot|destSlot|energyCardId");
    }

    @Test
    void blankTargetIdIsRejected() {
        GameAggregate game = gameWithOccupiedActiveAndBench("fairy-energy");
        UUID player = game.currentTurnPlayer();

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(ctx(game, player, "ACTIVE", "   ")));
        assertTrue(ex.getMessage().contains("formato"));
    }

    @Test
    void nullTargetIdIsRejected() {
        GameAggregate game = gameWithOccupiedActiveAndBench("fairy-energy");
        UUID player = game.currentTurnPlayer();

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(ctx(game, player, "ACTIVE", null)));
        assertTrue(ex.getMessage().contains("formato"));
    }

    @Test
    void partsLengthNotThreeIsRejected() {
        GameAggregate game = gameWithOccupiedActiveAndBench("fairy-energy");
        UUID player = game.currentTurnPlayer();

        // Only 1 part, no pipes at all
        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(ctx(game, player, "ACTIVE", "only-one-piece")));
        assertTrue(ex.getMessage().contains("formato"));
    }

    @Test
    void sourceSlotNotOccupiedIsRejected() {
        GameAggregate game = gameWithOccupiedActiveAndBench("fairy-energy");
        UUID player = game.currentTurnPlayer();

        // BENCH_2 is left unoccupied (default bench initialization = nulls).
        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(ctx(game, player, "ACTIVE", "BENCH_2|BENCH_0|fairy-energy")));
        assertTrue(ex.getMessage().contains("origen"));
    }

    @Test
    void destinationSlotNotOccupiedIsRejected() {
        GameAggregate game = gameWithOccupiedActiveAndBench("fairy-energy");
        UUID player = game.currentTurnPlayer();
        // Set BENCH_0 to null in the bench list so the slot becomes unoccupied
        game.bench().get(player).set(0, null);

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(ctx(game, player, "ACTIVE", "ACTIVE|BENCH_0|fairy-energy")));
        assertTrue(ex.getMessage().contains("destino"));
    }

    @Test
    void happyPathStillPasses() {
        GameAggregate game = gameWithOccupiedActiveAndBench("fairy-energy");
        UUID player = game.currentTurnPlayer();

        var result = strategy.apply(ctx(game, player, "ACTIVE", "ACTIVE|BENCH_0|fairy-energy"));

        assertEquals("FAIRY_TRANSFER_APPLIED", result.result());
        assertEquals(0, game.attachedEnergyCount(player, "ACTIVE"));
        assertEquals(1, game.attachedEnergyCount(player, "BENCH_0"));
    }

    // ----- helpers -----

    private AbilityEffectContext ctx(GameAggregate game, UUID player, String slot, String targetId) {
        GameCard pokemon = game.cardCatalog().get(game.activePokemon().get(player));
        return new AbilityEffectContext(game, player, pokemon, slot, 0, targetId);
    }

    private GameAggregate gameWithOccupiedActiveAndBench(String energyId) {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(p1);
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());

        GameCard aromatisse = new GameCard("aromatisse", "Aromatisse", "Pokemon",
            Set.of("Basic"), 100, 10, 1, null, null, "Fairy", List.of(), 0, null,
            List.of("Fairy Transfer"));
        GameCard bench = new GameCard("bench-0", "Bench", "Pokemon",
            Set.of("Basic"), 80, 0, 0, null, null, "Colorless", List.of(), 0, null,
            List.of());
        game.registerCard(aromatisse);
        game.registerCard(bench);
        game.activePokemon().put(p1, aromatisse.id());
        game.activePokemon().put(p2, "defender-card");
        game.bench().get(p1).add(0, bench.id());
        game.attachEnergy(p1, "ACTIVE", new AttachedEnergy(energyId, "Fairy"));
        return game;
    }
}
