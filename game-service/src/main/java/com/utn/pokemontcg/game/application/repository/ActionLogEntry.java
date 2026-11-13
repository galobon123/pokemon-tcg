package com.utn.pokemontcg.game.application.repository;

import com.utn.pokemontcg.game.domain.model.GameActionType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ActionLogEntry(
    Long id,
    UUID gameId,
    GameActionType action,
    UUID playerId,
    String result,
    Map<String, Object> payload,
    Instant createdAt
) {
}
