package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.*;
import com.utn.pokemontcg.game.domain.engine.DamageCalculator;
import com.utn.pokemontcg.game.domain.engine.RuleValidator;
import com.utn.pokemontcg.game.domain.engine.StatusEffectManager;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.domain.model.*;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameApplicationServiceLastMileTest {

    private GameApplicationService service() {
        SetupEngineService setup = new SetupEngineService();
        return new GameApplicationService(
            new InMemoryGameStateRepository(),
            new GameEngineFacade(new GameEventPublisher()),
            setup,
            new TurnActionValidator(),
            new VictoryService(setup),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager()
        );
    }

    private void addCards(GameAggregate g, UUID player, int basics, int energies) {
        g.initializeZonesFor(player, new ArrayList<>());
        for (int i = 0; i < basics; i++) {
            String id = "bsc-" + player + "-" + i;
            g.deck().get(player).add(id);
            g.registerCard(new GameCard(id, "Pokemon" + i, "Pokemon", Set.of("Basic"), 60, 10, 1));
        }
        for (int i = 0; i < energies; i++) {
            String id = "enr-" + player + "-" + i;
            g.deck().get(player).add(id);
            g.registerCard(new GameCard(id, "Energy", "Energy", Set.of("Basic"), 0, 0, 0));
        }
        g.deckCardsRemaining().put(player, g.deck().get(player).size());
    }

    private GameAggregate createBasicGame(GameApplicationService svc) {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        GameAggregate g = svc.create(p1, null);
        svc.join(g.id(), p2, null);
        addCards(g, p1, 3, 9);
        addCards(g, p2, 3, 9);
        return g;
    }

    @Test
    void executeDrawAction() {
        GameApplicationService svc = service();
        GameAggregate g = createBasicGame(svc);
        g.setGameState(GameState.ACTIVE);
        g.setTurnPhase(TurnPhase.DRAW);
        g.setCurrentTurnPlayer(g.playerOne());
        g.setFirstPlayer(g.playerOne());
        g.setFirstTurn(false);
        g.activePokemon().put(g.playerOne(), "bsc-" + g.playerOne() + "-0");
        g.activePokemon().put(g.playerTwo(), "bsc-" + g.playerTwo() + "-0");
        g.deckCardsRemaining().put(g.playerOne(), 12);
        g.prizeCardsRemaining().put(g.playerOne(), 6);
        g.prizeCardsRemaining().put(g.playerTwo(), 6);
        GameAggregate result = svc.executeAction(g.id(), GameActionType.DRAW, null, null, null,
            List.of(), null, null, null, List.of(), List.of(), null, null, null);
        assertNotNull(result);
    }

    @Test
    void betweenTurnsResolveDraw() {
        GameApplicationService svc = service();
        GameAggregate g = createBasicGame(svc);
        g.setGameState(GameState.ACTIVE);
        g.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        g.setCurrentTurnPlayer(g.playerOne());
        g.setFirstPlayer(g.playerOne());
        g.setFirstTurn(false);
        g.activePokemon().put(g.playerOne(), "bsc-" + g.playerOne() + "-0");
        g.activePokemon().put(g.playerTwo(), "bsc-" + g.playerTwo() + "-0");
        g.activeHp().put(g.playerOne(), 60);
        g.activeHp().put(g.playerTwo(), 60);
        g.deckCardsRemaining().put(g.playerOne(), 10);
        g.prizeCardsRemaining().put(g.playerOne(), 6);
        g.prizeCardsRemaining().put(g.playerTwo(), 6);
        GameAggregate result = svc.executeAction(g.id(), GameActionType.RESOLVE_BETWEEN_TURNS, null, null, null,
            List.of(), null, null, null, List.of(), List.of(), null, null, null);
        assertNotNull(result);
    }

    @Test
    void executeMainPhaseActionWithEnergy() {
        GameApplicationService svc = service();
        GameAggregate g = createBasicGame(svc);
        g.setGameState(GameState.ACTIVE);
        g.setTurnPhase(TurnPhase.MAIN);
        g.setCurrentTurnPlayer(g.playerOne());
        g.setFirstPlayer(g.playerOne());
        g.setFirstTurn(false);
        g.setTurnNumber(3);
        g.activePokemon().put(g.playerOne(), "bsc-" + g.playerOne() + "-0");
        g.activePokemon().put(g.playerTwo(), "bsc-" + g.playerTwo() + "-0");
        g.activeHp().put(g.playerOne(), 60);
        g.activeHp().put(g.playerTwo(), 60);
        g.deckCardsRemaining().put(g.playerOne(), 10);
        g.prizeCardsRemaining().put(g.playerOne(), 6);
        g.prizeCardsRemaining().put(g.playerTwo(), 6);
        g.hand().get(g.playerOne()).add("enr-" + g.playerOne() + "-0");
        g.registerCard(new GameCard("enr-" + g.playerOne() + "-0", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        GameAggregate result = svc.executeAction(g.id(), GameActionType.ATTACH_ENERGY, null, "enr-" + g.playerOne() + "-0",
            "ACTIVE", List.of(), null, null, null, List.of(), List.of(), null, null, null);
        assertNotNull(result);
    }

    @Test
    void activePlayerDiscoversCards() {
        GameApplicationService svc = service();
        GameAggregate g = createBasicGame(svc);
        assertNotNull(svc.getHandCardIds(g.id(), g.playerOne()));
        assertNotNull(svc.getDeckCardIds(g.id(), g.playerOne()));
        assertNotNull(svc.deckPreview(g.id(), g.playerOne(), 5));
    }

    @Test
    void flipCoinFullFlow() {
        GameApplicationService svc = service();
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        GameAggregate g = svc.create(p1, null);
        svc.join(g.id(), p2, null);
        g.setGameState(GameState.COIN_FLIP);
        GameAggregate result = svc.flipCoin(g.id(), "heads");
        assertNotNull(result);
    }

    @Test
    void retreatAction_coversRetreatActivePokemon() {
        GameApplicationService svc = service();
        GameAggregate g = createBasicGame(svc);
        g.setGameState(GameState.ACTIVE);
        g.setTurnPhase(TurnPhase.MAIN);
        g.setCurrentTurnPlayer(g.playerOne());
        g.setFirstPlayer(g.playerOne());
        g.setFirstTurn(false);
        g.setTurnNumber(3);

        String activeId = "act-ret-" + g.playerOne();
        String benchId = "bnch-ret-" + g.playerOne();
        GameCard active = new GameCard(activeId, "ActiveMon", "Pokemon", Set.of("Basic"), 100, 10, 1, "Fire", List.of(), 0);
        GameCard bench = new GameCard(benchId, "BenchMon", "Pokemon", Set.of("Basic"), 80, 10, 1);
        g.registerCard(active);
        g.registerCard(bench);
        g.activePokemon().put(g.playerOne(), activeId);
        g.activePokemon().put(g.playerTwo(), "bsc-" + g.playerTwo() + "-0");
        g.bench().get(g.playerOne()).add(benchId);
        g.deck().get(g.playerOne()).add(activeId);
        g.deck().get(g.playerOne()).add(benchId);

        GameAggregate result = svc.executeAction(g.id(), GameActionType.RETREAT, null, null,
            null, List.of(), null, null, null, List.of(), List.of(), null, null, null);
        assertNotNull(result);
    }

    @Test
    void startInteractiveSetup_withNullDeckSize_throws() {
        GameApplicationService svc = service();
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        GameAggregate g = svc.create(p1, null);
        svc.join(g.id(), p2, null);
        assertThrows(IllegalArgumentException.class, () ->
            svc.startInteractiveSetup(g.id(), null, 10, 60, 12, null, null)
        );
    }
}
