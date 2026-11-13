package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import org.springframework.stereotype.Component;

@Component
public class SweetVeilAbilityEffectStrategy implements AbilityEffectStrategy {

    @Override
    public String abilityName() {
        return "Sweet Veil";
    }

    @Override
    public TrainerEffectResult apply(AbilityEffectContext context) {
        throw new InvalidActionException(
            "Sweet Veil es una habilidad continua que se aplica automaticamente mientras este Pokemon sea tu Pokemon Activo"
        );
    }
}
