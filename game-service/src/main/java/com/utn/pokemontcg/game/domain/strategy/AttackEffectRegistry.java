package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class AttackEffectRegistry {

    private final List<AttackEffectStrategy> strategies;

    public AttackEffectRegistry(List<AttackEffectStrategy> strategies) {
        this.strategies = strategies.stream()
            .sorted(Comparator.comparing(strategy -> strategy instanceof DefaultAttackEffectStrategy))
            .toList();
    }

    public void apply(AttackContext context) {
        applyPostDamageEffects(context);
    }

    public void applyPreDamageModifiers(AttackContext context) {
        strategies.stream()
            .filter(this::isDamageModifier)
            .filter(strategy -> strategy.supports(context))
            .forEach(strategy -> strategy.apply(context));
    }

    public void applyPostDamageEffects(AttackContext context) {
        List<AttackEffectStrategy> matched = strategies.stream()
            .filter(strategy -> !(strategy instanceof DefaultAttackEffectStrategy))
            .filter(strategy -> !isDamageModifier(strategy))
            .filter(strategy -> strategy.supports(context))
            .toList();
        if (matched.isEmpty()) {
            strategies.stream()
                .filter(DefaultAttackEffectStrategy.class::isInstance)
                .findFirst()
                .ifPresent(strategy -> strategy.apply(context));
            return;
        }
        matched.forEach(strategy -> strategy.apply(context));
    }

    private boolean isDamageModifier(AttackEffectStrategy strategy) {
        return strategy instanceof CoinBonusDamageAttackEffectStrategy
            || strategy instanceof CoinFlipDamageAttackEffectStrategy
            || strategy instanceof ConditionalDamageAttackEffectStrategy;
    }
}
