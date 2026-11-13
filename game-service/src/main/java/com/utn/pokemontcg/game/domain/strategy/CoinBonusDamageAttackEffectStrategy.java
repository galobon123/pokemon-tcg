package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import org.springframework.stereotype.Component;

import java.util.function.BooleanSupplier;

@Component
public class CoinBonusDamageAttackEffectStrategy extends AbstractAttackEffectStrategy {

    private final BooleanSupplier coinFlip;

    public CoinBonusDamageAttackEffectStrategy() {
        this(() -> Math.random() < 0.5);
    }

    public CoinBonusDamageAttackEffectStrategy(BooleanSupplier coinFlip) {
        this.coinFlip = coinFlip;
    }

    @Override
    public String effectName() {
        return "COIN_BONUS_DAMAGE";
    }

    @Override
    public boolean supports(AttackContext context) {
        String text = effectText(context);
        return text.contains("flip a coin")
            && containsAny(text, "more damage", "+");
    }

    @Override
    public void apply(AttackContext context) {
        if (context.cancelled()) {
            return;
        }
        String text = effectText(context);
        if (hasCoinCondition(text)) {
            if (!coinConditionAllowsEffect(context)) {
                return;
            }
        } else {
            boolean heads = context.coinFlip().getAsBoolean();
            context.audit("AttackEffect:coin_bonus_flip=" + (heads ? "heads" : "tails"));
            if (!heads) {
                return;
            }
        }
        int bonus = firstExplicitBonus(text, 10);
        context.setPreAttackBonus(context.preAttackBonus() + bonus);
        context.audit("AttackEffect:coin_bonus_damage=" + bonus);
    }
}
