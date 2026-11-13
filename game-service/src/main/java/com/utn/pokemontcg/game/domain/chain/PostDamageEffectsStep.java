package com.utn.pokemontcg.game.domain.chain;

import com.utn.pokemontcg.game.domain.strategy.AttackEffectRegistry;

public class PostDamageEffectsStep implements AttackStep {

    private final AttackEffectRegistry attackEffectRegistry;

    public PostDamageEffectsStep() {
        this(null);
    }

    public PostDamageEffectsStep(AttackEffectRegistry attackEffectRegistry) {
        this.attackEffectRegistry = attackEffectRegistry;
    }

    @Override
    public void execute(AttackContext context) {
        context.audit("PostDamageEffectsStep:start");
        if (context.cancelled()) return;
        if (attackEffectRegistry != null) {
            attackEffectRegistry.applyPostDamageEffects(context);
        }
        context.setPostEffectsApplied(true);
        context.audit("PostDamageEffectsStep:effects_applied");
    }
}
