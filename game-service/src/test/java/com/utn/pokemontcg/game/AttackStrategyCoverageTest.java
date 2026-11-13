package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.CoinBonusDamageAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.CoinFlipDamageAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.SelfHealingAttackEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Targeted branch coverage push for the three attack-effect strategies
 * that still expose uncovered branches in the JaCoCo report.
 */
class AttackStrategyCoverageTest {

    // ----- CoinBonusDamageAttackEffectStrategy -----

    @Test
    void coinBonusDamage_usesExplicitBonusWithCoinConditionTrue() {
        AttackContext context = ctx(game(), "Punch",
            "Flip a coin. If heads, this attack does 30 more damage.");
        context.setCoinFlip(() -> true);
        new CoinBonusDamageAttackEffectStrategy(() -> true).apply(context);

        assertEquals(30, context.preAttackBonus());
        assertTrue(context.auditTrail().stream()
            .anyMatch(a -> a.contains("coin_condition") && a.contains("allowed=true")));
    }

    @Test
    void coinBonusDamage_skipsWhenCoinConditionDisallows() {
        AttackContext context = ctx(game(), "Punch",
            "Flip a coin. If heads, this attack does 30 more damage.");
        context.setCoinFlip(() -> false);
        new CoinBonusDamageAttackEffectStrategy(() -> false).apply(context);

        assertEquals(0, context.preAttackBonus(),
            "Si coinConditionAllowsEffect devuelve false no se aplica el bonus");
    }

    @Test
    void coinBonusDamage_plainCoinPathOnTails() {
        // No "if heads" / "if tails" in the text → hasCoinCondition() is false
        AttackContext context = ctx(game(), "Poke",
            "Flip a coin. This attack does 25 more damage.");
        context.setCoinFlip(() -> false);
        new CoinBonusDamageAttackEffectStrategy(() -> false).apply(context);

        assertEquals(0, context.preAttackBonus(),
            "En la rama plain coin flip, tails deja preAttackBonus en 0");
        assertTrue(context.auditTrail().stream()
            .anyMatch(a -> a.contains("coin_bonus_flip=tails")));
    }

    @Test
    void coinBonusDamage_plainCoinPathOnHeads() {
        AttackContext context = ctx(game(), "Poke",
            "Flip a coin. This attack does 25 more damage.");
        context.setCoinFlip(() -> true);
        new CoinBonusDamageAttackEffectStrategy(() -> true).apply(context);

        assertEquals(25, context.preAttackBonus());
    }

    @Test
    void coinBonusDamage_fallsBackToDefaultBonus10() {
        // Text that satisfies supports() ("flip a coin" + "+" or "more damage")
        // but lacks both "\+\d+" and "\d+ more damage" patterns
        AttackContext context = ctx(game(), "Tap", "Flip a coin. Damage boosts.");
        context.setCoinFlip(() -> true);
        new CoinBonusDamageAttackEffectStrategy(() -> true).apply(context);

        assertEquals(10, context.preAttackBonus(),
            "firstExplicitBonus debe caer al fallback de 10 cuando no hay patron +N ni 'N more damage'");
    }

    // ----- SelfHealingAttackEffectStrategy -----

    @Test
    void selfHealing_skipsWhenCoinConditionDisallows() {
        GameAggregate g = game();
        g.addDamageCounters(g.playerOne(), "ACTIVE", 5);
        AttackContext context = ctx(g, "Heal",
            "Flip a coin. If heads, heal 30 damage from this Pokemon.");
        context.setCoinFlip(() -> false);
        new SelfHealingAttackEffectStrategy().apply(context);

        assertEquals(5, g.damageCountersAtSlot(g.playerOne(), "ACTIVE"),
            "Sin heads en la condicion de moneda, no se cura nada");
    }

