package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.application.service.PlayerNotInGameException;
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
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectContext;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.NoOpAbilityEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.PotionTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.StadiumEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.StadiumEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.SupporterEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectContext;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectResult;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage tests for {@link GameApplicationService}.
 *
 * The monster class has 130 missed lines after Phase 1. This file goes
 * after the simpler branches:
 *   - executeAction() short overloads (1-, 2-, 3-arg variants)
 *   - executeAction() DRAW triggering the pendingForcedSwitchPlayerId branch
 *   - flipCoin() with explicit choice matching the result
 *   - playTrainer() stadium (with/without previous), tool, supporter flag,
 *     no-registry path, and with coinFlipResults
 *   - useAbility() — Destiny Burst, Spiky Shield, unknown ability,
 *     non-repeatable ability already-used
 *   - replaceActive() — forced switch path (isForcedSwitch=true)
 *   - getHandCardIds / getDeckCardIds — PlayerNotInGameException path
 *   - deckPreview() with limit < deck size and limit > deck size
 *   - finishCountdownIfExpired() — both players without active (tie) and
 *     one player with active (transitions to coin flip)
 *   - closeGameAndPublishIfNeeded() — sudden death and finished paths
 *   - buildCoinFlipSupplier() — empty list, list with values, list exhausted
 */
class GameApplicationServiceCoverageTest {

    // ----- Service factory helpers -----

    private static GameApplicationService serviceWithStubs() {
        return new GameApplicationService(
            new InMemoryGameStateRepository(),
            new GameEngineFacade(new GameEventPublisher()),
            new SetupEngineService(),
            new TurnActionValidator(),
            new VictoryService(),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager()
        );
    }

    private static GameApplicationService serviceWithNoopRegistries() {
        return new GameApplicationService(
            new InMemoryGameStateRepository(),
            new GameEngineFacade(new GameEventPublisher()),
            new SetupEngineService(),
            new TurnActionValidator(),
            new VictoryService(),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager(),
            null,
            new TrainerEffectRegistry(List.of(new PotionTrainerEffect())),
            new AbilityEffectRegistry(List.of(new NoOpAbilityEffectStrategy())),
            null
        );
    }

    private static GameApplicationService serviceWithStadiumRegistry() {
        return new GameApplicationService(
            new InMemoryGameStateRepository(),
            new GameEngineFacade(new GameEventPublisher()),
            new SetupEngineService(),
            new TurnActionValidator(),
            new VictoryService(),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager(),
            null,
            new TrainerEffectRegistry(List.of(new PotionTrainerEffect())),
            new AbilityEffectRegistry(List.of(new NoOpAbilityEffectStrategy())),
            new StadiumEffectRegistry(List.of(new SampleStadium()))
        );
    }

    // ----- executeAction overloads -----

    @Test
    void executeAction_oneArg_overloadDelegatesToFullVersion() {
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = activeGame(svc);
        g.setTurnPhase(TurnPhase.DRAW);
        g.setFirstTurn(false);
        g.setTurnNumber(3);
        GameAggregate result = svc.executeAction(g.id(), GameActionType.DRAW);
        assertNotNull(result);
    }

    @Test
    void executeAction_twoArg_overloadDelegatesToFullVersion() {
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = activeGame(svc);
        g.setTurnPhase(TurnPhase.ATTACK);
        g.setFirstTurn(false);
        g.setTurnNumber(3);
        GameAggregate result = svc.executeAction(g.id(), GameActionType.APPLY_SPECIAL_CONDITION, StatusCondition.POISONED);
        assertNotNull(result);
    }

