package com.utn.pokemontcg.card.presentation;

import com.utn.pokemontcg.card.application.CardProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CardApiExceptionHandlerTest {

    private final CardApiExceptionHandler handler = new CardApiExceptionHandler();

    @Test
    void providerUnavailableReturnsBadGatewayWithExceptionMessageAndEnvelope() {
        Throwable cause = new IllegalStateException("upstream timeout");
        CardProviderException ex = new CardProviderException("Pokemon TCG provider down", cause);

        ResponseEntity<Map<String, Object>> response = handler.providerUnavailable(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());

        Map<String, Object> body = response.getBody();
        assertNotNull(body, "Response body must not be null");
        assertEquals(HttpStatus.BAD_GATEWAY.value(), body.get("status"));
        assertEquals("Bad Gateway", body.get("error"));
        assertEquals("Pokemon TCG provider down", body.get("message"));
        assertInstanceOf(Instant.class, body.get("timestamp"), "timestamp must be an Instant");
    }

    @Test
    void providerUnavailableEchoesMessageFromMockedException() {
        CardProviderException ex = mock(CardProviderException.class);
        when(ex.getMessage()).thenReturn("upstream error message");

        ResponseEntity<Map<String, Object>> response = handler.providerUnavailable(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("upstream error message", response.getBody().get("message"));
    }
}
