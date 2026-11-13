package com.utn.pokemontcg.game.domain.strategy;

public interface AbilityEffectStrategy {
    String abilityName();
    TrainerEffectResult apply(AbilityEffectContext context);

    default boolean isRepeatable() {
        return false;
    }
}
