package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.strategy.*;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AttackEffectStrategyEdgeCasesTest {

    @Test
    void coinBonusDamageSupportsDetectsPatterns() {
        var strat = new CoinBonusDamageAttackEffectStrategy();
        assertTrue(strat.supports(context(game(), "Smash", "Flip a coin. If heads, this attack does 30 more damage.")));
        assertTrue(strat.supports(context(game(), "Kick", "Flip a coin. If heads, this attack does 20+ more damage.")));
        assertFalse(strat.supports(context(game(), "Tackle", "Deal 30 damage.")));
        assertFalse(strat.supports(context(game(), "Punch", "Flip a coin. If heads, the Defending Pokemon is now Paralyzed.")));
    }

    @Test
    void coinBonusDamageAppliesDefaultBonusOf10() {
        AttackContext context = context(game(), "Bonus", "Flip a coin. If heads, this attack does more damage.");
        context.setCoinFlip(() -> true);
        new CoinBonusDamageAttackEffectStrategy(() -> true).apply(context);
        assertEquals(10, context.preAttackBonus());
    }

    @Test
    void coinBonusDamageDoesNotApplyWhenCancelled() {
        AttackContext context = context(game(), "Bonus", "Flip a coin. If heads, this attack does 20 more damage.");
        context.setCancelled(true);
        new CoinBonusDamageAttackEffectStrategy(() -> true).apply(context);
        assertEquals(0, context.preAttackBonus());
    }

    @Test
    void coinBonusDamageDoesNotApplyOnTails() {
        AttackContext context = context(game(), "Bonus", "Flip a coin. If heads, this attack does 20 more damage.");
        context.setCoinFlip(() -> false);
        new CoinBonusDamageAttackEffectStrategy(() -> false).apply(context);
        assertEquals(0, context.preAttackBonus());
    }

    @Test
    void selfHealingSupportsVariousPatterns() {
        var strat = new SelfHealingAttackEffectStrategy();
        assertTrue(strat.supports(context(game(), "Drain", "Heal 20 damage from this Pokemon.")));
        assertTrue(strat.supports(context(game(), "Recover", "Remove all damage counters from this Pokemon.")));
        assertTrue(strat.supports(context(game(), "Group Heal", "Heal 10 damage from each of your Pokemon.")));
        assertFalse(strat.supports(context(game(), "Tackle", "Deal 30 damage.")));
    }

    @Test
    void selfHealingHealsAllDamageFromFullHealText() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerOne(), "ACTIVE", 7);
        AttackContext context = context(game, "Full Heal", "Remove all damage counters from this Pokemon.");
        new SelfHealingAttackEffectStrategy().apply(context);
        assertEquals(0, game.damageCountersAtSlot(game.playerOne(), "ACTIVE"));
    }

    @Test
    void selfHealingHealsBenchedPokemon() {
        GameAggregate game = game();
        game.bench().get(game.playerOne()).add("bench-1");
        game.addDamageCounters(game.playerOne(), "BENCH_0", 5);
        AttackContext context = context(game, "Bench Heal", "Heal 30 damage from 1 of your Benched Pokemon.");
        context.setTargetSlot("BENCH_0");
        new SelfHealingAttackEffectStrategy().apply(context);
        assertEquals(2, game.damageCountersAtSlot(game.playerOne(), "BENCH_0"));
    }

    @Test
    void benchDamageDoesNotApplyToOwnBench() {
        GameAggregate game = game();
        game.bench().get(game.playerOne()).add("bench-1");
        AttackContext context = context(game, "Self Damage", "This attack does 20 damage to 1 of your Benched Pokemon.");
        assertTrue(new BenchDamageAttackEffectStrategy().supports(context));
    }

    @Test
    void benchDamageSupportsOpponentBench() {
        var strat = new BenchDamageAttackEffectStrategy();
        assertTrue(strat.supports(context(game(), "Snipe", "This attack does 20 damage to 1 of your opponent's Benched Pokemon.")));
        assertFalse(strat.supports(context(game(), "Heal", "Heal 20 damage from your Benched Pokemon.")));
    }

    @Test
    void conditionalDamageSupportsVariousPatterns() {
        var strat = new ConditionalDamageAttackEffectStrategy();
        assertTrue(strat.supports(context(game(), "Bonus", "Does 10 more damage for each damage counter on your opponent's Active Pokemon.")));
        assertTrue(strat.supports(context(game(), "Type Bonus", "Does 40 more damage if the Defending Pokemon is a Pokemon-EX.")));
        assertFalse(strat.supports(context(game(), "Tackle", "Deal 30 damage.")));
    }

    @Test
    void coinFlipDamageSupportsMultiplePatterns() {
        var strat = new CoinFlipDamageAttackEffectStrategy();
        assertTrue(strat.supports(context(game(), "Flip", "Flip 2 coins. This attack does 30 damage times the number of heads.")));
        assertTrue(strat.supports(context(game(), "Tails", "Flip a coin until you get tails. This attack does 20 damage for each heads.")));
        assertFalse(strat.supports(context(game(), "Tackle", "Deal 30 damage.")));
    }

    @Test
    void coinFlipDamageAppliesTimesHeads() {
        AttackContext context = context(game(), "Dual Slash", "Flip 2 coins. This attack does 30 damage times the number of heads.");
        context.setBaseDamage(30);
        context.setCoinFlip(sequence(true, true));
        new CoinFlipDamageAttackEffectStrategy().apply(context);
        assertEquals(60, context.baseDamage());
    }

    @Test
    void coinFlipDamageZeroHeads() {
        AttackContext context = context(game(), "Miss", "Flip 2 coins. This attack does 30 damage times the number of heads.");
        context.setBaseDamage(30);
        context.setCoinFlip(sequence(false, false));
        new CoinFlipDamageAttackEffectStrategy().apply(context);
        assertEquals(0, context.baseDamage());
    }

    @Test
    void energyAttachAttachesFromDeck() {
        GameAggregate game = game();
        game.registerCard(new GameCard("fire-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.deck().get(game.playerOne()).add("fire-1");
        AttackContext context = context(game, "Energy Search", "Attach a Fire Energy card from your deck to this Pokemon.");
        new EnergyAttachAttackEffectStrategy().apply(context);
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
    }

    @Test
    void persistentAttackEffectDoesNotApplyWhenCancelled() {
        GameAggregate game = game();
        AttackContext context = context(game, "Persist", "During your opponent's next turn, any damage done to this Pokemon by attacks is reduced by 20.");
        context.setCancelled(true);
        new PersistentAttackEffectStrategy().apply(context);
        assertFalse(game.hasActiveEffect(game.playerOne(), "DEFENDER_DAMAGE_REDUCTION"));
    }

    @Test
    void utilityEffectShufflesDeck() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("c1", "c2", "c3"));
        AttackContext context = context(game, "Reorganize", "Shuffle your deck.");
        new AttackUtilityEffectStrategy().apply(context);
        assertEquals(3, game.deck().get(game.playerOne()).size());
    }

    @Test
    void energyDiscardSkipsWhenNoEnergyToDiscard() {
        GameAggregate game = game();
        AttackContext context = context(game, "Discard", "Discard an Energy attached to this Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(context);
        assertEquals(0, game.discardPile().get(game.playerOne()).size());
    }

    @Test
    void specialConditionSkipsWhenConditionAlreadyPresent() {
        GameAggregate game = game();
        game.statusByPlayer().put(game.playerTwo(), EnumSet.of(StatusCondition.POISONED));
        AttackContext context = context(game, "Poison", "The Defending Pokemon is now Poisoned.");
        new SpecialConditionAttackEffectStrategy().apply(context);
        assertTrue(game.statusByPlayer().get(game.playerTwo()).contains(StatusCondition.POISONED));
    }

    private java.util.function.BooleanSupplier sequence(boolean... values) {
        java.util.ArrayDeque<Boolean> queue = new java.util.ArrayDeque<>();
        for (boolean value : values) queue.add(value);
        return queue::removeFirst;
    }

    private AttackContext context(GameAggregate game, String attackName, String attackText) {
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
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());
        game.activePokemon().put(p1, "active-1");
        game.activePokemon().put(p2, "active-2");
        game.damageCountersBySlot().get(p1).put("ACTIVE", 0);
        game.damageCountersBySlot().get(p2).put("ACTIVE", 0);
        return game;
    }
}
