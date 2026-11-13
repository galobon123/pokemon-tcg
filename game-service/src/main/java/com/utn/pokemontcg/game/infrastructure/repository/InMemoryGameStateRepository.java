package com.utn.pokemontcg.game.infrastructure.repository;

import com.utn.pokemontcg.game.application.repository.ActionLogEntry;
import com.utn.pokemontcg.game.application.repository.GameStateRepository;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameState;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@Profile("memory")
public class InMemoryGameStateRepository implements GameStateRepository {

    private final Map<UUID, GameAggregate> store = new ConcurrentHashMap<>();
    private final Map<UUID, List<ActionLogEntry>> logs = new ConcurrentHashMap<>();
    private final AtomicLong logIds = new AtomicLong();

    @Override
    public GameAggregate save(GameAggregate game) {
        store.put(game.id(), game);
        return game;
    }

    @Override
    public Optional<GameAggregate> findById(UUID gameId) {
        return Optional.ofNullable(store.get(gameId));
    }

    @Override
    public void appendActionLog(UUID gameId, GameActionType action, UUID playerId, String result, Instant timestamp) {
        appendActionLog(gameId, action, playerId, result, timestamp, Map.of());
    }

    @Override
    public void appendActionLog(UUID gameId, GameActionType action, UUID playerId, String result, Instant timestamp, Map<String, Object> payload) {
        logs.computeIfAbsent(gameId, id -> new ArrayList<>())
            .add(new ActionLogEntry(
                logIds.incrementAndGet(),
                gameId,
                action,
                playerId,
                result,
                payload == null ? Map.of() : new HashMap<>(payload),
                timestamp
            ));
    }

    @Override
    public List<ActionLogEntry> actionLog(UUID gameId) {
        return List.copyOf(logs.getOrDefault(gameId, List.of()));
    }

    @Override
    public Optional<GameAggregate> latestSnapshot(UUID gameId) {
        return findById(gameId);
    }

    @Override
    public void deleteAll() {
        store.clear();
        logs.clear();
    }

    @Override
    public List<GameAggregate> findFinishedByPlayerId(UUID playerId) {
        return store.values().stream()
            .filter(game -> game.gameState() == GameState.FINISHED)
            .filter(game -> playerId.equals(game.playerOne()) || playerId.equals(game.playerTwo()))
            .collect(Collectors.toList());
    }
}
