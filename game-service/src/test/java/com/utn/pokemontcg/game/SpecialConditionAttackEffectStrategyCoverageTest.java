package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.strategy.SpecialConditionAttackEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage for {@link SpecialConditionAttackEffectStrategy}.
 *
 * The strategy picks conditions from the attack text (via {@code conditionsFor})
 * but lets the client override that list when the text contains "choose either"
 * and a {@code selectedCondition} was supplied. It also self-targets attacks that
 * say "this Pokemon is now Asleep/Confused/Paralyzed" and applies to BOTH players
 * when the text contains "both active". Defenders holding a Sweet Veil ability
 * with a Fairy energy attached are immune to the condition.
 */
class SpecialConditionAttackEffectStrategyCoverageTest {

    private final SpecialConditionAttackEffectStrategy s = new SpecialConditionAttackEffectStrategy();

    // -----------------------------------------------------------------------
    // (a) "choose either" + valid selectedCondition → use the client choice
    // -----------------------------------------------------------------------
    @Test
    void applyUsesClientSelectedConditionWhenValid() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Hypnosis",
            "Choose either Asleep or Poisoned. The Defending Pokemon is now affected by that Special Condition.");
        ctx.setSelectedCondition("BURNED");
        s.apply(ctx);
        EnumSet<StatusCondition> defenderStatuses = game.statusByPlayer()
            .getOrDefault(game.playerTwo(), EnumSet.noneOf(StatusCondition.class));
        assertTrue(defenderStatuses.contains(StatusCondition.BURNED));
        assertFalse(defenderStatuses.contains(StatusCondition.ASLEEP));
        assertFalse(defenderStatuses.contains(StatusCondition.POISONED));
    }

    // -----------------------------------------------------------------------
    // (b) "choose either" + invalid selectedCondition → fallback to text-inferred
    // -----------------------------------------------------------------------
    @Test
    void applyFallsBackToTextInferredWhenSelectedInvalid() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Hypnosis",
            "Choose either Asleep or Poisoned. The Defending Pokemon is now affected by that Special Condition.");
        // StatusCondition.valueOf("INVALID") throws → caught and ignored, conditions stay as parsed from text
        ctx.setSelectedCondition("INVALID");
        s.apply(ctx);
        EnumSet<StatusCondition> defenderStatuses = game.statusByPlayer()
            .getOrDefault(game.playerTwo(), EnumSet.noneOf(StatusCondition.class));
        assertTrue(defenderStatuses.contains(StatusCondition.ASLEEP));
        assertTrue(defenderStatuses.contains(StatusCondition.POISONED));
        assertFalse(defenderStatuses.contains(StatusCondition.BURNED));
    }

    // -----------------------------------------------------------------------
    // (c) "both active" → applies to both players
    // -----------------------------------------------------------------------
    @Test
    void applyBothActiveTargetsAttackerAndDefender() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Miasma",
            "The Defending Pokemon and this Pokemon are now both affected by Poisoned. Both active Pokemon are now Poisoned.");
        s.apply(ctx);
        EnumSet<StatusCondition> attackerStatuses = game.statusByPlayer()
            .getOrDefault(game.playerOne(), EnumSet.noneOf(StatusCondition.class));
        EnumSet<StatusCondition> defenderStatuses = game.statusByPlayer()
            .getOrDefault(game.playerTwo(), EnumSet.noneOf(StatusCondition.class));
        assertTrue(attackerStatuses.contains(StatusCondition.POISONED));
        assertTrue(defenderStatuses.contains(StatusCondition.POISONED));
    }

    // -----------------------------------------------------------------------
    // (d) "this pokemon" + "now asleep" → self-targeted, applies to attacker
    // -----------------------------------------------------------------------
    @Test
    void applySelfTargetedAsleepAppliesToAttacker() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Rest",
            "This Pokemon is now Asleep.");
        s.apply(ctx);
        EnumSet<StatusCondition> attackerStatuses = game.statusByPlayer()
            .getOrDefault(game.playerOne(), EnumSet.noneOf(StatusCondition.class));
        EnumSet<StatusCondition> defenderStatuses = game.statusByPlayer()
            .getOrDefault(game.playerTwo(), EnumSet.noneOf(StatusCondition.class));
        assertTrue(attackerStatuses.contains(StatusCondition.ASLEEP));
        assertFalse(defenderStatuses.contains(StatusCondition.ASLEEP));
    }

    // -----------------------------------------------------------------------
    // (e) protectedBySweetVeil → defender blocks the condition
    // -----------------------------------------------------------------------
    @Test
    void applyBlockedBySweetVeilOnDefender() {
        GameAggregate game = game();
        // Replace the defender's active card with Slurpuff (Sweet Veil ability) + Fairy energy
        GameCard slurpuff = new GameCard("slurpuff", "Slurpuff", "Pokemon", Set.of("Basic"),
            100, 10, 1, "Colorless", List.of(), 1, null, List.of("Sweet Veil"));
        game.registerCard(slurpuff);
        game.activePokemon().put(game.playerTwo(), slurpuff.id());
        game.attachEnergy(game.playerTwo(), "ACTIVE", new AttachedEnergy("fairy-energy", "Fairy"));

        AttackContext ctx = context(game, "Toxic",
            "The Defending Pokemon is now Poisoned.");
        s.apply(ctx);

        EnumSet<StatusCondition> defenderStatuses = game.statusByPlayer()
            .getOrDefault(game.playerTwo(), EnumSet.noneOf(StatusCondition.class));
        assertFalse(defenderStatuses.contains(StatusCondition.POISONED),
            "Sweet Veil + Fairy energy must block the condition on the active");
        // The audit trail records the block.
        assertTrue(ctx.auditTrail().stream().anyMatch(a -> a.contains("sweet_veil_blocked")));
    }

    // -----------------------------------------------------------------------
    // (f) applyTo with ASLEEP → removes CONFUSED and PARALYZED first
    // -----------------------------------------------------------------------
    @Test
    void applyAsleepRemovesParalysisFirst() {
        GameAggregate game = game();
        // Seed the attacker with PARALYZED; when ASLEEP is applied, ASLEEP/CONFUSED/PARALYZED
        // are cleared, then ASLEEP is added.
        game.statusByPlayer().put(game.playerOne(), EnumSet.of(StatusCondition.PARALYZED));
        AttackContext ctx = context(game, "Rest",
            "This Pokemon is now Asleep.");
        s.apply(ctx);
        EnumSet<StatusCondition> attackerStatuses = game.statusByPlayer()
            .getOrDefault(game.playerOne(), EnumSet.noneOf(StatusCondition.class));
        assertTrue(attackerStatuses.contains(StatusCondition.ASLEEP));
        assertFalse(attackerStatuses.contains(StatusCondition.PARALYZED),
            "ASLEEP must clear PARALYZED when applied");
    }

    // -----------------------------------------------------------------------
    // (g) conditionsFor with multiple keywords → both POISONED and BURNED
    // -----------------------------------------------------------------------
    @Test
    void applyMultiKeywordTextAddsMultipleConditions() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Scorching Toxin",
            "The Defending Pokemon is now Poisoned and Burned.");
        s.apply(ctx);
        EnumSet<StatusCondition> defenderStatuses = game.statusByPlayer()
            .getOrDefault(game.playerTwo(), EnumSet.noneOf(StatusCondition.class));
        assertEquals(EnumSet.of(StatusCondition.POISONED, StatusCondition.BURNED), defenderStatuses);
    }

    // -----------------------------------------------------------------------
    // (h) context.cancelled() == true → early return at line 37-38.
    // -----------------------------------------------------------------------
    @Test
    void applySkipsWhenContextIsCancelled() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Toxic",
            "The Defending Pokemon is now Poisoned.");
        ctx.setCancelled(true);
        s.apply(ctx);
        EnumSet<StatusCondition> defenderStatuses = game.statusByPlayer()
            .getOrDefault(game.playerTwo(), EnumSet.noneOf(StatusCondition.class));
        assertFalse(defenderStatuses.contains(StatusCondition.POISONED),
            "Cancelled context must short-circuit before applying the condition");
    }

    // -----------------------------------------------------------------------
    // (i) empty conditions list (e.g. only "Asleep" without "is"/"now"
    //     trigger) → early return at line 37.
    // -----------------------------------------------------------------------
    @Test
    void applyNoOpWhenTextInferredConditionsAreEmpty() {
        GameAggregate game = game();
        // "damage 30" has no poison/burn/sleep/confuse/paraly keyword →
        // conditionsFor() returns an empty list → early return.
        AttackContext ctx = context(game, "Boring Hit",
            "This attack does 30 damage.");
        s.apply(ctx);
        assertTrue(game.statusByPlayer().getOrDefault(game.playerTwo(),
            EnumSet.noneOf(StatusCondition.class)).isEmpty());
        assertTrue(game.statusByPlayer().getOrDefault(game.playerOne(),
            EnumSet.noneOf(StatusCondition.class)).isEmpty());
    }

    // -----------------------------------------------------------------------
    // (j) attacker == null → defender falls back to playerOne (line 44-46).
    // -----------------------------------------------------------------------
    @Test
    void applyUsesPlayerOneAsDefenderWhenCurrentTurnPlayerIsNull() {
        GameAggregate game = game();
        game.setCurrentTurnPlayer(null);
        AttackContext ctx = context(game, "Toxic",
            "The Defending Pokemon is now Poisoned.");
        s.apply(ctx);
        // No attacker → defender expression falls through to playerOne.
        EnumSet<StatusCondition> playerOneStatuses = game.statusByPlayer()
            .getOrDefault(game.playerOne(), EnumSet.noneOf(StatusCondition.class));
        assertTrue(playerOneStatuses.contains(StatusCondition.POISONED),
            "When the attacker is null the condition must still be applied (to playerOne)");
    }

    // -----------------------------------------------------------------------
    // (k) "now Confused" / "now Paralyzed" — alternate text variations of
    //     the self-targeted condition detection (lines 65-66).
    // -----------------------------------------------------------------------
    @Test
    void applySelfTargetedNowConfusedTargetsAttacker() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Confuse",
            "This Pokemon is now Confused.");
        s.apply(ctx);
        EnumSet<StatusCondition> attackerStatuses = game.statusByPlayer()
            .getOrDefault(game.playerOne(), EnumSet.noneOf(StatusCondition.class));
        EnumSet<StatusCondition> defenderStatuses = game.statusByPlayer()
            .getOrDefault(game.playerTwo(), EnumSet.noneOf(StatusCondition.class));
        assertTrue(attackerStatuses.contains(StatusCondition.CONFUSED));
        assertFalse(defenderStatuses.contains(StatusCondition.CONFUSED));
    }

    @Test
    void applySelfTargetedNowParalyzedTargetsAttacker() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Body Slam",
            "This Pokemon is now Paralyzed.");
        s.apply(ctx);
        EnumSet<StatusCondition> attackerStatuses = game.statusByPlayer()
            .getOrDefault(game.playerOne(), EnumSet.noneOf(StatusCondition.class));
        EnumSet<StatusCondition> defenderStatuses = game.statusByPlayer()
            .getOrDefault(game.playerTwo(), EnumSet.noneOf(StatusCondition.class));
        assertTrue(attackerStatuses.contains(StatusCondition.PARALYZED));
        assertFalse(defenderStatuses.contains(StatusCondition.PARALYZED));
    }

    // -----------------------------------------------------------------------
    // (l) "this pokemon is now Asleep" + opponent mentioned → not
    //     self-targeted (lines 70-71). The defender gets the condition.
    // -----------------------------------------------------------------------
    @Test
    void applyThisPokemonAndOpponentAsleepRoutesToDefender() {
        GameAggregate game = game();
        // Both attacker and opponent get mentioned with "asleep" → the
        // isSelfTargetedCondition() guard returns false (defender is the
        // target) and "this pokemon" is present in the text.
        AttackContext ctx = context(game, "Bad Dreams",
            "This Pokemon is now Asleep. The opponent Pokemon is now Asleep.");
        s.apply(ctx);
        EnumSet<StatusCondition> attackerStatuses = game.statusByPlayer()
            .getOrDefault(game.playerOne(), EnumSet.noneOf(StatusCondition.class));
        EnumSet<StatusCondition> defenderStatuses = game.statusByPlayer()
            .getOrDefault(game.playerTwo(), EnumSet.noneOf(StatusCondition.class));
        assertFalse(attackerStatuses.contains(StatusCondition.ASLEEP),
            "When opponent is also mentioned asleep, the condition must NOT self-target the attacker");
        assertTrue(defenderStatuses.contains(StatusCondition.ASLEEP));
    }

    // -----------------------------------------------------------------------
    // (m) "this pokemon is now Confused" + opponent confused → not
    //     self-targeted (confused branch of the opponent check, line 70).
    // -----------------------------------------------------------------------
    @Test
    void applyThisPokemonAndOpponentConfusedRoutesToDefender() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Mind Twist",
            "This Pokemon is now Confused. The opponent Pokemon is now Confused.");
        s.apply(ctx);
        EnumSet<StatusCondition> attackerStatuses = game.statusByPlayer()
            .getOrDefault(game.playerOne(), EnumSet.noneOf(StatusCondition.class));
        EnumSet<StatusCondition> defenderStatuses = game.statusByPlayer()
            .getOrDefault(game.playerTwo(), EnumSet.noneOf(StatusCondition.class));
        assertFalse(attackerStatuses.contains(StatusCondition.CONFUSED));
        assertTrue(defenderStatuses.contains(StatusCondition.CONFUSED));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private AttackContext context(GameAggregate game, String attackName, String attackText) {
        AttackContext ctx = new AttackContext(game);
        ctx.setAttackName(attackName);
        ctx.setAttackText(attackText);
        return ctx;
    }

    private GameAggregate game() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(p1);
        game.setFirstPlayer(p1);
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());
        game.activePokemon().put(p1, "active-1");
        game.activePokemon().put(p2, "active-2");
        game.registerCard(new GameCard("active-1", "Attacker", "Pokemon", Set.of("Basic"), 100, 10, 1));
        game.registerCard(new GameCard("active-2", "Defender", "Pokemon", Set.of("Basic"), 100, 10, 1));
        game.damageCountersBySlot().get(p1).put("ACTIVE", 0);
        game.damageCountersBySlot().get(p2).put("ACTIVE", 0);
        return game;
    }
}