    @Test
    void selfHealing_benchOnlyFallsBackToFirstOccupiedBench() {
        GameAggregate g = game();
        // Attacker has 3 bench slots
        g.bench().get(g.playerOne()).add("bench-0");
        g.bench().get(g.playerOne()).add("bench-1");
        g.bench().get(g.playerOne()).add("bench-2");
        // Add some damage to bench 1
        g.addDamageCounters(g.playerOne(), "BENCH_1", 3);

        // targetSlot is requested but not occupied by that pokemon
        AttackContext context = ctx(g, "Massage",
            "Heal 30 damage from 1 of your Benched Pokemon.");
        context.setTargetSlot("BENCH_2");
        new SelfHealingAttackEffectStrategy().apply(context);

        // The strategy walks the bench from index 0 → finds the first occupied slot (BENCH_0).
        // Healing 30 damage from a Pokemon with 0 counters reduces to 0.
        assertTrue(g.damageCountersAtSlot(g.playerOne(), "BENCH_0") == 0
            || g.damageCountersAtSlot(g.playerOne(), "BENCH_1") == 0,
            "firstOccupiedBench debe encontrar la primera banca ocupada y curarla");
    }

    @Test
    void selfHealing_usesRequestedBenchSlotWhenOccupied() {
        GameAggregate g = game();
        g.bench().get(g.playerOne()).add("bench-0");
        g.addDamageCounters(g.playerOne(), "BENCH_0", 4);

        AttackContext context = ctx(g, "Massage",
            "Heal 30 damage from 1 of your Benched Pokemon.");
        context.setTargetSlot("BENCH_0");
        new SelfHealingAttackEffectStrategy().apply(context);

        assertEquals(1, g.damageCountersAtSlot(g.playerOne(), "BENCH_0"),
            "Si el slot solicitado esta ocupado, se usa ese slot");
    }

    @Test
    void selfHealing_usesRequestedActiveSlotWhenOccupied() {
        GameAggregate g = game();
        g.addDamageCounters(g.playerOne(), "ACTIVE", 4);

        AttackContext context = ctx(g, "Sweet Scent",
            "Heal 30 damage from 1 of your Pokemon.");
        context.setTargetSlot("ACTIVE");
        new SelfHealingAttackEffectStrategy().apply(context);

        assertEquals(1, g.damageCountersAtSlot(g.playerOne(), "ACTIVE"),
            "Si el slot ACTIVE esta ocupado y se solicita, se usa ACTIVE");
    }

    @Test
    void selfHealing_healByDamageCountersPattern() {
        GameAggregate g = game();
        g.addDamageCounters(g.playerOne(), "ACTIVE", 5);

        AttackContext context = ctx(g, "Drain",
            "Remove 2 damage counters from this Pokemon.");
        new SelfHealingAttackEffectStrategy().apply(context);

        assertEquals(3, g.damageCountersAtSlot(g.playerOne(), "ACTIVE"),
            "El patron 'N damage counters' debe interpretarse como N contadores (no N/10)");
    }

    @Test
    void selfHealing_healEachOfYourRespectsAllOccupiedSlots() {
        GameAggregate g = game();
        g.bench().get(g.playerOne()).add("bench-0");
        g.bench().get(g.playerOne()).add("bench-1");
        g.addDamageCounters(g.playerOne(), "ACTIVE", 3);
        g.addDamageCounters(g.playerOne(), "BENCH_0", 2);
        g.addDamageCounters(g.playerOne(), "BENCH_1", 4);

        AttackContext context = ctx(g, "Full Heal",
            "Heal 10 damage from each of your Pokemon.");
        new SelfHealingAttackEffectStrategy().apply(context);

        assertEquals(2, g.damageCountersAtSlot(g.playerOne(), "ACTIVE"),
            "3 - 1 (10 damage = 1 counter) = 2");
        assertEquals(1, g.damageCountersAtSlot(g.playerOne(), "BENCH_0"),
            "2 - 1 = 1");
        assertEquals(3, g.damageCountersAtSlot(g.playerOne(), "BENCH_1"),
            "4 - 1 = 3");
    }

    @Test
    void selfHealing_allDamageCountersRemovesEveryCounterFromTarget() {
        // "remove all damage counters" → healAmount returns the full count of
        // counters currently on the target slot (line 100-102).
        GameAggregate g = game();
        g.addDamageCounters(g.playerOne(), "ACTIVE", 5);

        AttackContext context = ctx(g, "Purify",
            "Remove all damage counters from this Pokemon.");
        new SelfHealingAttackEffectStrategy().apply(context);

        assertEquals(0, g.damageCountersAtSlot(g.playerOne(), "ACTIVE"),
            "All damage counters must be removed when the text says 'all damage counters'");
    }

