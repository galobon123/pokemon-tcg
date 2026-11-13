package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.BenchDamageAttackEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage for {@link BenchDamageAttackEffectStrategy}.
 *
 * Targets the 12 missed branches listed in the coverage report:
 *  (a) coin condition failure (returns early without mutating the game)
 *  (b/c) supports() returns false for "heal" and "remove damage counter" text
 *  (d) "each of your opponent" with an explicit target uses the single-target path
 *  (e) " 2 of your opponent" with explicit target collects 2 slots (resilience fill)
 *  (f) "each of your" (own bench) applies to the attacker's bench
 *  (g) targetSlot starts with "BENCH_" and is occupied, prepended to the chosen list
 *  (h) resilience fill adds remaining occupied bench slots to reach `targets`
 *  (i) chosen.size() > targets triggers the `applied >= targets` break
 */
class BenchDamageAttackEffectStrategyCoverageTest {

    private final BenchDamageAttackEffectStrategy s = new BenchDamageAttackEffectStrategy();

    // -----------------------------------------------------------------------
    // supports() — healing/removal text must NOT be classified as bench damage
    // -----------------------------------------------------------------------

    @Test
    void supportsReturnsFalseForHealText() {
        // "heal" branch in supports(): text mentions bench + damage, but heals → false
        AttackContext ctx = context(game(), "Massage",
            "Choose 1 of your Benched Pokemon. Heal 30 damage from that Pokemon.");
        assertFalse(s.supports(ctx));
    }

    @Test
    void supportsReturnsFalseForRemoveDamageCounterText() {
        // "remove" && "damage counter" branch in supports(): the text mentions bench + damage,
        // but the attack only removes damage counters from the bench → false
        AttackContext ctx = context(game(), "Cleanse",
            "Choose 1 of your opponent's Benched Pokemon. Remove 6 damage counters from that Pokemon.");
        assertFalse(s.supports(ctx));
    }

    // -----------------------------------------------------------------------
    // apply() — early returns: cancelled, coin condition
    // -----------------------------------------------------------------------

    @Test
    void applyDoesNothingWhenCoinConditionFails() {
        // Branch (a): `!coinConditionAllowsEffect(context)` returns early.
        // Text "If heads, this attack does 10 damage..." with a tails result → not allowed.
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).add("b1");
        AttackContext ctx = context(game, "Lucky Hit",
            "Flip a coin. If heads, this attack does 10 damage to 1 of your opponent's Benched Pokemon.");
        ctx.setCoinFlipResults(List.of(false));
        s.apply(ctx);
        assertEquals(0, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
    }

    @Test
    void applyDoesNothingWhenContextCancelled() {
        // "if tails, does nothing" pattern causes PreAttackStep to set cancelled=true.
        // We model that here by setting the flag directly.
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).add("b1");
        AttackContext ctx = context(game, "Risky Strike",
            "Flip a coin. If tails, this attack does nothing. This attack does 10 damage to 1 of your opponent's Benched Pokemon.");
        ctx.setCancelled(true);
        s.apply(ctx);
        assertEquals(0, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
    }

    // -----------------------------------------------------------------------
    // apply() — "each of your opponent" with explicit target (line 52-55)
    // -----------------------------------------------------------------------

    @Test
    void applyEachOfOpponentWithExplicitTargetUsesSingleTargetPath() {
        // Branch (d): text contains "each of your opponent" AND has an explicit target
        // → only the chosen slot takes damage, the rest of the bench is skipped.
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).addAll(List.of("b1", "b2", "b3"));
        AttackContext ctx = context(game, "Quake",
            "This attack does 10 damage to each of your opponent's Benched Pokemon.");
        ctx.setTargetSlot("BENCH_0");
        s.apply(ctx);
        assertEquals(1, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
        assertEquals(0, game.damageCountersAtSlot(game.playerTwo(), "BENCH_1"));
        assertEquals(0, game.damageCountersAtSlot(game.playerTwo(), "BENCH_2"));
        assertTrue(ctx.auditTrail().stream().anyMatch(a -> a.contains("bench_damage_single_counters=1")));
    }

    @Test
    void applyEachOfOpponentWithoutTargetHitsAllBenchSlots() {
        // No explicit target → loop over the entire bench (the else branch at line 57-61).
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).addAll(List.of("b1", "b2", "b3"));
        AttackContext ctx = context(game, "Quake",
            "This attack does 10 damage to each of your opponent's Benched Pokemon.");
        s.apply(ctx);
        assertEquals(1, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
        assertEquals(1, game.damageCountersAtSlot(game.playerTwo(), "BENCH_1"));
        assertEquals(1, game.damageCountersAtSlot(game.playerTwo(), "BENCH_2"));
        assertTrue(ctx.auditTrail().stream().anyMatch(a -> a.contains("bench_damage_each_counters=1")));
    }

