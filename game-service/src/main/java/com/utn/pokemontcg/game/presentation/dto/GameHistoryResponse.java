package com.utn.pokemontcg.game.presentation.dto;

import java.time.Instant;
import java.util.UUID;

public record GameHistoryResponse(
    UUID gameId,
    String opponentName,
    String opponentAvatar,
    boolean won,
    String reason,
    Instant finishedAt
) {}
