package com.utn.pokemontcg.game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.infrastructure.persistence.GameActionLogEntity;
import com.utn.pokemontcg.game.infrastructure.persistence.GameSnapshotEntity;
import com.utn.pokemontcg.game.infrastructure.persistence.JpaGameStateRepository;
import com.utn.pokemontcg.game.infrastructure.persistence.GameActionLogJpaRepository;
import com.utn.pokemontcg.game.infrastructure.persistence.GameSnapshotJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch coverage push for {@link JpaGameStateRepository}. Targets the
 * catch blocks of {@code serialize/deserialize}, the deserializePayload
 * null/blank/malformed paths, and {@code findFinishedByPlayerId} when
 * the player matches as playerTwo.
 */
class JpaGameStateRepositoryCoverageTest {

    private GameSnapshotJpaRepository snapshots;
    private GameActionLogJpaRepository logs;
    private JpaGameStateRepository repository;

    @BeforeEach
    void setUp() {
        snapshots = mock(GameSnapshotJpaRepository.class);
        logs = mock(GameActionLogJpaRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        repository = new JpaGameStateRepository(snapshots, logs, objectMapper);
    }

    @Test
    void saveWrapsSerializeJsonProcessingException() {
        ObjectMapper failing = new FailingObjectMapper();
        JpaGameStateRepository failingRepo =
            new JpaGameStateRepository(snapshots, logs, failing);
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        when(snapshots.findTopByGameIdOrderByVersionDesc(game.id())).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> failingRepo.save(game));
        assertTrue(ex.getMessage().contains("Failed to serialize"));
    }

