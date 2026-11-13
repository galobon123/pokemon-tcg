package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import org.springframework.stereotype.Component;

@Component
public class ForestCurseAbilityEffectStrategy implements AbilityEffectStrategy {

    @Override
    public String abilityName() {
        return "Forest's Curse";
    }

    @Override
    public TrainerEffectResult apply(AbilityEffectContext context) {
        throw new InvalidActionException(
            "Forest's Curse es una habilidad continua que se aplica automaticamente mientras este Pokemon sea tu Pokemon Activo"
        );
    }
}
