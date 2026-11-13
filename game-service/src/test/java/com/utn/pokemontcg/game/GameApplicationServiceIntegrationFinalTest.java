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

class GameApplicationServiceIntegrationFinalTest {

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
            String id = "basic-" + player + "-" + i;
            g.deck().get(player).add(id);
            g.registerCard(new GameCard(id, "Pokemon" + i, "Pokemon", Set.of("Basic"), 60, 10, 1));
        }
        for (int i = 0; i < energies; i++) {
            String id = "energy-" + player + "-" + i;
            g.deck().get(player).add(id);
            g.registerCard(new GameCard(id, "Energy", "Energy", Set.of("Basic"), 0, 0, 0));
        }
        g.deckCardsRemaining().put(player, g.deck().get(player).size());
    }

    @Test
    void concedeByPlayerOne() {
        GameApplicationService svc = service();
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        GameAggregate g = svc.create(p1, null);
        svc.join(g.id(), p2, null);
        g.setGameState(GameState.ACTIVE);
        GameAggregate result = svc.concede(g.id(), p1);
        assertEquals(p2, result.winner());
    }

    @Test
    void concedeByPlayerTwo() {
        GameApplicationService svc = service();
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        GameAggregate g = svc.create(p1, null);
        svc.join(g.id(), p2, null);
        g.setGameState(GameState.ACTIVE);
        GameAggregate result = svc.concede(g.id(), p2);
        assertEquals(p1, result.winner());
    }

    @Test
    void getHandAndDeck() {
        GameApplicationService svc = service();
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        GameAggregate g = svc.create(p1, null);
        svc.join(g.id(), p2, null);
        assertEquals(0, svc.getHandCardIds(g.id(), p1).size());
        assertNotNull(svc.getDeckCardIds(g.id(), p1));
    }

    @Test
    void actionLog() {
        GameApplicationService svc = service();
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        GameAggregate g = svc.create(p1, null);
        svc.join(g.id(), p2, null);
        assertTrue(svc.actionLog(g.id()).isEmpty());
    }

    @Test
    void deckPreview() {
        GameApplicationService svc = service();
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        GameAggregate g = svc.create(p1, null);
        svc.join(g.id(), p2, null);
        addCards(g, p1, 3, 9);
        assertNotNull(svc.deckPreview(g.id(), p1, 3));
    }

    @Test
    void flipCoinRequiresTwoPlayers() {
        GameApplicationService svc = service();
        GameAggregate g = svc.create(UUID.randomUUID(), null);
        assertThrows(IllegalStateException.class, () -> svc.flipCoin(g.id(), "heads"));
    }

    @Test
    void getGame() {
        GameApplicationService svc = service();
        UUID p1 = UUID.randomUUID();
        GameAggregate g = svc.create(p1, null);
        GameAggregate found = svc.get(g.id());
        assertEquals(g.id(), found.id());
    }
}
