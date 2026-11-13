package com.utn.pokemontcg.game.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record BenchPokemonRequest(
    @NotBlank String cardInstanceId,
    String targetSlot
) {
}
