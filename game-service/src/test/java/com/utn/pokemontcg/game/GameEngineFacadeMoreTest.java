package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.event.GameEventType;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameEngineFacadeMoreTest {

    private GameEngineFacade facadeWithPublisher(GameEventPublisher publisher) {
        return new GameEngineFacade(publisher);
    }

    private GameAggregate createActiveGame() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        game.initializeZonesFor(game.playerOne(), List.of("c1"));
        game.initializeZonesFor(game.playerTwo(), List.of("c2"));
        GameCard attacker = new GameCard("c1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1,
            "Thunder Shock", "Flip a coin. If heads, the Defending Pokemon is now Paralyzed.",
            "Lightning", List.of("Lightning"), 1, null, List.of());
        GameCard defender = new GameCard("c2", "Charmander", "Pokemon", Set.of("Basic"), 50, 10, 1);
        game.registerCard(attacker);
        game.registerCard(defender);
        game.activePokemon().put(game.playerOne(), "c1");
        game.activePokemon().put(game.playerTwo(), "c2");
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Lightning"));
        return game;
    }

    @Test
    void resolveAttackWithAttackIndexAndCoinFlips() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = createActiveGame();
        facade.resolveAttack(game, Integer.valueOf(0), List.of(true, true));
        assertEquals(TurnPhase.BETWEEN_TURNS, game.turnPhase());
    }

    @Test
    void resolveAttackWithTargetSlot() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = createActiveGame();
        facade.resolveAttack(game, Integer.valueOf(0), List.of(), "ACTIVE");
        assertEquals(TurnPhase.BETWEEN_TURNS, game.turnPhase());
    }

    @Test
    void resolveAttackWithAllParameters() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = createActiveGame();
        facade.resolveAttack(game, Integer.valueOf(0), List.of(true), "ACTIVE", game.playerTwo(),
            List.of(), List.of(), null, null, null);
        assertEquals(TurnPhase.BETWEEN_TURNS, game.turnPhase());
    }

    @Test
    void resolveAttackWithSelectedCardIds() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = createActiveGame();
        facade.resolveAttack(game, Integer.valueOf(0), List.of(), null, null,
            List.of("c2"), List.of(), null, null, null);
        assertEquals(TurnPhase.BETWEEN_TURNS, game.turnPhase());
    }

    @Test
    void resolveAttackWithSelectedEnergyCardIds() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = createActiveGame();
        facade.resolveAttack(game, Integer.valueOf(0), List.of(), null, null,
            List.of(), List.of("e1"), null, null, null);
        assertEquals(TurnPhase.BETWEEN_TURNS, game.turnPhase());
    }

    @Test
    void resolveAttackWithSelectedCondition() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = createActiveGame();
        facade.resolveAttack(game, Integer.valueOf(0), List.of(), null, null,
            List.of(), List.of(), "POISONED", null, null);
        assertEquals(TurnPhase.BETWEEN_TURNS, game.turnPhase());
    }

    @Test
    void resolveAttackWithAcceptedOptionalEffect() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = createActiveGame();
        facade.resolveAttack(game, Integer.valueOf(0), List.of(), null, null,
            List.of(), List.of(), null, true, null);
        assertEquals(TurnPhase.BETWEEN_TURNS, game.turnPhase());
    }

    @Test
    void resolveAttackWithSelectedOpponentAttackIndex() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = createActiveGame();
        facade.resolveAttack(game, Integer.valueOf(0), List.of(), null, null,
            List.of(), List.of(), null, null, Integer.valueOf(0));
        assertEquals(TurnPhase.BETWEEN_TURNS, game.turnPhase());
    }

    @Test
    void publishSendsEvent() {
        GameEventPublisher publisher = new GameEventPublisher();
        List<Object> events = new ArrayList<>();
        publisher.subscribe(events::add);
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = createActiveGame();
        facade.publish(game, GameEventType.TURN_STARTED, Map.of("phase", "DRAW"));
        assertEquals(1, events.size());
    }

    @Test
    void resolveAttackWithAttackIndexAndCoinFlipsOnly() {
        GameEventPublisher publisher = new GameEventPublisher();
        GameEngineFacade facade = facadeWithPublisher(publisher);
        GameAggregate game = createActiveGame();
        facade.resolveAttack(game, Integer.valueOf(0), List.of(true, false, true));
        assertEquals(TurnPhase.BETWEEN_TURNS, game.turnPhase());
    }

    @Test
    void eventPublisherPublishesEvents() {
        GameEventPublisher publisher = new GameEventPublisher();
        List<Object> events = new ArrayList<>();
        publisher.subscribe(events::add);
        publisher.publish(com.utn.pokemontcg.game.domain.event.GameEvent.of(
            UUID.randomUUID(), GameEventType.GAME_CREATED, Map.of()));
        assertEquals(1, events.size());
    }
}
