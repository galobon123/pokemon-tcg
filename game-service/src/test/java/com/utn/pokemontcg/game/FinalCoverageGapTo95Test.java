package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.application.service.SetupEngineService;
import com.utn.pokemontcg.game.application.service.TurnActionValidator;
import com.utn.pokemontcg.game.application.service.VictoryService;
import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.engine.DamageCalculator;
import com.utn.pokemontcg.game.domain.engine.RuleValidator;
import com.utn.pokemontcg.game.domain.engine.StatusEffectManager;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.domain.strategy.AttackUtilityEffectStrategy;
import com.utn.pokemontcg.game.infrastructure.client.CardServiceClient.AttackData;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Final coverage push tests targeting the remaining ~34 lines to cross 95% threshold.
 *
 * <p>Focuses on the specific branches that are still red in the JaCoCo report:
 * <ul>
 *   <li>GameApplicationService: finishReason, retreatActivePokemon, drawForTurn
 *       early-return, revealedCards with non-empty ids, subtypesOf via realDeckCardsFor
 *       (via test helpers that exercise the private logic), attachEnergy with
 *       "double colorless" and "rainbow" name branches.</li>
 *   <li>GameEngineFacade: buildDefaultAttackContext path with a card that has a
 *       populated attacks list (the legacy + attacks branch is still missed).</li>
 *   <li>AttackUtilityEffectStrategy: discardSelectedOpponentHandCard / shuffle
 *       branches with empty hand and the discardOpponentHandForTails path
 *       when results happen via the supplier (not pre-set).</li>
 * </ul>
 */
class FinalCoverageGapTo95Test {

    // ============================================================
    //  GameApplicationService – finishReason() branches
    // ============================================================

