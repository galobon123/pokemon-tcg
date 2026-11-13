package com.utn.pokemontcg.game.domain.strategy;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RollerSkatesTrainerEffect extends AbstractTrainerEffectStrategy {
    @Override
    public String cardName() {
        return "Roller Skates";
    }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        boolean heads = context.coinFlip().getAsBoolean();
        var drawn = heads ? draw(context.game(), context.player(), 3) : java.util.List.<String>of();
        return result("COIN_FLIPPED", Map.of("heads", heads, "cardsDrawn", drawn.size()));
    }
}
