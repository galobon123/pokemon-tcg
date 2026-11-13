package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.application.service.GameNotFoundException;
import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.application.service.SetupEngineService;
import com.utn.pokemontcg.game.application.service.TurnActionValidator;
import com.utn.pokemontcg.game.application.service.VictoryService;
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
import com.utn.pokemontcg.game.domain.model.SetupPhase;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.AttackEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.AttackUtilityEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.NoOpAbilityEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.PotionTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectRegistry;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckCardInput;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Third batch of coverage tests for {@link GameApplicationService}.
 *
 * Targets the remaining high-value uncovered methods:
 *   - revealedCards (private, called from publishAttackResolved) — needs an
 *     attack that searches the deck and reveals a card.
 *   - rawCardId / setId (private) — called from revealedCards.
 *   - finishReason (private) — exercised via game finish paths.
 *   - registerRequestedDeckIds (private) — exercised via startInteractiveSetup.
 *   - realDeckCardsFor (private) — exercised via startInteractiveSetup with
 *     mock deck ids and DeckService.
 */
class GameApplicationServiceCoverageTest3 {

    private static GameApplicationService serviceWithDeckService(
        com.utn.pokemontcg.game.application.service.deck.DeckService deckService) {
        return new GameApplicationService(
            new InMemoryGameStateRepository(),
            new GameEngineFacade(
                new GameEventPublisher(),
                new AttackEffectRegistry(List.of(new AttackUtilityEffectStrategy())),
                null
            ),
            new SetupEngineService(),
            new TurnActionValidator(),
            new VictoryService(),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager(),
            deckService,
            new TrainerEffectRegistry(List.of(new PotionTrainerEffect())),
            new AbilityEffectRegistry(List.of(new NoOpAbilityEffectStrategy())),
            null
        );
    }

    private static GameApplicationService service() {
        return serviceWithDeckService(null);
    }

    private static GameAggregate activeGame(GameApplicationService svc) {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = svc.create(p1);
        svc.join(created.id(), p2);
        return svc.runInitialSetup(created.id(), 60, 12, 60, 12);
    }

    // ----- revealedCards (via attack that searches deck) -----

    @Test
    void executeAction_attackWithDeckSearch_revealsCard() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        UUID attacker = g.playerOne();
        UUID defender = g.playerTwo();
        g.setFirstPlayer(attacker);
        g.setCurrentTurnPlayer(attacker);
        g.setFirstTurn(false);
        g.setTurnNumber(3);

        // Replace the attacker with a custom card that searches the deck.
        String attackerId = "searcher-" + attacker;
        g.registerCard(new GameCard(attackerId, "Searcher", "Pokemon", Set.of("Basic"),
            80, 10, 1, "Fetch",
            "Search your deck for a Pokemon and put it into your hand. Shuffle your deck.",
            "Colorless", List.of("Colorless"), 1, null, List.of()));
        g.activePokemon().put(attacker, attackerId);
        // Add a Pokemon to the deck to be revealed.
        String searchTarget = "search-target-" + attacker;
        g.registerCard(new GameCard(searchTarget, "Target", "Pokemon", Set.of("Basic"), 60, 10, 1));
        g.deck().get(attacker).add(0, searchTarget);
        g.deckCardsRemaining().put(attacker, g.deck().get(attacker).size());
        g.attachEnergy(attacker, "ACTIVE", new AttachedEnergy("e-search", "Colorless"));
        g.setTurnPhase(TurnPhase.ATTACK);

        GameAggregate result = svc.executeAction(g.id(), GameActionType.ATTACK,
            null, null, null, List.of(),
            0, List.of(), null, List.of(searchTarget), List.of(),
            null, null, null);

