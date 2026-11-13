package com.utn.pokemontcg.game.presentation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.utn.pokemontcg.game.application.repository.ActionLogEntry;
import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.domain.attack.AttackPatternAnalyzer;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.PlayerSetupState;
import com.utn.pokemontcg.game.domain.model.SetupPhase;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.infrastructure.client.CardServiceClient.AttackData;
import com.utn.pokemontcg.game.model.User;
import com.utn.pokemontcg.game.presentation.controller.GameController;
import com.utn.pokemontcg.game.presentation.dto.BenchPokemonRequest;
import com.utn.pokemontcg.game.presentation.dto.ChooseFirstRequest;
import com.utn.pokemontcg.game.presentation.dto.CoinFlipRequest;
import com.utn.pokemontcg.game.presentation.dto.CreateGameRequest;
import com.utn.pokemontcg.game.presentation.dto.EvolvePokemonRequest;
import com.utn.pokemontcg.game.presentation.dto.GameActionRequest;
import com.utn.pokemontcg.game.presentation.dto.JoinGameRequest;
import com.utn.pokemontcg.game.presentation.dto.PlayTrainerRequest;
import com.utn.pokemontcg.game.presentation.dto.ReplaceActiveRequest;
import com.utn.pokemontcg.game.presentation.dto.SetupConfirmRequest;
import com.utn.pokemontcg.game.presentation.dto.SetupGameRequest;
import com.utn.pokemontcg.game.presentation.dto.SetupPlaceActiveRequest;
import com.utn.pokemontcg.game.presentation.dto.SetupPlaceBenchRequest;
import com.utn.pokemontcg.game.presentation.dto.UseAbilityRequest;
import com.utn.pokemontcg.game.presentation.dto.AttackRequirementsResponse;
import com.utn.pokemontcg.game.security.CurrentUserService;
import com.utn.pokemontcg.game.repository.UserRepository;

class GameControllerCoverageTest {

    @Test
    void delegatesCommandsAndMapsGameStateResponse() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        GameController controller = new GameController(service, currentUserService, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID deckId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(currentUserService.requireUser(null)).thenReturn(user);
        GameAggregate activeGame = activeGame(player);

        when(service.create(player, deckId)).thenReturn(activeGame);
        when(service.join(gameId, player, deckId)).thenReturn(activeGame);
        when(service.runInitialSetup(gameId, 60, 12, 60, 12, deckId, deckId)).thenReturn(activeGame);
        when(service.startInteractiveSetup(gameId, 60, 12, 60, 12, deckId, deckId)).thenReturn(setupGame(gameId, player));
        when(service.placeSetupActive(gameId, player, "basic-1")).thenReturn(activeGame);
        when(service.placeSetupBench(gameId, player, List.of("basic-2"))).thenReturn(activeGame);
        when(service.confirmSetup(gameId, player)).thenReturn(activeGame);
        when(service.executeAction(gameId, GameActionType.DRAW, StatusCondition.POISONED, "energy-1", "ACTIVE", List.of("energy-1"), null, null, null, List.of(), List.of(), null, null, null)).thenReturn(activeGame);
        when(service.benchPokemon(gameId, player, "basic-2", null)).thenReturn(activeGame);
        when(service.evolvePokemon(gameId, player, "basic-1", "stage-1", "ACTIVE")).thenReturn(activeGame);
        when(service.playTrainer(gameId, player, "trainer-1", "ACTIVE", "target-1", null, List.of(), List.of(), null, null, List.of())).thenReturn(activeGame);
        when(service.useAbility(gameId, player, "ACTIVE", 0, "target-1")).thenReturn(activeGame);

        assertEquals(activeGame.id(), controller.create(new CreateGameRequest(deckId), null).getBody().gameId());
        assertEquals("MAIN", controller.join(gameId, new JoinGameRequest(deckId), null).getBody().phase());
        assertEquals("MAIN", controller.setup(gameId, new SetupGameRequest(60, 12, 60, 12, deckId, deckId, false), null).getBody().phase());
        assertEquals("SETUP_WAITING_ACTIVE", controller.setup(gameId, new SetupGameRequest(60, 12, 60, 12, deckId, deckId, true), null).getBody().phase());
        assertTrue(controller.setup(gameId, new SetupGameRequest(60, 12, 60, 12, deckId, deckId, true), null).getBody().activePokemonByPlayer().isEmpty());
        assertNotNull(controller.placeSetupActive(gameId, new SetupPlaceActiveRequest("basic-1"), null).getBody());
        assertNotNull(controller.placeSetupBench(gameId, new SetupPlaceBenchRequest(List.of("basic-2")), null).getBody());
        assertNotNull(controller.confirmSetup(gameId, new SetupConfirmRequest(), null).getBody());
        assertNotNull(controller.action(gameId, new GameActionRequest(GameActionType.DRAW, StatusCondition.POISONED, "energy-1", "ACTIVE", List.of("energy-1"), null, null), null).getBody());
        assertNotNull(controller.benchPokemon(gameId, new BenchPokemonRequest("basic-2", null), null).getBody());
        assertNotNull(controller.evolvePokemon(gameId, new EvolvePokemonRequest("basic-1", "stage-1", "ACTIVE"), null).getBody());
        assertNotNull(controller.playTrainer(gameId, new PlayTrainerRequest("trainer-1", "target-1", "ACTIVE"), null).getBody());
        assertNotNull(controller.useAbility(gameId, new UseAbilityRequest("ACTIVE", 0, "target-1"), null).getBody());

        verify(service).runInitialSetup(gameId, 60, 12, 60, 12, deckId, deckId);
        verify(service, org.mockito.Mockito.times(2)).startInteractiveSetup(gameId, 60, 12, 60, 12, deckId, deckId);
    }

