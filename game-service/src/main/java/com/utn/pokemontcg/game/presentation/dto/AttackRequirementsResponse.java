package com.utn.pokemontcg.game.presentation.dto;

import java.util.List;

public record AttackRequirementsResponse(
    String gameId,
    String playerId,
    int attackIndex,
    String attackName,
    String attackText,
    List<AttackRequirementStep> steps,
    List<AttackEffectPreview> effects,
    int coinFlipCount
) {
    public AttackRequirementsResponse {
        steps = steps == null ? List.of() : List.copyOf(steps);
        effects = effects == null ? List.of() : List.copyOf(effects);
    }
}
