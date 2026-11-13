package com.utn.pokemontcg.game.presentation.dto;

import jakarta.validation.constraints.Pattern;

public record CoinFlipRequest(
    @Pattern(regexp = "HEADS|TAILS", message = "Debe ser HEADS o TAILS")
    String choice
) {
}
