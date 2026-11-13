package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.chain.ModifierStep;
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

class AttackEffectStrategyUncoveredPatternsTest {

    @Test
    void conditionalDamage_lunatoneOnBench() {
        GameAggregate game = game();
        game.registerCard(new GameCard("lunatone-1", "Lunatone", "Pokemon", Set.of("Basic"), 70, 10, 1));
        game.bench().get(game.playerOne()).add("lunatone-1");
        AttackContext context = context(game, "Moon Blast",
            "If Lunatone is on your bench, this attack does 30 more damage.");
        new ConditionalDamageAttackEffectStrategy().apply(context);
        assertEquals(30, context.preAttackBonus());
    }

    @Test
    void conditionalDamage_alreadyHasDamageCounters() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerTwo(), "ACTIVE", 2);
        AttackContext context = context(game, "Pressure",
            "If your opponent's Active Pokemon already has any damage counters on it, this attack does 40 more damage.");
        new ConditionalDamageAttackEffectStrategy().apply(context);
        assertEquals(40, context.preAttackBonus());
    }

    @Test
    void conditionalDamage_affectedBySpecialCondition() {
        GameAggregate game = game();
        game.statusByPlayer().put(game.playerTwo(), EnumSet.of(StatusCondition.POISONED));
        AttackContext context = context(game, "Purify",
            "If your opponent's Active Pokemon is affected by a Special Condition, this attack does 60 more damage.");
        new ConditionalDamageAttackEffectStrategy().apply(context);
        assertEquals(60, context.preAttackBonus());
    }

    @Test
    void conditionalDamage_affectedBySpecialConditionRemovesCondition() {
        GameAggregate game = game();
        game.statusByPlayer().put(game.playerTwo(), EnumSet.of(StatusCondition.POISONED));
        AttackContext context = context(game, "Purify",
            "If your opponent's Active Pokemon is affected by a Special Condition, this attack does 60 more damage. Remove all Special Conditions.");
        new ConditionalDamageAttackEffectStrategy().apply(context);
        assertFalse(game.statusByPlayer().containsKey(game.playerTwo()));
    }

    @Test
    void conditionalDamage_forEachDamageCounterOnThis() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerOne(), "ACTIVE", 3);
        AttackContext context = context(game, "Revenge",
            "Does 10 more damage for each damage counter on this Pokemon.");
        new ConditionalDamageAttackEffectStrategy().apply(context);
        assertEquals(30, context.preAttackBonus());
    }

    @Test
    void conditionalDamage_forEachFireEnergy() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("fire-1", "Fire"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("fire-2", "Fire"));
        AttackContext context = context(game, "Fire Blast",
            "Does 20 more damage for each Fire Energy attached to this Pokemon.");
        new ConditionalDamageAttackEffectStrategy().apply(context);
        assertEquals(40, context.preAttackBonus());
    }

    @Test
    void conditionalDamage_hasPsychicEnergy() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("psychic-1", "Psychic"));
        AttackContext context = context(game, "Psy Strike",
            "If this Pokemon has any Psychic Energy attached, this attack does 30 more damage.");
        new ConditionalDamageAttackEffectStrategy().apply(context);
        assertEquals(30, context.preAttackBonus());
    }

    @Test
    void conditionalDamage_forEachDifferentBasicEnergyType() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("fire-1", "Fire"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("water-1", "Water"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("grass-1", "Grass"));
        AttackContext context = context(game, "Rainbow Blast",
            "Does 30 more damage for each different type of Basic Energy attached to this Pokemon.");
        new ConditionalDamageAttackEffectStrategy().apply(context);
        assertEquals(90, context.preAttackBonus());
    }

    @Test
    void conditionalDamage_timesEnergyAttachedToThis() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("fire-1", "Fire"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("fire-2", "Fire"));
        AttackContext context = context(game, "Energy Burst",
            "Does 20 damage times the amount of Energy attached to this Pokemon.");
        new ConditionalDamageAttackEffectStrategy().apply(context);
        assertEquals(40, context.baseDamage());
    }

    @Test
    void coinFlipDamage_untilYouGetTails() {
        AttackContext context = context(game(), "Tails Spin",
            "Flip a coin until you get tails. This attack does 20 damage for each heads.");
        context.setBaseDamage(20);
        context.setCoinFlip(sequence(true, true, false));
        new CoinFlipDamageAttackEffectStrategy().apply(context);
        assertEquals(40, context.baseDamage());
    }

    @Test
    void coinFlipDamage_forEachDamageCounterFlips() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerOne(), "ACTIVE", 3);
        AttackContext context = context(game, "Rage",
            "Flip a coin for each damage counter on this Pokemon. This attack does 20 damage for each heads.");
        context.setCoinFlip(sequence(true, false, true));
        new CoinFlipDamageAttackEffectStrategy().apply(context);
        assertEquals(40, context.baseDamage());
    }

    @Test
    void coinFlipDamage_preventAllEffectsWhenAllHeads() {
        GameAggregate game = game();
        game.setTurnNumber(3);
        AttackContext context = context(game, "Flash Shield",
            "Flip 2 coins. If both of them are heads, prevent all effects of attacks done to this Pokemon during your opponent's next turn.");
        context.setCoinFlip(sequence(true, true));
        new CoinFlipDamageAttackEffectStrategy().apply(context);
        assertTrue(game.hasActiveEffect(game.playerOne(), ModifierStep.DEFENDER_DAMAGE_REDUCTION));
    }

    @Test
    void selfHealing_healsEachOfYour() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerOne(), "ACTIVE", 4);
        game.bench().get(game.playerOne()).add("bench-1");
        game.addDamageCounters(game.playerOne(), "BENCH_0", 2);
        AttackContext context = context(game, "Group Heal",
            "Heal 20 damage from each of your Pokemon.");
        new SelfHealingAttackEffectStrategy().apply(context);
        assertEquals(2, game.damageCountersAtSlot(game.playerOne(), "ACTIVE"));
        assertEquals(0, game.damageCountersAtSlot(game.playerOne(), "BENCH_0"));
    }

    @Test
    void selfHealing_healsEachOfYourRemovesSpecialConditions() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerOne(), "ACTIVE", 2);
        game.statusByPlayer().put(game.playerOne(), EnumSet.of(StatusCondition.POISONED));
        AttackContext context = context(game, "Full Heal",
            "Heal 20 damage from each of your Pokemon. Remove all Special Conditions.");
        new SelfHealingAttackEffectStrategy().apply(context);
        assertFalse(game.statusByPlayer().containsKey(game.playerOne()));
    }

    @Test
    void selfHealing_healsFromBenched() {
        GameAggregate game = game();
        game.bench().get(game.playerOne()).add("bench-1");
        game.addDamageCounters(game.playerOne(), "BENCH_0", 6);
        AttackContext context = context(game, "Bench Massage",
            "Remove 6 damage counters from 1 of your Benched Pokemon.");
        context.setTargetSlot("BENCH_0");
        new SelfHealingAttackEffectStrategy().apply(context);
        assertEquals(0, game.damageCountersAtSlot(game.playerOne(), "BENCH_0"));
    }

    @Test
    void selfHealing_usesFirstOccupiedBenchAsFallback() {
        GameAggregate game = game();
        game.bench().get(game.playerOne()).add("bench-1");
        game.addDamageCounters(game.playerOne(), "BENCH_0", 3);
        AttackContext context = context(game, "Auto Heal",
            "Heal 30 damage from 1 of your Benched Pokemon.");
        new SelfHealingAttackEffectStrategy().apply(context);
        assertEquals(0, game.damageCountersAtSlot(game.playerOne(), "BENCH_0"));
    }

    @Test
    void selfHealing_healsFromPokemonWhenTextSays1OfYourPokemon() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerOne(), "ACTIVE", 5);
        AttackContext context = context(game, "Scent",
            "Heal 30 damage from 1 of your Pokemon.");
        new SelfHealingAttackEffectStrategy().apply(context);
        assertEquals(2, game.damageCountersAtSlot(game.playerOne(), "ACTIVE"));
    }

    @Test
    void benchDamage_appliesToOwnBench() {
        GameAggregate game = game();
        game.bench().get(game.playerOne()).add("bench-1");
        AttackContext context = context(game, "Self Splash",
            "This attack does 10 damage to 1 of your Benched Pokemon.");
        context.setTargetSlot("BENCH_0");
        new BenchDamageAttackEffectStrategy().apply(context);
        assertEquals(1, game.damageCountersAtSlot(game.playerOne(), "BENCH_0"));
    }

    @Test
    void benchDamage_appliesToEachOpponentBench() {
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).addAll(List.of("b1", "b2", "b3"));
        AttackContext context = context(game, "Earthquake",
            "This attack does 10 damage to each of your opponent's Benched Pokemon.");
        new BenchDamageAttackEffectStrategy().apply(context);
        assertEquals(1, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
        assertEquals(1, game.damageCountersAtSlot(game.playerTwo(), "BENCH_1"));
        assertEquals(1, game.damageCountersAtSlot(game.playerTwo(), "BENCH_2"));
    }

    @Test
    void benchDamage_resilienceFillsMissingTargets() {
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).addAll(List.of("b1", "b2"));
        AttackContext context = context(game, "Hydro Bombard",
            "This attack does 20 damage to 2 of your opponent's Benched Pokemon.");
        context.setTargetSlot("BENCH_0");
        new BenchDamageAttackEffectStrategy().apply(context);
        assertEquals(2, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
    }

    @Test
    void benchDamage_noTargetWhenBenchEmpty() {
        GameAggregate game = game();
        AttackContext context = context(game, "Snipe",
            "This attack does 20 damage to 1 of your opponent's Benched Pokemon.");
        new BenchDamageAttackEffectStrategy().apply(context);
        assertEquals(0, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
    }

    @Test
    void energyAttach_fromHand() {
        GameAggregate game = game();
        game.registerCard(new GameCard("fire-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.hand().get(game.playerOne()).add("fire-1");
        AttackContext context = context(game, "Manual Attach",
            "Attach a Fire Energy card from your hand to this Pokemon.");
        new EnergyAttachAttackEffectStrategy().apply(context);
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
    }

    @Test
    void energyAttach_toBench() {
        GameAggregate game = game();
        game.registerCard(new GameCard("water-1", "Water Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Water", List.of(), 0));
        game.deck().get(game.playerOne()).add("water-1");
        game.bench().get(game.playerOne()).add("bench-1");
        AttackContext context = context(game, "Water Drop",
            "Attach a Water Energy card from your deck to 1 of your Benched Pokemon.");
        context.setTargetSlot("BENCH_0");
        new EnergyAttachAttackEffectStrategy().apply(context);
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
    }

    @Test
    void energyAttach_requiredTypeFilter() {
        GameAggregate game = game();
        game.registerCard(new GameCard("fire-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.registerCard(new GameCard("water-1", "Water Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Water", List.of(), 0));
        game.deck().get(game.playerOne()).addAll(List.of("fire-1", "water-1"));
        AttackContext context = context(game, "Water Search",
            "Attach a Water Energy card from your deck to this Pokemon.");
        new EnergyAttachAttackEffectStrategy().apply(context);
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
        assertEquals("Water", game.attachedEnergiesFor(game.playerOne(), "ACTIVE").get(0).energyType());
    }

    @Test
    void energyAttach_basicOnlyFilter() {
        GameAggregate game = game();
        game.registerCard(new GameCard("special-1", "Double Colorless Energy", "Energy", Set.of("Special"), 0, 0, 0, "Colorless", List.of(), 0));
        game.registerCard(new GameCard("basic-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.deck().get(game.playerOne()).addAll(List.of("special-1", "basic-1"));
        AttackContext context = context(game, "Basic Search",
            "Attach a Basic Energy card from your deck to this Pokemon.");
        new EnergyAttachAttackEffectStrategy().apply(context);
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
    }

    @Test
    void energyAttach_noSourceCardsDoesNothing() {
        GameAggregate game = game();
        AttackContext context = context(game, "Empty Search",
            "Attach a Fire Energy card from your deck to this Pokemon.");
        new EnergyAttachAttackEffectStrategy().apply(context);
        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
    }

    @Test
    void selfHealing_cancelledDoesNothing() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerOne(), "ACTIVE", 3);
        AttackContext context = context(game, "Heal", "Heal 20 damage from this Pokemon.");
        context.setCancelled(true);
        new SelfHealingAttackEffectStrategy().apply(context);
        assertEquals(3, game.damageCountersAtSlot(game.playerOne(), "ACTIVE"));
    }

    @Test
    void benchDamage_cancelledDoesNothing() {
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).add("b1");
        AttackContext context = context(game, "Snipe", "Does 10 damage to 1 of your opponent's Benched Pokemon.");
        context.setCancelled(true);
        new BenchDamageAttackEffectStrategy().apply(context);
        assertEquals(0, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
    }

    @Test
    void conditionalDamage_cancelledDoesNothing() {
        AttackContext context = context(game(), "Conditional", "Does 10 more damage for each damage counter on your opponent's Active Pokemon.");
        context.setCancelled(true);
        new ConditionalDamageAttackEffectStrategy().apply(context);
        assertEquals(0, context.preAttackBonus());
    }

    @Test
    void energyAttach_cancelledDoesNothing() {
        GameAggregate game = game();
        game.registerCard(new GameCard("fire-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.deck().get(game.playerOne()).add("fire-1");
        AttackContext context = context(game, "Energy", "Attach a Fire Energy card from your deck to this Pokemon.");
        context.setCancelled(true);
        new EnergyAttachAttackEffectStrategy().apply(context);
        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
    }

    @Test
    void coinFlipDamage_moreDamagePattern() {
        AttackContext context = context(game(), "Coin Bonus",
            "Flip a coin. If heads, this attack does 30 more damage.");
        context.setCoinFlip(() -> true);
        new CoinFlipDamageAttackEffectStrategy().apply(context);
        assertEquals(30, context.preAttackBonus());
    }

    @Test
    void conditionalDamage_forEachWaterEnergy() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("water-1", "Water"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("water-2", "Water"));
        AttackContext context = context(game, "Hydro Pump",
            "Does 20 more damage for each Water Energy attached to this Pokemon.");
        new ConditionalDamageAttackEffectStrategy().apply(context);
        assertEquals(40, context.preAttackBonus());
    }

    @Test
    void benchDamage_appliesToEachOwnBench() {
        GameAggregate game = game();
        game.bench().get(game.playerOne()).addAll(List.of("b1", "b2"));
        AttackContext context = context(game, "Self Splash All",
            "This attack does 10 damage to each of your Benched Pokemon.");
        new BenchDamageAttackEffectStrategy().apply(context);
        assertEquals(1, game.damageCountersAtSlot(game.playerOne(), "BENCH_0"));
        assertEquals(1, game.damageCountersAtSlot(game.playerOne(), "BENCH_1"));
    }

    @Test
    void conditionalDamage_youMayDoMoreDamage() {
        AttackContext context = context(game(), "Optional",
            "You may do 30 more damage. If you do, this Pokemon is now Asleep.");
        context.setAcceptedOptionalEffect(true);
        assertTrue(new ConditionalDamageAttackEffectStrategy().supports(context));
        new ConditionalDamageAttackEffectStrategy().apply(context);
        assertEquals(30, context.preAttackBonus());
    }

    @Test
    void conditionalDamage_supportsAdditionalDamagePattern() {
        AttackContext context = context(game(), "Extra",
            "You may do 20 additional damage.");
        assertTrue(new ConditionalDamageAttackEffectStrategy().supports(context));
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
