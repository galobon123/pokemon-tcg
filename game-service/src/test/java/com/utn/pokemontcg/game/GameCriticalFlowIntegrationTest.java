package com.utn.pokemontcg.game;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.utn.pokemontcg.game.application.repository.ActionLogEntry;
import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.application.service.SetupEngineService;
import com.utn.pokemontcg.game.application.service.TurnActionValidator;
import com.utn.pokemontcg.game.application.service.VictoryService;
import com.utn.pokemontcg.game.domain.engine.DamageCalculator;
import com.utn.pokemontcg.game.domain.engine.RuleValidator;
import com.utn.pokemontcg.game.domain.engine.StatusEffectManager;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;

class GameCriticalFlowIntegrationTest {

    @Test
    void deckOutFinishesGameWhenActivePlayerCannotDraw() {
        GameApplicationService service = service();
        GameAggregate game = activeGame(service);

        UUID drawingPlayer = game.currentTurnPlayer();
        UUID opponent = opponentOf(game, drawingPlayer);
        game.setFirstTurn(false);
        game.setTurnPhase(TurnPhase.DRAW);
        game.deck().get(drawingPlayer).clear();
        game.deckCardsRemaining().put(drawingPlayer, 0);

        GameAggregate afterDraw = service.executeAction(game.id(), GameActionType.DRAW);

        assertEquals(GameState.FINISHED, afterDraw.gameState());
        assertEquals(opponent, afterDraw.winner());
    }

    @Test
    void knockoutWithoutBenchFinishesGameForAttacker() {
        GameApplicationService service = service();
        GameAggregate game = activeGame(service);

        UUID attacker = game.playerOne();
        UUID defender = game.playerTwo();
        game.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        game.bench().get(defender).clear();
        game.activeHp().put(defender, 0);
        game.activeDamageCounters().put(defender, 99);

        GameAggregate afterKo = service.executeAction(game.id(), GameActionType.RESOLVE_BETWEEN_TURNS);

        assertEquals(GameState.FINISHED, afterKo.gameState());
        assertEquals(attacker, afterKo.winner());
        assertFalse(afterKo.hasPokemonInPlay(defender));
    }

    @Test
    void takingLastPrizeFinishesGameForAttacker() {
        GameApplicationService service = service();
        GameAggregate game = activeGame(service);

        UUID attacker = game.playerOne();
        UUID defender = game.playerTwo();
        game.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        game.prizeCards().put(attacker, new java.util.ArrayList<>(List.of("last-prize")));
        game.prizeCardsRemaining().put(attacker, 1);
        game.activeHp().put(defender, 0);
        game.activeDamageCounters().put(defender, 99);

        GameAggregate afterPrize = service.executeAction(game.id(), GameActionType.RESOLVE_BETWEEN_TURNS);

        assertEquals(GameState.FINISHED, afterPrize.gameState());
        assertEquals(attacker, afterPrize.winner());
        assertEquals(0, afterPrize.prizeCardsRemaining().get(attacker));
    }

    @Test
    void knockoutWithEquippedToolDiscardsToolAndPokemon() {
        GameApplicationService service = service();
        GameAggregate game = activeGame(service);

        UUID attacker = game.playerOne();
        UUID defender = game.playerTwo();
        String knockedOut = game.activePokemon().get(defender);
        game.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        game.equipTool(defender, "ACTIVE", "tool-card-1");
        game.bench().get(defender).clear();
        game.activeHp().put(defender, 0);
        game.activeDamageCounters().put(defender, 99);

        GameAggregate afterKo = service.executeAction(game.id(), GameActionType.RESOLVE_BETWEEN_TURNS);

        assertEquals(GameState.FINISHED, afterKo.gameState());
        assertEquals(attacker, afterKo.winner());
        assertEquals(List.of(knockedOut, "tool-card-1"), afterKo.discardPile().get(defender));
        assertNull(afterKo.toolAtSlot(defender, "ACTIVE"));
    }

    @Test
    void knockoutWithSingleBenchPromotesAutomaticallyWithoutWaitingReplacement() {
        GameApplicationService service = service();
        GameAggregate game = activeGame(service);

        UUID defender = game.playerTwo();
        ensureBenchSize(game, defender, 1);
        String expectedPromotion = game.bench().get(defender).getFirst();
        game.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        game.activeHp().put(defender, 0);
        game.activeDamageCounters().put(defender, 99);

        GameAggregate afterKo = service.executeAction(game.id(), GameActionType.RESOLVE_BETWEEN_TURNS);

        assertEquals(GameState.ACTIVE, afterKo.gameState());
        assertEquals(expectedPromotion, afterKo.activePokemon().get(defender));
        assertNull(afterKo.replacementPlayerId());
    }

    @Test
    void knockoutWithTwoBenchPokemonWaitsForReplacementChoice() {
        GameApplicationService service = service();
        GameAggregate game = activeGame(service);

        UUID defender = game.playerTwo();
        game.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        ensureBenchSize(game, defender, 2);
        game.activeHp().put(defender, 0);
        game.activeDamageCounters().put(defender, 99);

        GameAggregate afterKo = service.executeAction(game.id(), GameActionType.RESOLVE_BETWEEN_TURNS);

        assertEquals(GameState.WAITING_REPLACEMENT, afterKo.gameState());
        assertEquals(defender, afterKo.replacementPlayerId());
        assertNull(afterKo.activePokemon().get(defender));
        assertEquals(2, afterKo.bench().get(defender).size());
    }