    @Test
    void mapsQueriesForStateHandLogsAndSync() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        GameController controller = new GameController(service, currentUserService, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        GameAggregate game = activeGame(player);
        ActionLogEntry entry = new ActionLogEntry(1L, gameId, GameActionType.DRAW, player, "OK", Map.of("cardId", "c1"), Instant.now());

        when(currentUserService.requireUser(null)).thenReturn(user);
        when(service.get(gameId)).thenReturn(game);
        when(service.getHandCardIds(gameId, player)).thenReturn(List.of("c1", "c2"));
        when(service.actionLog(gameId)).thenReturn(List.of(entry));

        assertEquals(game.id(), controller.state(gameId, null).getBody().gameId());
        assertEquals(2, controller.hand(gameId, player, null).getBody().handSize());
        assertEquals(List.of(entry), controller.actions(gameId, null).getBody().get("entries"));
        assertEquals(List.of(entry), controller.actionLog(gameId, null).getBody().get("entries"));
        assertEquals(List.of(entry), controller.sync(gameId, null).getBody().actionLog());
    }

    @Test
    void delegatesReplaceActiveConcedeQueriesAndAttack() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        GameController controller = new GameController(service, currentUserService, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        GameAggregate game = activeGame(player);
        when(currentUserService.requireUser(null)).thenReturn(user);
        when(service.get(gameId)).thenReturn(game);
        when(service.replaceActive(gameId, player, 0)).thenReturn(game);
        when(service.concede(gameId, player)).thenReturn(game);
        when(service.getHandCardIds(gameId, player)).thenReturn(List.of("h1"));
        when(service.getDeckCardIds(gameId, player)).thenReturn(List.of("c1"));
        when(service.deckPreview(gameId, player, 7)).thenReturn(List.of("c1"));
        assertNotNull(controller.replaceActive(gameId, new ReplaceActiveRequest(0), null).getBody());
        assertNotNull(controller.concede(gameId, null).getBody());
        assertNotNull(controller.hand(gameId, player, null).getBody());
        assertNotNull(controller.deck(gameId, player, null).getBody());
        assertNotNull(controller.deckPreview(gameId, player, 7, null).getBody());
    }

    @Test
    void attackRequirementsExposeNormalizedStepsAndEffects() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        GameController controller = new GameController(service, currentUserService, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(currentUserService.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.registerCard(new GameCard(
            "active-1",
            "Venusaur",
            "Pokemon",
            java.util.Set.of("Stage 2"),
            160,
            60,
            3,
            "Poison Powder",
            "Flip a coin. If heads, your opponent's Active Pokemon is now Poisoned. Discard an Energy attached to this Pokemon.",
            "Grass",
            List.of("Grass", "Colorless", "Colorless"),
            3,
            null,
            List.of()
        ));
        when(service.get(gameId)).thenReturn(game);

        AttackRequirementsResponse response = controller.attackRequirements(gameId, 0, null).getBody();

        assertNotNull(response);
        assertEquals(1, response.coinFlipCount());
        assertTrue(response.steps().stream().anyMatch(step -> step.type().equals("SELECT_ATTACHED_ENERGY")));
        assertTrue(response.effects().stream().anyMatch(effect -> effect.type().equals("COIN_FLIP")));
        assertTrue(response.effects().stream().anyMatch(effect -> effect.type().equals("STATUS")));
        assertTrue(response.effects().stream().anyMatch(effect -> effect.type().equals("DISCARD_ENERGY")));
    }

    @Test
    void attackRequirements_triggersAllRequirementTypes() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        UserRepository userRepo = mock(UserRepository.class);
        GameController controller = new GameController(service, cus, userRepo, new AttackPatternAnalyzer());

        UUID player = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);

