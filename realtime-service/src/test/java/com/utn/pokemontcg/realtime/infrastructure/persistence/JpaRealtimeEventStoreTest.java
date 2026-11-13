package com.utn.pokemontcg.realtime.infrastructure.persistence;

import com.utn.pokemontcg.realtime.application.RealtimeEventStore;
import com.utn.pokemontcg.realtime.dto.GameEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(JpaRealtimeEventStore.class)
class JpaRealtimeEventStoreTest {

    @Autowired
    private RealtimeEventStore store;

    @Test
    void shouldSaveAndFindAllOrdered() {
        UUID gameId = UUID.randomUUID();
        store.save(createEvent(gameId, GameEventType.GAME_CREATED));
        store.save(createEvent(gameId, GameEventType.PLAYER_JOINED));

        assertEquals(2, store.findAllOrdered().size());
    }

    @Test
    void shouldFindByGameId() {
        UUID gameId = UUID.randomUUID();
        store.save(createEvent(gameId, GameEventType.GAME_CREATED));
        store.save(createEvent(gameId, GameEventType.PLAYER_JOINED));

        assertEquals(2, store.findByGameId(gameId).size());
    }

    @Test
    void shouldFindByGameIdAfterSequence() {
        UUID gameId = UUID.randomUUID();
        var first = store.save(createEvent(gameId, GameEventType.GAME_CREATED));
        store.save(createEvent(gameId, GameEventType.TURN_STARTED));

        var events = store.findByGameIdAfterSequence(gameId, first.sequence());
        assertEquals(1, events.size());
        assertEquals(GameEventType.TURN_STARTED, events.get(0).type());
    }

    @Test
    void shouldFindLatestByGameId() {
        UUID gameId = UUID.randomUUID();
        store.save(createEvent(gameId, GameEventType.GAME_CREATED));
        var latest = store.save(createEvent(gameId, GameEventType.ENERGY_ATTACHED));

        var found = store.findLatestByGameId(gameId);
        assertTrue(found.isPresent());
        assertEquals(latest.sequence(), found.get().sequence());
        assertEquals(GameEventType.ENERGY_ATTACHED, found.get().type());
    }

    @Test
    void shouldReturnEmptyForMissingGame() {
        assertTrue(store.findLatestByGameId(UUID.randomUUID()).isEmpty());
        assertTrue(store.findByGameId(UUID.randomUUID()).isEmpty());
    }

    private RealtimeEventEntity createEvent(UUID gameId, GameEventType type) {
        return new RealtimeEventEntity(
            1, gameId, type, "{}",
            Instant.parse("2026-05-06T18:00:00Z"),
            Instant.parse("2026-05-06T18:00:01Z")
        );
    }
}
