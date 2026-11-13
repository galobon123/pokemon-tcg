package com.utn.pokemontcg.game.domain.chain;

import java.util.UUID;
import com.utn.pokemontcg.game.domain.model.GameCard;

public class ModifierStep implements AttackStep {
    public static final String ATTACKER_DAMAGE_BONUS = "ATTACKER_DAMAGE_BONUS";
    public static final String DEFENDER_DAMAGE_REDUCTION = "DEFENDER_DAMAGE_REDUCTION";
    public static final String DEFENDER_PREVENT_DAMAGE_MAX = "DEFENDER_PREVENT_DAMAGE_MAX";
    public static final String CANNOT_ATTACK = "CANNOT_ATTACK";
    public static final String CANNOT_RETREAT = "CANNOT_RETREAT";
    public static final String SUPPORTER_LOCK = "SUPPORTER_LOCK";
    public static final String ABILITY_LOCK = "ABILITY_LOCK";
    public static final String BLOCKED_ATTACK_PREFIX = "BLOCKED_ATTACK_";

    @Override
    public void execute(AttackContext context) {
        context.audit("ModifierStep:start");
        if (context.cancelled()) return;
        UUID attacker = context.game().currentTurnPlayer();
        UUID defender = attacker != null && attacker.equals(context.game().playerOne())
            ? context.game().playerTwo()
            : context.game().playerOne();

        int attackerBonus = attacker == null ? 0 : context.game().consumeActiveEffect(attacker, ATTACKER_DAMAGE_BONUS);
        if (attacker != null) {
            String attackName = context.attackName();
            if (attackName != null && !attackName.isBlank()) {
                attackerBonus += context.game().consumeActiveEffect(attacker, ATTACKER_DAMAGE_BONUS + ":" + attackName);
            }
        }
        int defenderReduction = defender == null ? 0 : context.game().consumeActiveEffect(defender, DEFENDER_DAMAGE_REDUCTION);
        attackerBonus += toolDamageBonus(context, attacker, "ACTIVE");
        defenderReduction += toolDamageReduction(context, defender, "ACTIVE");
        defenderReduction += furCoatDamageReduction(context, defender);
        context.setAttackerModifier(context.attackerModifier() + attackerBonus);
        context.setDefenderModifier(context.defenderModifier() - Math.max(0, defenderReduction));

        String text = ((context.attackName() == null ? "" : context.attackName()) + " " + (context.attackText() == null ? "" : context.attackText())).toLowerCase(java.util.Locale.ROOT);
        if (text.contains("isn't affected by weakness") || text.contains("isn't affected by weakness, resistance")
            || text.contains("isn't affected by weakness or resistance")) {
            context.setWeaknessMultiplier(1);
        }
        if (text.contains("isn't affected by resistance") || text.contains("isn't affected by weakness, resistance")
            || text.contains("isn't affected by weakness or resistance")) {
            context.setResistanceReduction(0);
        }

        if (context.weaknessMultiplier() < 1) context.setWeaknessMultiplier(1);
        if (context.resistanceReduction() < 0) context.setResistanceReduction(0);
        context.audit("ModifierStep:attacker_modifier=" + context.attackerModifier()
            + ",defender_modifier=" + context.defenderModifier()
            + ",weakness=" + context.weaknessMultiplier()
            + ",resistance=" + context.resistanceReduction());
    }

    private int toolDamageBonus(AttackContext context, UUID player, String slot) {
        if (player == null) return 0;
        GameCard tool = context.game().cardCatalog().get(context.game().toolAtSlot(player, slot));
        return tool != null && "Muscle Band".equalsIgnoreCase(tool.name()) ? 20 : 0;
    }

    private int toolDamageReduction(AttackContext context, UUID player, String slot) {
        if (player == null) return 0;
        GameCard tool = context.game().cardCatalog().get(context.game().toolAtSlot(player, slot));
        return tool != null && "Hard Charm".equalsIgnoreCase(tool.name()) ? 20 : 0;
    }

    private int furCoatDamageReduction(AttackContext context, UUID defender) {
        if (defender == null) return 0;
        String activeCardId = context.game().activePokemon().get(defender);
        if (activeCardId == null) return 0;
        GameCard activeCard = context.game().cardCatalog().get(activeCardId);
        if (activeCard == null || activeCard.abilities() == null) return 0;
        return activeCard.abilities().stream()
            .anyMatch(a -> "Fur Coat".equalsIgnoreCase(a)) ? 20 : 0;
    }
}
