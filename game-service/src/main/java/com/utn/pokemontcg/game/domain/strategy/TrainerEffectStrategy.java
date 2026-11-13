package com.utn.pokemontcg.game.domain.strategy;

public interface TrainerEffectStrategy {
    String cardName();
    TrainerEffectResult apply(TrainerEffectContext context);
}
