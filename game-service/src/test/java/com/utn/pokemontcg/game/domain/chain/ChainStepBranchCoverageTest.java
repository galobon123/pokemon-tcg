package com.utn.pokemontcg.game.domain.chain;

import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage push for chain steps.
 * Targets the missed branches in {@link ModifierStep} (74.1% branch) and
 * {@link ConfusionCheckStep} (83.3% branch).
 */
class ChainStepBranchCoverageTest {

    @Test
    void modifierStepReturnsImmediatelyWhenCancelled() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        game.setCurrentTurnPlayer(game.playerOne());
        game.initializeZonesFor(game.playerOne(), List.of());
        game.initializeZonesFor(game.playerTwo(), List.of());

        AttackContext ctx = new AttackContext(game);
        ctx.setCancelled(true);
        ctx.setAttackerModifier(99);
        ctx.setDefenderModifier(99);

        new ModifierStep().execute(ctx);

        assertEquals(99, ctx.attackerModifier());
        assertEquals(99, ctx.defenderModifier());
    }

    @Test
    void modifierStepAppliesMuscleBandAndHardCharmWhenPresent() {
        GameAggregate game = gameWithPlayers();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        GameCard muscle = new GameCard("m", "Muscle Band", "Trainer", Set.of("Pokemon Tool"), 0, 0, 0);
        GameCard hardCharm = new GameCard("h", "Hard Charm", "Trainer", Set.of("Pokemon Tool"), 0, 0, 0);
        game.registerCard(muscle);
        game.registerCard(hardCharm);
        game.equipTool(attacker, "ACTIVE", "m");
        game.equipTool(defender, "ACTIVE", "h");

        AttackContext ctx = new AttackContext(game);
        ctx.setAttackName("Tackle");
        ctx.setAttackText("");
        ctx.setBaseDamage(50);

        new ModifierStep().execute(ctx);

        assertEquals(20, ctx.attackerModifier());
        assertEquals(-20, ctx.defenderModifier());
    }

    @Test
    void modifierStepAppliesFurCoatWhenDefenderHasAbility() {
        GameAggregate game = gameWithPlayers();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        GameCard activeWithFur = new GameCard("f", "Fur Coat Mon", "Pokemon",
            Set.of("Basic"), 100, 10, 1, "Bite", "", "Colorless",
            List.of(), 0, null, List.of("Fur Coat"));
        game.registerCard(activeWithFur);
        game.activePokemon().put(defender, "f");

        AttackContext ctx = new AttackContext(game);
        ctx.setAttackName("Tackle");
        ctx.setAttackText("");
        ctx.setBaseDamage(50);

        new ModifierStep().execute(ctx);

        assertEquals(-20, ctx.defenderModifier());
    }

    @Test
    void modifierStepHandlesNullCurrentTurnPlayer() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        // currentTurnPlayer is null

        AttackContext ctx = new AttackContext(game);
        ctx.setAttackName("Tackle");
        ctx.setAttackText("");
        ctx.setBaseDamage(50);

        new ModifierStep().execute(ctx);

        // No exceptions; defaults to 0 modifiers
        assertEquals(0, ctx.attackerModifier());
        assertEquals(0, ctx.defenderModifier());
    }

    @Test
    void modifierStepResetsWeaknessAndResistanceFromAttackText() {
        GameAggregate game = gameWithPlayers();
        AttackContext ctx = new AttackContext(game);
        ctx.setAttackName("Sky Attack");
        ctx.setAttackText("This attack isn't affected by Weakness, Resistance.");
        ctx.setWeaknessMultiplier(0);
        ctx.setResistanceReduction(-50);
        ctx.setBaseDamage(50);

        new ModifierStep().execute(ctx);

        assertEquals(1, ctx.weaknessMultiplier(),
            "Texto 'isn't affected by Weakness, Resistance' debe forzar weaknessMultiplier=1");
        assertEquals(0, ctx.resistanceReduction(),
            "Texto 'isn't affected by Weakness, Resistance' debe forzar resistanceReduction=0");
    }

    @Test
    void modifierStepAppliesAttackNameBonus() {
        GameAggregate game = gameWithPlayers();
        UUID attacker = game.currentTurnPlayer();
        game.addActiveEffect(attacker, ModifierStep.ATTACKER_DAMAGE_BONUS + ":Tackle", 30);

        AttackContext ctx = new AttackContext(game);
        ctx.setAttackName("Tackle");
        ctx.setAttackText("");
        ctx.setBaseDamage(50);

        new ModifierStep().execute(ctx);

        assertEquals(30, ctx.attackerModifier());
    }

    @Test
    void modifierStepNullAttackNameIsTreatedAsEmpty() {
        GameAggregate game = gameWithPlayers();
        AttackContext ctx = new AttackContext(game);
        ctx.setAttackName(null);
        ctx.setAttackText(null);
        ctx.setBaseDamage(50);

        new ModifierStep().execute(ctx);

        // No exception; bonuses are 0
        assertEquals(0, ctx.attackerModifier());
        assertEquals(0, ctx.defenderModifier());
    }

    @Test
    void modifierStepHandlesNonNegativeResistanceReduction() {
        GameAggregate game = gameWithPlayers();
        AttackContext ctx = new AttackContext(game);
        ctx.setAttackName("Tackle");
        ctx.setAttackText("");
        ctx.setBaseDamage(50);
        ctx.setResistanceReduction(20); // positive reduction, not negative
        // Without explicit "isn't affected by resistance" text, the value stays
        // positive. After clamp check: resistanceReduction < 0 -> false, so stays 20.
        new ModifierStep().execute(ctx);

        assertEquals(20, ctx.resistanceReduction());
    }

    // ====================================================================
    // ConfusionCheckStep
    // ====================================================================

    @Test
    void confusionCheckSkipsWhenNotConfused() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        new ConfusionCheckStep().execute(ctx);
        assertTrue(ctx.auditTrail().stream()
            .anyMatch(a -> a.contains("skip_not_confused")));
    }

    @Test
    void confusionCheckCancelledStopsExecution() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        ctx.setCancelled(true);
        ctx.setConfused(true);
        new ConfusionCheckStep().execute(ctx);
        // When cancelled, only the "start" entry is added — no skip_not_confused,
        // tails_cancelled, or heads_continue entry.
        assertTrue(ctx.auditTrail().contains("ConfusionCheckStep:start"));
        assertTrue(ctx.auditTrail().stream().noneMatch(a -> a.contains("skip_not_confused")));
        assertTrue(ctx.auditTrail().stream().noneMatch(a -> a.contains("tails_cancelled")));
        assertTrue(ctx.auditTrail().stream().noneMatch(a -> a.contains("heads_continue")));
    }

    // ====================================================================
    // helpers
    // ====================================================================

    private GameAggregate gameWithPlayers() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(p1);
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());
        return game;
    }
}
