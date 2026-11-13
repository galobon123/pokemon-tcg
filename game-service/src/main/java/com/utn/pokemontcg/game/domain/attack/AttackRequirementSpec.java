package com.utn.pokemontcg.game.domain.attack;

import java.util.List;

public record AttackRequirementSpec(
    String type,
    String scope,
    int min,
    int max,
    int count,
    boolean required,
    String message,
    List<String> options
) {
    public AttackRequirementSpec {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public AttackRequirementSpec(String type, String scope, int min, int max, int count, boolean required, String message) {
        this(type, scope, min, max, count, required, message, List.of());
    }
}
