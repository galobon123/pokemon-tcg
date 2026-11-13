package com.utn.pokemontcg.game.presentation;

import com.utn.pokemontcg.game.application.repository.GameStateRepository;
import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.model.User;
import com.utn.pokemontcg.game.presentation.controller.HistoryController;
import com.utn.pokemontcg.game.presentation.dto.GameHistoryResponse;
import com.utn.pokemontcg.game.repository.UserRepository;
import com.utn.pokemontcg.game.security.CurrentUserService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoryControllerTest {

    @Test
    void clearHistoryReturns204() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        GameStateRepository gameStateRepository = mock(GameStateRepository.class);
        HistoryController controller = new HistoryController(service, currentUserService, userRepository, gameStateRepository);
        User user = new User();
        UUID playerId = UUID.randomUUID();
        user.setPlayerId(playerId);
        when(currentUserService.requireUser(null)).thenReturn(user);

        var response = controller.clearHistory(null);

        assertEquals(204, response.getStatusCode().value());
        verify(gameStateRepository).deleteAll();
    }

    @Test
    void historyReturnsListOfGames() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        UserRepository userRepository = mock(UserRepository.class);
        GameStateRepository gameStateRepository = mock(GameStateRepository.class);
        HistoryController controller = new HistoryController(service, currentUserService, userRepository, gameStateRepository);
        UUID playerId = UUID.randomUUID();
        UUID opponentId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(playerId);
        User opponent = new User();
        opponent.setPlayerId(opponentId);
        opponent.setUsername("Opponent");
        when(currentUserService.requireUser(null)).thenReturn(user);
        when(userRepository.findByPlayerId(opponentId)).thenReturn(Optional.of(opponent));

        GameAggregate game = new GameAggregate(playerId);
        game.setPlayerTwo(opponentId);
        game.setWinner(playerId);
        game.activePokemon().put(opponentId, "opponent-active");
        game.prizeCardsRemaining().put(playerId, 1);
        game.deck().put(opponentId, List.of("d1"));

        when(service.getHistory(playerId)).thenReturn(List.of(game));

        var response = controller.history(null);

        assertEquals(200, response.getStatusCode().value());
        List<GameHistoryResponse> history = response.getBody();
        assertNotNull(history);
        assertEquals(1, history.size());
        GameHistoryResponse entry = history.get(0);
        assertEquals(game.id(), entry.gameId());
        assertEquals("Opponent", entry.opponentName());
        assertTrue(entry.won());
        assertNotNull(entry.finishedAt());
    }

    @Test
    void historyReturnsUnknownOpponentWhenNotFound() {
        var service = mock(GameApplicationService.class);
        var currentUserService = mock(CurrentUserService.class);
        var userRepository = mock(UserRepository.class);
        var gameStateRepository = mock(GameStateRepository.class);
        var controller = new HistoryController(service, currentUserService, userRepository, gameStateRepository);
        UUID playerId = UUID.randomUUID();
        UUID opponentId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(playerId);
        when(currentUserService.requireUser(null)).thenReturn(user);
        when(userRepository.findByPlayerId(opponentId)).thenReturn(Optional.empty());

        GameAggregate game = new GameAggregate(playerId);
        game.setPlayerTwo(opponentId);
        game.setWinner(playerId);
        game.activePokemon().put(opponentId, "opponent-active");
        game.prizeCardsRemaining().put(playerId, 1);
        game.deck().put(opponentId, List.of("d1"));
        when(service.getHistory(playerId)).thenReturn(List.of(game));

        var response = controller.history(null);
        var history = response.getBody();
        assertEquals("Unknown", history.get(0).opponentName());
    }

    @Test
    void historyPlayerAsPlayerTwoLost() {
        var service = mock(GameApplicationService.class);
        var currentUserService = mock(CurrentUserService.class);
        var userRepository = mock(UserRepository.class);
        var gameStateRepository = mock(GameStateRepository.class);
        var controller = new HistoryController(service, currentUserService, userRepository, gameStateRepository);
        UUID playerOneId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(playerId);
        when(currentUserService.requireUser(null)).thenReturn(user);

        GameAggregate game = new GameAggregate(playerOneId);
        game.setPlayerTwo(playerId);
        game.setWinner(playerOneId);
        game.activePokemon().put(playerId, "me-active");
        game.prizeCardsRemaining().put(playerOneId, 0);
        game.deck().put(playerId, List.of("d1"));
        when(service.getHistory(playerId)).thenReturn(List.of(game));

        var response = controller.history(null);
        var history = response.getBody();
        assertEquals(1, history.size());
        assertFalse(history.get(0).won());
    }

    @Test
    void historyResolveFinishReasonPrizes() {
        var service = mock(GameApplicationService.class);
        var currentUserService = mock(CurrentUserService.class);
        var userRepository = mock(UserRepository.class);
        var gameStateRepository = mock(GameStateRepository.class);
        var controller = new HistoryController(service, currentUserService, userRepository, gameStateRepository);
        UUID playerId = UUID.randomUUID();
        UUID opponentId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(playerId);
        when(currentUserService.requireUser(null)).thenReturn(user);

        GameAggregate game = new GameAggregate(playerId);
        game.setPlayerTwo(opponentId);
        game.setWinner(playerId);
        game.activePokemon().put(opponentId, "opp-active");
        game.prizeCardsRemaining().put(playerId, 0);
        game.deck().put(opponentId, List.of("d1"));
        when(service.getHistory(playerId)).thenReturn(List.of(game));

        var response = controller.history(null);
        var history = response.getBody();
        assertEquals(1, history.size());
    }

    @Test
    void historyResolveFinishReasonKoTotal() {
        var service = mock(GameApplicationService.class);
        var currentUserService = mock(CurrentUserService.class);
        var userRepository = mock(UserRepository.class);
        var gameStateRepository = mock(GameStateRepository.class);
        var controller = new HistoryController(service, currentUserService, userRepository, gameStateRepository);
        UUID playerId = UUID.randomUUID();
        UUID opponentId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(playerId);
        when(currentUserService.requireUser(null)).thenReturn(user);

        GameAggregate game = new GameAggregate(playerId);
        game.setPlayerTwo(opponentId);
        game.setWinner(playerId);
        game.prizeCardsRemaining().put(playerId, 2);
        game.deck().put(opponentId, List.of("d1"));
        game.activePokemon().put(opponentId, null);
        when(service.getHistory(playerId)).thenReturn(List.of(game));

        var response = controller.history(null);
        var history = response.getBody();
        assertEquals(1, history.size());
    }

    @Test
    void historyResolveFinishReasonDeckOut() {
        var service = mock(GameApplicationService.class);
        var currentUserService = mock(CurrentUserService.class);
        var userRepository = mock(UserRepository.class);
        var gameStateRepository = mock(GameStateRepository.class);
        var controller = new HistoryController(service, currentUserService, userRepository, gameStateRepository);
        UUID playerId = UUID.randomUUID();
        UUID opponentId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(playerId);
        when(currentUserService.requireUser(null)).thenReturn(user);

        GameAggregate game = new GameAggregate(playerId);
        game.setPlayerTwo(opponentId);
        game.setWinner(playerId);
        game.activePokemon().put(opponentId, "opp-active");
        game.prizeCardsRemaining().put(playerId, 2);
        game.deck().put(opponentId, List.of());
        when(service.getHistory(playerId)).thenReturn(List.of(game));

        var response = controller.history(null);
        var history = response.getBody();
        assertEquals(1, history.size());
    }

    @Test
    void historyResolveFinishReasonUnknownWhenLoserNull() {
        var service = mock(GameApplicationService.class);
        var currentUserService = mock(CurrentUserService.class);
        var userRepository = mock(UserRepository.class);
        var gameStateRepository = mock(GameStateRepository.class);
        var controller = new HistoryController(service, currentUserService, userRepository, gameStateRepository);
        UUID playerId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(playerId);
        when(currentUserService.requireUser(null)).thenReturn(user);

        GameAggregate game = new GameAggregate(playerId);
        game.setWinner(playerId);
        when(service.getHistory(playerId)).thenReturn(List.of(game));

        var response = controller.history(null);
        var history = response.getBody();
        assertEquals(1, history.size());
    }

    @Test
    void historyMultipleGamesSorted() {
        var service = mock(GameApplicationService.class);
        var currentUserService = mock(CurrentUserService.class);
        var userRepository = mock(UserRepository.class);
        var gameStateRepository = mock(GameStateRepository.class);
        var controller = new HistoryController(service, currentUserService, userRepository, gameStateRepository);
        UUID playerId = UUID.randomUUID();
        UUID opponentId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(playerId);
        when(currentUserService.requireUser(null)).thenReturn(user);

        GameAggregate game1 = new GameAggregate(playerId);
        game1.setPlayerTwo(opponentId);
        game1.setWinner(playerId);
        game1.activePokemon().put(opponentId, "a");
        game1.prizeCardsRemaining().put(playerId, 1);
        game1.deck().put(opponentId, List.of("d1"));

        GameAggregate game2 = new GameAggregate(playerId);
        game2.setPlayerTwo(opponentId);
        game2.setWinner(opponentId);
        game2.activePokemon().put(playerId, "b");
        game2.prizeCardsRemaining().put(opponentId, 1);
        game2.deck().put(playerId, List.of("d2"));

        when(service.getHistory(playerId)).thenReturn(List.of(game1, game2));

        var response = controller.history(null);
        var history = response.getBody();
        assertEquals(2, history.size());
    }
}
