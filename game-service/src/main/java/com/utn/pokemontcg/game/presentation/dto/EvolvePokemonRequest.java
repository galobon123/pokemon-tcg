package com.utn.pokemontcg.game.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record EvolvePokemonRequest(
    @NotBlank String fromCardInstanceId,
    @NotBlank String toCardInstanceId,
    @NotBlank String targetSlot
) {
}
