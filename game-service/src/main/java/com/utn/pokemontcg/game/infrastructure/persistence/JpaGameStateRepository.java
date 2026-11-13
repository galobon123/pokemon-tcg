package com.utn.pokemontcg.game.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.game.application.repository.ActionLogEntry;
import com.utn.pokemontcg.game.application.repository.GameStateRepository;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameState;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!memory")
@Transactional
public class JpaGameStateRepository implements GameStateRepository {

    private final GameSnapshotJpaRepository snapshots;
    private final GameActionLogJpaRepository logs;
    private final ObjectMapper objectMapper;

    public JpaGameStateRepository(GameSnapshotJpaRepository snapshots,
                                  GameActionLogJpaRepository logs,
                                  ObjectMapper objectMapper) {
        this.snapshots = snapshots;
        this.logs = logs;
        this.objectMapper = objectMapper;
    }

    @Override
    public GameAggregate save(GameAggregate game) {
        long nextVersion = snapshots.findTopByGameIdOrderByVersionDesc(game.id())
            .map(GameSnapshotEntity::getVersion)
            .orElse(0L) + 1;
        GameSnapshotEntity entity = new GameSnapshotEntity();
        entity.setGameId(game.id());
        entity.setVersion(nextVersion);
        entity.setPayload(serialize(game));
        entity.setUpdatedAt(Instant.now());
        snapshots.save(entity);
        return game;
    }

    @Override
    public Optional<GameAggregate> findById(UUID gameId) {
        return latestSnapshot(gameId);
    }

    @Override
    public void appendActionLog(UUID gameId, GameActionType action, UUID playerId, String result, Instant timestamp) {
        appendActionLog(gameId, action, playerId, result, timestamp, Map.of());
    }

    @Override
    public void appendActionLog(UUID gameId, GameActionType action, UUID playerId, String result, Instant timestamp, Map<String, Object> payload) {
        GameActionLogEntity log = new GameActionLogEntity();
        log.setGameId(gameId);
        log.setCreatedAt(timestamp);
        log.setAction(action);
        log.setPlayerId(playerId);
        log.setResult(result);
        log.setPayload(serializePayload(payload));
        log.setEntry(timestamp + " | player=" + playerId + " | action=" + action + " | result=" + result);
        logs.save(log);
    }

    @Override
    public List<ActionLogEntry> actionLog(UUID gameId) {
        return logs.findByGameIdOrderByCreatedAtAsc(gameId).stream()
            .map(this::toActionLogEntry)
            .toList();
    }

    @Override
    public Optional<GameAggregate> latestSnapshot(UUID gameId) {
        return snapshots.findTopByGameIdOrderByVersionDesc(gameId).map(e -> deserialize(e.getPayload()));
    }

    @Override
    public void deleteAll() {
        logs.deleteAll();
        snapshots.deleteAll();
    }

    @Override
    public List<GameAggregate> findFinishedByPlayerId(UUID playerId) {
        List<UUID> gameIds = snapshots.findAllDistinctGameIds();
        List<GameAggregate> result = new ArrayList<>();
        for (UUID gameId : gameIds) {
            latestSnapshot(gameId).ifPresent(game -> {
                if (game.gameState() == GameState.FINISHED
                    && (playerId.equals(game.playerOne()) || playerId.equals(game.playerTwo()))) {
                    result.add(game);
                }
            });
        }
        return result;
    }

    private String serialize(GameAggregate game) {
        try {
            return objectMapper.writeValueAsString(game);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize game aggregate", e);
        }
    }

    private GameAggregate deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, GameAggregate.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize game aggregate", e);
        }
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize action log payload", e);
        }
    }

    private Map<String, Object> deserializePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of("raw", payload);
        }
    }

    private ActionLogEntry toActionLogEntry(GameActionLogEntity entity) {
        return new ActionLogEntry(
            entity.getId(),
            entity.getGameId(),
            entity.getAction(),
            entity.getPlayerId(),
            entity.getResult(),
            deserializePayload(entity.getPayload()),
            entity.getCreatedAt()
        );
    }
}