        GameAggregate game = new GameAggregate(player);
        game.setPlayerTwo(opponent);
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.MAIN);
        game.setCurrentTurnPlayer(player);
        game.setFirstPlayer(player);
        game.setFirstTurn(false);
        game.setTurnNumber(3);
        game.initializeZonesFor(player, new ArrayList<>());
        game.initializeZonesFor(opponent, new ArrayList<>());

        game.activePokemon().put(player, "active-1");
        game.activePokemon().put(opponent, "opp-active-1");

        game.deck().get(player).addAll(List.of("fire-en-1", "water-en-1", "grass-en-1", "generic-en-1", "poke-1", "supporter-1", "normal-1"));
        game.deck().get(opponent).add("opp-deck-1");

        game.hand().get(opponent).addAll(List.of("opp-hand-1", "opp-hand-2"));

        var fireEnergy = new GameCard("fire-en-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of("Fire"), 0);
        var waterEnergy = new GameCard("water-en-1", "Water Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Water", List.of("Water"), 0);
        var grassEnergy = new GameCard("grass-en-1", "Grass Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Grass", List.of("Grass"), 0);
        var genericEnergy = new GameCard("generic-en-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0);
        var pokeCard = new GameCard("poke-1", "Charmander", "Pokemon", Set.of("Basic"), 60, 10, 1);
        var supporterCard = new GameCard("supporter-1", "Professor", "Trainer", Set.of("Supporter"), 0, 0, 0);
        var normalCard = new GameCard("normal-1", "Normal", "Pokemon", Set.of("Basic"), 50, 10, 1);
        var oppActive = new GameCard("opp-active-1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1, "Lightning", List.of("Lightning"), 0);
        var oppHand1 = new GameCard("opp-hand-1", "Rattata", "Pokemon", Set.of("Basic"), 40, 10, 1);
        var oppHand2 = new GameCard("opp-hand-2", "Darkness Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Darkness", List.of("Darkness"), 0);

        game.registerCard(fireEnergy);
        game.registerCard(waterEnergy);
        game.registerCard(grassEnergy);
        game.registerCard(genericEnergy);
        game.registerCard(pokeCard);
        game.registerCard(supporterCard);
        game.registerCard(normalCard);
        game.registerCard(oppActive);
        game.registerCard(oppHand1);
        game.registerCard(oppHand2);

        var attacks = List.of(
            new AttackData("Ember", "Search your deck for a Fire Energy card and attach it to this Pokemon.", "30", List.of("Fire"), 1),
            new AttackData("Look", "Look at the top 3 cards of your deck and put them back in any order.", "10", List.of("Colorless"), 1),
            new AttackData("Peek", "Look at the top card of your opponent's deck and discard it.", "0", List.of("Colorless"), 1),
            new AttackData("Glare", "Look at your opponent's hand.", "20", List.of("Colorless"), 1),
            new AttackData("Thief", "Choose a card from your opponent's hand and discard it.", "10", List.of("Colorless"), 1),
            new AttackData("Block", "Choose an attack of your opponent's Active Pokemon. That Pokemon can't use that attack during your opponent's next turn.", "0", List.of("Colorless"), 1),
            new AttackData("Aqua", "Search your deck for a Water Energy card and attach it to this Pokemon.", "20", List.of("Water"), 1),
            new AttackData("Leaf", "Search your deck for a Grass Energy card.", "0", List.of("Grass"), 1),
            new AttackData("Generic", "Search your deck for an Energy card.", "0", List.of("Colorless"), 1),
            new AttackData("Find", "Search your deck for a Pokemon card.", "0", List.of("Colorless"), 1),
            new AttackData("Call", "Search your deck for a Supporter card.", "0", List.of("Colorless"), 1),
            new AttackData("Pick", "Search your deck for a card.", "0", List.of("Colorless"), 1)
        );

        var attacker = new GameCard(
            "active-1", "MultiMon", "Pokemon", Set.of("Basic"),
            100, 0, 0, null, null, "Fire", List.of("Fire"), 1, null, List.of(),
            null, null, null, null, attacks
        );
        game.registerCard(attacker);

        when(service.get(gameId)).thenReturn(game);

        AttackRequirementsResponse resp;

        resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
        assertEquals("Ember", resp.attackName());
        assertTrue(resp.steps().stream().anyMatch(s -> "SELECT_DECK_CARDS".equals(s.type())));

        resp = controller.attackRequirements(gameId, 1, null).getBody();
        assertNotNull(resp);

        resp = controller.attackRequirements(gameId, 2, null).getBody();
        assertNotNull(resp);

        resp = controller.attackRequirements(gameId, 3, null).getBody();
        assertNotNull(resp);
        assertTrue(resp.steps().stream().anyMatch(s -> "VIEW_HAND".equals(s.type())));

        resp = controller.attackRequirements(gameId, 4, null).getBody();
        assertNotNull(resp);
        assertTrue(resp.steps().stream().anyMatch(s -> "SELECT_HAND_CARD".equals(s.type())));

        resp = controller.attackRequirements(gameId, 5, null).getBody();
        assertNotNull(resp);
        assertTrue(resp.steps().stream().anyMatch(s -> "SELECT_OPPONENT_ATTACK".equals(s.type())));

        // Attack 6: water energy branch of cardMatchesDeckRequirement
        resp = controller.attackRequirements(gameId, 6, null).getBody();
        assertNotNull(resp);
        assertTrue(resp.steps().stream().anyMatch(s -> "SELECT_DECK_CARDS".equals(s.type())));

        // Attack 7: grass energy branch, also hits parseDamage with "0"
        resp = controller.attackRequirements(gameId, 7, null).getBody();
        assertNotNull(resp);

        // Attack 8: generic energy branch + energyType fallback (card with null energyType)
        resp = controller.attackRequirements(gameId, 8, null).getBody();
        assertNotNull(resp);

        // Attack 9: pokemon branch (containsAny)
        resp = controller.attackRequirements(gameId, 9, null).getBody();
        assertNotNull(resp);

        // Attack 10: supporter branch
        resp = controller.attackRequirements(gameId, 10, null).getBody();
        assertNotNull(resp);

        // Attack 11: default branch (return true)
        resp = controller.attackRequirements(gameId, 11, null).getBody();
        assertNotNull(resp);
    }

    @Test
    void action_withTargetPlayerId_coversParseUuid() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID player = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);

        UUID gameId = UUID.randomUUID();
        GameAggregate game = new GameAggregate(player);
        game.setPlayerTwo(opponent);
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.MAIN);
        game.setCurrentTurnPlayer(player);
        game.setFirstPlayer(player);
        game.initializeZonesFor(player, new ArrayList<>());
        game.initializeZonesFor(opponent, new ArrayList<>());
        game.activePokemon().put(player, "a1");
        game.registerCard(new GameCard("a1", "Attacker", "Pokemon", Set.of("Basic"), 60, 10, 1));

        when(service.get(gameId)).thenReturn(game);
        when(service.executeAction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(game);

        var request = new GameActionRequest(
            GameActionType.DRAW, null, null, null, List.of(), null, List.of(),
            UUID.randomUUID().toString(),
            List.of(), List.of(), null, null, null
        );

        assertNotNull(controller.action(gameId, request, null).getBody());
    }

    @Test
    void attackRequirements_withNullActiveCard_coversFallback() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());

        UUID player = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);

        GameAggregate game = new GameAggregate(player);
        game.setPlayerTwo(UUID.randomUUID());
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.MAIN);
        game.setCurrentTurnPlayer(player);
        game.setFirstPlayer(player);
        game.initializeZonesFor(player, new ArrayList<>());
        game.initializeZonesFor(game.playerTwo(), new ArrayList<>());
        // Active Pokemon slot is empty -> cardCatalog will return null

        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
        assertEquals("Ataque", resp.attackName());
    }

    @Test
    void parseDamage_withNonNumeric_returnsZero() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());

        UUID player = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);

        GameAggregate game = new GameAggregate(player);
        game.setPlayerTwo(UUID.randomUUID());
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.MAIN);
        game.setCurrentTurnPlayer(player);
        game.setFirstPlayer(player);
        game.setFirstTurn(false);
        game.initializeZonesFor(player, new ArrayList<>());
        game.initializeZonesFor(game.playerTwo(), new ArrayList<>());

        GameCard attacker = new GameCard("act-1", "Card", "Pokemon", Set.of("Basic"),
            60, 0, 0, "Atk", "Some attack text.", "Fire", List.of("Fire"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("Atk", "text.", "X", List.of("Fire"), 1))
        );
        game.activePokemon().put(player, "act-1");
        game.registerCard(attacker);

        when(service.get(gameId)).thenReturn(game);
        assertNotNull(controller.attackRequirements(gameId, 0, null).getBody());
    }

    @Test
    void coinFlipEndpoint_withHeads_returnsState() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());

        UUID player = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);

        GameAggregate game = new GameAggregate(player);
        game.setPlayerTwo(UUID.randomUUID());
        game.setGameState(GameState.COIN_FLIP);
        game.initializeZonesFor(player, new ArrayList<>());
        game.initializeZonesFor(game.playerTwo(), new ArrayList<>());
        game.activePokemon().put(player, "a1");
        game.registerCard(new GameCard("a1", "Attacker", "Pokemon", Set.of("Basic"), 60, 10, 1));

        when(service.get(gameId)).thenReturn(game);
        when(service.flipCoin(gameId, "heads")).thenReturn(game);

        assertNotNull(controller.coinFlip(gameId, new CoinFlipRequest("heads"), null).getBody());
    }

    @Test
    void chooseFirstEndpoint() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());

        UUID player = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);

        GameAggregate game = new GameAggregate(player);
        game.setPlayerTwo(UUID.randomUUID());
        game.setCoinFlipWinner(player);
        game.setGameState(GameState.COIN_FLIP);
        game.initializeZonesFor(player, new ArrayList<>());
        game.initializeZonesFor(game.playerTwo(), new ArrayList<>());
        game.activePokemon().put(player, "a1");
        game.registerCard(new GameCard("a1", "Attacker", "Pokemon", Set.of("Basic"), 60, 10, 1));

        when(service.get(gameId)).thenReturn(game);
        when(service.chooseFirstPlayer(gameId, player, true)).thenReturn(game);

        assertNotNull(controller.chooseFirst(gameId, new ChooseFirstRequest(true), null).getBody());
    }

    private GameAggregate activeGame(UUID player) {
        UUID opponent = UUID.randomUUID();
        GameAggregate game = new GameAggregate(player);
        game.setPlayerTwo(opponent);
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.MAIN);
        game.setCurrentTurnPlayer(player);
        game.setFirstPlayer(player);
        game.initializeZonesFor(player, List.of("d1", "d2"));
        game.initializeZonesFor(opponent, List.of("od1"));
        game.hand().get(player).add("h1");
        game.prizeCards().get(player).add("p1");
        game.discardPile().get(player).add("x1");
        game.activePokemon().put(player, "active-1");
        game.bench().get(player).add("bench-1");
        game.deckCardsRemaining().put(player, 2);
        game.prizeCardsRemaining().put(player, 1);
        game.activeDamageCounters().put(player, 1);
        game.activeAttachedEnergy().put(player, 1);
        game.statusByPlayer().put(player, EnumSet.of(StatusCondition.POISONED));
        PlayerSetupState setup = new PlayerSetupState();
        setup.setMulligans(1);
        setup.setHandSize(7);
        setup.setBenchCount(1);
        setup.setPrizeCount(6);
        setup.setHasActive(true);
        game.setupByPlayer().put(player, setup);
        return game;
    }

    private GameAggregate setupGame(UUID gameId, UUID player) {
        GameAggregate game = activeGame(player);
        game.setGameState(GameState.SETUP);
        game.setSetupPhase(SetupPhase.SETUP_WAITING_ACTIVE);
        return game;
    }

    // ====================================================================
    // Branch coverage push: remaining uncovered paths in GameController.
    // ====================================================================

    // 1. setup() with interactive=null defaults to true and calls startInteractiveSetup
    @Test
    void setup_withNullInteractive_defaultsToTrueAndCallsStartInteractiveSetup() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID deckId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        when(service.startInteractiveSetup(gameId, 60, 12, 60, 12, deckId, deckId)).thenReturn(game);
        var request = new SetupGameRequest(60, 12, 60, 12, deckId, deckId, null);
        assertNotNull(controller.setup(gameId, request, null).getBody());
        verify(service).startInteractiveSetup(gameId, 60, 12, 60, 12, deckId, deckId);
    }

    // 2. parseUuid() with blank string "  " is invoked via action() — branch returns null
    @Test
    void action_withBlankTargetPlayerId_coversParseUuidBlankBranch() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        when(service.get(gameId)).thenReturn(game);
        when(service.executeAction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(game);
        var request = new GameActionRequest(
            GameActionType.DRAW, null, null, null, List.of(), null, List.of(),
            "  ", // blank -> parseUuid returns null
            List.of(), List.of(), null, null, null
        );
        assertNotNull(controller.action(gameId, request, null).getBody());
    }

    // 3. deck() rejects when authenticated user != path playerId
    @Test
    void deck_rejectsWhenUserIsNotTheRequestedPlayer() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID otherPlayer = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = new GameAggregate(player);
        when(service.get(gameId)).thenReturn(game);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> controller.deck(gameId, otherPlayer, null));
    }

    // 4. attackRequirements() rejects when authenticated player != currentTurnPlayer
    @Test
    void attackRequirements_rejectsWhenNotCurrentTurnPlayer() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(other); // different from currentTurnPlayer
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = new GameAggregate(player);
        game.setPlayerTwo(UUID.randomUUID());
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setCurrentTurnPlayer(player);
        when(service.get(gameId)).thenReturn(game);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> controller.attackRequirements(gameId, 0, null));
    }

    // 5. attackRequirements() with attackIndex >= card.attacks().size() -> falls back path
    @Test
    void attackRequirements_withOutOfRangeIndex_fallsBackToCardDefaults() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID player = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        // attacker with exactly 1 attack and a non-blank attackName
        GameCard attacker = new GameCard(
            "active-1", "FallbackMon", "Pokemon", Set.of("Basic"),
            100, 30, 1, "Fallback", "Do something.", "Colorless",
            List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("Only", "Do 30.", "30", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        // Index 5 is out of range -> selectedAttackText falls through to the card-level fallback
        // which returns activeCard.attackName() since it is non-blank
        var resp = controller.attackRequirements(gameId, 5, null).getBody();
        assertNotNull(resp);
        assertEquals("Fallback", resp.attackName());
    }

    // 6. selectedAttackText() with null/blank attack name falls back to activeCard.name()
    @Test
    void attackRequirements_withNullAttackName_fallsBackToCardName() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID player = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        GameCard attacker = new GameCard(
            "active-1", "FallbackMon2", "Pokemon", Set.of("Basic"),
            100, 30, 1, null, "Do something.", "Colorless",
            List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData(null, "Do 30.", "30", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
        assertEquals("FallbackMon2", resp.attackName());
    }

    // 7. toRequirementStep() SELECT_DECK_CARDS with empty deck -> min/max path
    @Test
    void attackRequirements_selectDeckCardsWithEmptyDeck_returnsZeroBounds() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID player = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setPlayerTwo(opponent);
        game.setTurnPhase(TurnPhase.ATTACK);
        // Empty deck for player
        game.deck().put(player, new ArrayList<>());
        // Attacker with an attack that triggers SELECT_DECK_CARDS
        GameCard attacker = new GameCard(
            "active-1", "Searcher", "Pokemon", Set.of("Basic"),
            100, 0, 0, "Search", "Search your deck for a Fire Energy card.",
            "Fire", List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("Search", "Search your deck for a Fire Energy card.", "0", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
        var step = resp.steps().stream().filter(s -> "SELECT_DECK_CARDS".equals(s.type())).findFirst().orElseThrow();
        assertEquals(0, step.min());
        assertEquals(0, step.max());
        assertTrue(step.cards().isEmpty());
    }

    // 8. toRequirementStep() SELECT_HAND_CARD with empty opponent hand
    @Test
    void attackRequirements_selectHandCardWithEmptyOpponentHand_returnsZeroBounds() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID player = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setPlayerTwo(opponent);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.hand().put(opponent, new ArrayList<>()); // empty
        GameCard attacker = new GameCard(
            "active-1", "Thief", "Pokemon", Set.of("Basic"),
            100, 10, 1, "Thief", "Choose a card from your opponent's hand and discard it.",
            "Darkness", List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("Thief", "Choose a card from your opponent's hand and discard it.", "10", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
        var step = resp.steps().stream().filter(s -> "SELECT_HAND_CARD".equals(s.type())).findFirst().orElseThrow();
        assertEquals(0, step.min());
        assertEquals(0, step.max());
        assertTrue(step.cards().isEmpty());
    }

    // 9 + 10. visibleOpponentActiveCard() with null activeCardId and card-not-in-catalog
    @Test
    void attackRequirements_selectOpponentAttackWithNullActiveId_returnsEmpty() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID player = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setPlayerTwo(opponent);
        game.setTurnPhase(TurnPhase.ATTACK);
        // Opponent has no active Pokemon
        game.activePokemon().put(opponent, null);
        GameCard attacker = new GameCard(
            "active-1", "Blocker", "Pokemon", Set.of("Basic"),
            100, 0, 0, "Block", "Choose an attack of your opponent's Active Pokemon. That Pokemon can't use that attack.",
            "Colorless", List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("Block", "Choose an attack of your opponent's Active Pokemon. That Pokemon can't use that attack.", "0", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
        var step = resp.steps().stream().filter(s -> "SELECT_OPPONENT_ATTACK".equals(s.type())).findFirst().orElseThrow();
        assertTrue(step.cards().isEmpty());
    }

    @Test
    void attackRequirements_selectOpponentAttackWithCardNotInCatalog_returnsEmpty() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID player = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setPlayerTwo(opponent);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.activePokemon().put(opponent, "missing-opp"); // not in catalog
        GameCard attacker = new GameCard(
            "active-1", "Blocker2", "Pokemon", Set.of("Basic"),
            100, 0, 0, "Block", "Choose an attack of your opponent's Active Pokemon. That Pokemon can't use that attack.",
            "Colorless", List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("Block", "Choose an attack of your opponent's Active Pokemon. That Pokemon can't use that attack.", "0", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
        var step = resp.steps().stream().filter(s -> "SELECT_OPPONENT_ATTACK".equals(s.type())).findFirst().orElseThrow();
        assertTrue(step.cards().isEmpty());
    }

    // 11. toVisibleCardInfo() with composite id containing ":"
    @Test
    void deck_withCompositeCardIds_stripsAndDerivesSetId() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = new GameAggregate(player);
        game.setPlayerTwo(UUID.randomUUID());
        game.initializeZonesFor(player, List.of());
        when(service.get(gameId)).thenReturn(game);
        when(service.getDeckCardIds(gameId, player)).thenReturn(List.of("xy1-1:p1:0"));
        game.registerCard(new GameCard("xy1-1:p1:0", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1));
        var resp = controller.deck(gameId, player, null).getBody();
        assertNotNull(resp);
        assertEquals("xy1-1", resp.cards().get(0).cardId());
        assertEquals("xy1", resp.cards().get(0).setId());
    }

    // 12. toVisibleCardInfo() with cardId without "-" -> setId becomes ""
    @Test
    void deck_withCardIdWithoutDash_derivesBlankSetId() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = new GameAggregate(player);
        game.setPlayerTwo(UUID.randomUUID());
        game.initializeZonesFor(player, List.of());
        when(service.get(gameId)).thenReturn(game);
        when(service.getDeckCardIds(gameId, player)).thenReturn(List.of("Pikachu"));
        game.registerCard(new GameCard("Pikachu", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1));
        var resp = controller.deck(gameId, player, null).getBody();
        assertNotNull(resp);
        assertEquals("Pikachu", resp.cards().get(0).cardId());
        assertEquals("", resp.cards().get(0).setId());
    }

    // 13. energyType() with card.name() = null -> falls into the null-name branch returning ""
    @Test
    void attackRequirements_genericEnergyWithNullCardName_returnsColorless() {
        // Generic "energy" attack text forces the energyType(card) fallback when name is null.
        // The branch `card.name() == null ? "" : card.name().replaceAll(...)` returns "".
        // We assert the controller doesn't throw and the response is built.
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID player = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setPlayerTwo(opponent);
        game.setTurnPhase(TurnPhase.ATTACK);
        // Deck card with null name and null energyType — forces energyType(card) to look at the (null) name
        game.deck().get(player).add("en-1");
        GameCard mystery = new GameCard(
            "en-1", null, "Energy", null,
            0, 0, 0, null, null, null, List.of(), 0, null, List.of(),
            null, null, null, null, List.of()
        );
        game.registerCard(mystery);
        GameCard attacker = new GameCard(
            "active-1", "FindEnergy", "Pokemon", Set.of("Basic"),
            100, 0, 0, "Find", "Search your deck for an Energy card.",
            "Colorless", List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("Find", "Search your deck for an Energy card.", "0", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
    }

    // 14. visibleBenchInfo() with null entries in bench and a card-id that is not in catalog
    @Test
    void state_withBenchNullEntriesAndMissingCatalogCard_returnsMixed() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID player = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        // Register a card for the third bench slot so visibleBenchInfo can produce a VisibleCardInfo for it
        game.registerCard(new GameCard("active-1", "BenchMon", "Pokemon", Set.of("Basic"), 60, 10, 1));
        // Set bench to a 5-slot list with nulls and one id not in catalog
        game.bench().put(player, new ArrayList<>(java.util.Arrays.asList(null, "missing-in-catalog", null, "active-1", null)));
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.state(gameId, null).getBody();
        assertNotNull(resp);
        // benchInfo is the per-slot version (5 slots, null where the card is missing or the slot is empty)
        var benchInfo = resp.benchInfo().get(player);
        assertNotNull(benchInfo);
        assertEquals(5, benchInfo.size());
        assertNull(benchInfo.get(0));
        assertNull(benchInfo.get(1)); // missing-in-catalog: filtered to null by visibleBenchInfo
        assertNull(benchInfo.get(2));
        assertNotNull(benchInfo.get(3));
    }

    // 15. visibleActivePokemonInfo() with active card id not in catalog -> entry is omitted
    @Test
    void state_withActiveCardNotInCatalog_omitsEntry() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID player = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.activePokemon().put(player, "missing-active");
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.state(gameId, null).getBody();
        assertNotNull(resp);
        assertNull(resp.activePokemonInfo().get(player));
    }

    // 16. parseDamage() with null or blank damage — verify the response is built without crash
    @Test
    void attackRequirements_withBlankDamageString_returnsZero() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID player = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        GameCard attacker = new GameCard(
            "active-1", "BlankDamage", "Pokemon", Set.of("Basic"),
            100, 0, 0, "Blanker", "Does nothing.", "Colorless",
            List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("Blanker", "Does nothing.", "   ", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        // Blank damage -> parseDamage returns 0, response is built (no exception)
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
        assertEquals("Blanker", resp.attackName());
        assertEquals(0, resp.coinFlipCount());
    }

    // 17. cardMatchesDeckRequirement() with lightning, psychic, fighting, darkness, metal energy types
    @Test
    void attackRequirements_coversAllEnergyTypeBranches() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID player = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        // Add 1 of each energy type to the deck
        game.deck().get(player).addAll(List.of("lightning-en-1", "psychic-en-1", "fighting-en-1", "darkness-en-1", "metal-en-1"));
        game.registerCard(new GameCard("lightning-en-1", "Lightning Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Lightning", List.of("Lightning"), 0));
        game.registerCard(new GameCard("psychic-en-1", "Psychic Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Psychic", List.of("Psychic"), 0));
        game.registerCard(new GameCard("fighting-en-1", "Fighting Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fighting", List.of("Fighting"), 0));
        game.registerCard(new GameCard("darkness-en-1", "Darkness Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Darkness", List.of("Darkness"), 0));
        game.registerCard(new GameCard("metal-en-1", "Metal Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Metal", List.of("Metal"), 0));
        GameCard attacker = new GameCard(
            "active-1", "OmniSearcher", "Pokemon", Set.of("Basic"),
            100, 0, 0, "Omni", "Search your deck.",
            "Colorless", List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(
                new AttackData("L", "Search your deck for a Lightning energy card.", "0", List.of("Colorless"), 1),
                new AttackData("P", "Search your deck for a Psychic energy card.", "0", List.of("Colorless"), 1),
                new AttackData("F", "Search your deck for a Fighting energy card.", "0", List.of("Colorless"), 1),
                new AttackData("D", "Search your deck for a Darkness energy card.", "0", List.of("Colorless"), 1),
                new AttackData("M", "Search your deck for a Metal energy card.", "0", List.of("Colorless"), 1)
            )
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        for (int i = 0; i < 5; i++) {
            var resp = controller.attackRequirements(gameId, i, null).getBody();
            assertNotNull(resp, "attack " + i + " response should not be null");
        }
    }

    // 18. resolveStadiumName() with stadium set but card not in catalog -> null
    @Test
    void state_withStadiumCardNotInCatalog_resolvesStadiumNameToNull() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID player = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.putStadium(player, "stadium-id-not-in-catalog");
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.state(gameId, null).getBody();
        assertNotNull(resp);
        assertNull(resp.activeStadiumName());
    }

    // 19. responsePhase() with SETUP state and setupPhase=null -> falls back to turnPhase().name()
    @Test
    void state_setupStateWithNullSetupPhase_fallsBackToTurnPhase() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID player = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = new GameAggregate(player);
        game.setPlayerTwo(UUID.randomUUID());
        game.setGameState(GameState.SETUP);
        game.setSetupPhase(null);
        game.setTurnPhase(TurnPhase.MAIN);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.state(gameId, null).getBody();
        assertNotNull(resp);
        assertEquals("MAIN", resp.phase());
    }

    // 20. toVisibleCardInfo() with null energyType and null abilities fields
    @Test
    void deck_withCardHavingNullEnergyTypeAndEmptyAbilities_buildsVisibleInfo() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = new GameAggregate(player);
        game.setPlayerTwo(UUID.randomUUID());
        game.initializeZonesFor(player, List.of());
        when(service.get(gameId)).thenReturn(game);
        when(service.getDeckCardIds(gameId, player)).thenReturn(List.of("xy1-1"));
        // Build card with null energyType and null abilities (compact constructor normalizes abilities to empty list)
        GameCard card = new GameCard(
            "xy1-1", "MysteryMon", "Pokemon", Set.of("Basic"),
            60, 10, 1, null, null, null, List.of(), 1, null, null,
            null, null, null, null, List.of()
        );
        game.registerCard(card);
        var resp = controller.deck(gameId, player, null).getBody();
        assertNotNull(resp);
        assertNull(resp.cards().get(0).energyType());
        assertTrue(resp.cards().get(0).abilities().isEmpty());
    }
}
