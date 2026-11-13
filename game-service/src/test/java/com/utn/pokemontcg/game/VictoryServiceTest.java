package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.VictoryService;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VictoryServiceTest {

    private final VictoryService service = new VictoryService();

    private GameAggregate gameWithActive(UUID p1, UUID p2) {
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.initializeZonesFor(p1, List.of("deck1", "deck2", "deck3"));
        game.initializeZonesFor(p2, List.of("deck1", "deck2", "deck3"));
        game.placeSetupActive(p1, "p1-active");
        game.placeSetupActive(p2, "p2-active");
        game.registerCard(new GameCard("p1-active", "Active", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard("p2-active", "Defender", "Pokemon", Set.of("Basic"), 60, 10, 1));
        return game;
    }

    @Test
    void checkWinnerReturnsNullWhenBothPlayersNull() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        assertNull(service.checkWinner(game));
    }

    @Test
    void checkWinnerReturnsNullWhenNoWinCondition() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = gameWithActive(p1, p2);
        assertNull(service.checkWinner(game));
    }

    @Test
    void checkWinnerDetectsPrizeWin() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = gameWithActive(p1, p2);
        game.prizeCardsRemaining().put(p1, 0);
        assertEquals(p1, service.checkWinner(game));
    }

    @Test
    void checkWinnerDetectsDeckOut() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = gameWithActive(p1, p2);
        game.prizeCardsRemaining().put(p1, 6);
        game.prizeCardsRemaining().put(p2, 6);
        game.deck().get(p2).clear();
        game.deckCardsRemaining().put(p2, 0);
        assertEquals(p1, service.checkWinner(game));
    }

    @Test
    void checkWinnerDetectsNoPokemonInPlay() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = gameWithActive(p1, p2);
        game.removeCardAtSlot(p2, "ACTIVE");
        assertEquals(p1, service.checkWinner(game));
    }

    @Test
    void applyKnockoutReturnsNoneWhenDefenderHpIsPositive() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = gameWithActive(p1, p2);
        game.activeHp().put(p2, 10);
        var result = service.applyKnockoutAndPrizes(game, p1, p2);
        assertFalse(result.knockedOut());
    }

    @Test
    void applyKnockoutProcessesFullKnockout() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = gameWithActive(p1, p2);
        game.prizeCards().put(p1, new ArrayList<>(List.of("prize1", "prize2", "prize3")));
        game.prizeCardsRemaining().put(p1, 3);
        game.activeHp().put(p2, 0);
        game.activeDamageCounters().put(p2, 60);
        var result = service.applyKnockoutAndPrizes(game, p1, p2);
        assertTrue(result.knockedOut());
        assertEquals(1, result.prizesTaken());
    }

    @Test
    void applyBenchKnockoutsReturnsEmptyForNullPlayers() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        assertTrue(service.applyBenchKnockoutsAndPrizes(game, null, null).isEmpty());
    }

    @Test
    void closeGameSetsFinishedState() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = gameWithActive(p1, p2);
        game.prizeCardsRemaining().put(p1, 0);
        service.closeGameIfNeeded(game);
        assertEquals(GameState.FINISHED, game.gameState());
        assertEquals(p1, game.winner());
    }

    @Test
    void checkWinnerTriggersSuddenDeathOnSimultaneousPrizeWin() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = gameWithActive(p1, p2);
        game.prizeCardsRemaining().put(p1, 0);
        game.prizeCardsRemaining().put(p2, 0);
        assertNull(service.checkWinner(game));
        assertTrue(game.suddenDeath());
    }
}
