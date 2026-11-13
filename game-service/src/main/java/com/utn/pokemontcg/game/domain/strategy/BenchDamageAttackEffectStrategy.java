package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BenchDamageAttackEffectStrategy extends AbstractAttackEffectStrategy {
    @Override
    public String effectName() {
        return "BENCH_DAMAGE";
    }

    @Override
    public boolean supports(AttackContext context) {
        String text = effectText(context);
        // Healing attacks (e.g. Massage: "remove 6 damage counters from 1 of your Benched Pokémon")
        // also mention "bench" and "damage"; they must NOT deal bench damage.
        if (text.contains("heal") || (text.contains("remove") && text.contains("damage counter"))) {
            return false;
        }
        return text.contains("bench") && text.contains("damage");
    }

    @Override
    public void apply(AttackContext context) {
        if (context.cancelled()) {
            return;
        }
        if (!coinConditionAllowsEffect(context)) {
            return;
        }
        boolean ownBench = effectText(context).contains("your benched") && !effectText(context).contains("opponent");
        var targetPlayer = ownBench ? attacker(context) : defender(context);
        List<String> defenderBench = context.game().bench().getOrDefault(targetPlayer, List.of());
        boolean hasBenchTarget = false;
        for (String entry : defenderBench) {
            if (entry != null) { hasBenchTarget = true; break; }
        }
        if (!hasBenchTarget) {
            context.audit("AttackEffect:bench_damage_no_target");
            return;
        }
        int damage = firstDamageAmount(effectText(context), 10);
        int counters = Math.max(1, damage / 10);
        String targetSlot = context.game().normalizeSlot(context.targetSlot());
        boolean hasExplicitTarget = targetSlot != null && !targetSlot.isBlank()
            && targetSlot.startsWith("BENCH_") && context.game().isOccupiedSlot(targetPlayer, targetSlot);
        if (effectText(context).contains("each of your") || effectText(context).contains("each of your opponent")) {
            if (hasExplicitTarget) {
                context.game().addDamageCounters(targetPlayer, targetSlot, counters);
                context.audit("AttackEffect:bench_damage_single_counters=" + counters + ",target=" + targetSlot);
                return;
            }
            for (int i = 0; i < defenderBench.size(); i++) {
                if (defenderBench.get(i) != null) {
                    context.game().addDamageCounters(targetPlayer, "BENCH_" + i, counters);
                }
            }
            context.audit("AttackEffect:bench_damage_each_counters=" + counters);
            return;
        }
        int targets = effectText(context).contains(" 2 of your opponent") ? 2 : 1;
        // Collect the player's chosen bench targets. Multiple targets (e.g. Hydro Bombard hits 2)
        // are carried in selectedCardIds; the single targetSlot is honored for backwards compat.
        List<String> chosen = new ArrayList<>();
        for (String slot : selectedSlots(context)) {
            if (slot.startsWith("BENCH_") && context.game().isOccupiedSlot(targetPlayer, slot) && !chosen.contains(slot)) {
                chosen.add(slot);
            }
        }
        if (targetSlot != null && targetSlot.startsWith("BENCH_")
            && context.game().isOccupiedSlot(targetPlayer, targetSlot) && !chosen.contains(targetSlot)) {
            chosen.add(0, targetSlot);
        }
        // Resilience: if the client supplied fewer targets than required, fill from the next
        // occupied bench positions so the attack still resolves.
        for (int i = 0; i < defenderBench.size() && chosen.size() < targets; i++) {
            String slot = "BENCH_" + i;
            if (defenderBench.get(i) != null && !chosen.contains(slot)) {
                chosen.add(slot);
            }
        }
        if (chosen.isEmpty()) {
            context.audit("AttackEffect:bench_damage_no_target");
            return;
        }
        int applied = 0;
        for (String slot : chosen) {
            if (applied >= targets) {
                break;
            }
            context.game().addDamageCounters(targetPlayer, slot, counters);
            applied++;
        }
        context.audit("AttackEffect:bench_damage_counters=" + counters + ",targets=" + applied);
    }
}
