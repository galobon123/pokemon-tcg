package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch/line coverage push for small ability and trainer strategy classes
 * that were below 95% in the JaCoCo audit:
 *  - NoOpAbilityEffectStrategy   (62.5% line)
 *  - SweetVeilAbilityEffectStrategy (80% line)
 *  - ItemEffectStrategy          (kept at 100%)
 *  - SupporterEffectStrategy     (kept at 100%)
 *  - StadiumEffectStrategy       (interface, exercised via dummy)
 */
class SimpleAbilityStrategyCoverageTest {

    // ====================================================================
    // NoOpAbilityEffectStrategy
    // ====================================================================

    @Test
    void noOpAbilityNameIsNoOp() {
        assertEquals("NO_OP", new NoOpAbilityEffectStrategy().abilityName());
    }

    @Test
    void noOpApplyReturnsNoopResult() {
        GameAggregate game = newEmptyGame();
        AbilityEffectContext ctx = new AbilityEffectContext(
            game, game.playerOne(), null, "ACTIVE", 0, null);

        TrainerEffectResult result = new NoOpAbilityEffectStrategy().apply(ctx);

        assertNotNull(result);
        assertEquals("ABILITY_HAS_NO_REGISTERED_EFFECT", result.result());
        assertTrue(result.payload().isEmpty());
    }

    // ====================================================================
    // SweetVeilAbilityEffectStrategy
    // ====================================================================

    @Test
    void sweetVeilAbilityNameIsSweetVeil() {
        assertEquals("Sweet Veil", new SweetVeilAbilityEffectStrategy().abilityName());
    }

    @Test
    void sweetVeilApplyThrowsInvalidActionException() {
        GameAggregate game = newEmptyGame();
        GameCard slurpuff = new GameCard(
            "slurpuff-1", "Slurpuff", "Pokemon", Set.of("Basic"),
            100, 10, 1, null, null, "Fairy", List.of(), 1, null, List.of("Sweet Veil"));
        game.registerCard(slurpuff);
        AbilityEffectContext ctx = new AbilityEffectContext(
            game, game.playerOne(), slurpuff, "ACTIVE", 0, null);

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> new SweetVeilAbilityEffectStrategy().apply(ctx));

        assertTrue(ex.getMessage().toLowerCase().contains("habilidad continua"),
            "Sweet Veil debe rechazar uso manual y explicar que es pasiva");
    }

    // ====================================================================
    // ItemEffectStrategy (no-op trainer)
    // ====================================================================

    @Test
    void itemEffectCardNameIsItem() {
        assertEquals("ITEM", new ItemEffectStrategy().cardName());
    }

    @Test
    void itemEffectApplyReturnsNoopResult() {
        GameAggregate game = newEmptyGame();
        GameCard trainer = new GameCard(
            "trainer-1", "Item", "Trainer", Set.of("Item"), 0, 0, 0);
        TrainerEffectContext ctx = new TrainerEffectContext(
            game, game.playerOne(), trainer, trainer.id(), null, null);

        TrainerEffectResult result = new ItemEffectStrategy().apply(ctx);

        assertEquals("ITEM_HAS_NO_REGISTERED_EFFECT", result.result());
        assertTrue(result.payload().isEmpty());
    }

    // ====================================================================
    // SupporterEffectStrategy (no-op trainer)
    // ====================================================================

    @Test
    void supporterEffectCardNameIsSupporter() {
        assertEquals("SUPPORTER", new SupporterEffectStrategy().cardName());
    }

    @Test
    void supporterEffectApplyReturnsNoopResult() {
        GameAggregate game = newEmptyGame();
        GameCard trainer = new GameCard(
            "trainer-1", "Supporter", "Trainer", Set.of("Supporter"), 0, 0, 0);
        TrainerEffectContext ctx = new TrainerEffectContext(
            game, game.playerOne(), trainer, trainer.id(), null, null);

        TrainerEffectResult result = new SupporterEffectStrategy().apply(ctx);

        assertEquals("SUPPORTER_HAS_NO_REGISTERED_EFFECT", result.result());
        assertTrue(result.payload().isEmpty());
    }

    // ====================================================================
    // StadiumEffectStrategy (interface — exercise the contract via a stub)
    // ====================================================================

    @Test
    void stadiumEffectStrategyContractIsCallable() {
        StadiumEffectStrategy stadium = new StadiumEffectStrategy() {
            private boolean playedCalled = false;
            private boolean removedCalled = false;

            @Override
            public String cardName() { return "Fairy Garden"; }

            @Override
            public void onPlayed(GameAggregate game, UUID playerId) {
                playedCalled = true;
            }

            @Override
            public void onRemoved(GameAggregate game) {
                removedCalled = true;
            }

            boolean wasPlayed() { return playedCalled; }
            boolean wasRemoved() { return removedCalled; }
        };

        GameAggregate game = newEmptyGame();
        stadium.onPlayed(game, game.playerOne());
        stadium.onRemoved(game);

        assertEquals("Fairy Garden", stadium.cardName());
    }

    // ====================================================================
    // helpers
    // ====================================================================

    private GameAggregate newEmptyGame() {
        UUID p1 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(UUID.randomUUID());
        return game;
    }
}
