package com.utn.pokemontcg.game.domain.strategy;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class LysandreTrainerEffect extends AbstractTrainerEffectStrategy {
    @Override
    public String cardName() {
        return "Lysandre";
    }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        UUID opponent = opponentOf(context.game(), context.player());
        String slot = context.game().normalizeSlot(context.targetSlot());
        int benchIndex = context.game().benchIndex(slot);
        context.game().swapActiveWithBench(opponent, benchIndex);
        return result("OPPONENT_ACTIVE_SWITCHED", Map.of("targetSlot", slot));
    }
}
