package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.application.service.TurnActionValidator;
import com.utn.pokemontcg.game.domain.exception.BenchFullException;
import com.utn.pokemontcg.game.domain.exception.EvolutionNotAllowedException;
import com.utn.pokemontcg.game.domain.exception.InvalidActionPhaseException;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TurnActionValidatorTest {

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

    @Test
    void shouldRejectAttachEnergyOutsideMainPhase() {
        UUID player = UUID.randomUUID();
        assertThrows(InvalidActionException.class,
            () -> validator.validate(activeGameAtPhase(player, TurnPhase.DRAW), GameActionType.ATTACH_ENERGY));
    }

    @Test
    void shouldAllowAttachEnergyInMainWhenNotUsed() {
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.MAIN);
        game.activePokemon().put(player, "active-1");
        game.hand().put(player, new ArrayList<>(List.of("energy-1")));
        game.registerCard(new GameCard("active-1", "Active", "Pokemon", Set.of("Basic"), 120, 30, 1));
        game.registerCard(new GameCard("energy-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        assertDoesNotThrow(() -> validator.validate(game, GameActionType.ATTACH_ENERGY, "energy-1", "ACTIVE", List.of()));
    }

    @Test
    void rejectsAttackOutsideAttackPhase() {
        UUID player = UUID.randomUUID();
        assertThrows(InvalidActionException.class,
            () -> validator.validate(activeGameAtPhase(player, TurnPhase.MAIN), GameActionType.ATTACK));
    }

    @Test
    void rejectsAttackOnFirstTurn() {
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.ATTACK);
        game.setFirstTurn(true);
        game.setFirstPlayer(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACK));
    }

    @Test
    void rejectsAttackWhenActiveIsNull() {
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.ATTACK);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACK));
    }

    @Test
    void rejectsAttackWhenSleeping() {
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.ATTACK);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.statusByPlayer().put(player, EnumSet.of(StatusCondition.ASLEEP));
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.ATTACK));
    }

    @Test
    void rejectsRetreatOutsideMain() {
        UUID player = UUID.randomUUID();
        assertThrows(InvalidActionException.class,
            () -> validator.validate(activeGameAtPhase(player, TurnPhase.DRAW), GameActionType.RETREAT));
    }

    @Test
    void rejectsRetreatWhenAlreadyRetreated() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.benchPokemon(player, "b-1", "BENCH_0");
        game.registerCard(new GameCard("b-1", "B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.turnFlags().setRetreated(true);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.RETREAT, null, null, List.of("e-1")));
    }

    @Test
    void rejectsRetreatWhenSleeping() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.statusByPlayer().put(player, EnumSet.of(StatusCondition.ASLEEP));
        game.benchPokemon(player, "b-1", "BENCH_0");
        game.registerCard(new GameCard("b-1", "B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.RETREAT));
    }

    @Test
    void rejectsRetreatWithNotEnoughEnergy() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1, "Fire", List.of("Fire"), 2));
        game.benchPokemon(player, "b-1", "BENCH_0");
        game.registerCard(new GameCard("b-1", "B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.RETREAT, null, "BENCH_0", List.of()));
    }

    @Test
    void acceptsRetreatWithCorrectEnergy() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1, "Fire", List.of("Fire"), 1));
        game.benchPokemon(player, "b-1", "BENCH_0");
        game.registerCard(new GameCard("b-1", "B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.attachEnergy(player, "ACTIVE", new AttachedEnergy("fire-energy", "Fire"));
        game.hand().put(player, new ArrayList<>());
        assertDoesNotThrow(() -> validator.validate(game, GameActionType.RETREAT, null, "BENCH_0", List.of("fire-energy")));
    }

    @Test
    void rejectsActionWhenGameNotActive() {
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.MAIN);
        game.setGameState(GameState.FINISHED);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.DRAW));
    }

    @Test
    void acceptsDrawInDrawPhase() {
        UUID player = UUID.randomUUID();
        assertDoesNotThrow(() -> validator.validate(activeGameAtPhase(player, TurnPhase.DRAW), GameActionType.DRAW));
    }

    @Test
    void acceptsEndTurnInMainPhase() {
        UUID player = UUID.randomUUID();
        assertDoesNotThrow(() -> validator.validate(activeGameAtPhase(player, TurnPhase.MAIN), GameActionType.END_TURN));
    }

    @Test
    void rejectsEndTurnOutsideMainOrAttack() {
        UUID player = UUID.randomUUID();
        assertThrows(InvalidActionException.class,
            () -> validator.validate(activeGameAtPhase(player, TurnPhase.DRAW), GameActionType.END_TURN));
    }

    @Test
    void validateTrainerRejectsCardNotInHand() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validateTrainer(game, player, "trainer-1", null));
    }

    @Test
    void validateTrainerAcceptsItemInHand() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(player, new ArrayList<>(List.of("item-1")));
        game.registerCard(new GameCard("item-1", "Potion", "Trainer", Set.of("Item"), 0, 0, 0));
        assertDoesNotThrow(() -> validator.validateTrainer(game, player, "item-1", "ACTIVE"));
    }

    @Test
    void validateTrainerRejectsNonTrainerCard() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(player, new ArrayList<>(List.of("poke-1")));
        game.registerCard(new GameCard("poke-1", "Pokemon", "Pokemon", Set.of("Basic"), 60, 10, 1));
        assertThrows(InvalidActionException.class,
            () -> validator.validateTrainer(game, player, "poke-1", null));
    }

    @Test
    void validateTrainerRejectsSupporterWhenPlayed() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(player, new ArrayList<>(List.of("supp-1")));
        game.registerCard(new GameCard("supp-1", "Shauna", "Trainer", Set.of("Supporter"), 0, 0, 0));
        game.turnFlags().setSupporterPlayed(true);
        assertThrows(InvalidActionException.class,
            () -> validator.validateTrainer(game, player, "supp-1", null));
    }

    @Test
    void validateTrainerRejectsToolOnEmptySlot() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(player, new ArrayList<>(List.of("tool-1")));
        game.registerCard(new GameCard("tool-1", "Tool", "Trainer", Set.of("Pokemon Tool"), 0, 0, 0));
        assertThrows(InvalidActionException.class,
            () -> validator.validateTrainer(game, player, "tool-1", "BENCH_0"));
    }

    @Test
    void validateTrainerFallsBackToItemWhenNoSubtype() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(player, new ArrayList<>(List.of("generic-1")));
        game.registerCard(new GameCard("generic-1", "Generic", "Trainer", Set.of(), 0, 0, 0));
        assertDoesNotThrow(() -> validator.validateTrainer(game, player, "generic-1", null));
    }

    @Test
    void validateEvolutionFailsWhenNotEnoughTurnCount() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertThrows(EvolutionNotAllowedException.class,
            () -> validator.validateEvolution(game, player, "from-1", "to-1", "ACTIVE"));
    }

    @Test
    void validateBenchPokemonRejectsFullBench() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(player, new ArrayList<>(List.of("card-1")));
        game.registerCard(new GameCard("card-1", "Basic", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.benchPokemon(player, "b0", "BENCH_0");
        game.benchPokemon(player, "b1", "BENCH_1");
        game.benchPokemon(player, "b2", "BENCH_2");
        game.benchPokemon(player, "b3", "BENCH_3");
        game.benchPokemon(player, "b4", "BENCH_4");
        assertThrows(BenchFullException.class,
            () -> validator.validateBenchPokemon(game, player, "card-1"));
    }

    @Test
    void validateBenchPokemonAcceptsWhenSpaceExists() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(player, new ArrayList<>(List.of("card-1")));
        game.registerCard(new GameCard("card-1", "Basic", "Pokemon", Set.of("Basic"), 60, 10, 1));
        assertDoesNotThrow(() -> validator.validateBenchPokemon(game, player, "card-1"));
    }

    @Test
    void validatesTakePrizeOnlyInBetweenTurns() {
        UUID player = UUID.randomUUID();
        assertThrows(InvalidActionException.class,
            () -> validator.validate(activeGameAtPhase(player, TurnPhase.DRAW), GameActionType.TAKE_PRIZE));
    }

    @Test
    void validatesApplyConditionInAttackPhase() {
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.ATTACK);
        game.activePokemon().put(player, "a-1");
        game.registerCard(new GameCard("a-1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        assertDoesNotThrow(() -> validator.validate(game, GameActionType.APPLY_SPECIAL_CONDITION));
    }

    @Test
    void validatesResolveBetweenTurnsOnlyInBetweenTurns() {
        UUID player = UUID.randomUUID();
        GameAggregate game = activeGameAtPhase(player, TurnPhase.BETWEEN_TURNS);
        assertDoesNotThrow(() -> validator.validate(game, GameActionType.RESOLVE_BETWEEN_TURNS));
    }

    @Test
    void validatesSetupActionsThrow() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.BENCH_POKEMON));
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.EVOLVE_POKEMON));
        assertThrows(InvalidActionException.class,
            () -> validator.validate(game, GameActionType.PLAY_TRAINER));
    }
}
