package com.utn.pokemontcg.game.domain.strategy;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ShaunaTrainerEffect extends AbstractTrainerEffectStrategy {
    @Override
    public String cardName() {
        return "Shauna";
    }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        shuffleHandIntoDeck(context.game(), context.player());
        int drawn = draw(context.game(), context.player(), 5).size();
        return result("HAND_SHUFFLED_AND_DRAWN", Map.of("cardsDrawn", drawn));
    }
}