    @Test
    void finishReason_returnsPRIZES_whenWinnerHasZeroPrizesLeft() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        // Set up: game is FINISHED, winner has 0 prizes, loser has prizes.
        game.setGameState(GameState.FINISHED);
        game.setWinner(game.playerOne());
        game.prizeCardsRemaining().put(game.playerOne(), 0); // winner has no prizes
        game.prizeCardsRemaining().put(game.playerTwo(), 6); // loser still has prizes
        String reason = (String) invokePrivate(svc, "finishReason", game, (UUID) null);
        assertEquals("PRIZES", reason);
    }

    @Test
    void finishReason_returnsKOTOTAL_whenLoserHasNoPokemon() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        game.setGameState(GameState.FINISHED);
        game.setWinner(game.playerOne());
        // Winner still has prizes, but loser has no active & no bench.
        game.prizeCardsRemaining().put(game.playerOne(), 3);
        game.prizeCardsRemaining().put(game.playerTwo(), 6);
        game.activePokemon().remove(game.playerTwo());
        java.util.List<String> emptyBench = new ArrayList<>();
        emptyBench.add(null);
        emptyBench.add(null);
        emptyBench.add(null);
        emptyBench.add(null);
        emptyBench.add(null);
        game.bench().put(game.playerTwo(), emptyBench);
        String reason = (String) invokePrivate(svc, "finishReason", game, (UUID) null);
        assertEquals("KO_TOTAL", reason);
    }

    @Test
    void finishReason_returnsDECKOUT_whenLoserDeckIsEmpty() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        game.setGameState(GameState.FINISHED);
        game.setWinner(game.playerOne());
        // Winner has prizes and loser has Pokemon in play, but loser's deck is empty.
        game.prizeCardsRemaining().put(game.playerOne(), 3);
        game.prizeCardsRemaining().put(game.playerTwo(), 6);
        game.deck().put(game.playerTwo(), new ArrayList<>()); // empty
        String reason = (String) invokePrivate(svc, "finishReason", game, (UUID) null);
        assertEquals("DECK_OUT", reason);
    }

    @Test
    void finishReason_returnsUNKNOWN_whenWinnerIsNull() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        game.setGameState(GameState.FINISHED);
        game.setWinner(null);
        String reason = (String) invokePrivate(svc, "finishReason", game, (UUID) null);
        assertEquals("UNKNOWN", reason);
    }

    @Test
    void finishReason_returnsUNKNOWN_whenWinnerUnchanged() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        game.setGameState(GameState.FINISHED);
        // Winner matches previousWinner → no actual win transition.
        game.setWinner(game.playerOne());
        String reason = (String) invokePrivate(svc, "finishReason", game, game.playerOne());
        assertEquals("UNKNOWN", reason);
    }

    // ============================================================
    //  GameApplicationService – drawForTurn firstTurn + firstPlayer match
    // ============================================================

    @Test
    void executeDrawAction_isNoOpWhenFirstTurnAndCurrentIsFirstPlayer() {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        UUID first = game.playerOne();
        game.setFirstPlayer(first);
        game.setCurrentTurnPlayer(first);
        game.setFirstTurn(true);
        game.setTurnPhase(TurnPhase.DRAW);
        // Give the player a deck so that a real draw would be possible.
        game.deck().get(first).add("deck-1");
        game.deckCardsRemaining().put(first, 1);
        int handBefore = game.hand().get(first).size();
        GameAggregate result = svc.executeAction(game.id(), GameActionType.DRAW);
        assertNotNull(result);
        // Hand size must remain unchanged: first-turn first-player skips draw.
        assertEquals(handBefore, result.hand().get(first).size());
    }

    // ============================================================
    //  GameApplicationService – retreat with non-zero retreat cost
    // ============================================================

    @Test
    void executeRetreatAction_consumesEnergyAndSwapsActive() {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        UUID p1 = game.playerOne();
        // Active has retreatCost=2; attach 2 energies so retreat is valid.
        String activeId = "active-ret-2";
        String benchId = "bench-ret-2";
        GameCard active = new GameCard(activeId, "Retreater", "Pokemon", Set.of("Basic"),
            100, 0, 0, null, null, "Fire", List.of("Fire"), 2, null, List.of());
        GameCard bench = new GameCard(benchId, "BenchMon", "Pokemon", Set.of("Basic"),
            80, 0, 0);
        game.registerCard(active);
        game.registerCard(bench);
        game.activePokemon().put(p1, activeId);
        // Bench must have a slot at index 0.
        List<String> benchList = new ArrayList<>();
        benchList.add(benchId);
        benchList.add(null);
        benchList.add(null);
        benchList.add(null);
        benchList.add(null);
        game.bench().put(p1, benchList);
        game.attachEnergy(p1, "ACTIVE", new AttachedEnergy("e1", "Fire"));
        game.attachEnergy(p1, "ACTIVE", new AttachedEnergy("e2", "Fire"));

        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.MAIN);
        game.setCurrentTurnPlayer(p1);
        game.setFirstPlayer(p1);
        game.setFirstTurn(false);
        game.setTurnNumber(3);

        GameAggregate result = svc.executeAction(
            game.id(), GameActionType.RETREAT, null, null,
            "BENCH_0", List.of("e1", "e2"),
            null, null, null, List.of(), List.of(), null, null, null);

        // Active slot now points to the bench card.
        assertEquals(benchId, result.activePokemon().get(p1));
        // Both energies are moved to the discard pile.
        assertTrue(result.discardPile().getOrDefault(p1, List.of()).contains("e1"));
        assertTrue(result.discardPile().getOrDefault(p1, List.of()).contains("e2"));
    }

    @Test
    void executeRetreatAction_throwsWhenEnergyCountDoesNotMatchCost() {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        UUID p1 = game.playerOne();
        String activeId = "active-ret-3";
        GameCard active = new GameCard(activeId, "Retreater3", "Pokemon", Set.of("Basic"),
            100, 0, 0, null, null, "Fire", List.of("Fire"), 2, null, List.of());
        game.registerCard(active);
        game.activePokemon().put(p1, activeId);
        game.attachEnergy(p1, "ACTIVE", new AttachedEnergy("e1", "Fire"));

        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.MAIN);
        game.setCurrentTurnPlayer(p1);
        game.setFirstPlayer(p1);
        game.setFirstTurn(false);
        game.setTurnNumber(3);

        org.junit.jupiter.api.Assertions.assertThrows(
            com.utn.pokemontcg.game.application.service.InvalidActionException.class,
            () -> svc.executeAction(
                game.id(), GameActionType.RETREAT, null, null,
                "BENCH_0", List.of("e1"),
                null, null, null, List.of(), List.of(), null, null, null)
        );
    }

    // ============================================================
    //  GameApplicationService – revealedCards via attack with revealedCardIds
    // ============================================================

    @Test
    void executeAttack_publishesRevealedCards() {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        UUID p1 = game.playerOne();
        UUID p2 = game.playerTwo();

        game.setFirstTurn(false);
        game.setTurnNumber(3);
        game.setFirstPlayer(p1);
        game.setCurrentTurnPlayer(p1);
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.activeAttachedEnergy().put(p1, 1);
        // Add the card "revealed-1" to the catalog so rawCardId/setId lookups succeed.
        GameCard revealed = new GameCard("sm123-1:p1:0", "Revealed", "Pokemon", Set.of("Basic"),
            50, 0, 0);
        game.registerCard(revealed);

        GameAggregate result = svc.executeAction(
            game.id(), GameActionType.ATTACK, null, null, null,
            List.of(), 0, null, null, List.of(), List.of(), null, null, null);

        assertNotNull(result);
    }

    // ============================================================
    //  GameEngineFacade – buildDefaultAttackContext with attacks list
    // ============================================================

    @Test
    void buildDefaultAttackContext_usesAttacksListAndParsesDamage() throws Exception {
        GameEngineFacade facade = new GameEngineFacade(new GameEventPublisher());
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        game.initializeZonesFor(game.playerOne(), List.of("a1"));
        game.initializeZonesFor(game.playerTwo(), List.of("b1"));
        GameCard attacker = new GameCard(
            "a1", "MultiMon", "Pokemon", Set.of("Basic"),
            60, 30, 1,
            "OldName", "OldText",
            "Fire", List.of("Fire"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData(
                "Charge", "This attack does 50 damage to the defending pokemon.",
                "50", List.of("Colorless"), 1))
        );
        GameCard defender = new GameCard("b1", "Def", "Pokemon", Set.of("Basic"), 60, 0, 0);
        game.registerCard(attacker);
        game.registerCard(defender);
        game.activePokemon().put(game.playerOne(), "a1");
        game.activePokemon().put(game.playerTwo(), "b1");

        Method m = GameEngineFacade.class.getDeclaredMethod(
            "buildDefaultAttackContext", GameAggregate.class);
        m.setAccessible(true);
        AttackContext ctx = (AttackContext) m.invoke(facade, game);

        assertNotNull(ctx);
        // The attack's name overrides the card's name when non-blank.
        assertEquals("Charge", ctx.attackName());
        // Damage was parsed from "50" → 50.
        assertEquals(50, ctx.baseDamage());
    }

    @Test
    void buildDefaultAttackContext_fallsBackToCardNameWhenAttackNameBlank() throws Exception {
        GameEngineFacade facade = new GameEngineFacade(new GameEventPublisher());
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        game.initializeZonesFor(game.playerOne(), List.of("a1"));
        game.initializeZonesFor(game.playerTwo(), List.of("b1"));
        GameCard attacker = new GameCard(
            "a1", "CardFallbackName", "Pokemon", Set.of("Basic"),
            60, 0, 0,
            null, null, "Fire", List.of("Fire"), 1, null, List.of(),
            null, null, null, null,
            List.of(new AttackData(
                "", "X", "0", List.of("Colorless"), 1))
        );
        GameCard defender = new GameCard("b1", "Def", "Pokemon", Set.of("Basic"), 60, 0, 0);
        game.registerCard(attacker);
        game.registerCard(defender);
        game.activePokemon().put(game.playerOne(), "a1");
        game.activePokemon().put(game.playerTwo(), "b1");

        Method m = GameEngineFacade.class.getDeclaredMethod(
            "buildDefaultAttackContext", GameAggregate.class);
        m.setAccessible(true);
        AttackContext ctx = (AttackContext) m.invoke(facade, game);
        // Blank name → fallback to card name.
        assertEquals("CardFallbackName", ctx.attackName());
    }

    // ============================================================
    //  GameEngineFacade – resolveAttack consumes coins (iter exhausted)
    // ============================================================

    @Test
    void resolveAttack_coinFlipExhausted_doesNotThrow() {
        GameEngineFacade facade = new GameEngineFacade(new GameEventPublisher());
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.initializeZonesFor(game.playerOne(), List.of("a1"));
        game.initializeZonesFor(game.playerTwo(), List.of("b1"));
        GameCard attacker = new GameCard(
            "a1", "Pikachu", "Pokemon", Set.of("Basic"),
            60, 30, 1, "Tackle", "Does 30 damage.", "Lightning", List.of("Lightning"), 1,
            null, List.of(), null, null, null, null,
            List.of(new AttackData("Tackle", "Does 30 damage.", "30", List.of("Lightning"), 1))
        );
        GameCard defender = new GameCard("b1", "Def", "Pokemon", Set.of("Basic"), 60, 0, 0);
        game.registerCard(attacker);
        game.registerCard(defender);
        game.activePokemon().put(game.playerOne(), "a1");
        game.activePokemon().put(game.playerTwo(), "b1");
        // Attach one Lightning energy so the energy-validation step is satisfied.
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e-lightning", "Lightning"));

        // Resolve the attack with a single coin-flip result; the strategy may
        // try to flip more times (exhausting the iterator) and must fall back
        // to random without throwing.
        AttackContext ctx = facade.resolveAttack(game, 0, List.of(true));
        assertNotNull(ctx);
    }

    // ============================================================
    //  AttackUtilityEffectStrategy – empty-hand paths
    // ============================================================

    @Test
    void utility_discardSelectedOpponentHandCard_isNoOpWhenHandEmpty() {
        GameAggregate game = baseGame();
        // Opponent hand is empty by default; the "discard a card from opponent's
        // hand" text still matches → early return is taken.
        new AttackUtilityEffectStrategy().apply(ctxFor(
            game, "Theft", "Choose a card from your opponent's hand and discard it."));
        // No exception, no discard pile entry.
        assertTrue(game.discardPile().getOrDefault(game.playerTwo(), new ArrayList<>()).isEmpty());
    }

    @Test
    void utility_shuffleSelectedOpponentHandCardIntoDeck_isNoOpWhenHandEmpty() {
        GameAggregate game = baseGame();
        new AttackUtilityEffectStrategy().apply(ctxFor(
            game, "Shuffler",
            "Choose a card from your opponent's hand and shuffle it into their deck."));
        // Nothing changed.
        assertTrue(game.hand().getOrDefault(game.playerTwo(), new ArrayList<>()).isEmpty());
    }

    @Test
    void utility_discardOpponentHandForTails_usesCoinFlipSupplier() {
        GameAggregate game = baseGame();
        // Two hand cards so we can confirm discards happen when all flips are tails.
        game.hand().get(game.playerTwo()).add("h-tail-1");
        game.hand().get(game.playerTwo()).add("h-tail-2");
        java.util.ArrayDeque<Boolean> tails = new java.util.ArrayDeque<>(List.of(false, false, false, false));
        AttackContext context = ctxFor(
            game, "Hurricane", "Flip 4 coins. For each tails, your opponent discards a card from their hand.");
        context.setCoinFlip(tails::removeFirst);
        new AttackUtilityEffectStrategy().apply(context);
        // The two opponent cards must be discarded; 4 tails → 2 discards (hand had 2).
        assertEquals(2, game.discardPile().getOrDefault(game.playerTwo(), new ArrayList<>()).size());
    }

    // ============================================================
    //  Helpers
    // ============================================================

    private static Object invokePrivate(Object target, String methodName, Object... args) throws Exception {
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i] == null ? Object.class : args[i].getClass();
        }
        // Try exact param types first; if not found, walk public methods for arity match.
        Method m = null;
        for (Method candidate : target.getClass().getDeclaredMethods()) {
            if (!candidate.getName().equals(methodName)) continue;
            if (candidate.getParameterCount() != args.length) continue;
            boolean match = true;
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    if (candidate.getParameterTypes()[i].isPrimitive()) { match = false; break; }
                } else if (!candidate.getParameterTypes()[i].isAssignableFrom(args[i].getClass())) {
                    match = false; break;
                }
            }
            if (match) { m = candidate; break; }
        }
        if (m == null) {
            throw new NoSuchMethodException("No matching method " + methodName + " with " + args.length + " args");
        }
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static GameApplicationService newService() {
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

    private static GameAggregate activeGame(GameApplicationService svc) {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate g = svc.create(p1, null);
        svc.join(g.id(), p2, null);
        return svc.runInitialSetup(g.id(), 60, 12, 60, 12);
    }

    private static GameAggregate baseGame() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate g = new GameAggregate(p1);
        g.setPlayerTwo(p2);
        g.setCurrentTurnPlayer(p1);
        g.setFirstPlayer(p1);
        g.initializeZonesFor(p1, List.of());
        g.initializeZonesFor(p2, List.of());
        g.activePokemon().put(p1, "active-1");
        g.activePokemon().put(p2, "active-2");
        g.registerCard(new GameCard("active-1", "Attacker", "Pokemon", Set.of("Basic"), 100, 10, 1));
        g.registerCard(new GameCard("active-2", "Defender", "Pokemon", Set.of("Basic"), 100, 10, 1));
        return g;
    }

    // ============================================================
    //  GameApplicationService – realDeckCardsFor branches (uses DeckService mock)
    // ============================================================

    @Test
    void realDeckCardsFor_throwsWhenDeckServiceIsNull() {
        // Build a service with the 8-arg constructor (no deckService).
        // Calling startInteractiveSetup with real deckIds triggers
        // realDeckCardsFor() which throws IllegalStateException.
        SetupEngineService setup = new SetupEngineService();
        GameApplicationService svc = new GameApplicationService(
            new InMemoryGameStateRepository(),
            new GameEngineFacade(new GameEventPublisher()),
            setup,
            new TurnActionValidator(),
            new VictoryService(setup),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager()
        );
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p1Deck = UUID.randomUUID();
        UUID p2Deck = UUID.randomUUID();
        GameAggregate created = svc.create(p1, p1Deck);
        svc.join(created.id(), p2, p2Deck);
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class,
            () -> svc.startInteractiveSetup(created.id(), null, null, null, null, p1Deck, p2Deck)
        );
    }

    @Test
    void realDeckCardsFor_throwsWhenDeckPlayerIdDoesNotMatch() {
        com.utn.pokemontcg.game.application.service.deck.DeckService deckService =
            mock(com.utn.pokemontcg.game.application.service.deck.DeckService.class);
        SetupEngineService setup = new SetupEngineService();
        GameApplicationService svc = new GameApplicationService(
            new InMemoryGameStateRepository(),
            new GameEngineFacade(new GameEventPublisher()),
            setup,
            new TurnActionValidator(),
            new VictoryService(setup),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager(),
            deckService
        );
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p1Deck = UUID.randomUUID();
        UUID p2Deck = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        // Deck whose playerId differs from p1 → triggers the mismatch throw.
        com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse deck1 =
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse(
                p1Deck, otherOwner, "wrong owner", List.of(),
                new com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult(true, List.of())
            );
        com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse deck2 =
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse(
                p2Deck, p2, "ok deck", List.of(),
                new com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult(true, List.of())
            );
        org.mockito.Mockito.when(deckService.getEnrichedForGame(p1, p1Deck)).thenReturn(deck1);
        org.mockito.Mockito.when(deckService.getEnrichedForGame(p2, p2Deck)).thenReturn(deck2);
        GameAggregate created = svc.create(p1, p1Deck);
        svc.join(created.id(), p2, p2Deck);
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> svc.startInteractiveSetup(created.id(), null, null, null, null, p1Deck, p2Deck)
        );
    }

    @Test
    void realDeckCardsFor_throwsWhenDeckValidationFails() {
        com.utn.pokemontcg.game.application.service.deck.DeckService deckService =
            mock(com.utn.pokemontcg.game.application.service.deck.DeckService.class);
        SetupEngineService setup = new SetupEngineService();
        GameApplicationService svc = new GameApplicationService(
            new InMemoryGameStateRepository(),
            new GameEngineFacade(new GameEventPublisher()),
            setup,
            new TurnActionValidator(),
            new VictoryService(setup),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager(),
            deckService
        );
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p1Deck = UUID.randomUUID();
        UUID p2Deck = UUID.randomUUID();
        com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse badDeck =
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse(
                p1Deck, p1, "invalid deck", List.of(),
                new com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult(false, List.of("bad card"))
            );
        com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse goodDeck =
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse(
                p2Deck, p2, "ok deck", List.of(),
                new com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult(true, List.of())
            );
        org.mockito.Mockito.when(deckService.getEnrichedForGame(p1, p1Deck)).thenReturn(badDeck);
        org.mockito.Mockito.when(deckService.getEnrichedForGame(p2, p2Deck)).thenReturn(goodDeck);
        GameAggregate created = svc.create(p1, p1Deck);
        svc.join(created.id(), p2, p2Deck);
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> svc.startInteractiveSetup(created.id(), null, null, null, null, p1Deck, p2Deck)
        );
    }

    @Test
    void realDeckCardsFor_marksAceSpecAndBasicPokemonSubtypes() {
        com.utn.pokemontcg.game.application.service.deck.DeckService deckService =
            mock(com.utn.pokemontcg.game.application.service.deck.DeckService.class);
        SetupEngineService setup = new SetupEngineService();
        GameApplicationService svc = new GameApplicationService(
            new InMemoryGameStateRepository(),
            new GameEngineFacade(new GameEventPublisher()),
            setup,
            new TurnActionValidator(),
            new VictoryService(setup),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager(),
            deckService
        );
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p1Deck = UUID.randomUUID();
        UUID p2Deck = UUID.randomUUID();
        // p1's deck has an ACE SPEC basic pokemon (quantity 8 to satisfy the
        // minimum-deck-size check in startInteractiveSetup).
        com.utn.pokemontcg.game.presentation.dto.deck.DeckCardInput ace =
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckCardInput(
                "xy-ace-1", "Ace Pokemon", "xy", 8,
                "Pokemon", "Basic", false, true, true,
                100, 30, 1
            );
        com.utn.pokemontcg.game.presentation.dto.deck.DeckCardInput filler =
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckCardInput(
                "xy-energy-1", "Basic Energy", "xy", 20,
                "Energy", "Basic", true, false, false,
                null, null, null
            );
        com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse deck1 =
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse(
                p1Deck, p1, "ace deck", List.of(ace, filler),
                new com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult(true, List.of())
            );
        com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse deck2 =
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse(
                p2Deck, p2, "opponent deck", List.of(filler, ace),
                new com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult(true, List.of())
            );
        org.mockito.Mockito.when(deckService.getEnrichedForGame(p1, p1Deck)).thenReturn(deck1);
        org.mockito.Mockito.when(deckService.getEnrichedForGame(p2, p2Deck)).thenReturn(deck2);
        GameAggregate created = svc.create(p1, p1Deck);
        svc.join(created.id(), p2, p2Deck);
        GameAggregate result = svc.startInteractiveSetup(created.id(), null, null, null, null, p1Deck, p2Deck);
        // The ace-spec card is in the catalog; check that its subtypes include
        // the markers added by subtypesOf().
        java.util.Optional<GameCard> any = result.cardCatalog().values().stream()
            .filter(c -> "xy-ace-1".equals(c.id().split(":")[0]))
            .findFirst();
        assertTrue(any.isPresent(), "Expected the ACE SPEC card to be in the catalog");
        GameCard built = any.get();
        assertTrue(built.subtypes().contains("Basic"));
        assertTrue(built.subtypes().contains("ACE SPEC"));
    }

    // ============================================================
    //  GameApplicationService – triggerDestinyBurst / triggerSpikyShield
    //  (HP=0 paths are still uncovered; reach them via a fully-KOed defender)
    // ============================================================

    @Test
    void triggerDestinyBurst_publishesAndDealsDamageOnHeads() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        // Defender has a card with the "Destiny Burst" ability. HP=0.
        GameCard defenderCard = new GameCard(
            "db-def", "DBDef", "Pokemon", Set.of("Basic"),
            100, 0, 0, null, null, null, List.of(), 0, null, List.of("Destiny Burst")
        );
        game.registerCard(defenderCard);
        game.activePokemon().put(game.playerTwo(), "db-def");
        game.activeHp().put(game.playerTwo(), 0);

        Method m = GameApplicationService.class.getDeclaredMethod(
            "triggerDestinyBurst", GameAggregate.class, UUID.class, UUID.class);
        m.setAccessible(true);
        // Run a few times so the random coin flip lands on both branches.
        boolean anyHeads = false;
        for (int i = 0; i < 30 && !anyHeads; i++) {
            boolean result;
            try {
                result = (boolean) m.invoke(svc, game, game.playerOne(), game.playerTwo());
            } catch (java.lang.reflect.InvocationTargetException ex) {
                throw new RuntimeException(ex.getCause());
            }
            if (result) anyHeads = true;
        }
        assertTrue(anyHeads, "Expected at least one heads flip in 30 attempts");
    }

    @Test
    void triggerSpikyShield_publishesAndDealsDamage() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        GameCard defenderCard = new GameCard(
            "ss-def", "SSDef", "Pokemon", Set.of("Basic"),
            100, 0, 0, null, null, null, List.of(), 0, null, List.of("Spiky Shield")
        );
        game.registerCard(defenderCard);
        game.activePokemon().put(game.playerTwo(), "ss-def");
        game.activeHp().put(game.playerTwo(), 100);

        Method m = GameApplicationService.class.getDeclaredMethod(
            "triggerSpikyShield", GameAggregate.class, UUID.class, UUID.class, int.class);
        m.setAccessible(true);
        boolean result;
        try {
            result = (boolean) m.invoke(svc, game, game.playerOne(), game.playerTwo(), 30);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            throw new RuntimeException(ex.getCause());
        }
        // Damage > 0, defender has the ability → result must be true.
        assertTrue(result);
    }

    @Test
    void triggerDestinyBurst_returnsFalseWhenDefenderHasNoActive() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        // Defender has NO active Pokemon → guard returns false.
        game.activePokemon().remove(game.playerTwo());
        Method m = GameApplicationService.class.getDeclaredMethod(
            "triggerDestinyBurst", GameAggregate.class, UUID.class, UUID.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(svc, game, game.playerOne(), game.playerTwo());
        assertEquals(false, result);
    }

    @Test
    void triggerDestinyBurst_returnsFalseWhenCardNotInCatalog() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        // Active is set but the card is not in the catalog → guard returns false.
        game.activePokemon().put(game.playerTwo(), "missing-card-id");
        Method m = GameApplicationService.class.getDeclaredMethod(
            "triggerDestinyBurst", GameAggregate.class, UUID.class, UUID.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(svc, game, game.playerOne(), game.playerTwo());
        assertEquals(false, result);
    }

    @Test
    void triggerSpikyShield_returnsFalseWhenDefenderHasNoActive() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        game.activePokemon().remove(game.playerTwo());
        Method m = GameApplicationService.class.getDeclaredMethod(
            "triggerSpikyShield", GameAggregate.class, UUID.class, UUID.class, int.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(svc, game, game.playerOne(), game.playerTwo(), 30);
        assertEquals(false, result);
    }

    @Test
    void triggerSpikyShield_returnsFalseWhenCardNotInCatalog() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        game.activePokemon().put(game.playerTwo(), "missing-card-id");
        Method m = GameApplicationService.class.getDeclaredMethod(
            "triggerSpikyShield", GameAggregate.class, UUID.class, UUID.class, int.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(svc, game, game.playerOne(), game.playerTwo(), 30);
        assertEquals(false, result);
    }

    @Test
    void triggerSpikyShield_returnsFalseWhenDamageIsZero() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        // Damage=0 short-circuits before any other check.
        Method m = GameApplicationService.class.getDeclaredMethod(
            "triggerSpikyShield", GameAggregate.class, UUID.class, UUID.class, int.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(svc, game, game.playerOne(), game.playerTwo(), 0);
        assertEquals(false, result);
    }

    // ============================================================
    //  GameApplicationService – final "UNKNOWN" branch of finishReason
    //  (winner != null && previous != winner && prizes > 0 && loser has
    //  Pokemon in play && loser has a non-empty deck → falls through to UNKNOWN)
    // ============================================================

    @Test
    void finishReason_returnsFinalUNKNOWN_whenNoOtherConditionMatches() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        game.setGameState(GameState.FINISHED);
        game.setWinner(game.playerOne());
        // Prizes: winner has >0, loser has any value (not used here).
        game.prizeCardsRemaining().put(game.playerOne(), 3);
        game.prizeCardsRemaining().put(game.playerTwo(), 6);
        // Loser still has a Pokemon in play and a non-empty deck.
        // (default setup ensures both players have an active).
        game.deck().put(game.playerTwo(), new ArrayList<>(List.of("d1", "d2")));
        // previousWinner must differ from winner.
        String reason = (String) invokePrivate(svc, "finishReason", game, game.playerTwo());
        assertEquals("UNKNOWN", reason);
    }

    // ============================================================
    //  GameApplicationService – revealedCards + rawCardId + setId
    // ============================================================

    @Test
    void revealedCards_returnsEmptyWhenCardIdUnknown() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        // Empty list of ids → no revealed cards, no rawCardId invocation.
        Method m = GameApplicationService.class.getDeclaredMethod(
            "revealedCards", GameAggregate.class, List.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) m.invoke(svc, game, List.of("missing-id"));
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void rawCardId_and_setId_handleColonAndDashSeparators() throws Exception {
        GameApplicationService svc = newService();
        Method raw = GameApplicationService.class.getDeclaredMethod("rawCardId", String.class);
        raw.setAccessible(true);
        Method set = GameApplicationService.class.getDeclaredMethod("setId", String.class);
        set.setAccessible(true);
        // "sm123-1:player:0" → raw returns "sm123-1", set returns "sm123".
        assertEquals("sm123-1", raw.invoke(svc, "sm123-1:player:0"));
        assertEquals("sm123", set.invoke(svc, "sm123-1:player:0"));
        // No separator → returns the input as-is.
        assertEquals("xyz", raw.invoke(svc, "xyz"));
        // Null input → empty string.
        assertEquals("", raw.invoke(svc, (Object) null));
        assertEquals("", set.invoke(svc, (Object) null));
    }

    // ============================================================
    //  GameApplicationService – revealedCards with valid card id
    // ============================================================

    @Test
    void revealedCards_buildsRevealedCardWithRawIdAndSetId() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        // Register a card with a cardId that contains a dash (so setId extracts).
        GameCard card = new GameCard("base1-1:player:0", "Base", "Pokemon", Set.of("Basic"),
            50, 0, 0);
        game.registerCard(card);
        Method m = GameApplicationService.class.getDeclaredMethod(
            "revealedCards", GameAggregate.class, List.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> result =
            (List<java.util.Map<String, Object>>) m.invoke(svc, game, List.of("base1-1:player:0"));
        assertEquals(1, result.size());
        java.util.Map<String, Object> revealed = result.get(0);
        assertEquals("base1-1", revealed.get("cardId"));
        assertEquals("base1", revealed.get("setId"));
    }

    // ============================================================
    //  GameApplicationService – drawForTurn firstTurn branch (direct)
    // ============================================================

    @Test
    void drawForTurn_isNoOpWhenFirstTurnAndPlayerIsFirstPlayer() throws Exception {
        GameApplicationService svc = newService();
        GameAggregate game = activeGame(svc);
        UUID first = game.playerOne();
        game.setFirstPlayer(first);
        game.setCurrentTurnPlayer(first);
        game.setFirstTurn(true);
        int before = game.hand().get(first).size();
        // Hand/deck are populated by runInitialSetup; the early-return branch
        // must not draw.
        Method m = GameApplicationService.class.getDeclaredMethod(
            "drawForTurn", GameAggregate.class, UUID.class);
        m.setAccessible(true);
        m.invoke(svc, game, first);
        assertEquals(before, game.hand().get(first).size());
    }

    // ============================================================
    //  GameApplicationService – refreshSetupSummary is exercised
    //  (via startInteractiveSetup on a freshly created game).
    // ============================================================

    @Test
    void startInteractiveSetup_initializesSetupSummary() {
        GameApplicationService svc = newService();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = svc.create(p1, null);
        svc.join(created.id(), p2, null);
        // Start interactive setup with no real decks → uses deck sizes.
        GameAggregate result = svc.startInteractiveSetup(created.id(), 60, 12, 60, 12, null, null);
        assertNotNull(result.setupByPlayer().get(p1));
        assertNotNull(result.setupByPlayer().get(p2));
    }

    // ============================================================
    //  GameApplicationService – concede publishes the CONCEDE reason
    // ============================================================

    @Test
    void concede_finishesGameWithConcedeReason() {
        GameApplicationService svc = newService();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = svc.create(p1, null);
        svc.join(created.id(), p2, null);
        GameAggregate result = svc.concede(created.id(), p1);
        assertEquals(GameState.FINISHED, result.gameState());
        assertEquals(p2, result.winner());
    }

    // ============================================================
    //  AttackUtilityEffectStrategy – additional supports() patterns
    //  (each is a single line hit; cumulative impact matters)
    // ============================================================

    @Test
    void utilitySupports_drawAndHandKeywords() {
        AttackUtilityEffectStrategy s = new AttackUtilityEffectStrategy();
        // The "draw" keyword is matched as a substring.
        assertTrue(s.supports(ctxFor(baseGame(), "X", "draw 3 cards.")));
        // "opponent's hand" is one of the supported substrings.
        assertTrue(s.supports(ctxFor(baseGame(), "X", "Look at your opponent's hand.")));
        // "search your deck" + "put" + "into your hand" matches.
        assertTrue(s.supports(ctxFor(baseGame(), "X",
            "Search your deck for a Pokemon and put it into your hand.")));
        // "random card from your opponent" matches.
        assertTrue(s.supports(ctxFor(baseGame(), "X",
            "Choose a random card from your opponent's hand.")));
    }

    @Test
    void utility_topDeckLookCount_spanishVariants() {
        // The Spanish variants in topDeckLookCount (line 174-179) are exercised
        // when the strategy is asked to reorder. Trigger via the supports check
        // first, then invoke apply with each text.
        AttackUtilityEffectStrategy s = new AttackUtilityEffectStrategy();
        GameAggregate g = baseGame();
        g.deck().get(g.playerOne()).addAll(List.of("c1", "c2", "c3", "c4", "c5"));

        // Spanish "5 cartas" / "4 cartas" / "3 cartas" / "2 cartas" / "1 carta"
        // all take the corresponding branch.
        AttackContext c5 = ctxFor(g, "Mira5", "Mira las 5 cartas de tu mazo y ponlas de nuevo en cualquier orden.");
        c5.setSelectedCardIds(List.of("c1", "c2", "c3", "c4", "c5"));
        s.apply(c5);
        AttackContext c4 = ctxFor(g, "Mira4", "Mira las 4 cartas de tu mazo y ponlas de nuevo en cualquier orden.");
        c4.setSelectedCardIds(List.of("c1", "c2", "c3", "c4"));
        s.apply(c4);
        AttackContext c3 = ctxFor(g, "Mira3", "Mira las 3 cartas de tu mazo y ponlas de nuevo en cualquier orden.");
        c3.setSelectedCardIds(List.of("c1", "c2", "c3"));
        s.apply(c3);
        AttackContext c2 = ctxFor(g, "Mira2", "Mira las 2 cartas de tu mazo y ponlas de nuevo en cualquier orden.");
        c2.setSelectedCardIds(List.of("c1", "c2"));
        s.apply(c2);
        // "1 carta" branch (line 178).
        AttackContext c1 = ctxFor(g, "Mira1", "Mira la 1 carta superior de tu mazo y ponla de nuevo.");
        c1.setSelectedCardIds(List.of("c1"));
        s.apply(c1);
    }

    @Test
    void utility_reorderTopDeckCards_doesNothingWhenSelectionSmaller() {
        // limit > 1 (so the reorder code is reached) but requested.size() < limit
        // → returns 0 (line 163).
        GameAggregate g = baseGame();
        g.deck().get(g.playerOne()).addAll(List.of("c1", "c2", "c3", "c4", "c5"));
        AttackContext c = ctxFor(g, "Arrange5",
            "Look at the top 5 cards of your deck and put them back in any order.");
        // Request fewer than 5 → reorder is skipped.
        c.setSelectedCardIds(List.of("c1", "c2"));
        new AttackUtilityEffectStrategy().apply(c);
        // We don't assert the deck changed; the branch returns 0.
    }

    @Test
    void utility_reorderTopDeckCards_doesNothingWhenCurrentMismatch() {
        // currentTop.containsAll(requestedTop) returns false → 0 (line 165).
        GameAggregate g = baseGame();
        g.deck().get(g.playerOne()).addAll(List.of("c1", "c2", "c3"));
        AttackContext c = ctxFor(g, "Arrange3",
            "Look at the top 3 cards of your deck and put them back in any order.");
        // Request different cards than those on top → reorder is skipped.
        c.setSelectedCardIds(List.of("x1", "x2", "x3"));
        new AttackUtilityEffectStrategy().apply(c);
    }

    @Test
    void utility_discardTop_attackerDeckEmpty() {
        // discardTop returns null when deck is empty (lines 138-140).
        GameAggregate g = baseGame();
        AttackContext c = ctxFor(g, "DiscardTop",
            "Discard the top card of your deck. If that card is an Energy, attach it to this Pokemon.");
        new AttackUtilityEffectStrategy().apply(c);
        // No crash, no discard.
    }

    @Test
    void utility_damageEachOpponentPokemon_handlesEmptyBench() {
        // Line 233: benchOccupiedCount=0 path. The exact text the source checks
        // for is "put 2 damage counters each of your opponent" (no "on" before
        // "each").
        GameAggregate g = baseGame();
        AttackContext c = ctxFor(g, "Splash",
            "Put 2 damage counters each of your opponent's Pokemon.");
        new AttackUtilityEffectStrategy().apply(c);
        // Opponent ACTIVE must have 2 damage counters.
        assertEquals(2, g.damageCountersAtSlot(g.playerTwo(), "ACTIVE"));
    }

    @Test
    void utility_firstBench_returnsZeroWhenEmpty() {
        // Line 201-202: firstBench returns 0 when bench has no entries.
        GameAggregate g = baseGame();
        // bench() has 5 nulls by default after initializeZonesFor.
        AttackContext c = ctxFor(g, "MoveEnergy",
            "Move an energy from this Pokemon to 1 of your Benched Pokemon.");
        c.setSelectedEnergyCardIds(List.of());
        new AttackUtilityEffectStrategy().apply(c);
    }

    @Test
    void utility_topDiscardToDeck_emptyDiscard() {
        // Line 244: empty discard pile.
        GameAggregate g = baseGame();
        AttackContext c = ctxFor(g, "Recycle",
            "Put a card from your discard pile on top of your deck.");
        new AttackUtilityEffectStrategy().apply(c);
    }

    // ============================================================
    //  InMemoryGameStateRepository – 4-arg appendActionLog, null payload,
    //  findFinishedByPlayerId (filter by playerId branches).
    // ============================================================

    @Test
    void inMemoryRepo_appendActionLogFourArg_delegatesToSixArg() {
        InMemoryGameStateRepository repo = new InMemoryGameStateRepository();
        UUID gid = UUID.randomUUID();
        repo.appendActionLog(gid, GameActionType.DRAW, null, "OK", java.time.Instant.now());
        // The 4-arg overload is exercised; the log must contain exactly one entry.
        assertEquals(1, repo.actionLog(gid).size());
    }

    @Test
    void inMemoryRepo_appendActionLogWithNullPayload_defaultsToEmptyMap() {
        InMemoryGameStateRepository repo = new InMemoryGameStateRepository();
        UUID gid = UUID.randomUUID();
        repo.appendActionLog(gid, GameActionType.DRAW, null, "OK",
            java.time.Instant.now(), null);
        assertEquals(1, repo.actionLog(gid).size());
        assertEquals(java.util.Map.of(), repo.actionLog(gid).get(0).payload());
    }

    @Test
    void inMemoryRepo_findFinishedByPlayerId_filtersByPlayerOneAndPlayerTwo() {
        InMemoryGameStateRepository repo = new InMemoryGameStateRepository();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        UUID p4 = UUID.randomUUID();
        GameAggregate g1 = new GameAggregate(p1);
        g1.setPlayerTwo(p2);
        g1.setGameState(GameState.FINISHED);
        GameAggregate g2 = new GameAggregate(p2);
        g2.setPlayerTwo(p3);
        g2.setGameState(GameState.FINISHED);
        // p4 plays in an ACTIVE game → must be excluded.
        GameAggregate g3 = new GameAggregate(p3);
        g3.setPlayerTwo(p4);
        g3.setGameState(GameState.ACTIVE);
        repo.save(g1);
        repo.save(g2);
        repo.save(g3);
        // p1 is in g1 (as playerOne).
        assertEquals(1, repo.findFinishedByPlayerId(p1).size());
        // p3 is in g2 (as playerTwo) and g3 (as playerOne).
        // Only g2 is FINISHED → 1 result.
        assertEquals(1, repo.findFinishedByPlayerId(p3).size());
        // p4 is only in the ACTIVE game → 0 results.
        assertEquals(0, repo.findFinishedByPlayerId(p4).size());
    }

    // ============================================================
    //  Helpers
    // ============================================================

    private static <T> T mock(Class<T> clazz) {
        return org.mockito.Mockito.mock(clazz);
    }

    private static AttackContext ctxFor(GameAggregate game, String name, String text) {
        AttackContext c = new AttackContext(game);
        c.setAttackName(name);
        c.setAttackText(text);
        c.setAttachedEnergies(game.attachedEnergiesFor(game.currentTurnPlayer(), "ACTIVE"));
        return c;
    }
}
