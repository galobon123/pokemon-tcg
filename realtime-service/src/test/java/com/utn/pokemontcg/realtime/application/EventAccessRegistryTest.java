package com.utn.pokemontcg.realtime.application;

import com.utn.pokemontcg.realtime.dto.GameEventEnvelope;
import com.utn.pokemontcg.realtime.dto.GameEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventAccessRegistryTest {

    @Test
    void shouldAllowOnlyRegisteredGamePlayers() {
        EventAccessRegistry registry = new EventAccessRegistry();
        UUID gameId = UUID.randomUUID();
        UUID playerOne = UUID.randomUUID();

        registry.register(new GameEventEnvelope(
            1L, 1, gameId,
            GameEventType.GAME_CREATED,
            Map.of("playerOne", playerOne.toString()),
            Instant.parse("2026-05-06T18:00:00Z"),
            Instant.parse("2026-05-06T18:00:01Z")
        ));

        assertTrue(registry.canSubscribe(gameId, playerOne));
        assertFalse(registry.canSubscribe(gameId, UUID.randomUUID()));
    }

    @Test
    void shouldRegisterPlayerTwoOnPlayerJoined() {
        EventAccessRegistry registry = new EventAccessRegistry();
        UUID gameId = UUID.randomUUID();
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();

        registry.register(new GameEventEnvelope(
            1L, 1, gameId,
            GameEventType.GAME_CREATED,
            Map.of("playerOne", playerOne.toString()),
            Instant.parse("2026-05-06T18:00:00Z"),
            Instant.parse("2026-05-06T18:00:01Z")
        ));
        registry.register(new GameEventEnvelope(
            2L, 1, gameId,
            GameEventType.PLAYER_JOINED,
            Map.of("playerTwo", playerTwo.toString()),
            Instant.parse("2026-05-06T18:01:00Z"),
            Instant.parse("2026-05-06T18:01:01Z")
        ));

        assertTrue(registry.canSubscribe(gameId, playerOne));
        assertTrue(registry.canSubscribe(gameId, playerTwo));
    }

    @Test
    void shouldRegisterPlayerThroughDirectMethod() {
        EventAccessRegistry registry = new EventAccessRegistry();
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        registry.registerPlayer(gameId, playerId);

        assertTrue(registry.canSubscribe(gameId, playerId));
    }

    @Test
    void shouldIgnoreNullParametersInRegisterPlayer() {
        EventAccessRegistry registry = new EventAccessRegistry();
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        registry.registerPlayer(null, playerId);
        registry.registerPlayer(gameId, null);
        registry.registerPlayer(null, null);

        assertFalse(registry.canSubscribe(gameId, playerId));
    }

    @Test
    void shouldIgnoreUnrelatedEventTypes() {
        EventAccessRegistry registry = new EventAccessRegistry();
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        registry.register(new GameEventEnvelope(
            1L, 1, gameId,
            GameEventType.ENERGY_ATTACHED,
            Map.of("player", playerId.toString()),
            Instant.parse("2026-05-06T18:00:00Z"),
            Instant.parse("2026-05-06T18:00:01Z")
        ));

        assertFalse(registry.canSubscribe(gameId, playerId));
    }

    @Test
    void shouldHandleNonStringPayloadValues() {
        EventAccessRegistry registry = new EventAccessRegistry();
        UUID gameId = UUID.randomUUID();

        registry.register(new GameEventEnvelope(
            1L, 1, gameId,
            GameEventType.GAME_CREATED,
            Map.of("playerOne", 123),
            Instant.parse("2026-05-06T18:00:00Z"),
            Instant.parse("2026-05-06T18:00:01Z")
        ));

        assertFalse(registry.canSubscribe(gameId, UUID.randomUUID()));
    }

    @Test
    void shouldHandleMissingPayloadEntries() {
        EventAccessRegistry registry = new EventAccessRegistry();
        UUID gameId = UUID.randomUUID();

        registry.register(new GameEventEnvelope(
            1L, 1, gameId,
            GameEventType.GAME_CREATED,
            Map.of(),
            Instant.parse("2026-05-06T18:00:00Z"),
            Instant.parse("2026-05-06T18:00:01Z")
        ));
        registry.register(new GameEventEnvelope(
            2L, 1, gameId,
            GameEventType.PLAYER_JOINED,
            Map.of(),
            Instant.parse("2026-05-06T18:01:00Z"),
            Instant.parse("2026-05-06T18:01:01Z")
        ));

        assertFalse(registry.canSubscribe(gameId, UUID.randomUUID()));
    }

    @Test
    void shouldHandleBlankStringPayloadValues() {
        EventAccessRegistry registry = new EventAccessRegistry();
        UUID gameId = UUID.randomUUID();

        registry.register(new GameEventEnvelope(
            1L, 1, gameId,
            GameEventType.GAME_CREATED,
            Map.of("playerOne", ""),
            Instant.parse("2026-05-06T18:00:00Z"),
            Instant.parse("2026-05-06T18:00:01Z")
        ));

        assertFalse(registry.canSubscribe(gameId, UUID.randomUUID()));
    }

    @Test
    void shouldRegisterWithUuidPayloadValue() {
        EventAccessRegistry registry = new EventAccessRegistry();
        UUID gameId = UUID.randomUUID();
        UUID playerOne = UUID.randomUUID();

        // playerOne as UUID object → parseUuid instanceof UUID path (L49)
        registry.register(new GameEventEnvelope(
            1L, 1, gameId,
            GameEventType.GAME_CREATED,
            Map.of("playerOne", playerOne),
            Instant.parse("2026-05-06T18:00:00Z"),
            Instant.parse("2026-05-06T18:00:01Z")
        ));

        assertTrue(registry.canSubscribe(gameId, playerOne));
    }
}

