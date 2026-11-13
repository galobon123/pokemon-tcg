package com.utn.pokemontcg.game.presentation.dto.deck;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DeckUpsertSelfRequest(
    @NotBlank String name,
    @Valid @NotNull List<DeckCardInput> cards
) {
}
