package com.utn.pokemontcg.game.application.repository;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameActionType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface GameStateRepository {
    GameAggregate save(GameAggregate game);
    Optional<GameAggregate> findById(UUID gameId);
    void appendActionLog(UUID gameId, GameActionType action, UUID playerId, String result, Instant timestamp);
    void appendActionLog(UUID gameId, GameActionType action, UUID playerId, String result, Instant timestamp, Map<String, Object> payload);
    List<ActionLogEntry> actionLog(UUID gameId);
    Optional<GameAggregate> latestSnapshot(UUID gameId);
    List<GameAggregate> findFinishedByPlayerId(UUID playerId);
    void deleteAll();
}
