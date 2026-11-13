package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SelfHealingAttackEffectStrategy extends AbstractAttackEffectStrategy {

    private static final Pattern DAMAGE_COUNTERS = Pattern.compile("(\\d+)\\s+damage counters");

    @Override
    public String effectName() {
        return "SELF_HEALING";
    }

    @Override
    public boolean supports(AttackContext context) {
        String text = effectText(context);
        return containsAny(text, "heal", "remove")
            && !text.contains("opponent")
            && containsAny(text, "from this pokemon", "from this pokémon", "from this pok", "from each of your", "from 1 of your", "from 1 of your benched");
    }

    @Override
    public void apply(AttackContext context) {
        if (context.cancelled()) {
            return;
        }
        if (!coinConditionAllowsEffect(context)) {
            return;
        }
        String text = effectText(context);
        UUID attacker = attacker(context);

        // "Heal X damage from each of your Pokémon" — heal every own Pokémon.
        if (text.contains("from each of your")) {
            int counters = healAmount(context, attacker, "ACTIVE", text);
            context.game().healDamageCounters(attacker, "ACTIVE", counters);
            List<String> bench = context.game().bench().getOrDefault(attacker, List.of());
            for (int i = 0; i < bench.size(); i++) {
                if (context.game().isOccupiedSlot(attacker, "BENCH_" + i)) {
                    int benchCounters = healAmount(context, attacker, "BENCH_" + i, text);
                    context.game().healDamageCounters(attacker, "BENCH_" + i, benchCounters);
                }
            }
            if (text.contains("remove all special conditions")) {
                context.game().statusByPlayer().remove(attacker);
            }
            context.audit("AttackEffect:heal_each_of_your");
            return;
        }

        String targetSlot = healTargetSlot(context, attacker, text);
        int counters = healAmount(context, attacker, targetSlot, text);
        context.game().healDamageCounters(attacker, targetSlot, counters);
        if (text.contains("remove all special conditions")) {
            context.game().statusByPlayer().remove(attacker);
        }
        context.audit("AttackEffect:heal_counters=" + counters + ",target=" + targetSlot);
    }

    /**
     * Resolve which of the attacker's own Pokémon is healed.
     * - "this Pokémon" (e.g. Recover) always targets the attacker's own ACTIVE.
     * - "benched" (e.g. Massage) targets a chosen Benched Pokémon.
     * - "1 of your Pokémon" (e.g. Sweet Scent) lets the player pick ACTIVE or a Bench slot.
     */
    private String healTargetSlot(AttackContext context, UUID attacker, String text) {
        if (containsAny(text, "this pokemon", "this pokémon")) {
            return "ACTIVE";
        }
        String requested = context.game().normalizeSlot(context.targetSlot());
        boolean benchOnly = text.contains("benched");
        if (benchOnly) {
            if (requested != null && requested.startsWith("BENCH_") && context.game().isOccupiedSlot(attacker, requested)) {
                return requested;
            }
            return firstOccupiedBench(context, attacker);
        }
        if (requested != null
            && (requested.equals("ACTIVE") || requested.startsWith("BENCH_"))
            && context.game().isOccupiedSlot(attacker, requested)) {
            return requested;
        }
        return "ACTIVE";
    }

    /**
     * Number of damage counters to remove from the target slot:
     * - "remove all damage counters" → all counters currently on that Pokémon.
     * - "remove N damage counters" → exactly N counters (the number IS the counter count).
     * - "heal N damage" → N/10 counters.
     */
    private int healAmount(AttackContext context, UUID attacker, String slot, String text) {
        if (text.contains("all damage")) {
            return context.game().damageCountersAtSlot(attacker, slot);
        }
        Matcher counters = DAMAGE_COUNTERS.matcher(text);
        if (counters.find()) {
            return Integer.parseInt(counters.group(1));
        }
        return Math.max(1, firstDamageAmount(text, 20) / 10);
    }

    private String firstOccupiedBench(AttackContext context, UUID attacker) {
        List<String> bench = context.game().bench().getOrDefault(attacker, List.of());
        for (int i = 0; i < bench.size(); i++) {
            if (context.game().isOccupiedSlot(attacker, "BENCH_" + i)) {
                return "BENCH_" + i;
            }
        }
        return "ACTIVE";
    }
}
