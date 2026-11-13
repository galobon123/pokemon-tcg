package com.utn.pokemontcg.game.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UseAbilityRequest(
    @NotBlank String slotReference,
    @Min(0) int abilityIndex,
    String targetId
) {
}
