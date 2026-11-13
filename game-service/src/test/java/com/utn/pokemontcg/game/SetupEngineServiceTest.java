package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.SetupEngineService;
import com.utn.pokemontcg.game.domain.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SetupEngineServiceTest {

    @Test
    void shouldPrepareValidInitialSetupState() {
        SetupEngineService service = new SetupEngineService();

        PlayerSetupState setup = service.preparePlayer(60, 10, 2);

        assertTrue(setup.hasActive());
        assertEquals(6, setup.prizeCount());
        assertTrue(setup.benchCount() >= 0 && setup.benchCount() <= 5);
        assertEquals(9, setup.handSize());
        assertTrue(setup.mulligans() >= 0);
    }

    @Test
    void shouldRejectOpeningHandWithTooFewCards() {
        SetupEngineService service = new SetupEngineService();
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.initializeZonesFor(game.playerOne(), new ArrayList<>());

        assertThrows(IllegalArgumentException.class,
            () -> service.preparePlayerOpeningHand(game.playerOne(), List.of(), 0, game)
        );
    }

    @Test
    void shouldRejectOpeningHandWithNoBasics() {
        SetupEngineService service = new SetupEngineService();
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.initializeZonesFor(game.playerOne(), new ArrayList<>());
        List<GameCard> energyOnly = List.of(
            new GameCard("e1", "Energy", "Energy", Set.of("Basic"), 0, 0, 0)
        );
        assertThrows(IllegalArgumentException.class,
            () -> service.preparePlayerOpeningHand(game.playerOne(), energyOnly, 0, game)
        );
    }
}
