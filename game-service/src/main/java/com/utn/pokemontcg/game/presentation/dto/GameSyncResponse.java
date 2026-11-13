package com.utn.pokemontcg.game.presentation.dto;

import com.utn.pokemontcg.game.application.repository.ActionLogEntry;

import java.time.Instant;
import java.util.List;

public record GameSyncResponse(
    GameStateResponse state,
    List<ActionLogEntry> actionLog,
    Instant syncedAt
) {
}
