package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.engine.StatusEffectManager;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusEffectManagerTest {

    /*
     * JaCoCo gap analysis before this pass:
     * - Burned branch missed the path where the coin is not tails.
     * - Asleep branch missed both the wake-up path and the asleep-but-stays-asleep path.
     * - No-condition processing needed an explicit no-op assertion.
     */
    private final StatusEffectManager manager = new StatusEffectManager();

    @Test
    void applyingAsleepAddsConditionWhenNoPriorStatusExists() {
        UUID player = UUID.randomUUID();
        GameAggregate game = gameWithActive(player);

        manager.applyCondition(game, player, StatusCondition.ASLEEP);

        assertTrue(game.statusByPlayer().get(player).contains(StatusCondition.ASLEEP));
    }

    @Test
    void confusionReplacesAsleep() {
        UUID player = UUID.randomUUID();
        GameAggregate game = gameWithActive(player);

        manager.applyCondition(game, player, StatusCondition.ASLEEP);
        manager.applyCondition(game, player, StatusCondition.CONFUSED);

        assertFalse(game.statusByPlayer().get(player).contains(StatusCondition.ASLEEP));
        assertTrue(game.statusByPlayer().get(player).contains(StatusCondition.CONFUSED));
    }

    @Test
    void paralysisReplacesConfusionAndCoexistsWithBurnedAndPoisoned() {
        UUID player = UUID.randomUUID();
        GameAggregate game = gameWithActive(player);

        manager.applyCondition(game, player, StatusCondition.CONFUSED);
        manager.applyCondition(game, player, StatusCondition.PARALYZED);
        manager.applyCondition(game, player, StatusCondition.BURNED);
        manager.applyCondition(game, player, StatusCondition.POISONED);

        assertFalse(game.statusByPlayer().get(player).contains(StatusCondition.CONFUSED));
        assertTrue(game.statusByPlayer().get(player).contains(StatusCondition.PARALYZED));
        assertTrue(game.statusByPlayer().get(player).contains(StatusCondition.BURNED));
        assertTrue(game.statusByPlayer().get(player).contains(StatusCondition.POISONED));
    }

    @Test
    void poisonedAndBurnedCoexistAndResolveInDamageOrder() {
        UUID player = UUID.randomUUID();
        GameAggregate game = gameWithActive(player);

        manager.applyCondition(game, player, StatusCondition.POISONED);
        manager.applyCondition(game, player, StatusCondition.BURNED);
        manager.resolveBetweenTurns(game, player, true, false);

        assertEquals(3, game.activeDamageCounters().get(player));
        assertTrue(game.statusByPlayer().get(player).contains(StatusCondition.POISONED));
        assertTrue(game.statusByPlayer().get(player).contains(StatusCondition.BURNED));
    }

    @Test
    void poisonedAlwaysAddsOneCounterWithoutCoinFlip() {
        UUID player = UUID.randomUUID();
        GameAggregate game = gameWithActive(player);

        manager.applyCondition(game, player, StatusCondition.POISONED);
        manager.resolveBetweenTurns(game, player, false, false);

        assertEquals(1, game.activeDamageCounters().get(player));
    }

    @Test
    void burnedDoesNotAddDamageWhenCoinIsHeads() {
        UUID player = UUID.randomUUID();
        GameAggregate game = gameWithActive(player);

        manager.applyCondition(game, player, StatusCondition.BURNED);
        manager.resolveBetweenTurns(game, player, false, false);

        assertEquals(0, game.activeDamageCounters().get(player));
        assertTrue(game.statusByPlayer().get(player).contains(StatusCondition.BURNED));
    }

    @Test
    void asleepCanWakeBetweenTurns() {
        UUID player = UUID.randomUUID();
        GameAggregate game = gameWithActive(player);

        manager.applyCondition(game, player, StatusCondition.ASLEEP);
        manager.resolveBetweenTurns(game, player, false, true);

        assertFalse(game.statusByPlayer().getOrDefault(player, java.util.EnumSet.noneOf(StatusCondition.class)).contains(StatusCondition.ASLEEP));
    }

    @Test
    void asleepStaysAsleepWhenCoinIsTails() {
        UUID player = UUID.randomUUID();
        GameAggregate game = gameWithActive(player);

        manager.applyCondition(game, player, StatusCondition.ASLEEP);
        manager.resolveBetweenTurns(game, player, false, false);

        assertTrue(game.statusByPlayer().get(player).contains(StatusCondition.ASLEEP));
    }

    @Test
    void paralyzedClearsOnOwnTurnEndCheckup() {
        UUID player = UUID.randomUUID();
        GameAggregate game = gameWithActive(player);
        game.setCurrentTurnPlayer(player);

        manager.applyCondition(game, player, StatusCondition.PARALYZED);
        manager.resolveBetweenTurns(game, player, false, false);

        assertFalse(game.statusByPlayer().getOrDefault(player, java.util.EnumSet.noneOf(StatusCondition.class)).contains(StatusCondition.PARALYZED));
    }

    @Test
    void paralyzedPersistsThroughOpponentTurnEndCheckup() {
        UUID player = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        GameAggregate game = gameWithActive(player);
        // The opponent's turn just ended; the paralyzed player has not had their own turn yet,
        // so paralysis must NOT clear (it would otherwise skip having any effect).
        game.setCurrentTurnPlayer(opponent);

        manager.applyCondition(game, player, StatusCondition.PARALYZED);
        manager.resolveBetweenTurns(game, player, false, false);

        assertTrue(game.statusByPlayer().get(player).contains(StatusCondition.PARALYZED));
    }

    @Test
    void clearingBenchSlotDoesNotRemoveActiveStatus() {
        UUID player = UUID.randomUUID();
        GameAggregate game = gameWithActive(player);
        manager.applyCondition(game, player, StatusCondition.POISONED);

        manager.clearSpecialConditions(game, player, "BENCH_0");

        assertTrue(game.statusByPlayer().get(player).contains(StatusCondition.POISONED));
    }

    @Test
    void clearingActiveSlotRemovesAllSpecialConditions() {
        UUID player = UUID.randomUUID();
        GameAggregate game = gameWithActive(player);
        manager.applyCondition(game, player, StatusCondition.POISONED);
        manager.applyCondition(game, player, StatusCondition.BURNED);

        manager.clearSpecialConditions(game, player, "ACTIVE");

        assertFalse(game.statusByPlayer().containsKey(player));
    }

    @Test
    void clearingConditionsDirectlyModelsRetreatOrEvolutionCleanup() {
        UUID player = UUID.randomUUID();
        GameAggregate game = gameWithActive(player);
        manager.applyCondition(game, player, StatusCondition.POISONED);

        manager.clearSpecialConditions(game, player);

        assertFalse(game.statusByPlayer().containsKey(player));
    }

    @Test
    void resolvingPokemonWithoutConditionsIsNoOp() {
        UUID player = UUID.randomUUID();
        GameAggregate game = gameWithActive(player);

        manager.resolveBetweenTurns(game, player, true, true);

        assertEquals(0, game.activeDamageCounters().get(player));
        assertTrue(game.statusByPlayer().getOrDefault(player, java.util.EnumSet.noneOf(StatusCondition.class)).isEmpty());
    }

    private GameAggregate gameWithActive(UUID player) {
        GameAggregate game = new GameAggregate(player);
        game.registerCard(new GameCard("active", "Active", "Pokemon", Set.of("Basic"), 100, 20, 1));
        game.activePokemon().put(player, "active");
        game.activeDamageCounters().put(player, 0);
        game.activeHp().put(player, 100);
        return game;
    }
}
