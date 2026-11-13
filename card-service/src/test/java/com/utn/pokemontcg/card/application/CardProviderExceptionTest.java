package com.utn.pokemontcg.card.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardProviderExceptionTest {

    @Test
    void storesMessageAndCauseProvidedAtConstruction() {
        Throwable cause = new IllegalStateException("upstream timeout");

        CardProviderException ex = new CardProviderException("Pokemon TCG provider unavailable", cause);

        assertEquals("Pokemon TCG provider unavailable", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void isARuntimeExceptionSoItCanPropagateUnchecked() {
        CardProviderException ex = new CardProviderException("msg", new RuntimeException("inner"));

        assertTrue(ex instanceof RuntimeException, "CardProviderException should extend RuntimeException");
    }

    @Test
    void canBeThrownAndCaughtPreservingMessageAndCause() {
        IllegalArgumentException root = new IllegalArgumentException("bad request");

        CardProviderException caught = assertThrows(CardProviderException.class, () -> {
            throw new CardProviderException("provider blew up", root);
        });

        assertEquals("provider blew up", caught.getMessage());
        assertSame(root, caught.getCause());
    }
}
