package com.utn.pokemontcg.game.presentation.dto;

import java.util.List;

public record HandResponse(
    String gameId,
    String playerId,
    List<String> cardIds,
    int handSize
) {
}
