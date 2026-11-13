package com.utn.pokemontcg.game.domain.strategy;

import org.springframework.stereotype.Component;

@Component
public class NoOpAbilityEffectStrategy implements AbilityEffectStrategy {
    @Override
    public String abilityName() {
        return "NO_OP";
    }

    @Override
    public TrainerEffectResult apply(AbilityEffectContext context) {
        return TrainerEffectResult.noop("ABILITY_HAS_NO_REGISTERED_EFFECT");
    }
}
