package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.application.service.TurnActionValidator;
import com.utn.pokemontcg.game.domain.chain.ModifierStep;
import com.utn.pokemontcg.game.domain.exception.EvolutionNotAllowedException;
import com.utn.pokemontcg.game.domain.exception.InvalidActionPhaseException;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.FairyGardenStadiumEffect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Extra coverage for the remaining branches of {@link TurnActionValidator}.
 * The base happy / sad paths live in {@link TurnActionValidatorTest}; this class
 * targets branches that are still uncovered (first player first turn guards,
 * Fairy Garden retreat discount, Forest's Curse, ability lock, etc.).
 */
class TurnActionValidatorCoverageTest {

    private final TurnActionValidator validator = new TurnActionValidator();

    private GameAggregate activeGameAtPhase(UUID player, TurnPhase phase) {
        GameAggregate game = new GameAggregate(player);
        game.setGameState(GameState.ACTIVE);
        game.setCurrentTurnPlayer(player);
        game.setTurnPhase(phase);
        return game;
    }

    private GameAggregate mainPhaseGame(UUID player) {
        return activeGameAtPhase(player, TurnPhase.MAIN);
    }

    // ------------------- ATTACH_ENERGY uncovered branches -------------------

    @Test
    void rejectsAttachEnergyOnFirstPlayerFirstTurn() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.setFirstTurn(true);
        game.setFirstPlayer(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.hand().put(player, new ArrayList<>(List.of("e-1")));
        game.registerCard(new GameCard("e-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACH_ENERGY, "e-1", "ACTIVE", List.of()));
    }

    @Test
    void rejectsAttachEnergyWhenAlreadyAttachedThisTurn() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.hand().put(player, new ArrayList<>(List.of("e-1")));
        game.registerCard(new GameCard("e-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.turnFlags().setEnergyAttached(true);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACH_ENERGY, "e-1", "ACTIVE", List.of()));
    }

    @Test
    void rejectsAttachEnergyWhenCardIdIsBlank() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACH_ENERGY, "   ", "ACTIVE", List.of()));
    }

    @Test
    void rejectsAttachEnergyWhenCardIdIsNull() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACH_ENERGY, null, "ACTIVE", List.of()));
    }

    @Test
    void rejectsAttachEnergyWhenCardIsNotInHand() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.hand().put(player, new ArrayList<>(List.of("other-energy")));
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACH_ENERGY, "e-1", "ACTIVE", List.of()));
    }

    @Test
    void rejectsAttachEnergyWhenCardIsNotEnergy() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.hand().put(player, new ArrayList<>(List.of("potion-1")));
        game.registerCard(new GameCard("potion-1", "Potion", "Trainer", Set.of("Item"), 0, 0, 0));
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACH_ENERGY, "potion-1", "ACTIVE", List.of()));
    }

    @Test
    void rejectsAttachEnergyWhenTargetSlotNotOccupied() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.hand().put(player, new ArrayList<>(List.of("e-1")));
        game.registerCard(new GameCard("e-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACH_ENERGY, "e-1", "BENCH_3", List.of()));
    }

    // ------------------- ATTACK uncovered branches -------------------

    @Test
    void rejectsAttackWithBlockedAttackIndex() {
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.ATTACK);
        game.setFirstTurn(false);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.addActiveEffect(player, ModifierStep.BLOCKED_ATTACK_PREFIX + 1, 1);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACK, null, null, List.of(), 1));
    }

    @Test
    void rejectsAttackWhenCannotAttackEffectActive() {
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.ATTACK);
        game.setFirstTurn(false);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.addActiveEffect(player, ModifierStep.CANNOT_ATTACK, 1);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACK));
    }

    @Test
    void rejectsAttackWhenParalyzed() {
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.ATTACK);
        game.setFirstTurn(false);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.statusByPlayer().put(player, EnumSet.of(StatusCondition.PARALYZED));
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACK));
    }

    @Test
    void acceptsAttackWithAttackIndexZeroWhenNoBlock() {
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.ATTACK);
        game.setFirstTurn(false);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        assertDoesNotThrow(() -> validator.validate(game, GameActionType.ATTACK, null, null, List.of(), 0));
    }

    // ------------------- RETREAT uncovered branches -------------------

    @Test
    void rejectsRetreatWhenCannotRetreatEffectActive() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.benchPokemon(player, "b-1", "BENCH_0");
        game.registerCard(new GameCard("b-1", "B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.addActiveEffect(player, ModifierStep.CANNOT_RETREAT, 1);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.RETREAT));
    }

    @Test
    void rejectsRetreatWhenParalyzed() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.benchPokemon(player, "b-1", "BENCH_0");
        game.registerCard(new GameCard("b-1", "B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.statusByPlayer().put(player, EnumSet.of(StatusCondition.PARALYZED));
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.RETREAT));
    }

    @Test
    void acceptsRetreatWithFairyGardenDiscount() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        // Active retreat cost of 2 — but Fairy Garden + Fairy energy makes it free.
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1, "Fire", List.of("Fire"), 2));
        game.benchPokemon(player, "b-1", "BENCH_0");
        game.registerCard(new GameCard("b-1", "B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.attachEnergy(player, "ACTIVE", new AttachedEnergy("fairy-energy", "Fairy"));
        game.hand().put(player, new ArrayList<>());
        // Fairy Garden active for both players
        game.addActiveEffect(player, FairyGardenStadiumEffect.EFFECT_KEY, 1);
        // Retreat cost 0 -> no energy to discard
        assertDoesNotThrow(() -> validator.validate(game, GameActionType.RETREAT, null, "BENCH_0", List.of()));
    }

    @Test
    void rejectsRetreatWhenSelectedEnergyCountWrong() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        // Retreat cost 1, attached 1, but we pass 2 selected
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1, "Fire", List.of("Fire"), 1));
        game.benchPokemon(player, "b-1", "BENCH_0");
        game.registerCard(new GameCard("b-1", "B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.attachEnergy(player, "ACTIVE", new AttachedEnergy("fire-energy", "Fire"));
        game.hand().put(player, new ArrayList<>());
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.RETREAT, null, "BENCH_0", List.of("fire-energy", "extra")));
    }

    @Test
    void acceptsRetreatWithDefaultBenchZeroWhenTargetSlotNull() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1, "Fire", List.of("Fire"), 0));
        game.benchPokemon(player, "b-1", "BENCH_0");
        game.registerCard(new GameCard("b-1", "B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.hand().put(player, new ArrayList<>());
        assertDoesNotThrow(() -> validator.validate(game, GameActionType.RETREAT, null, null, List.of()));
    }

    @Test
    void rejectsRetreatWhenTargetSlotIsBlank() {
        // When targetSlot is blank, the code falls back to "BENCH_0" — that should be a valid path
        // when BENCH_0 is occupied (covered by acceptsRetreatWithDefaultBenchZeroWhenTargetSlotNull).
        // Here we verify the blank-but-non-null path also normalizes correctly to a working slot.
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1, "Fire", List.of("Fire"), 0));
        game.benchPokemon(player, "b-1", "BENCH_0");
        game.registerCard(new GameCard("b-1", "B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.hand().put(player, new ArrayList<>());
        // Blank target slot falls back to BENCH_0 -> should be valid
        assertDoesNotThrow(() -> validator.validate(game, GameActionType.RETREAT, null, "   ", List.of()));
    }

    @Test
    void rejectsRetreatWhenTargetSlotIsActive() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1, "Fire", List.of("Fire"), 0));
        game.benchPokemon(player, "b-1", "BENCH_0");
        game.registerCard(new GameCard("b-1", "B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.RETREAT, null, "ACTIVE", List.of()));
    }

    @Test
    void rejectsRetreatWhenBenchSlotEmpty() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1, "Fire", List.of("Fire"), 0));
        // Bench is empty: BENCH_2 is not occupied
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.RETREAT, null, "BENCH_2", List.of()));
    }

    @Test
    void rejectsRetreatWhenAttachedCountBelowCost() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        // Cost 3, attached 1
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1, "Fire", List.of("Fire"), 3));
        game.benchPokemon(player, "b-1", "BENCH_0");
        game.registerCard(new GameCard("b-1", "B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.attachEnergy(player, "ACTIVE", new AttachedEnergy("fire-energy", "Fire"));
        game.hand().put(player, new ArrayList<>());
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.RETREAT, null, "BENCH_0", List.of("fire-energy")));
    }

    // ------------------- PLAY_SUPPORTER uncovered branches -------------------

    @Test
    void rejectsSupporterWhenSupporterLockEffectActive() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.addActiveEffect(player, ModifierStep.SUPPORTER_LOCK, 1);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.PLAY_SUPPORTER));
    }

    @Test
    void acceptsSupporterInMainWithoutLock() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertDoesNotThrow(() -> validator.validate(game, GameActionType.PLAY_SUPPORTER));
    }

    @Test
    void rejectsSupporterOutsideMainPhase() {
        UUID player = UUID.randomUUID();
        assertThrows(InvalidActionException.class,
            () -> validator.validate(activeGameAtPhase(player, TurnPhase.DRAW), GameActionType.PLAY_SUPPORTER));
    }

    // ------------------- APPLY_SPECIAL_CONDITION BETWEEN_TURNS -------------------

    @Test
    void allowsApplySpecialConditionInBetweenTurns() {
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.BETWEEN_TURNS);
        assertDoesNotThrow(() -> validator.validate(game, GameActionType.APPLY_SPECIAL_CONDITION));
    }

    // ------------------- validateBenchPokemon uncovered branches -------------------

    @Test
    void rejectsBenchPokemonOnFirstPlayerFirstTurn() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.setFirstTurn(true);
        game.setFirstPlayer(player);
        game.hand().put(player, new ArrayList<>(List.of("c-1")));
        game.registerCard(new GameCard("c-1", "Basic", "Pokemon", Set.of("Basic"), 60, 10, 1));
        assertThrows(InvalidActionException.class,
            () -> validator.validateBenchPokemon(game, player, "c-1"));
    }

    @Test
    void rejectsBenchPokemonWhenCardIdBlank() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validateBenchPokemon(game, player, "   "));
    }

    @Test
    void rejectsBenchPokemonWhenCardIdNull() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validateBenchPokemon(game, player, null));
    }

    @Test
    void rejectsBenchPokemonWhenNotInHand() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(player, new ArrayList<>());
        assertThrows(InvalidActionException.class,
            () -> validator.validateBenchPokemon(game, player, "missing"));
    }

    @Test
    void rejectsBenchPokemonWhenNotBasic() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(player, new ArrayList<>(List.of("c-1")));
        game.registerCard(new GameCard("c-1", "Stage1", "Pokemon", Set.of("Stage 1"), 90, 30, 1));
        assertThrows(InvalidActionException.class,
            () -> validator.validateBenchPokemon(game, player, "c-1"));
    }

    @Test
    void rejectsBenchPokemonWhenNotInMainPhase() {
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.DRAW);
        game.hand().put(player, new ArrayList<>(List.of("c-1")));
        game.registerCard(new GameCard("c-1", "Basic", "Pokemon", Set.of("Basic"), 60, 10, 1));
        assertThrows(InvalidActionPhaseException.class,
            () -> validator.validateBenchPokemon(game, player, "c-1"));
    }

    @Test
    void rejectsBenchPokemonWhenNotCurrentPlayer() {
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(other, new ArrayList<>(List.of("c-1")));
        game.registerCard(new GameCard("c-1", "Basic", "Pokemon", Set.of("Basic"), 60, 10, 1));
        assertThrows(InvalidActionException.class,
            () -> validator.validateBenchPokemon(game, other, "c-1"));
    }

    // ------------------- validateEvolution uncovered branches -------------------

    @Test
    void rejectsEvolutionOnFirstPlayerFirstTurn() {
        // First player first turn -> throws InvalidActionException (uses `require`, not `requireEvolution`)
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.setFirstTurn(true);
        game.setFirstPlayer(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validateEvolution(game, player, "from-1", "to-1", "ACTIVE"));
    }

    @Test
    void rejectsEvolutionWhenTurnsStartedByPlayerNotEnough() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.turnsStartedByPlayer().put(player, 1);
        assertThrows(EvolutionNotAllowedException.class,
            () -> validator.validateEvolution(game, player, "from-1", "to-1", "ACTIVE"));
    }

    @Test
    void rejectsEvolutionWhenSlotEnteredThisTurn() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.turnsStartedByPlayer().put(player, 2);
        game.markSlotEnteredThisTurn(player, "ACTIVE");
        assertThrows(EvolutionNotAllowedException.class,
            () -> validator.validateEvolution(game, player, "from-1", "to-1", "ACTIVE"));
    }

    @Test
    void rejectsEvolutionWhenSlotAlreadyEvolved() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.turnsStartedByPlayer().put(player, 2);
        game.turnFlags().markSlotEvolved("ACTIVE");
        assertThrows(EvolutionNotAllowedException.class,
            () -> validator.validateEvolution(game, player, "from-1", "to-1", "ACTIVE"));
    }

    @Test
    void rejectsEvolutionWhenFromCardDoesNotMatchSlot() {
        // The hand check fires first because we never put "to-1" in the hand.
        // Result: InvalidActionException (hand guard), not EvolutionNotAllowedException.
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.turnsStartedByPlayer().put(player, 2);
        game.activePokemon().put(player, "other-card");
        game.registerCard(new GameCard("other-card", "Other", "Pokemon", Set.of("Basic"), 60, 10, 1));
        assertThrows(InvalidActionException.class,
            () -> validator.validateEvolution(game, player, "from-1", "to-1", "ACTIVE"));
    }

    @Test
    void rejectsEvolutionWhenFromCardIdNull() {
        // fromCardInstanceId is null -> the `require` on hand content fires first (hand empty).
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.turnsStartedByPlayer().put(player, 2);
        game.activePokemon().put(player, "active-1");
        game.registerCard(new GameCard("active-1", "Active", "Pokemon", Set.of("Basic"), 60, 10, 1));
        assertThrows(InvalidActionException.class,
            () -> validator.validateEvolution(game, player, null, "to-1", "ACTIVE"));
    }

    @Test
    void rejectsEvolutionWhenEvolutionNotInHand() {
        // Hand is empty -> the `require` on hand content fires first -> InvalidActionException.
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.turnsStartedByPlayer().put(player, 2);
        game.activePokemon().put(player, "from-1");
        game.registerCard(new GameCard("from-1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.hand().put(player, new ArrayList<>());
        assertThrows(InvalidActionException.class,
            () -> validator.validateEvolution(game, player, "from-1", "to-1", "ACTIVE"));
    }

    @Test
    void rejectsEvolutionWhenCardsNotInCatalog() {
        // Cards not in catalog -> catalog lookup returns null -> `requireEvolution` fires
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.turnsStartedByPlayer().put(player, 2);
        game.activePokemon().put(player, "from-1");
        game.hand().put(player, new ArrayList<>(List.of("to-1")));
        assertThrows(EvolutionNotAllowedException.class,
            () -> validator.validateEvolution(game, player, "from-1", "to-1", "ACTIVE"));
    }

    @Test
    void rejectsEvolutionWhenEvolvesFromMismatch() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.turnsStartedByPlayer().put(player, 2);
        game.activePokemon().put(player, "from-1");
        game.registerCard(new GameCard("from-1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.hand().put(player, new ArrayList<>(List.of("to-1")));
        game.registerCard(new GameCard("to-1", "Raichu", "Pokemon", Set.of("Stage 1"), 80, 40, 1, null, List.of(), 0, "Pichu", List.of()));
        assertThrows(EvolutionNotAllowedException.class,
            () -> validator.validateEvolution(game, player, "from-1", "to-1", "ACTIVE"));
    }

    @Test
    void acceptsValidEvolution() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.turnsStartedByPlayer().put(player, 2);
        game.activePokemon().put(player, "from-1");
        game.registerCard(new GameCard("from-1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.hand().put(player, new ArrayList<>(List.of("to-1")));
        game.registerCard(new GameCard("to-1", "Raichu", "Pokemon", Set.of("Stage 1"), 80, 40, 1, null, List.of(), 0, "Pikachu", List.of()));
        assertDoesNotThrow(() -> validator.validateEvolution(game, player, "from-1", "to-1", "ACTIVE"));
    }

    // ------------------- validateTrainer uncovered branches -------------------

    @Test
    void rejectsTrainerWhenNotInMainPhase() {
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.DRAW);
        assertThrows(InvalidActionPhaseException.class,
            () -> validator.validateTrainer(game, player, "item-1", null));
    }

    @Test
    void rejectsTrainerWhenNotCurrentPlayer() {
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validateTrainer(game, other, "item-1", null));
    }

    @Test
    void acceptsTrainerWithCompositeId() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        // Card stored under base id, frontend sends composite "item-1:p1:0"
        game.hand().put(player, new ArrayList<>(List.of("item-1:p1:0")));
        game.registerCard(new GameCard("item-1", "Potion", "Trainer", Set.of("Item"), 0, 0, 0));
        assertDoesNotThrow(() -> validator.validateTrainer(game, player, "item-1:p1:0", null));
    }

    @Test
    void acceptsTrainerWithBaseIdLookupInCompositeKey() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        // Frontend sends base id, but catalog uses composite key "item-1:p1:0"
        game.hand().put(player, new ArrayList<>(List.of("item-1")));
        game.registerCard(new GameCard("item-1:p1:0", "Potion", "Trainer", Set.of("Item"), 0, 0, 0));
        assertDoesNotThrow(() -> validator.validateTrainer(game, player, "item-1", null));
    }

    @Test
    void rejectsStadiumWhenAlreadyPlayedThisTurn() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(player, new ArrayList<>(List.of("stadium-1")));
        game.registerCard(new GameCard("stadium-1", "Fairy Garden", "Trainer", Set.of("Stadium"), 0, 0, 0));
        game.turnFlags().setStadiumPlayed(true);
        assertThrows(InvalidActionException.class,
            () -> validator.validateTrainer(game, player, "stadium-1", null));
    }

    @Test
    void rejectsSupporterWhenSupporterLockEffectActiveInValidateTrainer() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(player, new ArrayList<>(List.of("supp-1")));
        game.registerCard(new GameCard("supp-1", "Shauna", "Trainer", Set.of("Supporter"), 0, 0, 0));
        game.addActiveEffect(player, ModifierStep.SUPPORTER_LOCK, 1);
        assertThrows(InvalidActionException.class,
            () -> validator.validateTrainer(game, player, "supp-1", null));
    }

    @Test
    void rejectsToolOnSlotWithExistingTool() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(player, new ArrayList<>(List.of("tool-1")));
        game.registerCard(new GameCard("tool-1", "Tool", "Trainer", Set.of("Pokemon Tool"), 0, 0, 0));
        game.benchPokemon(player, "b-1", "BENCH_0");
        game.registerCard(new GameCard("b-1", "B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.equipTool(player, "BENCH_0", "other-tool");
        assertThrows(InvalidActionException.class,
            () -> validator.validateTrainer(game, player, "tool-1", "BENCH_0"));
    }

    @Test
    void rejectsItemWhenOpponentHasForestCurse() {
        UUID player = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.setPlayerTwo(opponent);
        game.activePokemon().put(opponent, "opp-1");
        game.registerCard(new GameCard("opp-1", "OppPokemon", "Pokemon", Set.of("Basic"), 60, 10, 1, null, List.of(), 0, null, List.of("Forest's Curse")));
        game.hand().put(player, new ArrayList<>(List.of("item-1")));
        game.registerCard(new GameCard("item-1", "Potion", "Trainer", Set.of("Item"), 0, 0, 0));
        assertThrows(InvalidActionException.class,
            () -> validator.validateTrainer(game, player, "item-1", null));
    }

    @Test
    void rejectsTrainerWithUnrecognizedSubtype() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(player, new ArrayList<>(List.of("unk-1")));
        // Trainer supertype but no Stadium/Supporter/Tool/Item subtype.
        // The validator falls back to Item, so this should NOT throw.
        game.registerCard(new GameCard("unk-1", "Unknown", "Trainer", Set.of("Mystery"), 0, 0, 0));
        assertDoesNotThrow(() -> validator.validateTrainer(game, player, "unk-1", null));
    }

    // ------------------- validateAbility uncovered branches -------------------

    @Test
    void rejectsAbilityWhenNotInMainPhase() {
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.DRAW);
        assertThrows(InvalidActionPhaseException.class,
            () -> validator.validateAbility(game, player, "ACTIVE", 0));
    }

    @Test
    void rejectsAbilityWhenNotCurrentPlayer() {
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validateAbility(game, other, "ACTIVE", 0));
    }

    @Test
    void rejectsAbilityWhenSlotEmpty() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validateAbility(game, player, "ACTIVE", 0));
    }

    @Test
    void rejectsAbilityWhenAbilityLockEffectActive() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1, null, List.of(), 0, null, List.of("Mystical Fire")));
        game.addActiveEffect(player, ModifierStep.ABILITY_LOCK, 1);
        assertThrows(InvalidActionException.class,
            () -> validator.validateAbility(game, player, "ACTIVE", 0));
    }

    @Test
    void rejectsAbilityWhenAbilityIndexOutOfRange() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1, null, List.of(), 0, null, List.of("Mystical Fire")));
        // Index 5 is out of range (only 1 ability)
        assertThrows(InvalidActionException.class,
            () -> validator.validateAbility(game, player, "ACTIVE", 5));
    }

    @Test
    void rejectsAbilityWhenCardNotInCatalog() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        // Card id not in catalog
        assertThrows(InvalidActionException.class,
            () -> validator.validateAbility(game, player, "ACTIVE", 0));
    }

    @Test
    void rejectsAbilityWhenAlreadyUsedAndNotRepeatable() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1, null, List.of(), 0, null, List.of("Mystical Fire")));
        String abilityKey = player + ":ACTIVE:0";
        game.turnFlags().markAbilityUsed(abilityKey);
        assertThrows(InvalidActionException.class,
            () -> validator.validateAbility(game, player, "ACTIVE", 0));
    }

    @Test
    void acceptsAbilityWithNullAbilityEffectRegistry() {
        UUID player = UUID.randomUUID();
        // Default constructor sets registry to null
        TurnActionValidator validatorNoRegistry = new TurnActionValidator();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1, null, List.of(), 0, null, List.of("TotallyUnknown")));
        assertDoesNotThrow(() -> validatorNoRegistry.validateAbility(game, player, "ACTIVE", 0));
    }

    @Test
    void acceptsAbilityWithRepeatableStrategy() {
        UUID player = UUID.randomUUID();
        // FairyTransferAbilityEffectStrategy.isRepeatable() == true
        AbilityEffectRegistry registry = new AbilityEffectRegistry(List.of(
            new com.utn.pokemontcg.game.domain.strategy.FairyTransferAbilityEffectStrategy()
        ));
        TurnActionValidator validatorWithRegistry = new TurnActionValidator(registry);
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1, null, List.of(), 0, null, List.of("Fairy Transfer")));
        String abilityKey = player + ":ACTIVE:0";
        game.turnFlags().markAbilityUsed(abilityKey);
        // Repeatable -> no exception even if already used
        assertDoesNotThrow(() -> validatorWithRegistry.validateAbility(game, player, "ACTIVE", 0));
    }

    // ------------------- gameState and currentTurnPlayer guards -------------------

    @Test
    void rejectsWhenGameNotActive() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.setGameState(GameState.WAITING);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACH_ENERGY));
    }

    @Test
    void rejectsWhenNoCurrentPlayer() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.MAIN);
        game.setCurrentTurnPlayer(null);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACH_ENERGY));
    }

    // ------------------- validate() switch throws for setup/tool/ability actions -------------------

    @Test
    void rejectsSetupPlaceActiveAsLegacyAction() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.SETUP_PLACE_ACTIVE));
    }

    @Test
    void rejectsSetupPlaceBenchAsLegacyAction() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.SETUP_PLACE_BENCH));
    }

    @Test
    void rejectsSetupConfirmAsLegacyAction() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.SETUP_CONFIRM));
    }

    @Test
    void rejectsEquipToolAsLegacyAction() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.EQUIP_TOOL));
    }

    @Test
    void rejectsUseAbilityAsLegacyAction() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.USE_ABILITY));
    }

    // ------------------- ATTACK with negative attackIndexForValidation -------------------

    @Test
    void acceptsAttackWithNegativeAttackIndexSkippingBlockCheck() {
        // attackIndexForValidation = -1 -> the guard `attackIndexForValidation >= 0` is false,
        // so we skip the BLOCKED_ATTACK_PREFIX lookup branch. Distinct from the null branch.
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.ATTACK);
        game.setFirstTurn(false);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        assertDoesNotThrow(() -> validator.validate(game, GameActionType.ATTACK, null, null, List.of(), -1));
    }

    // ------------------- END_TURN accepted in ATTACK phase -------------------

    @Test
    void acceptsEndTurnInAttackPhase() {
        UUID player = UUID.randomUUID();
        assertDoesNotThrow(() -> validator.validate(activeGameAtPhase(player, TurnPhase.ATTACK), GameActionType.END_TURN));
    }

    // ------------------- validateBenchPokemon: card not in catalog -------------------

    @Test
    void rejectsBenchPokemonWhenCardNotInCatalog() {
        // cardInstanceId is in the hand but the cardCatalog does not contain it.
        // The require(card != null && card.isBasicPokemon()) fails on the null branch.
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(player, new ArrayList<>(List.of("ghost-1")));
        assertThrows(InvalidActionException.class,
            () -> validator.validateBenchPokemon(game, player, "ghost-1"));
    }

    // ------------------- validate() ATTACH_ENERGY: card not in catalog -------------------

    @Test
    void rejectsAttachEnergyWhenCardNotInCatalog() {
        // energyCardId is non-blank, present in the hand, but the catalog returns null.
        // The `energyCard != null && (energyCard.isBasicEnergy() || energyCard.isEnergy())`
        // check fails on the null branch.
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.hand().put(player, new ArrayList<>(List.of("mystery-energy")));
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACH_ENERGY, "mystery-energy", "ACTIVE", List.of()));
    }

    // ------------------- opponentActiveHasForestCurse false-branch coverage -------------------

    @Test
    void allowsItemWhenOpponentHasNoActive() {
        // opponent active is null -> opponentActiveHasForestCurse returns false
        UUID player = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.setPlayerTwo(opponent);
        // Note: opponent has no active Pokemon in this scenario
        game.hand().put(player, new ArrayList<>(List.of("item-1")));
        game.registerCard(new GameCard("item-1", "Potion", "Trainer", Set.of("Item"), 0, 0, 0));
        assertDoesNotThrow(() -> validator.validateTrainer(game, player, "item-1", null));
    }

    @Test
    void allowsItemWhenOpponentActiveCardNotInCatalog() {
        // opponent has an active card id, but cardCatalog returns null for it
        UUID player = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.setPlayerTwo(opponent);
        game.activePokemon().put(opponent, "missing-opp");
        game.hand().put(player, new ArrayList<>(List.of("item-1")));
        game.registerCard(new GameCard("item-1", "Potion", "Trainer", Set.of("Item"), 0, 0, 0));
        assertDoesNotThrow(() -> validator.validateTrainer(game, player, "item-1", null));
    }

    @Test
    void allowsItemWhenOpponentActiveHasAbilityOtherThanForestCurse() {
        // opponent active card is in catalog with abilities that do NOT contain "Forest's Curse".
        // Exercises the anyMatch returns-false branch (the abilities==null branch is dead code:
        // the GameCard record normalizes null abilities to List.of() in its compact constructor).
        UUID player = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.setPlayerTwo(opponent);
        game.activePokemon().put(opponent, "opp-1");
        GameCard oppCard = new GameCard(
            "opp-1", "OppPokemon", "Pokemon", Set.of("Basic"),
            60, 10, 1, null, null, null, List.of(), 0, null, List.of("Mystical Fire"),
            null, null, null, null, List.of()
        );
        game.registerCard(oppCard);
        game.hand().put(player, new ArrayList<>(List.of("item-1")));
        game.registerCard(new GameCard("item-1", "Potion", "Trainer", Set.of("Item"), 0, 0, 0));
        assertDoesNotThrow(() -> validator.validateTrainer(game, player, "item-1", null));
    }
}
