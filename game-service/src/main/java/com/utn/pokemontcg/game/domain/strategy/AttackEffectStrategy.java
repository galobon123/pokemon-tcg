package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.chain.AttackContext;

public interface AttackEffectStrategy {
    String effectName();
    boolean supports(AttackContext context);
    void apply(AttackContext context);

    default int computeBaseDamage(GameAggregate game) {
        return game.activeAttackBaseDamage();
    }
}
