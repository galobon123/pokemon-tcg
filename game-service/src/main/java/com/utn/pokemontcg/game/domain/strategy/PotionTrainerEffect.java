package com.utn.pokemontcg.game.domain.strategy;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PotionTrainerEffect extends AbstractTrainerEffectStrategy {
    @Override
    public String cardName() {
        return "Potion";
    }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        String slot = context.game().normalizeSlot(context.targetSlot());
        int previous = context.game().damageCountersAtSlot(context.player(), slot);
        int healed = Math.min(2, previous);
        context.game().healDamageCounters(context.player(), slot, healed);
        return result("DAMAGE_HEALED", Map.of("targetSlot", slot, "healedCounters", healed));
    }
}
