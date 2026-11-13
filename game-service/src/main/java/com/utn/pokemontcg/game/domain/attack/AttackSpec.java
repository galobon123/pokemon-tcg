package com.utn.pokemontcg.game.domain.attack;

import java.util.List;

public record AttackSpec(
    List<AttackRequirementSpec> requirements,
    List<AttackEffectSpec> effects,
    int coinFlipCount
) {
    public AttackSpec {
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        effects = effects == null ? List.of() : List.copyOf(effects);
    }
}
