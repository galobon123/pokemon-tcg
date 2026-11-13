package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import org.springframework.stereotype.Component;

@Component
public class DefaultAttackEffectStrategy implements AttackEffectStrategy {
    @Override
    public String effectName() {
        return "DEFAULT";
    }

    @Override
    public boolean supports(AttackContext context) {
        return true;
    }

    @Override
    public void apply(AttackContext context) {
        context.audit("AttackEffect:no_special_effect");
    }

    @Override
    public int computeBaseDamage(GameAggregate game) {
        return 10;
    }
}
