package com.utn.pokemontcg.game.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Branch coverage push for {@link GameSnapshotEntity} (70% line, 100% branch).
 * Targets the 3/10 missed lines: most getters/setters on the entity.
 */
class GameSnapshotEntityBranchCoverageTest {

    @Test
    void newEntityStartsWithNullFields() {
        GameSnapshotEntity entity = new GameSnapshotEntity();
        assertNull(entity.getId());
        assertNull(entity.getGameId());
        assertEquals(0L, entity.getVersion());
        assertNull(entity.getPayload());
        assertNull(entity.getUpdatedAt());
    }

    @Test
    void settersStoreValues() {
        GameSnapshotEntity entity = new GameSnapshotEntity();
        UUID gameId = UUID.randomUUID();
        Instant now = Instant.now();

        entity.setGameId(gameId);
        entity.setVersion(7L);
        entity.setPayload("{\"foo\":\"bar\"}");
        entity.setUpdatedAt(now);

        assertEquals(gameId, entity.getGameId());
        assertEquals(7L, entity.getVersion());
        assertEquals("{\"foo\":\"bar\"}", entity.getPayload());
        assertEquals(now, entity.getUpdatedAt());
    }

    @Test
    void getIdIsReadableAfterSetters() {
        GameSnapshotEntity entity = new GameSnapshotEntity();
        // use reflection to simulate JPA-managed id assignment
        try {
            java.lang.reflect.Field field = GameSnapshotEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, 42L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertEquals(42L, entity.getId());
    }
}
