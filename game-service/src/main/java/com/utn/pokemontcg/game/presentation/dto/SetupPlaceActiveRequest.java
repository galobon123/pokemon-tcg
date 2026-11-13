package com.utn.pokemontcg.game.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record SetupPlaceActiveRequest(
    @NotBlank String cardInstanceId
) {}
