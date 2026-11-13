package com.utn.pokemontcg.game.domain.chain;

import com.utn.pokemontcg.game.domain.strategy.AttackEffectRegistry;

public class PreDamageEffectsStep implements AttackStep {

    private final AttackEffectRegistry attackEffectRegistry;

    public PreDamageEffectsStep(AttackEffectRegistry attackEffectRegistry) {
        this.attackEffectRegistry = attackEffectRegistry;
    }

    @Override
    public void execute(AttackContext context) {
        context.audit("PreDamageEffectsStep:start");
        if (context.cancelled() || attackEffectRegistry == null) return;
        attackEffectRegistry.applyPreDamageModifiers(context);
    }
}
