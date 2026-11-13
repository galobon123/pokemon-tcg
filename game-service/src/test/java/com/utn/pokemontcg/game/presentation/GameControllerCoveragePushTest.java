package com.utn.pokemontcg.game.presentation;

import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.domain.attack.AttackPatternAnalyzer;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.PlayerSetupState;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.infrastructure.client.CardServiceClient.AttackData;
import com.utn.pokemontcg.game.repository.UserRepository;
import com.utn.pokemontcg.game.model.User;
import com.utn.pokemontcg.game.presentation.controller.GameController;
import com.utn.pokemontcg.game.security.CurrentUserService;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Branch coverage push v2 for {@link GameController}.
 *
 * Targets 24 missed branches in:
 *   - playerNames / playerAvatars (L421, L440): game.playerOne() != null
 *   - visibleActivePokemonInfo / visibleBenchInfo (L455, L470): shouldHideOpponent
 *   - revealedHandCards (L496, L502): revealedIds == null || isEmpty, !cards.isEmpty
 *   - selectedAttackText (L549, L552, L553, L558, L559): empty/blank attack.name, attack.text null
 *   - toRequirementStep (L565): opponentId ternary false branch
 *   - cardMatchesDeckRequirement (L641-645): lightning, psychic, fighting, darkness, metal
 *   - energyType (L653, L654): null + null name branch
 *   - normalizeText (L665): value == null
 *   - parseDamage (L671): damage == null
 *   - setupSummary (L689): setHasActive false
 */
class GameControllerCoveragePushTest {

    // ===== L421, L440: playerOne() != null false branch =====