        assertNotNull(result);
        // The hand should contain the searched target.
        assertTrue(result.hand().get(attacker).contains(searchTarget));
    }

    // ----- rawCardId / setId -----

    @Test
    void executeAction_attackWithDeckSearch_exercisesRawCardIdAndSetId() {
        // Same as above but with a cardId that contains ":" and "-" so the
        // private rawCardId / setId branches are exercised.
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        UUID attacker = g.playerOne();
        g.setFirstPlayer(attacker);
        g.setCurrentTurnPlayer(attacker);
        g.setFirstTurn(false);
        g.setTurnNumber(3);

        String attackerId = "raw-attacker-" + attacker;
        g.registerCard(new GameCard(attackerId, "RawAttacker", "Pokemon", Set.of("Basic"),
            80, 10, 1, "Fetch",
            "Search your deck for a Pokemon and put it into your hand. Shuffle your deck.",
            "Colorless", List.of("Colorless"), 1, null, List.of()));
        g.activePokemon().put(attacker, attackerId);

        // Card id with the "raw:id" + "set-name" structure that
        // rawCardId / setId split apart.
        String target = "xy1-3:my-player-id:0";
        g.registerCard(new GameCard(target, "SplitTarget", "Pokemon", Set.of("Basic"), 60, 10, 1));
        g.deck().get(attacker).add(0, target);
        g.deckCardsRemaining().put(attacker, g.deck().get(attacker).size());
        g.attachEnergy(attacker, "ACTIVE", new AttachedEnergy("e-raw", "Colorless"));
        g.setTurnPhase(TurnPhase.ATTACK);

        GameAggregate result = svc.executeAction(g.id(), GameActionType.ATTACK,
            null, null, null, List.of(),
            0, List.of(), null, List.of(target), List.of(),
            null, null, null);

        assertTrue(result.hand().get(attacker).contains(target));
    }

    // ----- startInteractiveSetup with real deck ids -----

    @Test
    void startInteractiveSetup_withDeckService_usesRealDeckCards() {
        com.utn.pokemontcg.game.application.service.deck.DeckService deckService =
            mock(com.utn.pokemontcg.game.application.service.deck.DeckService.class);
        GameApplicationService svc = serviceWithDeckService(deckService);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p1Deck = UUID.randomUUID();
        UUID p2Deck = UUID.randomUUID();
        GameAggregate created = svc.create(p1, p1Deck);
        svc.join(created.id(), p2, p2Deck);

        DeckResponse deck1 = buildDeck(p1Deck, p1);
        DeckResponse deck2 = buildDeck(p2Deck, p2);
        when(deckService.getEnrichedForGame(p1, p1Deck)).thenReturn(deck1);
        when(deckService.getEnrichedForGame(p2, p2Deck)).thenReturn(deck2);

        GameAggregate result = svc.startInteractiveSetup(created.id(),
            null, null, null, null, p1Deck, p2Deck);

        assertEquals(GameState.SETUP, result.gameState());
        assertEquals(SetupPhase.SETUP_WAITING_ACTIVE, result.setupPhase());
        // The active setup state should be recorded for both players.
        assertNotNull(result.setupByPlayer().get(p1));
        assertNotNull(result.setupByPlayer().get(p2));
    }

    // ----- placeSetupBench happy path -----

    @Test
    void placeSetupBench_succeedsAfterActiveIsPlaced() {
        GameApplicationService svc = service();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = svc.create(p1);
        svc.join(created.id(), p2);
        // Use a manual setup so we stay in SETUP state.
        GameAggregate seeded = svc.get(created.id());
        seeded.setGameState(GameState.SETUP);
        seeded.setSetupPhase(SetupPhase.SETUP_WAITING_ACTIVE);
        seeded.setSetupCountdownEndsAt(Instant.now().plusSeconds(60));
        seeded.initializeZonesFor(p1, new ArrayList<>());
        seeded.initializeZonesFor(p2, new ArrayList<>());

        // Add a basic card to p1's hand for active.
        GameCard basic = new GameCard("bench-basic-1", "BenchBasic", "Pokemon", Set.of("Basic"), 60, 10, 1);
        seeded.registerCard(basic);
        seeded.hand().get(p1).add("bench-basic-1");
        // Place active first.
        svc.placeSetupActive(created.id(), p1, "bench-basic-1");
        // Add another basic for the bench.
        GameCard bench = new GameCard("bench-mon-1", "BenchMon", "Pokemon", Set.of("Basic"), 60, 10, 1);
        seeded.registerCard(bench);
        seeded.hand().get(p1).add("bench-mon-1");

        GameAggregate result = svc.placeSetupBench(created.id(), p1, List.of("bench-mon-1"));

        assertNotNull(result.bench().get(p1));
        assertTrue(result.bench().get(p1).contains("bench-mon-1"));
    }

    // ----- placeSetupBench with too many bench pokemon throws -----

    @Test
    void placeSetupBench_throwsWhenBenchFull() {
        GameApplicationService svc = service();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = svc.create(p1);
        svc.join(created.id(), p2);
        GameAggregate seeded = svc.get(created.id());
        seeded.setGameState(GameState.SETUP);
        seeded.setSetupPhase(SetupPhase.SETUP_WAITING_ACTIVE);
        seeded.setSetupCountdownEndsAt(Instant.now().plusSeconds(60));
        seeded.initializeZonesFor(p1, new ArrayList<>());
        seeded.initializeZonesFor(p2, new ArrayList<>());

        // Add 7 basics to the hand: 1 active + 6 bench (max bench is 5).
        List<String> basics = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String id = "bench-full-" + i;
            seeded.registerCard(new GameCard(id, "BenchFull" + i, "Pokemon", Set.of("Basic"), 60, 10, 1));
            seeded.hand().get(p1).add(id);
            basics.add(id);
        }
        // First place an active.
        svc.placeSetupActive(created.id(), p1, basics.get(0));
        // Try to bench 6 more — should overflow.
        assertThrows(InvalidActionException.class,
            () -> svc.placeSetupBench(created.id(), p1, basics.subList(1, 7)));
    }

    // ----- getDeckCardIds, getHandCardIds: success paths -----

    @Test
    void getDeckCardIds_returnsDeckForValidPlayer() {
        GameApplicationService svc = service();
        GameAggregate g = svc.create(UUID.randomUUID());
        g.deck().put(g.playerOne(), new ArrayList<>(List.of("a", "b", "c")));
        g.initializeZonesFor(g.playerOne(), List.of("a", "b", "c"));

        List<String> ids = svc.getDeckCardIds(g.id(), g.playerOne());
        assertEquals(List.of("a", "b", "c"), ids);
    }

    // ----- getHandCardIds throws for wrong game -----

    @Test
    void getHandCardIds_throwsWhenGameMissing() {
        GameApplicationService svc = service();
        assertThrows(GameNotFoundException.class,
            () -> svc.getHandCardIds(UUID.randomUUID(), UUID.randomUUID()));
    }

    // ----- startInteractiveSetup uses manual size parameters -----

    @Test
    void startInteractiveSetup_usesManualSizeParametersWhenNoDeck() {
        GameApplicationService svc = service();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = svc.create(p1);
        svc.join(created.id(), p2);

        GameAggregate result = svc.startInteractiveSetup(created.id(),
            20, 6, 20, 6, null, null);

        assertEquals(GameState.SETUP, result.gameState());
        assertEquals(SetupPhase.SETUP_WAITING_ACTIVE, result.setupPhase());
    }

    // ----- deckPreview returns cards in order -----

    @Test
    void deckPreview_returnsCardsInOrder() {
        GameApplicationService svc = service();
        GameAggregate g = svc.create(UUID.randomUUID());
        // Initialize the deck with a known order.
        List<String> order = List.of("first", "second", "third");
        g.deck().put(g.playerOne(), new ArrayList<>(order));
        g.initializeZonesFor(g.playerOne(), order);
        g.deckCardsRemaining().put(g.playerOne(), 3);

        List<String> preview = svc.deckPreview(g.id(), g.playerOne(), 2);
        assertEquals(List.of("first", "second"), preview);
    }

    // ----- deckPreview with empty deck -----

    @Test
    void deckPreview_withEmptyDeck_returnsEmptyList() {
        GameApplicationService svc = service();
        GameAggregate g = svc.create(UUID.randomUUID());
        g.deck().put(g.playerOne(), new ArrayList<>());
        g.initializeZonesFor(g.playerOne(), List.of());

        List<String> preview = svc.deckPreview(g.id(), g.playerOne(), 5);
        assertTrue(preview.isEmpty());
    }

    // ----- Helper -----

    private static DeckResponse buildDeck(UUID deckId, UUID playerId) {
        return new DeckResponse(
            deckId,
            playerId,
            "Mazo real",
            List.of(
                new DeckCardInput(
                    "xy1-1",
                    "Basic Real",
                    "xy1",
                    8,
                    "Pokemon",
                    "Basic",
                    false,
                    true,
                    false,
                    120,
                    30,
                    1
                ),
                new DeckCardInput(
                    "xy1-2",
                    "Basic Energy",
                    "xy1",
                    52,
                    "Energy",
                    "Basic",
                    true,
                    false,
                    false,
                    null,
                    null,
                    null
                )
            ),
            new DeckValidationResult(true, List.of())
        );
    }
}
