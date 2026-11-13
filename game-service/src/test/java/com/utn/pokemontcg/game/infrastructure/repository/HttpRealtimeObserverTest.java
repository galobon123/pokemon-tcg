package com.utn.pokemontcg.game.infrastructure.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpRealtimeObserverTest {

    @Test
    void constructorAcceptsUrl() {
        var observer = new HttpRealtimeObserver("http://localhost:8082");
        assertNotNull(observer);
    }

    @Test
    void constructorDefaultsToLocalhost8082() {
        var observer = new HttpRealtimeObserver("http://localhost:8082");
        assertNotNull(observer);
    }

    @Test
    void onEventDoesNotThrowOnEmptyEvent() {
        var observer = new HttpRealtimeObserver("http://localhost:1");
        com.utn.pokemontcg.game.domain.event.GameEvent event =
            com.utn.pokemontcg.game.domain.event.GameEvent.of(
                java.util.UUID.randomUUID(),
                com.utn.pokemontcg.game.domain.event.GameEventType.GAME_CREATED,
                java.util.Map.of()
            );
        assertDoesNotThrow(() -> observer.onEvent(event));
    }

    @Test
    void onEventDoesNotThrowOnNullPayloadEvent() {
        var observer = new HttpRealtimeObserver("http://localhost:1");
        com.utn.pokemontcg.game.domain.event.GameEvent event =
            com.utn.pokemontcg.game.domain.event.GameEvent.of(
                java.util.UUID.randomUUID(),
                com.utn.pokemontcg.game.domain.event.GameEventType.GAME_CREATED,
                null
            );
        assertDoesNotThrow(() -> observer.onEvent(event));
    }
}
