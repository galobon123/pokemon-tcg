package com.utn.pokemontcg.game.domain.engine;

import com.utn.pokemontcg.game.domain.model.GameCard;
import org.springframework.stereotype.Component;

@Component
public class DamageCalculator {

    public int calculate(int baseDamage, GameCard attacker, GameCard defender) {
        return calculate(baseDamage, 0, attacker, defender, 0);
    }

    public int calculate(int baseDamage, int attackerModifier, GameCard attacker, GameCard defender, int defenderModifier) {
        return calculate(baseDamage, attackerModifier, attacker, defender, defenderModifier,
            weaknessMultiplier(attacker, defender),
            resistanceReduction(attacker, defender));
    }

    public int calculate(int baseDamage, int attackerModifier, GameCard attacker, GameCard defender,
                         int defenderModifier, int weaknessMultiplier, int resistanceReduction) {
        int damage = Math.max(0, baseDamage + attackerModifier);
        damage *= weaknessMultiplier;
        damage = Math.max(0, damage - resistanceReduction);
        damage = Math.max(0, damage + defenderModifier);
        return damage;
    }

    public int weaknessMultiplier(GameCard attacker, GameCard defender) {
        if (!typeMatches(attacker, defender == null ? null : defender.weaknessType())) {
            return 1;
        }
        Integer amount = defender.weaknessAmount();
        return amount == null || amount < 1 ? 2 : amount;
    }

    public int resistanceReduction(GameCard attacker, GameCard defender) {
        if (!typeMatches(attacker, defender == null ? null : defender.resistanceType())) {
            return 0;
        }
        Integer amount = defender.resistanceAmount();
        if (amount == null) {
            return 20;
        }
        return amount < 0 ? Math.abs(amount) : amount;
    }

    public int calculate(int baseDamage, boolean weakness, boolean resistance, Object ignoredAttackerStatuses) {
        int damage = baseDamage;
        if (weakness) damage *= 2;
        if (resistance) damage -= 20;

        return Math.max(0, damage);
    }

    private boolean typeMatches(GameCard attacker, String defenderModifierType) {
        if (attacker == null || defenderModifierType == null || defenderModifierType.isBlank()) {
            return false;
        }
        String attackerType = attacker.energyType();
        return attackerType != null && attackerType.equalsIgnoreCase(defenderModifierType);
    }
}
