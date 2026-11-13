package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.*;
import com.utn.pokemontcg.game.domain.engine.DamageCalculator;
import com.utn.pokemontcg.game.domain.engine.RuleValidator;
import com.utn.pokemontcg.game.domain.engine.StatusEffectManager;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.event.GameEventType;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameApplicationServiceFlowIntegrationTest {

    @Test
    void createJoinSetupTurnAttack_flow_is_consistent() {
        CountingGameStateRepository repository = new CountingGameStateRepository();
        GameEventPublisher publisher = new GameEventPublisher();
        // Instanciación manual: Rápida, limpia y desacoplada del framework
        GameApplicationService gameApplicationService = new GameApplicationService(
                repository,
                new GameEngineFacade(publisher),
                new SetupEngineService(),
                new TurnActionValidator(),
                new VictoryService(),
                new RuleValidator(),
                new DamageCalculator(),
                new StatusEffectManager()
        );

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        // 1. Creación y Join
        var game = gameApplicationService.create(p1);
        gameApplicationService.join(game.id(), p2);

        // 2. Setup Inicial
        var afterSetup = gameApplicationService.runInitialSetup(
                game.id(), 60, 12, 60, 12
        );

        assertEquals(TurnPhase.MAIN, afterSetup.turnPhase());

        // 3. Acciones de Turno
        UUID currentPlayer = afterSetup.currentTurnPlayer();
        afterSetup.setFirstTurn(false);
        afterSetup.turnsStartedByPlayer().put(currentPlayer, 2);
        String energyCardId = afterSetup.hand().get(currentPlayer).stream()
            .filter(cardId -> afterSetup.cardCatalog().get(cardId).isEnergy())
            .findFirst()
            .orElseThrow();
        gameApplicationService.executeAction(game.id(), GameActionType.ATTACH_ENERGY, null, energyCardId, "ACTIVE", List.of());
        var afterAttach = gameApplicationService.get(game.id());
        assertEquals(false, afterAttach.hand().get(currentPlayer).contains(energyCardId));
        assertEquals(energyCardId, afterAttach.attachedEnergiesFor(currentPlayer, "ACTIVE").getFirst().cardId());
        gameApplicationService.executeAction(game.id(), GameActionType.PLAY_SUPPORTER);
        var afterMain = gameApplicationService.executeAction(game.id(), GameActionType.END_TURN);

        assertEquals(TurnPhase.ATTACK, afterMain.turnPhase());

        // 4. Simulación de Fase de Ataque
        var beforeAttackPhase = gameApplicationService.get(game.id());
        beforeAttackPhase.setTurnPhase(TurnPhase.ATTACK);
        beforeAttackPhase.setFirstTurn(false);
        int saveCountBeforeAttack = repository.saveCount();
        AtomicInteger saveCountAtAttackResolved = new AtomicInteger(-1);
        publisher.subscribe(event -> {
            if (GameEventType.ATTACK_RESOLVED.name().equals(event.type())) {
                saveCountAtAttackResolved.set(repository.saveCount());
            }
        });

        var afterAttack = gameApplicationService.executeAction(game.id(), GameActionType.ATTACK);

        // 5. Verificación de cambio de fase
        assertEquals(TurnPhase.BETWEEN_TURNS, afterAttack.turnPhase());
        assertEquals(saveCountBeforeAttack + 1, saveCountAtAttackResolved.get());

        var nextTurn = gameApplicationService.executeAction(game.id(), GameActionType.RESOLVE_BETWEEN_TURNS);
        UUID nextPlayer = nextTurn.currentTurnPlayer();
        int deckBeforeDraw = nextTurn.deckCardsRemaining().get(nextPlayer);

        var afterDraw = gameApplicationService.executeAction(game.id(), GameActionType.DRAW);

        assertEquals(TurnPhase.MAIN, afterDraw.turnPhase());
        assertEquals(deckBeforeDraw - 1, afterDraw.deckCardsRemaining().get(nextPlayer));
        assertEquals(afterDraw.deck().get(nextPlayer).size(), afterDraw.deckCardsRemaining().get(nextPlayer));
    }

    @Test
    void specialConditionCanBeSelectedAndBetweenTurnsResolutionStartsNextTurn() {
        GameApplicationService gameApplicationService = new GameApplicationService(
                new InMemoryGameStateRepository(),
                new GameEngineFacade(new GameEventPublisher()),
                new SetupEngineService(),
                new TurnActionValidator(),
                new VictoryService(),
                new RuleValidator(),
                new DamageCalculator(),
                new StatusEffectManager()
        );

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        var game = gameApplicationService.create(p1);
        gameApplicationService.join(game.id(), p2);
        var setup = gameApplicationService.runInitialSetup(game.id(), 60, 12, 60, 12);

        UUID activePlayer = setup.currentTurnPlayer();
        UUID defender = activePlayer.equals(p1) ? p2 : p1;
        setup.setTurnPhase(TurnPhase.BETWEEN_TURNS);

        var conditioned = gameApplicationService.executeAction(
            game.id(),
            GameActionType.APPLY_SPECIAL_CONDITION,
            StatusCondition.BURNED
        );

        assertEquals(StatusCondition.BURNED, conditioned.statusByPlayer().get(defender).iterator().next());

        var nextTurn = gameApplicationService.executeAction(game.id(), GameActionType.RESOLVE_BETWEEN_TURNS);

        assertEquals(TurnPhase.DRAW, nextTurn.turnPhase());
        assertEquals(defender, nextTurn.currentTurnPlayer());
    }

    private static class CountingGameStateRepository extends InMemoryGameStateRepository {
        private int saveCount;

        @Override
        public GameAggregate save(GameAggregate game) {
            saveCount++;
            return super.save(game);
        }

        int saveCount() {
            return saveCount;
        }
    }
}