    @Test
    void findByIdWrapsDeserializeJsonProcessingException() {
        ObjectMapper failing = new FailingObjectMapper();
        JpaGameStateRepository failingRepo =
            new JpaGameStateRepository(snapshots, logs, failing);
        UUID gameId = UUID.randomUUID();
        GameSnapshotEntity entity = new GameSnapshotEntity();
        entity.setGameId(gameId);
        entity.setVersion(1L);
        entity.setPayload("{not valid json");
        entity.setUpdatedAt(Instant.now());
        when(snapshots.findTopByGameIdOrderByVersionDesc(gameId))
            .thenReturn(Optional.of(entity));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> failingRepo.findById(gameId));
        assertTrue(ex.getMessage().contains("Failed to deserialize"));
    }

    @Test
    void actionLogReturnsEmptyPayloadForNullEntity() {
        UUID gameId = UUID.randomUUID();
        GameActionLogEntity entity = new GameActionLogEntity();
        entity.setGameId(gameId);
        entity.setAction(GameActionType.DRAW);
        entity.setPlayerId(UUID.randomUUID());
        entity.setResult("OK");
        entity.setPayload(null);  // null payload → deserializePayload returns Map.of()
        entity.setCreatedAt(Instant.now());
        when(logs.findByGameIdOrderByCreatedAtAsc(gameId)).thenReturn(List.of(entity));

        var result = repository.actionLog(gameId);

        assertEquals(1, result.size());
        assertNotNull(result.get(0).payload());
        assertTrue(result.get(0).payload().isEmpty());
    }

    @Test
    void actionLogReturnsEmptyPayloadForBlankEntity() {
        UUID gameId = UUID.randomUUID();
        GameActionLogEntity entity = new GameActionLogEntity();
        entity.setGameId(gameId);
        entity.setAction(GameActionType.DRAW);
        entity.setPlayerId(UUID.randomUUID());
        entity.setResult("OK");
        entity.setPayload("   ");
        entity.setCreatedAt(Instant.now());
        when(logs.findByGameIdOrderByCreatedAtAsc(gameId)).thenReturn(List.of(entity));

        var result = repository.actionLog(gameId);

        assertEquals(1, result.size());
        assertTrue(result.get(0).payload().isEmpty());
    }

    @Test
    void actionLogReturnsRawPayloadForMalformedJson() {
        UUID gameId = UUID.randomUUID();
        GameActionLogEntity entity = new GameActionLogEntity();
        entity.setGameId(gameId);
        entity.setAction(GameActionType.DRAW);
        entity.setPlayerId(UUID.randomUUID());
        entity.setResult("OK");
        String malformed = "{this is : not, valid json";
        entity.setPayload(malformed);
        entity.setCreatedAt(Instant.now());
        when(logs.findByGameIdOrderByCreatedAtAsc(gameId)).thenReturn(List.of(entity));

        var result = repository.actionLog(gameId);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).payload().size());
        assertEquals(malformed, result.get(0).payload().get("raw"));
    }

    @Test
    void appendActionLogWithNullPayloadSerializesEmptyMap() {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();
        AtomicReference<String> serializedPayload = new AtomicReference<>();

        when(logs.save(any(GameActionLogEntity.class))).thenAnswer(i -> {
            GameActionLogEntity saved = i.getArgument(0);
            serializedPayload.set(saved.getPayload());
            return saved;
        });

        repository.appendActionLog(gameId, GameActionType.DRAW, playerId, "OK", now, null);

        verify(logs).save(any(GameActionLogEntity.class));
        assertNotNull(serializedPayload.get());
        // {} is the serialized form of an empty map
        assertEquals("{}", serializedPayload.get());
    }

    @Test
    void findFinishedByPlayerIdReturnsMatchOnPlayerTwo() {
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();

        GameAggregate finished = new GameAggregate(playerOne);
        finished.setPlayerTwo(playerTwo);
        finished.setGameState(GameState.FINISHED);
        finished.setWinner(playerTwo);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String payload;
        try {
            payload = mapper.writeValueAsString(finished);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        GameSnapshotEntity entity = new GameSnapshotEntity();
        entity.setGameId(finished.id());
        entity.setVersion(1L);
        entity.setPayload(payload);

        when(snapshots.findAllDistinctGameIds()).thenReturn(List.of(finished.id()));
        when(snapshots.findTopByGameIdOrderByVersionDesc(finished.id()))
            .thenReturn(Optional.of(entity));

        // query as playerTwo — the player who did NOT create the game
        List<GameAggregate> result = repository.findFinishedByPlayerId(playerTwo);
        assertEquals(1, result.size(),
            "El jugador que es playerTwo en la partida finalizada debe recibir la partida");
    }

    @Test
    void findFinishedByPlayerIdSkipsWhenPlayerIsNotParticipant() {
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        GameAggregate finished = new GameAggregate(playerOne);
        finished.setPlayerTwo(playerTwo);
        finished.setGameState(GameState.FINISHED);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String payload;
        try {
            payload = mapper.writeValueAsString(finished);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        GameSnapshotEntity entity = new GameSnapshotEntity();
        entity.setGameId(finished.id());
        entity.setVersion(1L);
        entity.setPayload(payload);

        when(snapshots.findAllDistinctGameIds()).thenReturn(List.of(finished.id()));
        when(snapshots.findTopByGameIdOrderByVersionDesc(finished.id()))
            .thenReturn(Optional.of(entity));

        List<GameAggregate> result = repository.findFinishedByPlayerId(stranger);
        assertTrue(result.isEmpty(),
            "Un jugador que no participo en la partida no debe recibirla");
    }

    /**
     * Forces {@link ObjectMapper#writeValueAsString} to throw on any input
     * so we can exercise the catch blocks in serialize/deserialize.
     */
    private static class FailingObjectMapper extends ObjectMapper {
        @Override
        public String writeValueAsString(Object value) throws JsonProcessingException {
            throw new JsonProcessingException("forced failure for test") {};
        }

        @Override
        public <T> T readValue(String content, Class<T> valueType) throws JsonProcessingException {
            throw new JsonProcessingException("forced failure for test") {};
        }

        @Override
        public <T> T readValue(String content,
                               com.fasterxml.jackson.core.type.TypeReference<T> valueTypeRef)
                throws JsonProcessingException {
            throw new JsonProcessingException("forced failure for test") {};
        }
    }
}