    @Test
    void replaceActiveRejectsWrongPlayerAndEmptyBenchSlot() {
        GameApplicationService service = service();
        GameAggregate game = activeGameWaitingReplacement(service);

        InvalidActionException wrongPlayer = assertThrows(
            InvalidActionException.class,
            () -> service.replaceActive(game.id(), game.playerOne(), 0)
        );
        assertEquals("Solo el jugador pendiente puede elegir el reemplazo", wrongPlayer.getMessage());

        InvalidActionException emptySlot = assertThrows(
            InvalidActionException.class,
            () -> service.replaceActive(game.id(), game.playerTwo(), 4)
        );
        assertEquals("El benchSlot indicado esta vacio", emptySlot.getMessage());
    }

    @Test
    void replaceActivePromotesChosenBenchPokemonAndClearsConditions() {
        GameApplicationService service = service();
        GameAggregate game = activeGameWaitingReplacement(service);
        UUID defender = game.playerTwo();
        String expectedPromotion = game.bench().get(defender).get(1);
        game.statusByPlayer().put(defender, java.util.EnumSet.of(StatusCondition.POISONED));

        GameAggregate afterReplacement = service.replaceActive(game.id(), defender, 1);

        assertEquals(GameState.ACTIVE, afterReplacement.gameState());
        assertEquals(expectedPromotion, afterReplacement.activePokemon().get(defender));
        assertNull(afterReplacement.replacementPlayerId());
        assertFalse(afterReplacement.statusByPlayer().containsKey(defender));
    }

    @Test
    void poisonedKnockoutBetweenTurnsUsesReplacementFlow() {
        GameApplicationService service = service();
        GameAggregate game = activeGame(service);

        UUID defender = game.playerTwo();
        game.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        ensureBenchSize(game, defender, 2);
        game.statusByPlayer().put(defender, java.util.EnumSet.of(StatusCondition.POISONED));
        game.activeDamageCounters().put(defender, 11);
        game.damageCountersBySlot().get(defender).put("ACTIVE", 11);
        game.activeHp().put(defender, 10);

        GameAggregate afterBetweenTurns = service.executeAction(game.id(), GameActionType.RESOLVE_BETWEEN_TURNS);

        assertEquals(GameState.WAITING_REPLACEMENT, afterBetweenTurns.gameState());
        assertEquals(defender, afterBetweenTurns.replacementPlayerId());
    }

    @Test
    void benchKnockoutDiscardsBenchPokemonToolAndAwardsPrize() {
        GameApplicationService service = service();
        GameAggregate game = activeGame(service);

        UUID attacker = game.playerOne();
        UUID defender = game.playerTwo();
        game.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        game.bench().get(defender).clear();
        game.bench().get(defender).add("bench-ko");
        game.registerCard(new GameCard("bench-ko", "Bench Target", "Pokémon", Set.of("Basic"), 30, 0, 0));
        game.equipTool(defender, "BENCH_0", "bench-tool-1");
        game.damageCountersBySlot().get(defender).put("BENCH_0", 3);
        int prizesBefore = game.prizeCardsRemaining().get(attacker);

        GameAggregate afterBetweenTurns = service.executeAction(game.id(), GameActionType.RESOLVE_BETWEEN_TURNS);

        assertFalse(afterBetweenTurns.bench().get(defender).contains("bench-ko"));
        assertEquals(List.of("bench-ko", "bench-tool-1"), afterBetweenTurns.discardPile().get(defender));
        assertNull(afterBetweenTurns.toolAtSlot(defender, "BENCH_0"));
        assertEquals(prizesBefore - 1, afterBetweenTurns.prizeCardsRemaining().get(attacker));
    }

    @Test
    void actionLogStoresStructuredPayload() {
        GameApplicationService service = service();
        GameAggregate game = activeGame(service);

        List<ActionLogEntry> entries = service.actionLog(game.id());

        assertFalse(entries.isEmpty());
        ActionLogEntry setupEntry = entries.getFirst();
        assertEquals(GameActionType.DRAW, setupEntry.action());
        assertNotNull(setupEntry.createdAt());
        assertEquals(game.id(), setupEntry.gameId());
        assertEquals("SETUP_COMPLETED", setupEntry.result());
        assertEquals(GameActionType.DRAW.name(), setupEntry.payload().get("action"));
    }

    private GameAggregate activeGame(GameApplicationService service) {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = service.create(p1);
        service.join(created.id(), p2);
        return service.runInitialSetup(created.id(), 60, 12, 60, 12);
    }

    private GameAggregate activeGameWaitingReplacement(GameApplicationService service) {
        GameAggregate game = activeGame(service);
        UUID defender = game.playerTwo();
        game.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        ensureBenchSize(game, defender, 2);
        game.activeHp().put(defender, 0);
        game.activeDamageCounters().put(defender, 99);
        return service.executeAction(game.id(), GameActionType.RESOLVE_BETWEEN_TURNS);
    }

    private void ensureBenchSize(GameAggregate game, UUID player, int size) {
        List<String> bench = game.bench().computeIfAbsent(player, ignored -> new java.util.ArrayList<>());
        bench.clear();
        for (int i = 0; i < size; i++) {
            bench.add("bench-extra-" + i);
        }
    }

    private UUID opponentOf(GameAggregate game, UUID player) {
        return player.equals(game.playerOne()) ? game.playerTwo() : game.playerOne();
    }

    private GameApplicationService service() {
        SetupEngineService setupEngineService = new SetupEngineService();
        return new GameApplicationService(
            new InMemoryGameStateRepository(),
            new GameEngineFacade(new GameEventPublisher()),
            setupEngineService,
            new TurnActionValidator(),
            new VictoryService(setupEngineService),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager()
        );
    }
}
