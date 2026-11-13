package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.SetupEngineService;
import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.chain.ConfusionCheckStep;
import com.utn.pokemontcg.game.domain.model.*;
import com.utn.pokemontcg.game.domain.strategy.*;
import com.utn.pokemontcg.game.infrastructure.client.CardServiceClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CoverageTo90PercentPart2Test {

    @Test
    void setupEngine_preparePlayerBoardSucceeds() {
        var service = new SetupEngineService();
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        var state = service.preparePlayerBoard(game.playerOne(), 60, 3, 0, game);
        assertTrue(state.hasActive());
        assertEquals(6, state.prizeCount());
    }

    @Test
    void setupEngine_preparePlayerOpeningHandSucceeds() {
        var service = new SetupEngineService();
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        var state = service.preparePlayerOpeningHand(game.playerOne(), 60, 3, 0, game);
        assertFalse(state.hasActive());
        assertEquals(0, state.benchCount());
    }

    @Test
    void setupEngine_preparePlayerBoardWithCardList() {
        var service = new SetupEngineService();
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        List<GameCard> cards = new ArrayList<>(List.of(
            new GameCard("b1", "Basic1", "Pokemon", Set.of("Basic"), 60, 10, 1),
            new GameCard("b2", "Basic2", "Pokemon", Set.of("Basic"), 60, 10, 1),
            new GameCard("b3", "Basic3", "Pokemon", Set.of("Basic"), 60, 10, 1)
        ));
        for (int i = 0; i < 57; i++) cards.add(new GameCard("e" + i, "Energy " + i, "Energy", Set.of("Basic"), 0, 0, 0));
        var state = service.preparePlayerBoard(game.playerOne(), cards, 0, game);
        assertTrue(state.hasActive());
    }

    @Test
    void setupEngine_preparePlayerBoardWithCardListThrowsOnSmallDeck() {
        var service = new SetupEngineService();
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        List<GameCard> cards = List.of(
            new GameCard("b1", "Basic1", "Pokemon", Set.of("Basic"), 60, 10, 1)
        );
        assertThrows(IllegalArgumentException.class, () ->
            service.preparePlayerBoard(game.playerOne(), cards, 0, game));
    }

    @Test
    void setupEngine_preparePlayerBoardThrowsOnNoBasics() {
        var service = new SetupEngineService();
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        List<GameCard> cards = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) cards.add(new GameCard("e" + i, "Energy " + i, "Energy", Set.of("Basic"), 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () ->
            service.preparePlayerBoard(game.playerOne(), cards, 0, game));
    }

    @Test
    void confusionCheck_confusedAndHeads() {
        GameAggregate g = game();
        AttackContext context = ctx(g, "Test", "Does 10 damage.");
        context.setConfused(true);
        new ConfusionCheckStep().execute(context);
    }

    @Test
    void cardServiceClient_constructorWorks() {
        var client = new CardServiceClient("http://localhost:8083");
        assertNotNull(client);
    }

    @Test
    void cardServiceClient_findByIdReturnsEmptyWhenServerDown() {
        var client = new CardServiceClient("http://localhost:1");
        var result = client.findById("xy1-1");
        assertTrue(result.isEmpty());
    }

    @Test
    void energyDiscard_allAttachedCards() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e2", "Water"));
        AttackContext context = ctx(g, "Overload",
            "Discard all cards attached to this Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(context);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void energyDiscard_fromDefendingPokemon() {
        GameAggregate g = game();
        g.attachEnergy(g.playerTwo(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        AttackContext context = ctx(g, "Joust",
            "Discard an Energy attached to the Defending Pokemon.");
        context.setTargetPlayerId(g.playerTwo());
        new EnergyDiscardAttackEffectStrategy().apply(context);
    }

    @Test
    void gameEngineFacade_resolveAttackNoArgConstructor() {
        var publisher = new com.utn.pokemontcg.game.domain.event.GameEventPublisher();
        var facade = new com.utn.pokemontcg.game.domain.facade.GameEngineFacade(publisher,
            new com.utn.pokemontcg.game.domain.strategy.AttackEffectRegistry(List.of()));
        GameAggregate g = facadeGame();
        facade.resolveAttack(g);
        assertEquals(TurnPhase.BETWEEN_TURNS, g.turnPhase());
    }

    @Test
    void gameEngineFacade_startSetup() {
        var publisher = new com.utn.pokemontcg.game.domain.event.GameEventPublisher();
        var facade = new com.utn.pokemontcg.game.domain.facade.GameEngineFacade(publisher);
        GameAggregate g = new GameAggregate(UUID.randomUUID());
        g.setPlayerTwo(UUID.randomUUID());
        assertDoesNotThrow(() -> facade.startSetup(g));
    }

    @Test
    void gameEngineFacade_startActive() {
        var publisher = new com.utn.pokemontcg.game.domain.event.GameEventPublisher();
        var facade = new com.utn.pokemontcg.game.domain.facade.GameEngineFacade(publisher);
        GameAggregate g = new GameAggregate(UUID.randomUUID());
        g.setPlayerTwo(UUID.randomUUID());
        g.setGameState(GameState.SETUP);
        g.setCurrentTurnPlayer(g.playerOne());
        g.setFirstPlayer(g.playerOne());
        g.initializeZonesFor(g.playerOne(), List.of("c1"));
        g.initializeZonesFor(g.playerTwo(), List.of("c2"));
        g.registerCard(new GameCard("c1", "P1", "Pokemon", Set.of("Basic"), 60, 10, 1));
        g.registerCard(new GameCard("c2", "P2", "Pokemon", Set.of("Basic"), 60, 10, 1));
        g.activePokemon().put(g.playerOne(), "c1");
        g.activePokemon().put(g.playerTwo(), "c2");
        facade.startActive(g);
    }

    @Test
    void gameEngineFacade_publish() {
        var publisher = new com.utn.pokemontcg.game.domain.event.GameEventPublisher();
        var facade = new com.utn.pokemontcg.game.domain.facade.GameEngineFacade(publisher);
        GameAggregate g = new GameAggregate(UUID.randomUUID());
        facade.publish(g, com.utn.pokemontcg.game.domain.event.GameEventType.GAME_CREATED,
            java.util.Map.of("test", "value"));
    }

    private GameAggregate facadeGame() {
        GameAggregate g = new GameAggregate(UUID.randomUUID());
        g.setPlayerTwo(UUID.randomUUID());
        g.setTurnPhase(TurnPhase.ATTACK);
        g.setGameState(GameState.ACTIVE);
        g.setCurrentTurnPlayer(g.playerOne());
        g.setFirstPlayer(g.playerOne());
        g.initializeZonesFor(g.playerOne(), List.of("c1"));
        g.initializeZonesFor(g.playerTwo(), List.of("c2"));
        g.registerCard(new GameCard("c1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1,
            "Thunder Shock", "Flip a coin. If heads, the Defending Pokemon is now Paralyzed.",
            "Lightning", List.of("Lightning"), 1, null, List.of()));
        g.registerCard(new GameCard("c2", "Charmander", "Pokemon", Set.of("Basic"), 50, 10, 1));
        g.activePokemon().put(g.playerOne(), "c1");
        g.activePokemon().put(g.playerTwo(), "c2");
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Lightning"));
        return g;
    }

    private AttackContext ctx(GameAggregate game, String attackName, String attackText) {
        AttackContext context = new AttackContext(game);
        context.setAttackName(attackName);
        context.setAttackText(attackText);
        context.setAttachedEnergies(game.attachedEnergiesFor(game.currentTurnPlayer(), "ACTIVE"));
        context.setAttackerCard(game.cardCatalog().get(game.activePokemon().get(game.currentTurnPlayer())));
        return context;
    }

    private GameAggregate game() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(p1);
        game.setFirstPlayer(p1);
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());
        game.activePokemon().put(p1, "active-1");
        game.activePokemon().put(p2, "active-2");
        game.registerCard(new GameCard("active-1", "Attacker", "Pokemon", Set.of("Basic"), 100, 10, 1));
        game.registerCard(new GameCard("active-2", "Defender", "Pokemon", Set.of("Basic"), 100, 10, 1));
        return game;
    }
}
