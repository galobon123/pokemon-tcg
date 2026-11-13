package com.utn.pokemontcg.game.domain.chain;

import com.utn.pokemontcg.game.domain.engine.DamageCalculator;
import com.utn.pokemontcg.game.domain.strategy.ShadowCircleStadiumEffect;

import java.util.UUID;

public class DamageCalculationStep implements AttackStep {
    private final DamageCalculator damageCalculator = new DamageCalculator();

    @Override
    public void execute(AttackContext context) {
        context.audit("DamageCalculationStep:start");
        if (context.cancelled()) {
            context.setDamage(0);
            context.audit("DamageCalculationStep:cancelled_damage=0");
            return;
        }

        int damage;
        if (context.attackerCard() != null && context.defenderCard() != null) {
            context.setWeaknessMultiplier(damageCalculator.weaknessMultiplier(context.attackerCard(), context.defenderCard()));
            context.setResistanceReduction(damageCalculator.resistanceReduction(context.attackerCard(), context.defenderCard()));
            String text = ((context.attackName() == null ? "" : context.attackName()) + " " + (context.attackText() == null ? "" : context.attackText())).toLowerCase(java.util.Locale.ROOT);
            if (text.contains("isn't affected by weakness")) context.setWeaknessMultiplier(1);
            if (text.contains("isn't affected by resistance")
                || text.contains("isn't affected by weakness or resistance")
                || text.contains("isn't affected by weakness, resistance")) context.setResistanceReduction(0);
            if (context.weaknessMultiplier() > 1) {
                UUID defender = defender(context);
                if (defender != null && context.game().hasActiveEffect(defender, ShadowCircleStadiumEffect.EFFECT_KEY)) {
                    boolean hasDarkness = context.game().attachedEnergiesFor(defender, "ACTIVE").stream()
                        .anyMatch(e -> "Darkness".equalsIgnoreCase(e.energyType()));
                    if (hasDarkness) {
                        context.setWeaknessMultiplier(1);
                        context.audit("DamageCalculationStep:shadow_circle_suppressed_weakness");
                    }
                }
            }
            damage = damageCalculator.calculate(
                context.baseDamage(),
                context.preAttackBonus() + context.attackerModifier(),
                context.attackerCard(),
                context.defenderCard(),
                context.defenderModifier(),
                context.weaknessMultiplier(),
                context.resistanceReduction()
            );
        } else {
            int raw = (context.baseDamage() + context.preAttackBonus() + context.attackerModifier())
                * context.weaknessMultiplier()
                - context.resistanceReduction()
                + context.defenderModifier();
            damage = Math.max(raw, 0);
        }

        int preventionThreshold = defender(context) == null
            ? 0
            : context.game().activeEffectValue(defender(context), ModifierStep.DEFENDER_PREVENT_DAMAGE_MAX);
        if (preventionThreshold > 0 && damage <= preventionThreshold) {
            context.game().consumeActiveEffect(defender(context), ModifierStep.DEFENDER_PREVENT_DAMAGE_MAX);
            damage = 0;
            context.audit("DamageCalculationStep:damage_prevented_threshold=" + preventionThreshold);
        }

        context.setDamage(damage);
        context.audit("DamageCalculationStep:weakness=" + context.weaknessMultiplier() + ",resistance=" + context.resistanceReduction());
        context.audit("DamageCalculationStep:damage=" + context.damage());
    }

    private UUID defender(AttackContext context) {
        UUID attacker = context.game().currentTurnPlayer();
        return attacker != null && attacker.equals(context.game().playerOne())
            ? context.game().playerTwo()
            : context.game().playerOne();
    }
}
