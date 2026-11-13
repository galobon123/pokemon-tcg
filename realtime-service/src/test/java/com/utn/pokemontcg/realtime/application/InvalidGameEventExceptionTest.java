package com.utn.pokemontcg.realtime.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InvalidGameEventExceptionTest {

    @Test
    void shouldStoreMessage() {
        var ex = new InvalidGameEventException("test error");
        assertEquals("test error", ex.getMessage());
    }

    @Test
    void shouldAllowNullMessage() {
        var ex = new InvalidGameEventException(null);
        assertNull(ex.getMessage());
    }
}