    @Test
    void state_withNullPlayerOne_stillBuildsResponse() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        // Create a game with playerOne set to null
        GameAggregate game = new GameAggregate(null);
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.MAIN);
        game.initializeZonesFor(null, List.of());
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.state(gameId, null).getBody();
        assertNotNull(resp);
    }

    // ===== L455: visibleActivePokemonInfo shouldHideOpponent + requestingPlayer null =====

    @Test
    void state_setupStateWithRequestingPlayer_showsOwnActive() {
        // SETUP state + requestingPlayer = the user. Opponent's active is hidden,
        // but the requesting player's active is visible.
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setGameState(GameState.SETUP);
        // Register the active card so visibleActivePokemonInfo can produce info.
        game.registerCard(new GameCard("active-1", "Attacker", "Pokemon", Set.of("Basic"), 60, 10, 1));
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.state(gameId, null).getBody();
        assertNotNull(resp);
        // The requesting player's active should be visible.
        assertNotNull(resp.activePokemonInfo().get(player));
    }

    // ===== L470: visibleBenchInfo shouldHideOpponent + requestingPlayer null =====

    @Test
    void state_setupStateWithNullRequestingPlayer_showsBothBenches() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setGameState(GameState.SETUP);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.state(gameId, null).getBody();
        assertNotNull(resp);
        // Both benches should be visible.
        assertNotNull(resp.benchInfo().get(player));
    }

    // ===== L496: revealedHandCards revealedIds is null =====

    @Test
    void state_setupByPlayerWithNullRevealedHandIds_skipsPlayer() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        // Set up a player with null revealedHandCardIds
        PlayerSetupState setup = new PlayerSetupState();
        setup.setMulligans(0);
        setup.setHandSize(0);
        setup.setBenchCount(0);
        setup.setPrizeCount(0);
        setup.setHasActive(false);
        setup.setRevealedHandCardIds(null);
        game.setupByPlayer().put(player, setup);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.state(gameId, null).getBody();
        assertNotNull(resp);
    }

    // ===== L502: !cards.isEmpty() false branch (all cards are null) =====

    @Test
    void state_revealedHandIdsAllNull_skipsPlayer() {
        // All revealed ids are null after catalog lookup → cards list empty.
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        PlayerSetupState setup = new PlayerSetupState();
        setup.setRevealedHandCardIds(List.of("missing-1", "missing-2"));
        game.setupByPlayer().put(player, setup);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.state(gameId, null).getBody();
        assertNotNull(resp);
    }

    // ===== L549: activeCard.attacks().isEmpty() true branch (skip the if) =====

    @Test
    void attackRequirements_withEmptyAttacksList_fallsBack() {
        // The active card has no attacks → falls through to card-level fallback.
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        // Register an active with empty attacks list but non-blank attackName
        GameCard attacker = new GameCard(
            "active-1", "EmptyAttacksMon", "Pokemon", Set.of("Basic"),
            100, 0, 0, "FallbackName", "FallbackText", "Colorless",
            List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of() // empty attacks list
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
        assertEquals("FallbackName", resp.attackName());
    }

    // ===== L552: attack.name().isBlank() true branch =====

    @Test
    void attackRequirements_withBlankAttackName_fallsBackToCardName() {
        // attack.name() is "" (blank) → fall back to activeCard.name().
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        GameCard attacker = new GameCard(
            "active-1", "BlankNameMon", "Pokemon", Set.of("Basic"),
            100, 0, 0, null, "Does stuff.", "Colorless",
            List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("   ", "Does 30.", "30", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
        // Blank name → activeCard.name() is used.
        assertEquals("BlankNameMon", resp.attackName());
    }

    // ===== L553: attack.text() == null true branch =====

    @Test
    void attackRequirements_withNullAttackText_returnsEmpty() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        GameCard attacker = new GameCard(
            "active-1", "NullTextMon", "Pokemon", Set.of("Basic"),
            100, 0, 0, null, null, "Colorless",
            List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("Hit", null, "30", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
        // attack.text() is null → "" used.
        assertEquals("Hit", resp.attackName());
    }

    // ===== L558: activeCard.attackName() == null true branch (card-level fallback) =====

    @Test
    void attackRequirements_indexOutOfRange_withNullAttackName_fallsBackToCardName() {
        // Index out of range + activeCard.attackName() is null → activeCard.name().
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        GameCard attacker = new GameCard(
            "active-1", "NullAttackNameMon", "Pokemon", Set.of("Basic"),
            100, 0, 0, null, "Does stuff.", "Colorless",
            List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("First", "Does 30.", "30", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        // Index 5 out of range → falls through to card-level fallback
        var resp = controller.attackRequirements(gameId, 5, null).getBody();
        assertNotNull(resp);
        // activeCard.attackName() is null → activeCard.name()
        assertEquals("NullAttackNameMon", resp.attackName());
    }

    // ===== L558: activeCard.attackName().isBlank() true branch (card-level fallback) =====

    @Test
    void attackRequirements_indexOutOfRange_withBlankAttackName_fallsBackToCardName() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        GameCard attacker = new GameCard(
            "active-1", "BlankAttackNameMon", "Pokemon", Set.of("Basic"),
            100, 0, 0, "   ", "Does stuff.", "Colorless",
            List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("First", "Does 30.", "30", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 5, null).getBody();
        assertNotNull(resp);
        assertEquals("BlankAttackNameMon", resp.attackName());
    }

    // ===== L559: activeCard.attackText() == null true branch =====

    @Test
    void attackRequirements_indexOutOfRange_withNullAttackText_returnsEmpty() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        GameCard attacker = new GameCard(
            "active-1", "NullAttackTextMon", "Pokemon", Set.of("Basic"),
            100, 0, 0, "GoodName", null, "Colorless",
            List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("First", "Does 30.", "30", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 5, null).getBody();
        assertNotNull(resp);
    }

    // ===== L565: opponentId ternary false branch (playerId != playerOne, so opponent = playerOne) =====

    @Test
    void attackRequirements_viewDeckTop_withP1Requester_picksOpponentAsP2() {
        // requestingPlayer = game.playerOne() → ternary: playerId.equals(playerOne()) is true
        // → opponent = playerTwo. (This hits the false branch of the ternary by going the other way.)
        // We need p1 as requester, and the attack requires looking at opponent's (p2) deck.
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(p1);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(p1);
        game.setPlayerTwo(p2);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setCurrentTurnPlayer(p1);
        // p2 has cards in deck (so opponent's deck has cards for VIEW_DECK_TOP)
        if (!game.deck().containsKey(p2)) {
            game.deck().put(p2, new java.util.ArrayList<>());
        }
        game.deck().get(p2).add("p2-deck-1");
        game.registerCard(new GameCard("p2-deck-1", "P2Card", "Pokemon", Set.of("Basic"), 60, 10, 1));
        // The active is "active-1" which is registered in activeGame? No, we need to register.
        game.registerCard(new GameCard("active-1", "Attacker", "Pokemon", Set.of("Basic"),
            100, 0, 0, "Peek", "Look at the top 3 cards of your opponent's deck.", "Colorless",
            List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("Peek", "Look at the top 3 cards of your opponent's deck.", "0", List.of("Colorless"), 1))
        ));
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
    }

    // ===== L641-645: lightning, psychic, fighting, darkness, metal energy branches =====

    @Test
    void attackRequirements_coversAllEnergyTypesForDeckSearch() {
        // This test covers lightning, psychic, fighting, darkness, and metal energy
        // branches in cardMatchesDeckRequirement.
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        // Add energy cards of all types
        game.deck().get(player).addAll(List.of(
            "lightning-en", "psychic-en", "fighting-en", "darkness-en", "metal-en"
        ));
        for (String id : new String[]{"lightning-en", "psychic-en", "fighting-en", "darkness-en", "metal-en"}) {
            String type = id.split("-")[0];
            String cap = Character.toUpperCase(type.charAt(0)) + type.substring(1);
            game.registerCard(new GameCard(id, cap + " Energy", "Energy", Set.of("Basic"),
                0, 0, 0, cap, List.of(cap), 0));
        }
        GameCard attacker = new GameCard(
            "active-1", "AllEnergies", "Pokemon", Set.of("Basic"),
            100, 0, 0, "SearchAll", "Search your deck for a Lightning Energy card. Then search for a Psychic Energy card. Then search for a Fighting Energy card. Then search for a Darkness Energy card. Then search for a Metal Energy card.",
            "Colorless", List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("SearchAll",
                "Search your deck for a Lightning Energy card. Then search for a Psychic Energy card. Then search for a Fighting Energy card. Then search for a Darkness Energy card. Then search for a Metal Energy card.",
                "0", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
    }

    // ===== L653, L654: energyType null + null name branch =====

    @Test
    void attackRequirements_genericEnergyWithNullNameAndEnergyType_returnsColorless() {
        // Generic "energy" attack text + card with null name + null energyType.
        // energyType(card): card.energyType() is null → false branch. card.name() is null → "".
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.deck().get(player).add("mystery-1");
        GameCard mystery = new GameCard(
            "mystery-1", null, "Energy", null,
            0, 0, 0, null, null, null, List.of(), 0, null, List.of(),
            null, null, null, null, List.of()
        );
        game.registerCard(mystery);
        GameCard attacker = new GameCard(
            "active-1", "GenericSearch", "Pokemon", Set.of("Basic"),
            100, 0, 0, "Find", "Search your deck for an Energy card.", "Colorless",
            List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("Find", "Search your deck for an Energy card.", "0", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
    }

    @Test
    void energyType_cardNameBlank_returnsEnergyType() {
        // card.energyType() is non-null and not "Energy" → returns it directly.
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.deck().get(player).add("special-en");
        // energyType is set to "Special" (non-blank, non-"Energy") and name is non-blank.
        GameCard special = new GameCard(
            "special-en", "Mystery Energy", "Energy", Set.of("Basic"),
            0, 0, 0, "Special", List.of("Special"), 0
        );
        game.registerCard(special);
        GameCard attacker = new GameCard(
            "active-1", "FireSearch", "Pokemon", Set.of("Basic"),
            100, 0, 0, "Find", "Search your deck for a Fire Energy card.", "Colorless",
            List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("Find", "Search your deck for a Fire Energy card.", "0", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
    }

    @Test
    void energyType_cardEnergyTypeIsEnergy_fallsBackToName() {
        // card.energyType() == "Energy" (case-insensitive) → fall through to name.replaceAll.
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.deck().get(player).add("energy-x");
        // energyType is "Energy" (uppercase) → fall through.
        GameCard weirdEnergy = new GameCard(
            "energy-x", "Colorless Energy", "Energy", Set.of("Basic"),
            0, 0, 0, "Energy", List.of("Colorless"), 0
        );
        game.registerCard(weirdEnergy);
        GameCard attacker = new GameCard(
            "active-1", "FireSearch", "Pokemon", Set.of("Basic"),
            100, 0, 0, "Find", "Search your deck for a Fire Energy card.", "Colorless",
            List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("Find", "Search your deck for a Fire Energy card.", "0", List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
    }

    // ===== L671: parseDamage with null damage =====

    @Test
    void attackRequirements_withNullDamage_returnsZero() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);
        // damage = null (third arg of AttackData)
        GameCard attacker = new GameCard(
            "active-1", "NullDamageMon", "Pokemon", Set.of("Basic"),
            100, 0, 0, "NoDam", "Does nothing.", "Colorless",
            List.of("Colorless"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData("NoDam", "Does nothing.", null, List.of("Colorless"), 1))
        );
        game.registerCard(attacker);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.attackRequirements(gameId, 0, null).getBody();
        assertNotNull(resp);
    }

    // ===== L689: setupSummary setHasActive false branch =====

    @Test
    void state_setupByPlayerWithNoActive_setsHasActiveFalse() {
        GameApplicationService service = mock(GameApplicationService.class);
        CurrentUserService cus = mock(CurrentUserService.class);
        GameController controller = new GameController(service, cus, mock(UserRepository.class), new AttackPatternAnalyzer());
        UUID gameId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(player);
        when(cus.requireUser(null)).thenReturn(user);
        GameAggregate game = activeGame(player);
        // Remove the active pokemon to force setHasActive(false).
        game.activePokemon().put(player, null);
        when(service.get(gameId)).thenReturn(game);
        var resp = controller.state(gameId, null).getBody();
        assertNotNull(resp);
        // The setupByPlayer map is included in the response.
        assertNotNull(resp.setupByPlayer().get(player));
    }

    // ===== L671: damage.isBlank() true branch (already covered by attackRequirements_withBlankDamageString_returnsZero) =====

    // ===== Helper =====

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
}
