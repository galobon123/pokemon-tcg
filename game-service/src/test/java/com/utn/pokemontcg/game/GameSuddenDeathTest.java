package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.SetupEngineService;
import com.utn.pokemontcg.game.application.service.VictoryService;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSuddenDeathTest {

    @Test
    void simultaneousVictoryRestartsAsActiveSuddenDeathWithOnePrize() {
        SetupEngineService setupEngineService = new SetupEngineService();
        VictoryService victoryService = new VictoryService(setupEngineService);
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);

        setupEngineService.preparePlayerBoard(p1, seedDeckFor(p1), 0, game);
        setupEngineService.preparePlayerBoard(p2, seedDeckFor(p2), 0, game);
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        game.prizeCardsRemaining().put(p1, 0);
        game.prizeCardsRemaining().put(p2, 0);

        victoryService.closeGameIfNeeded(game);

        assertEquals(GameState.ACTIVE, game.gameState());
        assertTrue(game.suddenDeath());
        assertNull(game.winner());
        assertEquals(TurnPhase.MAIN, game.turnPhase());
        assertNotNull(game.firstPlayer());
        assertNotNull(game.currentTurnPlayer());
        assertNotNull(game.activePokemon().get(p1));
        assertNotNull(game.activePokemon().get(p2));
        assertEquals(1, game.prizeCards().get(p1).size());
        assertEquals(1, game.prizeCards().get(p2).size());
        assertEquals(1, game.prizeCardsRemaining().get(p1));
        assertEquals(1, game.prizeCardsRemaining().get(p2));
        assertEquals(60, game.originalDecks().get(p1).size());
        assertEquals(60, game.originalDecks().get(p2).size());
    }

    private List<GameCard> seedDeckFor(UUID player) {
        List<GameCard> cards = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            cards.add(new GameCard(
                "xy1-1:" + player + ":" + i,
                "Venusaur-EX",
                "Pokémon",
                Set.of("Basic", "EX"),
                180,
                60,
                3,
                "Poison Powder",
                "Your opponent's Active Pokemon is now Poisoned.",
                null,
                List.of("Grass", "Colorless", "Colorless"),
                4,
                null,
                List.of()
            ));
        }
        for (int i = 4; i < 60; i++) {
            cards.add(new GameCard(
                "xy1-132:" + player + ":" + i,
                "Grass Energy",
                "Energy",
                Set.of("Basic"),
                0,
                0,
                0,
                "Grass",
                List.of(),
                0
            ));
        }
        return cards;
    }
}