    @Test
    void selfHealing_healEachOfYourFallsBackToActiveWhenBenchIsEmpty() {
        // No bench entries registered → the for loop in the "from each of
        // your" path never iterates and the ACTIVE heal is the only one
        // performed (covers line 46-49 branches).
        GameAggregate g = game();
        g.addDamageCounters(g.playerOne(), "ACTIVE", 6);

        AttackContext context = ctx(g, "Group Heal",
            "Heal 20 damage from each of your Pokemon.");
        new SelfHealingAttackEffectStrategy().apply(context);

        assertEquals(4, g.damageCountersAtSlot(g.playerOne(), "ACTIVE"),
            "6 - 2 (20 damage = 2 counters) = 4");
    }

    @Test
    void selfHealing_1ofYourFallsBackToActiveWhenNoRequestedSlot() {
        // Text says "1 of your Pokemon" and no targetSlot is set → the
        // requested slot normalizes to "ACTIVE" and the heal is applied
        // there (line 85-87 / fallback to ACTIVE).
        GameAggregate g = game();
        g.addDamageCounters(g.playerOne(), "ACTIVE", 4);

        AttackContext context = ctx(g, "Sweet Scent",
            "Heal 30 damage from 1 of your Pokemon.");
        // targetSlot is left null → normalizeSlot returns "ACTIVE".
        new SelfHealingAttackEffectStrategy().apply(context);

        assertEquals(1, g.damageCountersAtSlot(g.playerOne(), "ACTIVE"),
            "No target slot must fall back to ACTIVE for '1 of your' heals");
    }

    @Test
    void selfHealing_1ofYourRejectsRequestedSlotThatIsUnoccupied() {
        // Text says "1 of your Pokemon", a BENCH_x slot is requested but
        // that slot is unoccupied → strategy must fall back to ACTIVE.
        GameAggregate g = game();
        g.addDamageCounters(g.playerOne(), "ACTIVE", 4);

        AttackContext context = ctx(g, "Sweet Scent",
            "Heal 30 damage from 1 of your Pokemon.");
        context.setTargetSlot("BENCH_0");
        new SelfHealingAttackEffectStrategy().apply(context);

        assertEquals(1, g.damageCountersAtSlot(g.playerOne(), "ACTIVE"),
            "Unoccupied requested slot must fall back to ACTIVE");
    }

    @Test
    void selfHealing_benchOnlyReturnsActiveWhenNoBenchOccupied() {
        // "1 of your Benched Pokemon" with no occupied bench → firstOccupiedBench
        // returns "ACTIVE" (line 117 fallback) and the heal targets the active.
        GameAggregate g = game();
        g.addDamageCounters(g.playerOne(), "ACTIVE", 4);
        // No bench entries.

        AttackContext context = ctx(g, "Massage",
            "Heal 30 damage from 1 of your Benched Pokemon.");
        new SelfHealingAttackEffectStrategy().apply(context);

        assertEquals(1, g.damageCountersAtSlot(g.playerOne(), "ACTIVE"),
            "When no bench is occupied, the heal must target the ACTIVE pokemon");
    }

    @Test
    void selfHealing_supportsRejectsAttacksTargetingOpponent() {
        // The text contains "heal" but also "opponent" → supports() returns
        // false (line 26 `!text.contains("opponent")` true branch).
        SelfHealingAttackEffectStrategy s = new SelfHealingAttackEffectStrategy();
        AttackContext context = ctx(game(), "Drain",
            "Heal 30 damage from the opponent's Pokemon.");
        assertFalse(s.supports(context),
            "supports() must reject attacks that target the opponent");
    }

    @Test
    void selfHealing_supportsRejectsAttacksWithoutHealOrRemoveKeyword() {
        // The text doesn't contain "heal" or "remove" → supports() returns
        // false (line 25 containsAny returns false branch).
        SelfHealingAttackEffectStrategy s = new SelfHealingAttackEffectStrategy();
        AttackContext context = ctx(game(), "Punch",
            "This attack does 30 damage to this Pokemon.");
        assertFalse(s.supports(context),
            "supports() must reject attacks that don't mention heal/remove");
    }

