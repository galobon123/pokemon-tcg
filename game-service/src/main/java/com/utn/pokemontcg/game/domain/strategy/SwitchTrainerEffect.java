package com.utn.pokemontcg.game.domain.strategy;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SwitchTrainerEffect extends AbstractTrainerEffectStrategy {
    @Override
    public String cardName() {
        return "Switch";
    }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        String slot = context.game().normalizeSlot(context.targetSlot());
        int benchIndex = context.game().benchIndex(slot);
        context.game().swapActiveWithBench(context.player(), benchIndex);
        return result("ACTIVE_SWITCHED", Map.of("targetSlot", slot));
    }
}
