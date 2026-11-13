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
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GamePerformanceEvidenceTest {

    @Test
    void turnActionsAverageUnderTwoHundredMilliseconds() {
        GameApplicationService service = service();
        GameAggregate game = activeGame(service);
        int samples = 40;
        long totalNanos = 0;

        for (int i = 0; i < samples; i++) {
            service.get(game.id()).setTurnPhase(TurnPhase.MAIN);

            long startedAt = System.nanoTime();
            service.executeAction(game.id(), GameActionType.END_TURN);
            totalNanos += System.nanoTime() - startedAt;
        }

        double averageMillis = totalNanos / 1_000_000.0 / samples;
        System.out.printf("PERF-EVIDENCE game-service action average: %.3f ms%n", averageMillis);
        assertTrue(
            averageMillis < 200,
            () -> "Promedio de accion esperado < 200ms, actual: " + averageMillis + "ms"
        );
    }

    private GameAggregate activeGame(GameApplicationService service) {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = service.create(p1);
        service.join(created.id(), p2);
        return service.runInitialSetup(created.id(), 60, 12, 60, 12);
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
