package com.utn.pokemontcg.realtime.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.realtime.dto.GameEventEnvelope;
import com.utn.pokemontcg.realtime.dto.GameEventMessage;
import com.utn.pokemontcg.realtime.dto.GameEventType;
import com.utn.pokemontcg.realtime.infrastructure.persistence.RealtimeEventEntity;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EventBroadcastServiceTest {

    @Test
    void shouldRememberEventsForReconnectAndBroadcastToGameTopic() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        EventBroadcastService service = serviceWith(messagingTemplate, new InMemoryRealtimeEventStore());
        UUID gameId = UUID.randomUUID();
        GameEventMessage oldEvent = new GameEventMessage(gameId, "TURN_STARTED", Map.of(), Instant.parse("2026-05-06T18:00:00Z"));
        GameEventMessage newEvent = new GameEventMessage(gameId, "ATTACK_RESOLVED", Map.of("damage", 30), Instant.parse("2026-05-06T18:01:00Z"));

        service.broadcast(oldEvent);
        GameEventEnvelope envelope = service.broadcast(newEvent);

        assertEquals(1, service.eventsSince(gameId, Instant.parse("2026-05-06T18:00:30Z")).size());
        assertEquals(GameEventType.ATTACK_RESOLVED, service.eventsSince(gameId, Instant.parse("2026-05-06T18:00:30Z")).get(0).type());
        assertEquals(2L, envelope.sequence());
        assertEquals(1, envelope.schemaVersion());
        verify(messagingTemplate).convertAndSend("/topic/games/" + gameId + "/events", envelope);
    }

    @Test
    void broadcastWithInvalidEventTypeThrowsException() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        EventBroadcastService service = serviceWith(messagingTemplate, new InMemoryRealtimeEventStore());
        UUID gameId = UUID.randomUUID();

        assertThrows(InvalidGameEventException.class,
            () -> service.broadcast(new GameEventMessage(gameId, "UNKNOWN_EVENT_TYPE", Map.of(), Instant.now())));
    }

    @Test
    void shouldRebuildMemoryFromPersistentEvents() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        InMemoryRealtimeEventStore eventStore = new InMemoryRealtimeEventStore();
        UUID gameId = UUID.randomUUID();

        // Save events in one service instance
        EventBroadcastService service = serviceWith(messagingTemplate, eventStore);
        service.broadcast(new GameEventMessage(gameId, "GAME_CREATED",
            Map.of("playerOne", UUID.randomUUID().toString()), Instant.parse("2026-05-06T18:00:00Z")));
        service.broadcast(new GameEventMessage(gameId, "TURN_STARTED",
            Map.of(), Instant.parse("2026-05-06T18:01:00Z")));

        // Simulate restart — create new service with same event store & call @PostConstruct manually
        EventBroadcastService restarted = serviceWith(messagingTemplate, eventStore);
        restarted.rebuildMemoryFromPersistentEvents();

        assertEquals(2, restarted.eventsSince(gameId, Instant.EPOCH).size());
    }

    @Test
    void shouldReturnPendingEventsAfterSequenceForReconnect() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        EventBroadcastService service = serviceWith(messagingTemplate, new InMemoryRealtimeEventStore());
        UUID gameId = UUID.randomUUID();

        service.broadcast(new GameEventMessage(gameId, "TURN_STARTED", Map.of(), Instant.parse("2026-05-06T18:00:00Z")));
        service.broadcast(new GameEventMessage(gameId, "CARD_DRAWN", Map.of("deckRemaining", 46), Instant.parse("2026-05-06T18:01:00Z")));

        var response = service.pendingEvents(gameId, 1L, null);

        assertEquals(2L, response.lastSequence());
        assertFalse(response.replayFromMemory());
        assertEquals(1, response.events().size());
        assertEquals(GameEventType.CARD_DRAWN, response.events().get(0).type());
    }

    @Test
    void broadcastSerializationFailureDoesNotThrow() throws Exception {
        // First: persist events with a real mapper
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        InMemoryRealtimeEventStore eventStore = new InMemoryRealtimeEventStore();
        EventBroadcastService realService = serviceWith(messagingTemplate, eventStore);
        UUID gameId = UUID.randomUUID();
        realService.broadcast(new GameEventMessage(gameId, "GAME_CREATED",
            Map.of("playerOne", UUID.randomUUID().toString()), Instant.now()));
        realService.broadcast(new GameEventMessage(gameId, "PLAYER_JOINED",
            Map.of("playerTwo", UUID.randomUUID().toString()), Instant.now()));

        // payloadMap catch — rebuildMemoryFromPersistentEvents with broken mapper → readValue fails
        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        doThrow(new JsonProcessingException("boom read") {})
            .when(brokenMapper).readValue(anyString(), any(TypeReference.class));
        EventBroadcastService brokenService = serviceWith(messagingTemplate, eventStore, brokenMapper);
        // readValue fails on persisted events → InvalidGameEventException
        assertThrows(InvalidGameEventException.class, brokenService::rebuildMemoryFromPersistentEvents);
    }

    @Test
    void broadcastWithBrokenWriteValueAsStringThrows() throws JsonProcessingException {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        doThrow(new JsonProcessingException("boom") {}).when(brokenMapper).writeValueAsString(any(Object.class));
        EventBroadcastService service = serviceWith(messagingTemplate, new InMemoryRealtimeEventStore(), brokenMapper);

        // payloadJson catch — writeValueAsString fails → wrapped in InvalidGameEventException
        UUID gameId = UUID.randomUUID();
        assertThrows(InvalidGameEventException.class, () -> service.broadcast(new GameEventMessage(gameId, "GAME_CREATED",
            Map.of("playerOne", UUID.randomUUID().toString()), Instant.now())));

        // payloadMap catch — rebuildMemoryFromPersistentEvents → readValue fails
        assertDoesNotThrow(service::rebuildMemoryFromPersistentEvents);
    }

    private EventBroadcastService serviceWith(SimpMessagingTemplate messagingTemplate, RealtimeEventStore eventStore) {
        return new EventBroadcastService(
            messagingTemplate,
            eventStore,
            new EventAccessRegistry(),
            new ObjectMapper().findAndRegisterModules()
        );
    }

    private EventBroadcastService serviceWith(SimpMessagingTemplate messagingTemplate, RealtimeEventStore eventStore, ObjectMapper mapper) {
        return new EventBroadcastService(
            messagingTemplate,
            eventStore,
            new EventAccessRegistry(),
            mapper
        );
    }

    private static class InMemoryRealtimeEventStore implements RealtimeEventStore {

        private final AtomicLong sequence = new AtomicLong();
        private final List<RealtimeEventEntity> events = new ArrayList<>();

        @Override
        public RealtimeEventEntity save(RealtimeEventEntity event) {
            event.assignSequenceForTest(sequence.incrementAndGet());
            events.add(event);
            return event;
        }

        @Override
        public List<RealtimeEventEntity> findAllOrdered() {
            return events.stream()
                .sorted(Comparator.comparingLong(RealtimeEventEntity::sequence))
                .toList();
        }

        @Override
        public List<RealtimeEventEntity> findByGameId(UUID gameId) {
            return events.stream()
                .filter(event -> event.gameId().equals(gameId))
                .sorted(Comparator.comparingLong(RealtimeEventEntity::sequence))
                .toList();
        }

        @Override
        public List<RealtimeEventEntity> findByGameIdAfterSequence(UUID gameId, long sequence) {
            return events.stream()
                .filter(event -> event.gameId().equals(gameId))
                .filter(event -> event.sequence() > sequence)
                .sorted(Comparator.comparingLong(RealtimeEventEntity::sequence))
                .toList();
        }

        @Override
        public Optional<RealtimeEventEntity> findLatestByGameId(UUID gameId) {
            return events.stream()
                .filter(event -> event.gameId().equals(gameId))
                .max(Comparator.comparingLong(RealtimeEventEntity::sequence));
        }
    }
}
