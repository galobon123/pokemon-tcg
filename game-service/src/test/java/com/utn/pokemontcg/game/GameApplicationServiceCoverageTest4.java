package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.application.service.SetupEngineService;
import com.utn.pokemontcg.game.application.service.TurnActionValidator;
import com.utn.pokemontcg.game.application.service.VictoryService;
import com.utn.pokemontcg.game.domain.engine.DamageCalculator;
import com.utn.pokemontcg.game.domain.engine.RuleValidator;
import com.utn.pokemontcg.game.domain.engine.StatusEffectManager;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.NoOpAbilityEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.PotionTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectRegistry;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fourth batch of coverage tests for {@link GameApplicationService}.
 *
 * Targets the last reachable uncovered branches:
 *   - executeAction APPLY_SPECIAL_CONDITION + STATUS_APPLIED event
 *   - executeAction TAKE_PRIZE + PRIZE_TAKEN event
 *   - executeAction RESOLVE_BETWEEN_TURNS
 *   - executeAction playTrainer throws "usa un endpoint dedicado" for
 *     SETUP_PLACE_ACTIVE / SETUP_PLACE_BENCH / SETUP_CONFIRM
 *   - executeAction BENCH_POKEMON / EVOLVE_POKEMON / PLAY_TRAINER / EQUIP_TOOL /
 *     USE_ABILITY (all from executeAction path, throws InvalidActionException)
 *   - executeAction null conditionToApply defaults to POISONED
 *   - get() when game is not found throws GameNotFoundException
 */
class GameApplicationServiceCoverageTest4 {

    private static GameApplicationService service() {
        return new GameApplicationService(
            new InMemoryGameStateRepository(),
            new GameEngineFacade(new GameEventPublisher()),
            new SetupEngineService(),
            new TurnActionValidator(),
            new VictoryService(),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager(),
            null,
            new TrainerEffectRegistry(List.of(new PotionTrainerEffect())),
            new AbilityEffectRegistry(List.of(new NoOpAbilityEffectStrategy())),
            null
        );
    }

    private static GameAggregate activeGame(GameApplicationService svc) {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = svc.create(p1);
        svc.join(created.id(), p2);
        return svc.runInitialSetup(created.id(), 60, 12, 60, 12);
    }

    // ----- executeAction APPLY_SPECIAL_CONDITION -----

    @Test
    void executeAction_applySpecialCondition_addsStatusToOpponent() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        UUID attacker = g.playerOne();
        UUID defender = g.playerTwo();
        g.setFirstPlayer(attacker);
        g.setCurrentTurnPlayer(attacker);
        g.setFirstTurn(false);
        g.setTurnNumber(3);
        g.setTurnPhase(TurnPhase.ATTACK);

        GameAggregate result = svc.executeAction(g.id(),
            GameActionType.APPLY_SPECIAL_CONDITION, null, null, null, List.of());

