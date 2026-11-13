package com.utn.pokemontcg.game.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JpaGameStateRepositoryTest {

    private GameSnapshotJpaRepository snapshots;
    private GameActionLogJpaRepository logs;
    private ObjectMapper objectMapper;
    private JpaGameStateRepository repository;

    @BeforeEach
    void setUp() {
        snapshots = mock(GameSnapshotJpaRepository.class);
        logs = mock(GameActionLogJpaRepository.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        repository = new JpaGameStateRepository(snapshots, logs, objectMapper);
    }

    @Test
    void savePersistsNewGameWithVersion1() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        when(snapshots.findTopByGameIdOrderByVersionDesc(game.id())).thenReturn(Optional.empty());
        when(snapshots.save(any())).thenAnswer(i -> i.getArgument(0));

        GameAggregate result = repository.save(game);

        assertNotNull(result);
        verify(snapshots).save(any(GameSnapshotEntity.class));
    }

    @Test
    void savePersistsGameWithIncrementedVersion() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        GameSnapshotEntity existing = new GameSnapshotEntity();
        existing.setVersion(3L);
        when(snapshots.findTopByGameIdOrderByVersionDesc(game.id())).thenReturn(Optional.of(existing));
        when(snapshots.save(any())).thenAnswer(i -> {
            GameSnapshotEntity saved = i.getArgument(0);
            assertEquals(4L, saved.getVersion());
            return saved;
        });

        repository.save(game);
        verify(snapshots).save(any(GameSnapshotEntity.class));
    }

    @Test
    void findByIdReturnsDeserializedGame() throws Exception {
        UUID gameId = UUID.randomUUID();
        GameAggregate original = new GameAggregate(UUID.randomUUID());
        original.setPlayerTwo(UUID.randomUUID());
        original.setGameState(GameState.ACTIVE);
        String payload = objectMapper.writeValueAsString(original);

        GameSnapshotEntity entity = new GameSnapshotEntity();
        entity.setGameId(gameId);
        entity.setVersion(1L);
        entity.setPayload(payload);
        entity.setUpdatedAt(Instant.now());

        when(snapshots.findTopByGameIdOrderByVersionDesc(gameId)).thenReturn(Optional.of(entity));

        Optional<GameAggregate> result = repository.findById(gameId);

        assertTrue(result.isPresent());
        assertEquals(original.id(), result.get().id());
    }

    @Test
    void findByIdReturnsEmptyWhenNoSnapshots() {
        UUID gameId = UUID.randomUUID();
        when(snapshots.findTopByGameIdOrderByVersionDesc(gameId)).thenReturn(Optional.empty());
        assertTrue(repository.findById(gameId).isEmpty());
    }

    @Test
    void appendActionLogSavesLogEntry() {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();

        repository.appendActionLog(gameId, GameActionType.DRAW, playerId, "OK", now);

        verify(logs).save(any(GameActionLogEntity.class));
    }

    @Test
    void appendActionLogWithPayloadSavesLogEntry() {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();

        repository.appendActionLog(gameId, GameActionType.DRAW, playerId, "OK", now, Map.of("cardId", "c1"));

        verify(logs).save(any(GameActionLogEntity.class));
    }

    @Test
    void actionLogReturnsEntriesOrderedByCreatedAt() {
        UUID gameId = UUID.randomUUID();
        GameActionLogEntity entry1 = new GameActionLogEntity();
        entry1.setGameId(gameId);
        entry1.setAction(GameActionType.DRAW);
        entry1.setPlayerId(UUID.randomUUID());
        entry1.setResult("OK");
        entry1.setPayload("{\"cardId\":\"c1\"}");
        entry1.setCreatedAt(Instant.now());

        when(logs.findByGameIdOrderByCreatedAtAsc(gameId)).thenReturn(List.of(entry1));

        var result = repository.actionLog(gameId);

        assertEquals(1, result.size());
        assertEquals(GameActionType.DRAW, result.get(0).action());
    }

    @Test
    void deleteAllDeletesAllRecords() {
        repository.deleteAll();
        verify(logs).deleteAll();
        verify(snapshots).deleteAll();
    }

    @Test
    void latestSnapshotReturnsLatestVersion() {
        UUID gameId = UUID.randomUUID();
        GameAggregate original = new GameAggregate(UUID.randomUUID());
        original.setPlayerTwo(UUID.randomUUID());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(original);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        GameSnapshotEntity entity = new GameSnapshotEntity();
        entity.setGameId(gameId);
        entity.setVersion(5L);
        entity.setPayload(payload);

        when(snapshots.findTopByGameIdOrderByVersionDesc(gameId)).thenReturn(Optional.of(entity));

        Optional<GameAggregate> result = repository.latestSnapshot(gameId);
        assertTrue(result.isPresent());
        assertEquals(original.id(), result.get().id());
    }

    @Test
    void findFinishedByPlayerIdReturnsMatchingGames() {
        UUID playerId = UUID.randomUUID();
        UUID opponentId = UUID.randomUUID();

        GameAggregate finished = new GameAggregate(playerId);
        finished.setPlayerTwo(opponentId);
        finished.setGameState(GameState.FINISHED);
        finished.setWinner(playerId);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(finished);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        GameSnapshotEntity entity = new GameSnapshotEntity();
        entity.setGameId(finished.id());
        entity.setVersion(1L);
        entity.setPayload(payload);

        when(snapshots.findAllDistinctGameIds()).thenReturn(List.of(finished.id()));
        when(snapshots.findTopByGameIdOrderByVersionDesc(finished.id())).thenReturn(Optional.of(entity));

        List<GameAggregate> result = repository.findFinishedByPlayerId(playerId);
        assertEquals(1, result.size());
        assertEquals(finished.id(), result.get(0).id());
    }

    @Test
    void findFinishedByPlayerIdFiltersNonFinishedGames() {
        UUID playerId = UUID.randomUUID();

        GameAggregate active = new GameAggregate(playerId);
        active.setPlayerTwo(UUID.randomUUID());
        active.setGameState(GameState.ACTIVE);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(active);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        GameSnapshotEntity entity = new GameSnapshotEntity();
        entity.setGameId(active.id());
        entity.setVersion(1L);
        entity.setPayload(payload);

        when(snapshots.findAllDistinctGameIds()).thenReturn(List.of(active.id()));
        when(snapshots.findTopByGameIdOrderByVersionDesc(active.id())).thenReturn(Optional.of(entity));

        List<GameAggregate> result = repository.findFinishedByPlayerId(playerId);
        assertTrue(result.isEmpty());
    }
}