    // -----------------------------------------------------------------------
    // apply() — "each of your" (own bench) branch (line 51 condition + iteration)
    // -----------------------------------------------------------------------

    @Test
    void applyEachOfOwnBenchAppliesToAttackerBench() {
        // Branch (f): "each of your" matches BOTH conditions, but the targetPlayer
        // resolves to the attacker because ownBench = true.
        GameAggregate game = game();
        game.bench().get(game.playerOne()).addAll(List.of("b1", "b2"));
        AttackContext ctx = context(game, "Self Quake",
            "This attack does 10 damage to each of your Benched Pokemon.");
        s.apply(ctx);
        assertEquals(1, game.damageCountersAtSlot(game.playerOne(), "BENCH_0"));
        assertEquals(1, game.damageCountersAtSlot(game.playerOne(), "BENCH_1"));
        assertEquals(0, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
    }

    // -----------------------------------------------------------------------
    // apply() — " 2 of your opponent" (targets = 2) explicit target (line 65)
    // -----------------------------------------------------------------------

    @Test
    void applyTwoOfOpponentWithExplicitTargetCollectsTwoSlots() {
        // Branch (e): text contains " 2 of your opponent" → targets = 2.
        // With selectedCardIds=[] but targetSlot="BENCH_0", chosen = ["BENCH_0"] at index 0
        // (line 74-77), then the resilience loop fills "BENCH_1" (line 80-85).
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).addAll(List.of("b1", "b2"));
        AttackContext ctx = context(game, "Hydro Bombard",
            "This attack does 20 damage to 2 of your opponent's Benched Pokemon.");
        ctx.setTargetSlot("BENCH_0");
        s.apply(ctx);
        assertEquals(2, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
        assertEquals(2, game.damageCountersAtSlot(game.playerTwo(), "BENCH_1"));
    }

    // -----------------------------------------------------------------------
    // apply() — targetSlot prepended at index 0 (line 74-77) — 1-target case
    // -----------------------------------------------------------------------

    @Test
    void applyTargetSlotOnlyPrependedAtIndexZeroForSingleTarget() {
        // Branch (g): targets=1, selectedCardIds empty, targetSlot valid → chosen=[targetSlot].
        // Confirms the prepend-at-index-zero path and the simple single-target resolution.
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).addAll(List.of("b1", "b2", "b3"));
        AttackContext ctx = context(game, "Snipe",
            "This attack does 10 damage to 1 of your opponent's Benched Pokemon.");
        ctx.setTargetSlot("BENCH_1");
        s.apply(ctx);
        assertEquals(0, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
        assertEquals(1, game.damageCountersAtSlot(game.playerTwo(), "BENCH_1"));
        assertEquals(0, game.damageCountersAtSlot(game.playerTwo(), "BENCH_2"));
    }

    // -----------------------------------------------------------------------
    // apply() — resilience fill when chosen.size() < targets (line 80-85)
    // -----------------------------------------------------------------------

    @Test
    void applyResilienceFillsRemainingOccupiedBenchSlots() {
        // Branch (h): " 2 of your opponent" + targetSlot set, but bench has 3 entries.
        // chosen starts as ["BENCH_0"] (from targetSlot), then resilience adds "BENCH_1"
        // (the next occupied slot not already in chosen).
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).addAll(List.of("b1", "b2", "b3"));
        AttackContext ctx = context(game, "Hydro Bombard",
            "This attack does 20 damage to 2 of your opponent's Benched Pokemon.");
        ctx.setTargetSlot("BENCH_0");
        s.apply(ctx);
        assertEquals(2, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
        assertEquals(2, game.damageCountersAtSlot(game.playerTwo(), "BENCH_1"));
        // The third bench entry must NOT have received damage (chosen.size() reached targets).
        assertEquals(0, game.damageCountersAtSlot(game.playerTwo(), "BENCH_2"));
    }

    // -----------------------------------------------------------------------
    // apply() — break when applied >= targets (line 92)
    // -----------------------------------------------------------------------

    @Test
    void applyBreaksAfterTargetCountReached() {
        // Branch (i): targets=1 but the client supplied 2 slot references via selectedCardIds.
        // The chosen list grows to ["BENCH_0", "BENCH_1"]; the loop applies damage to BENCH_0
        // and then hits `applied >= targets` (1 >= 1) → break, never touching BENCH_1.
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).addAll(List.of("b1", "b2"));
        AttackContext ctx = context(game, "Snipe",
            "This attack does 10 damage to 1 of your opponent's Benched Pokemon.");
        ctx.setSelectedCardIds(List.of("BENCH_0", "BENCH_1"));
        s.apply(ctx);
        assertEquals(1, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
        assertEquals(0, game.damageCountersAtSlot(game.playerTwo(), "BENCH_1"));
        assertTrue(ctx.auditTrail().stream().anyMatch(a -> a.contains("bench_damage_counters=1,targets=1")));
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
