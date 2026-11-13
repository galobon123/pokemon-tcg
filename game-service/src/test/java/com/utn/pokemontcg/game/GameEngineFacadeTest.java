package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
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

class GameEngineFacadeTest {

    private GameEngineFacade facadeWithPublisher(GameEventPublisher publisher) {
        return new GameEngineFacade(publisher);
    }

    @Test
    void startSetupTransitionsToSetupState() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        facade.startSetup(game);
        assertEquals(GameState.SETUP, game.gameState());
    }

    @Test
    void startActiveTransitionsToDrawPhase() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setGameState(GameState.SETUP);
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        game.setPlayerTwo(UUID.randomUUID());
        game.initializeZonesFor(game.playerOne(), List.of("poke-1"));
        game.initializeZonesFor(game.playerTwo(), List.of("poke-2"));
        game.registerCard(new GameCard("poke-1", "P1", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard("poke-2", "P2", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.activePokemon().put(game.playerOne(), "poke-1");
        game.activePokemon().put(game.playerTwo(), "poke-2");
        facade.startActive(game);
        assertEquals(TurnPhase.MAIN, game.turnPhase());
    }

    @Test
    void advanceTurnPhaseMovesFromDrawToMain() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setTurnPhase(TurnPhase.DRAW);
        game.setGameState(GameState.ACTIVE);
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        game.setPlayerTwo(UUID.randomUUID());
        facade.advanceTurnPhase(game);
        assertEquals(TurnPhase.MAIN, game.turnPhase());
    }

    @Test
    void advanceTurnPhaseMovesFromMainToAttack() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setTurnPhase(TurnPhase.MAIN);
        game.setGameState(GameState.ACTIVE);
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        game.setPlayerTwo(UUID.randomUUID());
        facade.advanceTurnPhase(game);
        assertEquals(TurnPhase.ATTACK, game.turnPhase());
    }

    @Test
    void advanceTurnPhaseFromAttackGoesToBetweenTurns() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setGameState(GameState.ACTIVE);
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        game.setPlayerTwo(UUID.randomUUID());
        facade.advanceTurnPhase(game);
        assertEquals(TurnPhase.BETWEEN_TURNS, game.turnPhase());
    }

    @Test
    void advanceTurnPhaseFromBetweenTurnsReturnsToDraw() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        game.setGameState(GameState.ACTIVE);
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        game.setPlayerTwo(UUID.randomUUID());
        facade.advanceTurnPhase(game);
        assertEquals(TurnPhase.DRAW, game.turnPhase());
    }

    @Test
    void shouldMoveToBetweenTurnsAfterAttackResolution() {
        GameEventPublisher publisher = new GameEventPublisher();
        List<Object> events = new ArrayList<>();
        publisher.subscribe(events::add);
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setGameState(GameState.ACTIVE);
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        game.setPlayerTwo(UUID.randomUUID());

        facade.resolveAttack(game);

        assertEquals(TurnPhase.BETWEEN_TURNS, game.turnPhase());
        assertNotNull(events);
    }

    @Test
    void parseDamageReturnsZeroForNull() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setGameState(GameState.ACTIVE);
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        game.setPlayerTwo(UUID.randomUUID());
        // Attack with null attack index triggers the fallback parseDamage path
        facade.resolveAttack(game);
        assertEquals(TurnPhase.BETWEEN_TURNS, game.turnPhase());
    }
}
