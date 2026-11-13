package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.ConfusionCheckStep;
import com.utn.pokemontcg.game.domain.chain.*;
import com.utn.pokemontcg.game.domain.exception.InsufficientEnergyException;
import com.utn.pokemontcg.game.domain.exception.GameRuleException;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.strategy.AttackEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.AttackUtilityEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.BenchDamageAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.ConditionalDamageAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.DefaultAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.EnergyDiscardAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.PersistentAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.SelfHealingAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.SpecialConditionAttackEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackPipelineTest {

    @Test
    void confusionCheckSkipsWhenNotConfused() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setConfused(false);
        context.setBaseDamage(50);
        new ConfusionCheckStep().execute(context);
        assertFalse(context.cancelled());
        assertEquals(0, context.selfDamage());
    }

    @Test
    void defaultEffectHasNoSpecialEffect() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        new DefaultAttackEffectStrategy().apply(context);
        assertTrue(context.auditTrail().stream().anyMatch(e -> e.contains("no_special_effect")));
    }

    @Test
    void defaultEffectSupportsAnything() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        assertTrue(new DefaultAttackEffectStrategy().supports(context));
    }

    @Test
    void shouldCancelAttackWhenEnergyIsInsufficient() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setRequiredEnergy(2);
        context.setAttachedEnergy(1);
        context.setBaseDamage(50);

        new AttackResolutionPipeline(List.of(
            new EnergyValidationStep(),
            new DamageCalculationStep()
        )).resolve(context);

        assertTrue(context.cancelled());
        assertEquals(0, context.damage());
    }

    @Test
    void shouldCalculateDamageWithWeaknessAndResistance() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setRequiredEnergy(1);
        context.setAttachedEnergy(1);
        context.setBaseDamage(40);
        context.setWeaknessMultiplier(2);
        context.setResistanceReduction(20);

        new AttackResolutionPipeline(List.of(
            new EnergyValidationStep(),
            new ModifierStep(),
            new DamageCalculationStep(),
            new PostDamageEffectsStep()
        )).resolve(context);

        assertEquals(60, context.damage());
        assertTrue(context.postEffectsApplied());
    }

    @Test
    void preDamageConditionalBonusIsAppliedBeforeWeakness() {
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        GameAggregate game = new GameAggregate(attacker);
        game.setPlayerTwo(defender);
        game.setCurrentTurnPlayer(attacker);
        game.initializeZonesFor(attacker, List.of());
        game.initializeZonesFor(defender, List.of());
        game.addDamageCounters(defender, "ACTIVE", 1);

        GameCard attackerCard = new GameCard("attacker", "Skarmory-EX", "Pokemon", Set.of("Basic", "EX"), 170, 80, 0, "Tailspin Piledriver", "If your opponent's Active Pokemon already has any damage counters on it, this attack does 40 more damage.", "Metal", List.of(), 0, null, List.of(), null, null, null, null, List.of());
        GameCard defenderCard = new GameCard("defender", "Weak Defender", "Pokemon", Set.of("Basic"), 100, 0, 0, null, null, "Grass", List.of(), 0, null, List.of(), "Metal", 2, null, null, List.of());
        AttackContext context = new AttackContext(game);
        context.setAttackerCard(attackerCard);
        context.setDefenderCard(defenderCard);
        context.setAttackName(attackerCard.attackName());
        context.setAttackText(attackerCard.attackText());
        context.setBaseDamage(80);

        AttackEffectRegistry registry = new AttackEffectRegistry(List.of(new ConditionalDamageAttackEffectStrategy(), new DefaultAttackEffectStrategy()));
        new AttackResolutionPipeline(List.of(
            new EnergyValidationStep(),
            new PreDamageEffectsStep(registry),
            new DamageCalculationStep()
        )).resolve(context);

        assertEquals(240, context.damage());
    }

    @Test
    void shouldRejectAttackWhenTypedEnergyIsMissing() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setAttackName("Flame Tail");
        context.setRequiredEnergyCost(List.of("Fire", "Colorless"));
        context.setAttachedEnergies(List.of(new AttachedEnergy("water-1", "Water")));
        context.setBaseDamage(60);

        assertThrows(InsufficientEnergyException.class, () -> new EnergyValidationStep().execute(context));
    }

    @Test
    void shouldPayColorlessWithAnyRemainingEnergy() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setRequiredEnergyCost(List.of("Fire", "Colorless"));
        context.setAttachedEnergies(List.of(
            new AttachedEnergy("fire-1", "Fire"),
            new AttachedEnergy("water-1", "Water")
        ));

        new EnergyValidationStep().execute(context);

        assertTrue(context.auditTrail().contains("EnergyValidationStep:ok"));
    }

    @Test
    void preAttackCoinFlipAddsBonusForEachHeads() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setRequiredEnergy(0);
        context.setAttachedEnergy(0);
        context.setAttackText("Flip 2 coins. This attack does 20 more damage for each heads.");
        context.setBaseDamage(30);
        context.setCoinFlip(sequence(true, false));

        new AttackResolutionPipeline(List.of(
            new EnergyValidationStep(),
            new PreAttackStep(),
            new DamageCalculationStep()
        )).resolve(context);

        assertEquals(List.of(true, false), context.coinFlipResults());
        assertEquals(20, context.preAttackBonus());
        assertEquals(50, context.damage());
    }

    @Test
    void preAttackCoinFlipCancelsAttackWhenTailsDoesNothing() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setRequiredEnergy(0);
        context.setAttachedEnergy(0);
        context.setAttackText("Flip a coin. If tails, this attack does nothing.");
        context.setBaseDamage(40);
        context.setCoinFlip(() -> false);

        new AttackResolutionPipeline(List.of(
            new EnergyValidationStep(),
            new PreAttackStep(),
            new DamageCalculationStep()
        )).resolve(context);

        assertTrue(context.cancelled());
        assertEquals(0, context.damage());
    }

    @Test
    void selectionStepRejectsAttackThatRequiresTargetWithoutTargetSlot() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setAttackText("Choose 1 of your opponent's Benched Pokemon. This attack does 30 damage to that Pokemon.");

        GameRuleException exception = assertThrows(GameRuleException.class, () -> new SelectionStep().execute(context));

        assertEquals("TARGET_REQUIRED", exception.code());
    }

    @Test
    void selectionStepAcceptsAttackThatRequiresTargetWithTargetSlot() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setAttackText("Choose 1 of your opponent's Benched Pokemon. This attack does 30 damage to that Pokemon.");
        context.setTargetSlot("BENCH_0");

        new SelectionStep().execute(context);

        assertTrue(context.targetSelected());
    }

    @Test
    void modifierStepAppliesAndClearsOneTurnActiveEffects() {
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        GameAggregate game = new GameAggregate(attacker);
        game.setPlayerTwo(defender);
        game.setCurrentTurnPlayer(attacker);
        game.addActiveEffect(attacker, ModifierStep.ATTACKER_DAMAGE_BONUS, 20);
        game.addActiveEffect(defender, ModifierStep.DEFENDER_DAMAGE_REDUCTION, 10);
        AttackContext context = new AttackContext(game);
        context.setRequiredEnergy(0);
        context.setAttachedEnergy(0);
        context.setBaseDamage(50);

        new AttackResolutionPipeline(List.of(
            new EnergyValidationStep(),
            new ModifierStep(),
            new DamageCalculationStep()
        )).resolve(context);

        assertEquals(20, context.attackerModifier());
        assertEquals(-10, context.defenderModifier());
        assertEquals(60, context.damage());
        assertFalse(game.activeEffects().get(attacker).containsKey(ModifierStep.ATTACKER_DAMAGE_BONUS));
        assertFalse(game.activeEffects().get(defender).containsKey(ModifierStep.DEFENDER_DAMAGE_REDUCTION));
    }

    @Test
    void poisonPowderDealsDamageAndPoisonsDefender() {
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        GameAggregate game = gameWithActivePokemon(attacker, defender, venusaur("Poison Powder", "Your opponent's Active Pokemon is now Poisoned.", 60, List.of("Grass", "Colorless", "Colorless")));
        AttackContext context = venusaurContext(game, 60, "Poison Powder", "Your opponent's Active Pokemon is now Poisoned.", List.of("Grass", "Colorless", "Colorless"));

        new AttackResolutionPipeline(List.of(
            new EnergyValidationStep(),
            new ConfusionCheckStep(),
            new SelectionStep(),
            new PreAttackStep(),
            new ModifierStep(),
            new DamageCalculationStep(),
            new PostDamageEffectsStep(attackEffects())
        )).resolve(context);

        assertEquals(60, context.damage());
        assertTrue(game.statusByPlayer().getOrDefault(defender, EnumSet.noneOf(StatusCondition.class)).contains(StatusCondition.POISONED));
    }

    @Test
    void jungleHammerDealsDamageAndHealsAttacker() {
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        GameAggregate game = gameWithActivePokemon(attacker, defender, venusaur("Jungle Hammer", "Heal 30 damage from this Pokemon.", 90, List.of("Grass", "Grass", "Colorless", "Colorless")));
        game.addDamageCounters(attacker, "ACTIVE", 5);
        AttackContext context = venusaurContext(game, 90, "Jungle Hammer", "Heal 30 damage from this Pokemon.", List.of("Grass", "Grass", "Colorless", "Colorless"));

        new AttackResolutionPipeline(List.of(
            new EnergyValidationStep(),
            new ConfusionCheckStep(),
            new SelectionStep(),
            new PreAttackStep(),
            new ModifierStep(),
            new DamageCalculationStep(),
            new PostDamageEffectsStep(attackEffects())
        )).resolve(context);

        assertEquals(90, context.damage());
        assertEquals(2, game.damageCountersAtSlot(attacker, "ACTIVE"));
    }

    @Test
    void recoverHealsAllDamageThroughPipeline() {
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        GameAggregate game = gameWithActivePokemon(attacker, defender, venusaur("Recover", "Discard a [W] Energy attached to this Pokémon. If you do, remove all damage counters from this Pokémon.", 0, List.of("Water")));
        game.addDamageCounters(attacker, "ACTIVE", 7);
        AttackContext context = venusaurContext(game, 0, "Recover", "Discard a [W] Energy attached to this Pokémon. If you do, remove all damage counters from this Pokémon.", List.of("Water"));
        context.setAttachedEnergies(List.of(
            new AttachedEnergy("water-1", "Water"),
            new AttachedEnergy("water-2", "Water"),
            new AttachedEnergy("colorless-1", "Colorless"),
            new AttachedEnergy("colorless-2", "Colorless")
        ));

        new AttackResolutionPipeline(List.of(
            new EnergyValidationStep(),
            new ConfusionCheckStep(),
            new SelectionStep(),
            new PreAttackStep(),
            new ModifierStep(),
            new DamageCalculationStep(),
            new PostDamageEffectsStep(attackEffects())
        )).resolve(context);

        assertEquals(0, context.damage());
        assertEquals(0, game.damageCountersAtSlot(attacker, "ACTIVE"));
    }

    private BooleanSupplier sequence(boolean... values) {
        ArrayDeque<Boolean> queue = new ArrayDeque<>();
        for (boolean value : values) {
            queue.add(value);
        }
        return queue::removeFirst;
    }

    @Test
    void recoverDiscardsEnergyAndHealsAllDamageWithFullStrategyList() {
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        GameAggregate game = gameWithActivePokemon(attacker, defender, venusaur("Recover", "Discard a [W] Energy attached to this Pokémon. If you do, remove all damage counters from this Pokémon.", 0, List.of("Water")));
        game.addDamageCounters(attacker, "ACTIVE", 7);
        game.attachEnergy(attacker, "ACTIVE", new AttachedEnergy("water-1", "Water"));
        game.attachEnergy(attacker, "ACTIVE", new AttachedEnergy("colorless-1", "Colorless"));
        AttackContext context = venusaurContext(game, 0, "Recover", "Discard a [W] Energy attached to this Pokémon. If you do, remove all damage counters from this Pokémon.", List.of("Water"));
        context.setAttachedEnergies(List.of(
            new AttachedEnergy("water-1", "Water"),
            new AttachedEnergy("water-2", "Water"),
            new AttachedEnergy("colorless-1", "Colorless"),
            new AttachedEnergy("colorless-2", "Colorless")
        ));
        context.setSelectedEnergyCardIds(List.of("water-1"));

        new AttackResolutionPipeline(List.of(
            new EnergyValidationStep(),
            new ConfusionCheckStep(),
            new SelectionStep(),
            new PreAttackStep(),
            new ModifierStep(),
            new DamageCalculationStep(),
            new PostDamageEffectsStep(fullAttackEffects())
        )).resolve(context);

        assertEquals(0, context.damage());
        assertEquals(0, game.damageCountersAtSlot(attacker, "ACTIVE"));
        assertEquals(1, game.attachedEnergyCount(attacker, "ACTIVE"));
    }

    private AttackEffectRegistry fullAttackEffects() {
        return new AttackEffectRegistry(List.of(
            new SpecialConditionAttackEffectStrategy(),
            new SelfHealingAttackEffectStrategy(),
            new PersistentAttackEffectStrategy(),
            new EnergyDiscardAttackEffectStrategy(),
            new BenchDamageAttackEffectStrategy(),
            new AttackUtilityEffectStrategy(),
            new DefaultAttackEffectStrategy()
        ));
    }

    private AttackEffectRegistry attackEffects() {
        return new AttackEffectRegistry(List.of(
            new SpecialConditionAttackEffectStrategy(),
            new SelfHealingAttackEffectStrategy(),
            new PersistentAttackEffectStrategy(),
            new DefaultAttackEffectStrategy()
        ));
    }

    private GameAggregate gameWithActivePokemon(UUID attacker, UUID defender, GameCard attackerCard) {
        GameAggregate game = new GameAggregate(attacker);
        game.setPlayerTwo(defender);
        game.setCurrentTurnPlayer(attacker);
        GameCard defenderCard = new GameCard("defender", "Defender", "Pokémon", Set.of("Basic"), 120, 10, 1);
        game.registerCard(attackerCard);
        game.registerCard(defenderCard);
        game.activePokemon().put(attacker, attackerCard.id());
        game.activePokemon().put(defender, defenderCard.id());
        return game;
    }

    private AttackContext venusaurContext(GameAggregate game, int damage, String attackName, String attackText, List<String> cost) {
        AttackContext context = new AttackContext(game);
        context.setAttackerCard(game.cardCatalog().get("venusaur-ex"));
        context.setDefenderCard(game.cardCatalog().get("defender"));
        context.setAttackName(attackName);
        context.setAttackText(attackText);
        context.setBaseDamage(damage);
        context.setRequiredEnergyCost(cost);
        context.setAttachedEnergies(List.of(
            new AttachedEnergy("grass-1", "Grass"),
            new AttachedEnergy("grass-2", "Grass"),
            new AttachedEnergy("colorless-1", "Colorless"),
            new AttachedEnergy("colorless-2", "Colorless")
        ));
        return context;
    }

    private GameCard venusaur(String attackName, String attackText, int damage, List<String> cost) {
        return new GameCard(
            "venusaur-ex",
            "Venusaur-EX",
            "Pokémon",
            Set.of("Basic", "EX"),
            180,
            damage,
            cost.size(),
            attackName,
            attackText,
            "Grass",
            cost,
            4,
            null,
            List.of()
        );
    }
}
