package com.utn.pokemontcg.game.domain.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EscapeRopeTrainerEffect extends AbstractTrainerEffectStrategy {
    @Override
    public String cardName() {
        return "Escape Rope";
    }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        String playerSlot = context.game().normalizeSlot(context.targetSlot());
        int playerBenchIndex = context.game().benchIndex(playerSlot);
        context.game().swapActiveWithBench(context.player(), playerBenchIndex);
        UUID opponent = opponentOf(context.game(), context.player());
        boolean opponentSwapped = context.game().benchOccupiedCount(opponent) > 0;
        if (opponentSwapped) {
            List<String> oppBench = context.game().bench().getOrDefault(opponent, java.util.List.of());
            int oppBenchIndex = -1;
            for (int i = 0; i < oppBench.size(); i++) {
                if (oppBench.get(i) != null) { oppBenchIndex = i; break; }
            }
            if (oppBenchIndex >= 0) {
                context.game().swapActiveWithBench(opponent, oppBenchIndex);
            }
        }
        return result("ESCAPE_ROPE_RESOLVED", Map.of("playerTargetSlot", playerSlot, "opponentSwapped", opponentSwapped));
    }
}
