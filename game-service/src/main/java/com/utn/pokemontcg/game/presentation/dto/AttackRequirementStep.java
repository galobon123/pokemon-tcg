package com.utn.pokemontcg.game.presentation.dto;

import java.util.List;

public record AttackRequirementStep(
    String type,
    String scope,
    int min,
    int max,
    int count,
    boolean required,
    List<VisibleCardInfo> cards,
    String message,
    List<String> options
) {
    public AttackRequirementStep {
        cards = cards == null ? List.of() : List.copyOf(cards);
        options = options == null ? List.of() : List.copyOf(options);
    }
}
