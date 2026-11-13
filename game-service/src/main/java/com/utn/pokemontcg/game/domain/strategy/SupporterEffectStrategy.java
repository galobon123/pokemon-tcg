package com.utn.pokemontcg.game.domain.strategy;

public class SupporterEffectStrategy implements TrainerEffectStrategy {
    @Override
    public String cardName() {
        return "SUPPORTER";
    }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        return TrainerEffectResult.noop("SUPPORTER_HAS_NO_REGISTERED_EFFECT");
    }
}