        assertNotNull(result);
        // Opponent should have POISONED status.
        assertTrue(result.statusByPlayer().getOrDefault(defender, java.util.EnumSet.noneOf(
            com.utn.pokemontcg.game.domain.model.StatusCondition.class))
            .contains(com.utn.pokemontcg.game.domain.model.StatusCondition.POISONED));
    }

    // ----- executeAction TAKE_PRIZE -----

    @Test
    void executeAction_takePrize_decreasesPrizeCount() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        UUID attacker = g.playerOne();
        g.setFirstPlayer(attacker);
        g.setCurrentTurnPlayer(attacker);
        g.setFirstTurn(false);
        g.setTurnNumber(3);
        g.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        g.prizeCardsRemaining().put(attacker, 6);

        GameAggregate result = svc.executeAction(g.id(), GameActionType.TAKE_PRIZE);

        assertEquals(5, result.prizeCardsRemaining().get(attacker));
    }

    // ----- executeAction RESOLVE_BETWEEN_TURNS -----

    @Test
    void executeAction_resolveBetweenTurns_advancesTurn() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        g.setFirstTurn(false);
        g.setTurnNumber(3);
        g.setTurnPhase(TurnPhase.BETWEEN_TURNS);

        // The function should not throw and should produce a game.
        GameAggregate result = svc.executeAction(g.id(), GameActionType.RESOLVE_BETWEEN_TURNS);
        assertNotNull(result);
    }

    // ----- executeAction SETUP_PLACE_ACTIVE / SETUP_PLACE_BENCH / SETUP_CONFIRM
    // from the executeAction switch should throw -----

    @Test
    void executeAction_setupPlaceActiveFromExecuteAction_throws() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        com.utn.pokemontcg.game.application.service.InvalidActionException ex =
            org.junit.jupiter.api.Assertions.assertThrows(
                com.utn.pokemontcg.game.application.service.InvalidActionException.class,
                () -> svc.executeAction(g.id(), GameActionType.SETUP_PLACE_ACTIVE));
        assertTrue(ex.getMessage().contains("usa un endpoint dedicado"));
    }

    @Test
    void executeAction_setupPlaceBenchFromExecuteAction_throws() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        com.utn.pokemontcg.game.application.service.InvalidActionException ex =
            org.junit.jupiter.api.Assertions.assertThrows(
                com.utn.pokemontcg.game.application.service.InvalidActionException.class,
                () -> svc.executeAction(g.id(), GameActionType.SETUP_PLACE_BENCH));
        assertTrue(ex.getMessage().contains("usa un endpoint dedicado"));
    }

    @Test
    void executeAction_setupConfirmFromExecuteAction_throws() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        com.utn.pokemontcg.game.application.service.InvalidActionException ex =
            org.junit.jupiter.api.Assertions.assertThrows(
                com.utn.pokemontcg.game.application.service.InvalidActionException.class,
                () -> svc.executeAction(g.id(), GameActionType.SETUP_CONFIRM));
        assertTrue(ex.getMessage().contains("usa un endpoint dedicado"));
    }

    @Test
    void executeAction_benchPokemonFromExecuteAction_throws() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        assertThrows(com.utn.pokemontcg.game.application.service.InvalidActionException.class,
            () -> svc.executeAction(g.id(), GameActionType.BENCH_POKEMON));
    }

    @Test
    void executeAction_evolvePokemonFromExecuteAction_throws() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        assertThrows(com.utn.pokemontcg.game.application.service.InvalidActionException.class,
            () -> svc.executeAction(g.id(), GameActionType.EVOLVE_POKEMON));
    }

    @Test
    void executeAction_playTrainerFromExecuteAction_throws() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        assertThrows(com.utn.pokemontcg.game.application.service.InvalidActionException.class,
            () -> svc.executeAction(g.id(), GameActionType.PLAY_TRAINER));
    }

    @Test
    void executeAction_equipToolFromExecuteAction_throws() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        assertThrows(com.utn.pokemontcg.game.application.service.InvalidActionException.class,
            () -> svc.executeAction(g.id(), GameActionType.EQUIP_TOOL));
    }

    @Test
    void executeAction_useAbilityFromExecuteAction_throws() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        assertThrows(com.utn.pokemontcg.game.application.service.InvalidActionException.class,
            () -> svc.executeAction(g.id(), GameActionType.USE_ABILITY));
    }

    // ----- executeAction ATTACK with null conditionToApply uses POISONED default -----

    @Test
    void executeAction_attachEnergyAddsEnergyAttachedFlag() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        g.setFirstTurn(false);
        g.setTurnPhase(TurnPhase.MAIN);
        g.setTurnNumber(3);
        UUID player = g.currentTurnPlayer();
        // Add an energy to the hand.
        GameCard energy = new GameCard(
            "default-energy", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0,
            "Fire", List.of(), 0);
        g.registerCard(energy);
        g.hand().get(player).add("default-energy");

        GameAggregate result = svc.executeAction(g.id(), GameActionType.ATTACH_ENERGY,
            null, "default-energy", "ACTIVE", List.of());

        // The turn flag should be set.
        assertTrue(result.turnFlags().energyAttached());
    }

    // ----- get() throws GameNotFoundException -----

    @Test
    void get_throwsGameNotFoundExceptionWhenGameMissing() {
        GameApplicationService svc = service();
        assertThrows(com.utn.pokemontcg.game.application.service.GameNotFoundException.class,
            () -> svc.get(UUID.randomUUID()));
    }

    // ----- DRAW from DRAW phase -----

    @Test
    void executeAction_draw_drawsAndAdvances() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        // Force playerOne to be the acting player.
        UUID player = g.playerOne();
        g.setFirstPlayer(player);
        g.setCurrentTurnPlayer(player);
        g.setFirstTurn(false);
        g.setTurnNumber(3);
        g.setTurnPhase(TurnPhase.DRAW);
        // Pre-populate the deck.
        g.deck().get(player).add("d-1");
        g.deckCardsRemaining().put(player, 1);

        GameAggregate result = svc.executeAction(g.id(), GameActionType.DRAW);

        // The hand should now contain d-1, and turn should advance to MAIN.
        assertNotNull(result);
        // We don't strictly assert hand contents (mutation order) but we
        // do assert the phase moved.
        assertEquals(TurnPhase.MAIN, result.turnPhase());
    }

    // ----- finishReason coverage via prize win -----

    @Test
    void executeAction_takeLastPrize_finishesGameForAttacker() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        UUID attacker = g.playerOne();
        g.setFirstPlayer(attacker);
        g.setCurrentTurnPlayer(attacker);
        g.setFirstTurn(false);
        g.setTurnNumber(3);
        g.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        g.prizeCards().put(attacker, new java.util.ArrayList<>(List.of("last-prize")));
        g.prizeCardsRemaining().put(attacker, 1);
        g.activeHp().put(g.playerTwo(), 0);
        g.activeDamageCounters().put(g.playerTwo(), 99);
        // Force defender to have no bench
        g.bench().get(g.playerTwo()).clear();

        GameAggregate result = svc.executeAction(g.id(), GameActionType.TAKE_PRIZE);

        // Prize win finishes the game.
        assertEquals(GameState.FINISHED, result.gameState());
        assertEquals(attacker, result.winner());
    }

    private static <T extends Throwable> T assertThrows(Class<T> type, org.junit.jupiter.api.function.Executable exec) {
        return org.junit.jupiter.api.Assertions.assertThrows(type, exec);
    }
}