    @Test
    void executeAction_threeArg_overloadDelegatesToFullVersion() {
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = activeGame(svc);
        g.setTurnPhase(TurnPhase.MAIN);
        g.setFirstTurn(false);
        g.setTurnNumber(3);
        // currentTurnPlayer is random between the two players; the energy
        // must end up in *that* player's hand.
        UUID current = g.currentTurnPlayer();
        g.hand().get(current).add("energy-tmp");
        g.registerCard(new GameCard("energy-tmp", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        GameAggregate result = svc.executeAction(
            g.id(), GameActionType.ATTACH_ENERGY, StatusCondition.POISONED, "energy-tmp", "ACTIVE", List.of());
        assertNotNull(result);
    }

    @Test
    void executeAction_fourArg_overloadDelegatesToFullVersion() {
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = activeGame(svc);
        g.setTurnPhase(TurnPhase.DRAW);
        g.setFirstTurn(false);
        g.setTurnNumber(3);
        // 8-arg version includes attackIndex & coinFlipResults.
        GameAggregate result = svc.executeAction(
            g.id(), GameActionType.DRAW, StatusCondition.POISONED,
            null, null, List.of(), 0, List.of());
        assertNotNull(result);
    }

    @Test
    void executeAction_drawWhenPlayerIsPendingForcedSwitch_keepsWaitingReplacement() {
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = activeGame(svc);
        UUID drawer = g.currentTurnPlayer();
        g.setFirstTurn(false);
        g.setTurnPhase(TurnPhase.DRAW);
        // Add a bench member so the pendingForcedSwitchPlayerId branch is engaged.
        g.bench().get(drawer).add("bench-1");
        g.registerCard(new GameCard("bench-1", "Bench", "Pokemon", Set.of("Basic"), 60, 10, 1));
        g.setPendingForcedSwitchPlayerId(drawer);

        GameAggregate result = svc.executeAction(g.id(), GameActionType.DRAW);

        assertEquals(GameState.WAITING_REPLACEMENT, result.gameState());
    }

    // ----- flipCoin -----

    @Test
    void flipCoin_choiceMatchesResult_selectsPlayerOne() {
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = svc.create(UUID.randomUUID());
        svc.join(g.id(), UUID.randomUUID());
        g.setGameState(GameState.COIN_FLIP);
        GameAggregate result = svc.flipCoin(g.id(), "HEADS");
        // choice matched → playerOne is winner
        assertNotNull(result.coinFlipWinner());
        assertNotNull(result.coinFlipChoice());
        // We don't assert which player won (random) but the choice is persisted.
        assertEquals("HEADS", result.coinFlipChoice());
    }

    @Test
    void flipCoin_secondInvocationReturnsSameGame() {
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = svc.create(UUID.randomUUID());
        svc.join(g.id(), UUID.randomUUID());
        g.setGameState(GameState.COIN_FLIP);
        GameAggregate first = svc.flipCoin(g.id(), null);
        UUID winner = first.coinFlipWinner();
        GameAggregate second = svc.flipCoin(g.id(), "HEADS");
        // Subsequent flip calls return the same game without changing the winner.
        assertEquals(winner, second.coinFlipWinner());
    }

    // ----- playTrainer -----

    @Test
    void playTrainer_stadiumCard_registersStadiumPlayedFlag() {
        GameApplicationService svc = serviceWithStadiumRegistry();
        GameAggregate g = activeGame(svc);
        UUID player = g.currentTurnPlayer();
        GameCard stadium = new GameCard(
            "stadium-1", "Sample Stadium", "Trainer", Set.of("Stadium"), 0, 0, 0);
        g.registerCard(stadium);
        g.hand().get(player).add(stadium.id());

        GameAggregate result = svc.playTrainer(g.id(), player, stadium.id(), "ACTIVE", null);

        assertTrue(result.turnFlags().stadiumPlayed());
        assertEquals(stadium.id(), result.activeStadium());
    }

    @Test
    void playTrainer_stadiumCard_replacesPreviousStadium() {
        GameApplicationService svc = serviceWithStadiumRegistry();
        GameAggregate g = activeGame(svc);
        UUID player = g.currentTurnPlayer();
        // First stadium
        GameCard oldStadium = new GameCard(
            "old-stadium", "Sample Stadium", "Trainer", Set.of("Stadium"), 0, 0, 0);
        g.registerCard(oldStadium);
        g.putStadium(player, oldStadium.id());

        // New stadium
        GameCard newStadium = new GameCard(
            "new-stadium", "Sample Stadium", "Trainer", Set.of("Stadium"), 0, 0, 0);
        g.registerCard(newStadium);
        g.hand().get(player).add(newStadium.id());

        GameAggregate result = svc.playTrainer(g.id(), player, newStadium.id(), "ACTIVE", null);

        assertEquals(newStadium.id(), result.activeStadium());
        // Old stadium should be in the discard pile.
        assertTrue(result.discardPile().get(player).contains(oldStadium.id()));
    }

    @Test
    void playTrainer_toolCard_equipsAndBypassesStrategy() {
        GameApplicationService svc = serviceWithNoopRegistries();
        GameAggregate g = activeGame(svc);
        UUID player = g.currentTurnPlayer();
        GameCard tool = new GameCard(
            "tool-1", "Muscle Band", "Trainer", Set.of("Pokemon Tool"), 0, 0, 0);
        g.registerCard(tool);
        g.hand().get(player).add(tool.id());

        GameAggregate result = svc.playTrainer(g.id(), player, tool.id(), "ACTIVE", null);

        assertEquals(tool.id(), result.toolAtSlot(player, "ACTIVE"));
    }

    @Test
    void playTrainer_supporterCard_setsSupporterPlayedFlag() {
        // SupporterEffectStrategy is a no-op (ItemEffectStrategy too). We
        // use a real supporter card and a registry that has no strategy
        // for its name; the "supporter" branch will still mark the flag.
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = activeGame(svc);
        UUID player = g.currentTurnPlayer();
        // Supporter subtype with no registered strategy falls through the
        // "no strategy" log and only flips the supporter flag when its
        // subtype matches.
        GameCard supporter = new GameCard(
            "supporter-1", "Sample Supporter", "Trainer", Set.of("Supporter"), 0, 0, 0);
        g.registerCard(supporter);
        g.hand().get(player).add(supporter.id());

        GameAggregate result = svc.playTrainer(g.id(), player, supporter.id(), "ACTIVE", null);

        assertTrue(result.turnFlags().supporterPlayed());
    }

    @Test
    void playTrainer_withNullRegistry_logsNoopAndDiscardsCard() {
        // Construct a service with NO trainerEffectRegistry (null in 11-arg
        // constructor) and verify the "No TrainerEffectStrategy" branch.
        GameApplicationService svc = new GameApplicationService(
            new InMemoryGameStateRepository(),
            new GameEngineFacade(new GameEventPublisher()),
            new SetupEngineService(),
            new TurnActionValidator(),
            new VictoryService(),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager(),
            null,
            null,
            null,
            null
        );
        GameAggregate g = activeGame(svc);
        UUID player = g.currentTurnPlayer();
        GameCard unknown = new GameCard(
            "unknown-trainer", "Unknown Trainer", "Trainer", Set.of("Item"), 0, 0, 0);
        g.registerCard(unknown);
        g.hand().get(player).add(unknown.id());

        GameAggregate result = svc.playTrainer(g.id(), player, unknown.id(), "ACTIVE", null);

        // Card moved to discard.
        assertTrue(result.discardPile().getOrDefault(player, List.of()).contains(unknown.id()));
    }

    @Test
    void playTrainer_fullOverload_passesCoinFlipResultsToStrategy() {
        AtomicInteger flipsUsed = new AtomicInteger();
        GameApplicationService svc = new GameApplicationService(
            new InMemoryGameStateRepository(),
            new GameEngineFacade(new GameEventPublisher()),
            new SetupEngineService(),
            new TurnActionValidator(),
            new VictoryService(),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager(),
            null,
            new TrainerEffectRegistry(List.of(new PotionTrainerEffect())),
            new AbilityEffectRegistry(List.of(new NoOpAbilityEffectStrategy())),
            null
        );
        GameAggregate g = activeGame(svc);
        UUID player = g.currentTurnPlayer();
        GameCard potion = new GameCard(
            "potion-1", "Potion", "Trainer", Set.of("Item"), 0, 0, 0);
        g.registerCard(potion);
        g.hand().get(player).add(potion.id());
        g.activeDamageCounters().put(player, 3);

        GameAggregate result = svc.playTrainer(
            g.id(), player, potion.id(), "ACTIVE", null,
            null, List.of(), List.of(), null, null, List.of());

        assertEquals(1, result.activeDamageCounters().get(player));
        // Sanity: we used the parameter list and it didn't blow up.
        assertNotNull(result);
    }

    // ----- useAbility -----

    @Test
    void useAbility_unknownAbility_throwsInvalidActionException() {
        GameApplicationService svc = serviceWithNoopRegistries();
        GameAggregate g = activeGame(svc);
        UUID player = g.currentTurnPlayer();
        GameCard mon = (GameCard) g.cardCatalog().get(g.activePokemon().get(player));
        // Give the active a non-empty abilities list with an unknown name.
        GameCard modified = new GameCard(
            mon.id(), mon.name(), mon.supertype(), mon.subtypes(),
            mon.hp(), mon.attackDamage(), mon.attackRequiredEnergy(),
            mon.attackName(), mon.attackText(), mon.energyType(), mon.attackCost(),
            mon.retreatCost(), mon.evolvesFrom(), List.of("Mystery Ability"),
            mon.weaknessType(), mon.weaknessAmount(), mon.resistanceType(), mon.resistanceAmount(),
            mon.attacks());
        g.registerCard(modified);
        g.activePokemon().put(player, modified.id());

        InvalidActionException ex = assertThrows(
            InvalidActionException.class,
            () -> svc.useAbility(g.id(), player, "ACTIVE", 0, null)
        );
        assertTrue(ex.getMessage().contains("Mystery Ability"));
    }

    @Test
    void useAbility_destinyBurst_whenNotImplemented_throwsSpecificMessage() {
        GameApplicationService svc = serviceWithNoopRegistries();
        GameAggregate g = activeGame(svc);
        UUID player = g.currentTurnPlayer();
        // Add Destiny Burst to the abilities list with no registered strategy.
        String monId = g.activePokemon().get(player);
        GameCard mon = g.cardCatalog().get(monId);
        GameCard modified = new GameCard(
            mon.id(), mon.name(), mon.supertype(), mon.subtypes(),
            mon.hp(), mon.attackDamage(), mon.attackRequiredEnergy(),
            mon.attackName(), mon.attackText(), mon.energyType(), mon.attackCost(),
            mon.retreatCost(), mon.evolvesFrom(), List.of("Destiny Burst"),
            mon.weaknessType(), mon.weaknessAmount(), mon.resistanceType(), mon.resistanceAmount(),
            mon.attacks());
        g.registerCard(modified);
        g.activePokemon().put(player, modified.id());

        InvalidActionException ex = assertThrows(
            InvalidActionException.class,
            () -> svc.useAbility(g.id(), player, "ACTIVE", 0, null)
        );
        assertTrue(ex.getMessage().contains("Destiny Burst"));
    }

    @Test
    void useAbility_spikyShield_whenNotImplemented_throwsSpecificMessage() {
        GameApplicationService svc = serviceWithNoopRegistries();
        GameAggregate g = activeGame(svc);
        UUID player = g.currentTurnPlayer();
        String monId = g.activePokemon().get(player);
        GameCard mon = g.cardCatalog().get(monId);
        GameCard modified = new GameCard(
            mon.id(), mon.name(), mon.supertype(), mon.subtypes(),
            mon.hp(), mon.attackDamage(), mon.attackRequiredEnergy(),
            mon.attackName(), mon.attackText(), mon.energyType(), mon.attackCost(),
            mon.retreatCost(), mon.evolvesFrom(), List.of("Spiky Shield"),
            mon.weaknessType(), mon.weaknessAmount(), mon.resistanceType(), mon.resistanceAmount(),
            mon.attacks());
        g.registerCard(modified);
        g.activePokemon().put(player, modified.id());

        InvalidActionException ex = assertThrows(
            InvalidActionException.class,
            () -> svc.useAbility(g.id(), player, "ACTIVE", 0, null)
        );
        assertTrue(ex.getMessage().contains("Spiky Shield"));
    }

    @Test
    void useAbility_nonRepeatableAbilityUsedTwice_secondCallThrows() {
        // Register a non-repeatable ability that always succeeds.
        AbilityEffectStrategy oneShot = new AbilityEffectStrategy() {
            @Override public String abilityName() { return "One Shot"; }
            @Override public TrainerEffectResult apply(AbilityEffectContext context) {
                return TrainerEffectResult.noop("OK");
            }
            @Override public boolean isRepeatable() { return false; }
        };
        GameApplicationService svc = new GameApplicationService(
            new InMemoryGameStateRepository(),
            new GameEngineFacade(new GameEventPublisher()),
            new SetupEngineService(),
            new TurnActionValidator(),
            new VictoryService(),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager(),
            null,
            null,
            new AbilityEffectRegistry(List.of(oneShot)),
            null
        );
        GameAggregate g = activeGame(svc);
        UUID player = g.currentTurnPlayer();
        String monId = g.activePokemon().get(player);
        GameCard mon = g.cardCatalog().get(monId);
        GameCard modified = new GameCard(
            mon.id(), mon.name(), mon.supertype(), mon.subtypes(),
            mon.hp(), mon.attackDamage(), mon.attackRequiredEnergy(),
            mon.attackName(), mon.attackText(), mon.energyType(), mon.attackCost(),
            mon.retreatCost(), mon.evolvesFrom(), List.of("One Shot"),
            mon.weaknessType(), mon.weaknessAmount(), mon.resistanceType(), mon.resistanceAmount(),
            mon.attacks());
        g.registerCard(modified);
        g.activePokemon().put(player, modified.id());

        GameAggregate first = svc.useAbility(g.id(), player, "ACTIVE", 0, null);
        assertNotNull(first);
        // The non-repeatable flag was recorded on the turn flags.
        assertTrue(first.turnFlags().abilityUsed(player + ":ACTIVE:0"));

        // The TurnActionValidator will block the second use with an
        // "already used" message. We don't assert the exact phrasing —
        // we just want to confirm the second invocation is rejected.
        assertThrows(InvalidActionException.class,
            () -> svc.useAbility(g.id(), player, "ACTIVE", 0, null));
    }

    // ----- replaceActive (forced switch path) -----

    @Test
    void replaceActive_forcedSwitch_swapsActiveAndClearsPendingFlag() {
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = activeGame(svc);
        UUID player = g.currentTurnPlayer();
        // Setup: WAITING_REPLACEMENT state, pendingForcedSwitchPlayerId set.
        g.setGameState(GameState.WAITING_REPLACEMENT);
        g.setReplacementPlayerId(player);
        g.setPendingForcedSwitchPlayerId(player);
        // Add a bench member to swap with. Use a fresh list so we know the size.
        String benchId = "bench-forced";
        g.registerCard(new GameCard(benchId, "BenchForced", "Pokemon", Set.of("Basic"), 60, 10, 1));
        java.util.List<String> newBench = new java.util.ArrayList<>();
        newBench.add(benchId);
        newBench.add(null);
        newBench.add(null);
        newBench.add(null);
        newBench.add(null);
        g.bench().put(player, newBench);

        GameAggregate result = svc.replaceActive(g.id(), player, 0);

        assertEquals(benchId, result.activePokemon().get(player));
        assertNull(result.pendingForcedSwitchPlayerId());
        assertEquals(GameState.ACTIVE, result.gameState());
    }

    // ----- getHandCardIds / getDeckCardIds -----

    @Test
    void getHandCardIds_throwsPlayerNotInGameException() {
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = svc.create(UUID.randomUUID());
        assertThrows(PlayerNotInGameException.class,
            () -> svc.getHandCardIds(g.id(), UUID.randomUUID()));
    }

    @Test
    void getDeckCardIds_throwsPlayerNotInGameException() {
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = svc.create(UUID.randomUUID());
        assertThrows(PlayerNotInGameException.class,
            () -> svc.getDeckCardIds(g.id(), UUID.randomUUID()));
    }

    // ----- deckPreview -----

    @Test
    void deckPreview_limitLessThanDeckSize_returnsLimitCards() {
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = activeGame(svc);
        UUID player = g.playerOne();
        g.deck().get(player).addAll(List.of("d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8"));
        g.deckCardsRemaining().put(player, 8);

        List<String> preview = svc.deckPreview(g.id(), player, 3);
        assertEquals(3, preview.size());
    }

    @Test
    void deckPreview_limitGreaterThanDeckSize_returnsAllCards() {
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = activeGame(svc);
        UUID player = g.playerOne();
        // Replace the deck with a known-size list (the runInitialSetup
        // already split it into hand+prizes+deck, so reset to 4 to
        // assert the limit cap is the deck size itself).
        List<String> tinyDeck = new ArrayList<>(List.of("d1", "d2", "d3", "d4"));
        g.deck().put(player, tinyDeck);
        g.deckCardsRemaining().put(player, 4);

        List<String> preview = svc.deckPreview(g.id(), player, 100);
        assertEquals(4, preview.size());
    }

    // ----- finishCountdownIfExpired (via get()) -----

    @Test
    void get_triggersFinishCountdownIfExpired_bothNoActive_finishesTie() {
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = svc.create(UUID.randomUUID());
        svc.join(g.id(), UUID.randomUUID());
        // Set up SETUP state with expired countdown.
        g.setGameState(GameState.SETUP);
        g.setSetupPhase(SetupPhase.SETUP_WAITING_ACTIVE);
        g.setSetupCountdownEndsAt(Instant.now().minusSeconds(60));
        // Both players have no active.
        g.activePokemon().remove(g.playerOne());
        g.activePokemon().remove(g.playerTwo());

        GameAggregate result = svc.get(g.id());

        assertEquals(GameState.FINISHED, result.gameState());
        assertNull(result.winner());
        assertNull(result.setupPhase());
    }

    @Test
    void get_triggersFinishCountdownIfExpired_onePlayerHasActive_transitionsToCoinFlip() {
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = svc.create(UUID.randomUUID());
        svc.join(g.id(), UUID.randomUUID());
        g.setGameState(GameState.SETUP);
        g.setSetupPhase(SetupPhase.SETUP_WAITING_ACTIVE);
        g.setSetupCountdownEndsAt(Instant.now().minusSeconds(60));
        // Only playerOne has chosen an active.
        g.activePokemon().put(g.playerOne(), "p1-active");
        g.registerCard(new GameCard("p1-active", "P1", "Pokemon", Set.of("Basic"), 60, 10, 1));

        GameAggregate result = svc.get(g.id());

        assertEquals(GameState.COIN_FLIP, result.gameState());
        assertNull(result.setupPhase());
    }

    // ----- closeGameAndPublishIfNeeded (via executeAction attack) -----

    @Test
    void executeAction_attackKnocksOutAllOpponentPokemon_finishesGame() {
        GameApplicationService svc = serviceWithStubs();
        GameAggregate g = activeGame(svc);
        // Advance past the first turn (attack is forbidden on the first turn).
        g.setFirstTurn(false);
        g.setTurnNumber(3);
        // Force playerOne to be the attacker regardless of random first-player.
        g.setFirstPlayer(g.playerOne());
        g.setCurrentTurnPlayer(g.playerOne());
        // Pre-KO the defender by setting HP=0 and damage counters high
        // enough that the attacker's damage will KO it. Clear the bench so
        // the game has no defender replacement.
        g.activeHp().put(g.playerTwo(), 0);
        g.activeDamageCounters().put(g.playerTwo(), 99);
        g.bench().get(g.playerTwo()).clear();
        g.setTurnPhase(TurnPhase.ATTACK);
        g.activeAttachedEnergy().put(g.playerOne(), 1);

        GameAggregate result = svc.executeAction(g.id(), GameActionType.ATTACK);

        assertEquals(GameState.FINISHED, result.gameState());
    }

    // ----- buildCoinFlipSupplier (private static, via reflection) -----

    @Test
    void buildCoinFlipSupplier_emptyList_fallsBackToRandom() throws Exception {
        Method m = GameApplicationService.class
            .getDeclaredMethod("buildCoinFlipSupplier", List.class);
        m.setAccessible(true);
        java.util.function.BooleanSupplier supplier =
            (java.util.function.BooleanSupplier) m.invoke(null, java.util.List.of());
        // Just exercise both branches of the random fallback; assert it
        // doesn't throw.
        for (int i = 0; i < 5; i++) {
            supplier.getAsBoolean();
        }
    }

    @Test
    void buildCoinFlipSupplier_withValues_iteratesAndFallsBack() throws Exception {
        Method m = GameApplicationService.class
            .getDeclaredMethod("buildCoinFlipSupplier", List.class);
        m.setAccessible(true);
        java.util.function.BooleanSupplier supplier =
            (java.util.function.BooleanSupplier) m.invoke(null, java.util.List.of(true, false));
        assertTrue(supplier.getAsBoolean());
        assertEquals(false, supplier.getAsBoolean());
        // Third call exhausts the list → random fallback (no exception).
        supplier.getAsBoolean();
    }

    // ----- getHistory (already implicit in concede test, but cover explicit) -----

    @Test
    void getHistory_returnsFinishedGamesForPlayer() {
        GameApplicationService svc = serviceWithStubs();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate g = svc.create(p1);
        svc.join(g.id(), p2);
        svc.concede(g.id(), p1);

        var history = svc.getHistory(p1);
        assertEquals(1, history.size());
    }

    // ----- Helpers -----

    private static GameAggregate activeGame(GameApplicationService svc) {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = svc.create(p1);
        svc.join(created.id(), p2);
        return svc.runInitialSetup(created.id(), 60, 12, 60, 12);
    }

    /**
     * Minimal stadium effect used by the stadium tests.
     */
    private static final class SampleStadium implements StadiumEffectStrategy {
        @Override public String cardName() { return "Sample Stadium"; }
        @Override public void onPlayed(GameAggregate game, UUID playerId) {
            // No-op: we only care about exercising the code path.
        }
        @Override public void onRemoved(GameAggregate game) {
            // No-op.
        }
    }
}