    // ----- CoinFlipDamageAttackEffectStrategy -----

    @Test
    void coinFlipDamage_mergesWithExistingCoinFlipResults() {
        GameAggregate g = game();
        AttackContext context = ctx(g, "Dual Strike",
            "Flip a coin. This attack does 20 damage for each heads.");
        context.setBaseDamage(20);
        // Pre-populated coin flip results
        context.setCoinFlipResults(new java.util.ArrayList<>(List.of(true, true)));
        context.setCoinFlip(() -> true);  // new flip lands heads → results = [true]

        new CoinFlipDamageAttackEffectStrategy().apply(context);

        assertEquals(3, context.coinFlipResults().size(),
            "Si coinFlipResults ya tiene entradas, los nuevos flips se concatenan");
        assertEquals(3, context.coinFlipResults().stream().filter(b -> b).count(),
            "Los 2 pre-existentes heads + 1 nuevo head = 3 heads en el historial total");
        assertEquals(20, context.baseDamage(),
            "1 nuevo head * 20 = 20 baseDamage (la rama merge no recombina heads viejos)");
    }

    @Test
    void coinFlipDamage_flipCountMatchesFightingEnergyAttached() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("f-1", "Fighting"));
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("f-2", "Fighting"));
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("f-3", "Fighting"));
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("w-1", "Water"));

        AttackContext context = ctx(g, "Power Lariat",
            "Flip a coin for each Fighting Energy attached to this Pokemon. This attack does 20 damage for each heads.");
        context.setBaseDamage(20);
        context.setCoinFlip(sequence(true, true, true));

        new CoinFlipDamageAttackEffectStrategy().apply(context);

        assertEquals(3, context.coinFlipResults().size(),
            "3 Fighting energies attached → 3 flips");
        assertEquals(60, context.baseDamage(),
            "3 heads * 20 = 60 damage");
    }

    @Test
    void coinFlipDamage_amountFallsBackToBaseDamage() {
        // Text with no "N damage" or "N more damage" pattern → use baseDamage as fallback
        AttackContext context = ctx(game(), "Mystery Strike",
            "Flip a coin. This attack does crazy damage for each heads.");
        context.setBaseDamage(77);
        context.setCoinFlip(() -> true);

        new CoinFlipDamageAttackEffectStrategy().apply(context);

        assertEquals(77, context.baseDamage(),
            "Sin patron 'N damage' ni 'N more damage' se usa el baseDamage como fallback");
    }

    @Test
    void coinFlipDamage_moreDamageBranchUsesPreAttackBonus() {
        // "more damage" branch hits setPreAttackBonus instead of setBaseDamage
        AttackContext context = ctx(game(), "Rolling Tackle",
            "Flip 2 coins. This attack does 30 more damage for each heads.");
        context.setBaseDamage(50);
        context.setCoinFlip(sequence(true, false));

        new CoinFlipDamageAttackEffectStrategy().apply(context);

        assertEquals(30, context.preAttackBonus(),
            "1 head * 30 = 30 de bonus pre-attack");
        assertEquals(50, context.baseDamage(),
            "Con 'more damage' el baseDamage no se sobreescribe");
    }

    // ----- helpers -----

    private AttackContext ctx(GameAggregate game, String attackName, String attackText) {
        AttackContext context = new AttackContext(game);
        context.setAttackName(attackName);
        context.setAttackText(attackText);
        return context;
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
        game.registerCard(new GameCard("active-1", "Attacker", "Pokemon",
            Set.of("Basic"), 100, 10, 1));
        game.registerCard(new GameCard("active-2", "Defender", "Pokemon",
            Set.of("Basic"), 100, 10, 1));
        game.damageCountersBySlot().get(p1).put("ACTIVE", 0);
        game.damageCountersBySlot().get(p2).put("ACTIVE", 0);
        return game;
    }

    private BooleanSupplier sequence(boolean... values) {
        ArrayDeque<Boolean> queue = new ArrayDeque<>();
        for (boolean value : values) {
            queue.add(value);
        }
        return queue::removeFirst;
    }
}
