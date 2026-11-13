package com.utn.pokemontcg.game.domain.strategy;

public class ItemEffectStrategy implements TrainerEffectStrategy {
    @Override
    public String cardName() {
        return "ITEM";
    }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        return TrainerEffectResult.noop("ITEM_HAS_NO_REGISTERED_EFFECT");
    }
}
