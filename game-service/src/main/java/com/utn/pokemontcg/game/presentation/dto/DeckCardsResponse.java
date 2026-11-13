package com.utn.pokemontcg.game.presentation.dto;

import java.util.List;

public record DeckCardsResponse(
    String gameId,
    String playerId,
    List<String> cardIds,
    List<VisibleCardInfo> cards,
    int deckSize
) {
}
