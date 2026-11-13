package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.repository.ActionLogEntry;
import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.domain.attack.AttackPatternAnalyzer;
import com.utn.pokemontcg.game.domain.model.*;
import com.utn.pokemontcg.game.model.User;
import com.utn.pokemontcg.game.presentation.controller.GameController;
import com.utn.pokemontcg.game.presentation.dto.*;
import com.utn.pokemontcg.game.repository.UserRepository;
import com.utn.pokemontcg.game.security.CurrentUserService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameControllerDirectInvocationTest {

    private GameApplicationService svc = mock(GameApplicationService.class);
    private CurrentUserService cus = mock(CurrentUserService.class);
    private UserRepository userRepo = mock(UserRepository.class);

    private GameController ctrl() {
        return new GameController(svc, cus, userRepo, new AttackPatternAnalyzer());
    }

    private User mockUser(UUID pid) {
        User u = new User();
        u.setPlayerId(pid);
        return u;
    }

    @Test
    void state_WaitingGame() {
        UUID gid = UUID.randomUUID();
        GameAggregate g = new GameAggregate(UUID.randomUUID());
        g.setPlayerTwo(UUID.randomUUID());
        when(svc.get(gid)).thenReturn(g);
        when(cus.requireUser(null)).thenReturn(mockUser(UUID.randomUUID()));
        assertNotNull(ctrl().state(gid, null).getBody());
    }

    @Test
    void state_SetupWithCountdown() {
        UUID pid = UUID.randomUUID();
        UUID gid = UUID.randomUUID();
        GameAggregate g = new GameAggregate(pid);
        g.setPlayerTwo(UUID.randomUUID());
        g.setGameState(GameState.SETUP);
        g.setSetupPhase(SetupPhase.SETUP_WAITING_ACTIVE);
        g.setSetupCountdownEndsAt(Instant.now());
        g.initializeZonesFor(pid, List.of());
        g.initializeZonesFor(g.playerTwo(), List.of());
        g.activePokemon().put(pid, "a1");
        g.registerCard(new GameCard("a1", "Attacker", "Pokemon", Set.of("Basic"), 60, 10, 1));
        when(svc.get(gid)).thenReturn(g);
        when(cus.requireUser(null)).thenReturn(mockUser(pid));
        assertNotNull(ctrl().state(gid, null).getBody());
    }

    @Test
    void state_ActiveGameWithFullDataAndStadium() {
        UUID pid = UUID.randomUUID(), op = UUID.randomUUID(), gid = UUID.randomUUID();
        GameAggregate g = new GameAggregate(pid);
        g.setPlayerTwo(op);
        g.setGameState(GameState.ACTIVE);
        g.setTurnPhase(TurnPhase.MAIN);
        g.setCurrentTurnPlayer(pid);
        g.setFirstPlayer(pid);
        g.setTurnNumber(5);
        g.setFirstTurn(false);
        g.setSuddenDeath(true);
        g.setWinner(pid);
        g.setReplacementPlayerId(UUID.randomUUID());
        g.setPendingForcedSwitchPlayerId(op);
        g.setCoinFlipWinner(pid);
        g.setCoinResult("heads");
        g.setCoinFlipChoice("heads");
        g.initializeZonesFor(pid, new ArrayList<>());
        g.initializeZonesFor(op, new ArrayList<>());
        g.activePokemon().put(pid, "a1");
        g.activePokemon().put(op, "a2");
        g.bench().get(pid).add("b1");
        g.hand().get(pid).add("h1");
        g.prizeCards().get(pid).add("p1");
        g.discardPile().get(pid).add("x1");
        g.registerCard(new GameCard("a1", "Attacker", "Pokemon", Set.of("Basic"), 100, 10, 1));
        g.registerCard(new GameCard("a2", "Defender", "Pokemon", Set.of("Basic"), 100, 10, 1));
        g.attachEnergy(pid, "ACTIVE", new AttachedEnergy("e1", "Fire"));
        g.equipTool(pid, "ACTIVE", "rocky-helmet");
        g.addActiveEffect(pid, "DAMAGE_REDUCTION", 20);
        g.statusByPlayer().put(pid, EnumSet.of(StatusCondition.POISONED));
        g.activeDamageCounters().put(pid, 3);
        g.activeAttachedEnergy().put(pid, 1);
        g.deckCardsRemaining().put(pid, 15);
        g.prizeCardsRemaining().put(pid, 4);
        g.activePokemonEx().put(pid, true);
        g.activeHp().put(pid, 70);
        g.turnsStartedByPlayer().put(pid, 3);
        g.turnsStartedByPlayer().put(op, 2);
        g.putStadium(pid, "stadium-card");
        g.registerCard(new GameCard("stadium-card", "Shadow Circle", "Trainer", Set.of("Stadium"), 0, 0, 0));
        when(svc.get(gid)).thenReturn(g);
        when(cus.requireUser(null)).thenReturn(mockUser(pid));
        when(userRepo.findByPlayerId(pid)).thenReturn(Optional.of(mockUser(pid)));
        GameStateResponse resp = ctrl().state(gid, null).getBody();
        assertNotNull(resp);
    }

    @Test
    void state_CoinFlipWinner() {
        UUID pid = UUID.randomUUID(), gid = UUID.randomUUID();
        GameAggregate g = new GameAggregate(pid);
        g.setCoinFlipWinner(pid);
        when(svc.get(gid)).thenReturn(g);
        when(cus.requireUser(null)).thenReturn(mockUser(pid));
        assertNotNull(ctrl().state(gid, null).getBody());
    }

    @Test
    void actionLogReturnsEntries() {
        UUID pid = UUID.randomUUID(), gid = UUID.randomUUID();
        ActionLogEntry e = new ActionLogEntry(1L, gid, GameActionType.DRAW, pid, "OK", Map.of("card", "c1"), Instant.now());
        when(svc.actionLog(gid)).thenReturn(List.of(e));
        when(cus.requireUser(null)).thenReturn(mockUser(pid));
        var resp = ctrl().actionLog(gid, null).getBody();
        assertNotNull(resp);
        assertEquals(1, resp.get("entries").size());
    }

    @Test
    void actionsEndpoint() {
        UUID pid = UUID.randomUUID(), gid = UUID.randomUUID();
        ActionLogEntry e = new ActionLogEntry(1L, gid, GameActionType.DRAW, pid, "OK", Map.of(), Instant.now());
        when(svc.actionLog(gid)).thenReturn(List.of(e));
        when(cus.requireUser(null)).thenReturn(mockUser(pid));
        assertNotNull(ctrl().actions(gid, null).getBody());
    }

    @Test
    void syncEndpoint() {
        UUID pid = UUID.randomUUID(), gid = UUID.randomUUID();
        GameAggregate g = new GameAggregate(pid);
        g.setPlayerTwo(UUID.randomUUID());
        g.setGameState(GameState.ACTIVE);
        g.initializeZonesFor(pid, List.of());
        g.initializeZonesFor(g.playerTwo(), List.of());
        g.activePokemon().put(pid, "a1");
        g.registerCard(new GameCard("a1", "Attacker", "Pokemon", Set.of("Basic"), 60, 10, 1));
        ActionLogEntry e = new ActionLogEntry(1L, gid, GameActionType.DRAW, pid, "OK", Map.of(), Instant.now());
        when(svc.get(gid)).thenReturn(g);
        when(svc.actionLog(gid)).thenReturn(List.of(e));
        when(cus.requireUser(null)).thenReturn(mockUser(pid));
        assertNotNull(ctrl().sync(gid, null).getBody());
    }

    @Test
    void deckEndpoint() {
        UUID pid = UUID.randomUUID(), gid = UUID.randomUUID();
        GameAggregate g = new GameAggregate(pid);
        g.setPlayerTwo(UUID.randomUUID());
        g.initializeZonesFor(pid, List.of("c1", "c2"));
        g.initializeZonesFor(g.playerTwo(), List.of());
        g.registerCard(new GameCard("c1", "Card1", "Pokemon", Set.of("Basic"), 60, 10, 1));
        g.activePokemon().put(pid, "a1");
        g.registerCard(new GameCard("a1", "Attacker", "Pokemon", Set.of("Basic"), 60, 10, 1));
        when(svc.getDeckCardIds(gid, pid)).thenReturn(List.of("c1", "c2"));
        when(svc.get(gid)).thenReturn(g);
        when(cus.requireUser(null)).thenReturn(mockUser(pid));
        assertNotNull(ctrl().deck(gid, pid, null).getBody());
    }

    @Test
    void state_withRevealedHandCards_coversRevealedHandLambda() {
        UUID pid = UUID.randomUUID(), op = UUID.randomUUID(), gid = UUID.randomUUID();
        GameAggregate g = new GameAggregate(pid);
        g.setPlayerTwo(op);
        g.setGameState(GameState.SETUP);
        g.setSetupPhase(SetupPhase.SETUP_REVEAL);
        g.initializeZonesFor(pid, new ArrayList<>());
        g.initializeZonesFor(op, new ArrayList<>());
        g.activePokemon().put(pid, "a1");
        g.activePokemon().put(op, "a2");
        g.registerCard(new GameCard("a1", "Card1", "Pokemon", Set.of("Basic"), 60, 10, 1));
        g.registerCard(new GameCard("a2", "Card2", "Pokemon", Set.of("Basic"), 60, 10, 1));

        PlayerSetupState setup = new PlayerSetupState();
        setup.setMulligans(0);
        setup.setHandSize(5);
        setup.setBenchCount(0);
        setup.setPrizeCount(6);
        setup.setHasActive(true);
        setup.setRevealedHandCardIds(List.of("a1"));
        g.setupByPlayer().put(pid, setup);

        when(svc.get(gid)).thenReturn(g);
        when(cus.requireUser(null)).thenReturn(mockUser(pid));
        assertNotNull(ctrl().state(gid, null).getBody());
    }
}
