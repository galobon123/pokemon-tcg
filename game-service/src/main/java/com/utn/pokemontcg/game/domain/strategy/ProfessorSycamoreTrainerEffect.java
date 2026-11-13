package com.utn.pokemontcg.game.domain.strategy;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProfessorSycamoreTrainerEffect extends AbstractTrainerEffectStrategy {
    @Override
    public String cardName() {
        return "Professor Sycamore";
    }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        discardHand(context.game(), context.player());
        int drawn = draw(context.game(), context.player(), 7).size();
        return result("HAND_DISCARDED_AND_DRAWN", Map.of("cardsDrawn", drawn));
    }
}
