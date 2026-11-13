package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ConditionalDamageAttackEffectStrategy extends AbstractAttackEffectStrategy {
    @Override
    public String effectName() {
        return "CONDITIONAL_DAMAGE";
    }

    @Override
    public boolean supports(AttackContext context) {
        String text = effectText(context);
        return Pattern.compile("(does|do) \\d+ more damage").matcher(text).find()
            || text.contains("damage times the amount of energy")
            || text.contains("damage times the number of your benched")
            || text.contains("for each different type")
            || text.contains("more damage for each damage counter")
            || (text.contains("you may do") && text.contains("more damage"))
            || (text.contains("you may") && text.contains("additional damage"));
    }

    @Override
    public void apply(AttackContext context) {
        if (context.cancelled()) return;
        if (!coinConditionAllowsEffect(context)) return;
        String text = effectText(context);
        int bonus = 0;

        if (text.contains("active pokemon is a grass") || text.contains("active pokémon is a grass")) {
            bonus += defenderHasType(context, "Grass") ? firstExplicitBonus(text, 20) : 0;
        } else if (text.contains("lunatone is on your bench")) {
            bonus += benchContains(context, "Lunatone") ? firstExplicitBonus(text, 30) : 0;
        } else if (text.contains("already has any damage counters")) {
            bonus += context.game().damageCountersAtSlot(defender(context), "ACTIVE") > 0 ? firstExplicitBonus(text, 40) : 0;
        } else if (text.contains("active pokémon is a pokémon-ex") || text.contains("active pokemon is a pokemon-ex")) {
            bonus += context.defenderCard() != null && context.defenderCard().isPokemonEx() ? firstExplicitBonus(text, 60) : 0;
        } else if (text.contains("affected by a special condition")) {
            boolean affected = !context.game().statusByPlayer().getOrDefault(defender(context), EnumSet.noneOf(StatusCondition.class)).isEmpty();
            bonus += affected ? firstExplicitBonus(text, 60) : 0;
            if (affected && text.contains("remove all special conditions")) context.game().statusByPlayer().remove(defender(context));
        } else if (text.contains("for each damage counter on your opponent")) {
            bonus += context.game().damageCountersAtSlot(defender(context), "ACTIVE") * firstExplicitBonus(text, 10);
        } else if (text.contains("for each damage counter on this")) {
            bonus += context.game().damageCountersAtSlot(attacker(context), "ACTIVE") * firstExplicitBonus(text, 10);
        } else if (text.contains("for each fire energy attached to this")) {
            bonus += energyCount(context, "Fire") * firstExplicitBonus(text, 20);
        } else if (text.contains("for each water energy attached to this")) {
            bonus += energyCount(context, "Water") * firstExplicitBonus(text, 20);
        } else if (text.contains("has any psychic energy attached")) {
            bonus += energyCount(context, "Psychic") > 0 ? firstExplicitBonus(text, 30) : 0;
        } else if (text.contains("for each different type of basic energy")) {
            Set<String> types = new HashSet<>();
            context.game().attachedEnergiesFor(attacker(context), "ACTIVE").forEach(e -> types.add(e.energyType()));
            bonus += types.size() * firstExplicitBonus(text, 30);
        } else if (text.contains("times the number of your benched")) {
            int damage = firstDamageAmount(text, 20) * context.game().benchOccupiedCount(attacker(context));
            context.setBaseDamage(damage);
            context.audit("AttackEffect:conditional_damage=" + damage);
            return;
        } else if (text.contains("times the amount of energy attached to both active")) {
            int total = context.game().attachedEnergyCount(attacker(context), "ACTIVE") + context.game().attachedEnergyCount(defender(context), "ACTIVE");
            bonus += total * firstExplicitBonus(text, 20);
        } else if (text.contains("times the amount of energy attached to this")) {
            int damage = firstDamageAmount(text, 20) * context.game().attachedEnergyCount(attacker(context), "ACTIVE");
            context.setBaseDamage(damage);
            context.audit("AttackEffect:conditional_damage=" + damage);
            return;
        }

        if (text.contains("you may do") && text.contains("more damage") && context.acceptedOptionalEffect() != Boolean.FALSE) {
            bonus += firstExplicitBonus(text, 20);
        }
        // "(You may) discard an Energy attached to this Pokémon. If you do, this attack does N more
        // damage." (e.g. Electron Crush). The bonus applies when the discard happens: for an optional
        // discard that means the player did not decline (accepted or unspecified). This must stay in
        // sync with EnergyDiscardAttackEffectStrategy, which skips the discard only when declined.
        if (text.contains("discard") && text.contains("energy")
            && Pattern.compile("(does|do) \\d+ more damage").matcher(text).find()) {
            boolean optional = text.contains("you may");
            boolean discardHappens = !optional || context.acceptedOptionalEffect() != Boolean.FALSE;
            if (discardHappens) {
                bonus += firstExplicitBonus(text, 30);
            }
        }
        if (bonus > 0) {
            context.setPreAttackBonus(context.preAttackBonus() + bonus);
            context.audit("AttackEffect:conditional_bonus=" + bonus);
        }
    }

    private boolean defenderHasType(AttackContext context, String type) {
        return context.defenderCard() != null && context.defenderCard().energyType() != null && context.defenderCard().energyType().equalsIgnoreCase(type)
            || context.defenderCard() != null && context.defenderCard().attackCost().stream().anyMatch(type::equalsIgnoreCase);
    }

    private boolean benchContains(AttackContext context, String name) {
        return context.game().bench().getOrDefault(attacker(context), java.util.List.of()).stream()
            .map(id -> context.game().cardCatalog().get(id))
            .anyMatch(card -> card != null && name.equalsIgnoreCase(card.name()));
    }

    private int energyCount(AttackContext context, String type) {
        return (int) context.game().attachedEnergiesFor(attacker(context), "ACTIVE").stream()
            .filter(e -> type.equalsIgnoreCase(e.energyType()))
            .count();
    }
}
