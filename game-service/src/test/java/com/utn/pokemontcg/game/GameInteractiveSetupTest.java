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
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.SetupPhase;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.NoOpAbilityEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.PotionTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectRegistry;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameInteractiveSetupTest {

    @Test
    void interactiveSetup_waitsForActiveBenchAndBothConfirmationsBeforeStarting() {
        GameApplicationService service = service();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = service.create(p1);
        service.join(created.id(), p2);

        GameAggregate setup = service.startInteractiveSetup(created.id(), 60, 12, 60, 12, null, null);

        assertEquals(GameState.SETUP, setup.gameState());
        assertEquals(SetupPhase.SETUP_WAITING_ACTIVE, setup.setupPhase());
        assertNull(setup.currentTurnPlayer());
        assertTrue(setup.activePokemon().isEmpty());
        assertEquals(6, setup.prizeCardsRemaining().get(p1));
        assertEquals(6, setup.prizeCardsRemaining().get(p2));

        String p1Active = firstBasic(setup, p1);
        String p2Active = firstBasic(setup, p2);

        GameAggregate afterP1Active = service.placeSetupActive(created.id(), p1, p1Active);
        assertEquals(SetupPhase.SETUP_WAITING_ACTIVE, afterP1Active.setupPhase());
        assertEquals(p1Active, afterP1Active.activePokemon().get(p1));

        GameAggregate afterP2Active = service.placeSetupActive(created.id(), p2, p2Active);
        assertEquals(SetupPhase.SETUP_WAITING_BENCH, afterP2Active.setupPhase());
        assertEquals(p2Active, afterP2Active.activePokemon().get(p2));

        service.placeSetupBench(created.id(), p1, List.of());
        service.placeSetupBench(created.id(), p2, List.of());

        GameAggregate afterP1Confirm = service.confirmSetup(created.id(), p1);
        assertEquals(GameState.SETUP, afterP1Confirm.gameState());
        assertEquals(SetupPhase.SETUP_WAITING_BENCH, afterP1Confirm.setupPhase());

        GameAggregate afterP2Confirm = service.confirmSetup(created.id(), p2);
        assertEquals(GameState.COIN_FLIP, afterP2Confirm.gameState());

        GameAggregate flipped = service.flipCoin(created.id(), "HEADS");
        UUID winner = flipped.coinFlipWinner();
        assertNotNull(winner);

        GameAggregate started = service.chooseFirstPlayer(created.id(), winner, true);

        assertEquals(GameState.ACTIVE, started.gameState());
        assertEquals(TurnPhase.MAIN, started.turnPhase());
        assertNull(started.setupPhase());
        assertNotNull(started.firstPlayer());
        assertEquals(started.firstPlayer(), started.currentTurnPlayer());
        assertTrue(started.setupConfirmed(p1));
    }

    @Test
    void shouldCancelGameDueToInactivityWhenBothPlayersDoNotPlayActivePokemon() {
        GameApplicationService service = service();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = service.create(p1);
        service.join(created.id(), p2);

        GameAggregate setup = service.startInteractiveSetup(created.id(), 60, 12, 60, 12, null, null);

        assertEquals(GameState.SETUP, setup.gameState());
        assertEquals(SetupPhase.SETUP_WAITING_ACTIVE, setup.setupPhase());

        // Simulate that time has run out
        setup.setSetupCountdownEndsAt(java.time.Instant.now().minusSeconds(10));

        // Trigger finishCountdownIfExpired by calling get()
        service.get(setup.id());

        GameAggregate finished = service.get(setup.id());
        assertEquals(GameState.FINISHED, finished.gameState());
        assertNull(finished.winner());
        assertNull(finished.setupPhase());
        assertNull(finished.setupCountdownEndsAt());
    }


    private String firstBasic(GameAggregate game, UUID player) {
        return game.hand().get(player).stream()
            .filter(cardId -> game.cardCatalog().get(cardId).isBasicPokemon())
            .findFirst()
            .orElseThrow();
    }

    private GameApplicationService service() {
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
}
