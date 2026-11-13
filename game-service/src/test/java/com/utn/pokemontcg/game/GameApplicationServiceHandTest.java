package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.application.service.GameNotFoundException;
import com.utn.pokemontcg.game.application.service.PlayerNotInGameException;
import com.utn.pokemontcg.game.application.service.SetupEngineService;
import com.utn.pokemontcg.game.application.service.TurnActionValidator;
import com.utn.pokemontcg.game.application.service.VictoryService;
import com.utn.pokemontcg.game.domain.engine.DamageCalculator;
import com.utn.pokemontcg.game.domain.engine.RuleValidator;
import com.utn.pokemontcg.game.domain.engine.StatusEffectManager;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameApplicationServiceHandTest {

    private final InMemoryGameStateRepository repository = new InMemoryGameStateRepository();
    private final GameApplicationService service = new GameApplicationService(
        repository,
        new GameEngineFacade(new GameEventPublisher()),
        new SetupEngineService(),
        new TurnActionValidator(),
        new VictoryService(),
        new RuleValidator(),
        new DamageCalculator(),
        new StatusEffectManager()
    );

    @Test
    void shouldReturnHandCardIdsForValidPlayer() {
        UUID playerId = UUID.randomUUID();
        var game = service.create(playerId);
        game.hand().put(playerId, List.of("xy1-1", "xy1-2", "xy1-3"));
        repository.save(game);

        List<String> result = service.getHandCardIds(game.id(), playerId);

        assertEquals(List.of("xy1-1", "xy1-2", "xy1-3"), result);
    }

    @Test
    void shouldReturnEmptyHandWhenWaitingGameHasNoInitializedHand() {
        UUID playerId = UUID.randomUUID();
        var game = service.create(playerId);

        List<String> result = service.getHandCardIds(game.id(), playerId);

        assertEquals(List.of(), result);
    }

    @Test
    void shouldThrowWhenGameDoesNotExist() {
        assertThrows(
            GameNotFoundException.class,
            () -> service.getHandCardIds(UUID.randomUUID(), UUID.randomUUID())
        );
    }

    @Test
    void shouldThrowWhenPlayerDoesNotBelongToGame() {
        var game = service.create(UUID.randomUUID());

        assertThrows(
            PlayerNotInGameException.class,
            () -> service.getHandCardIds(game.id(), UUID.randomUUID())
        );
    }
}
