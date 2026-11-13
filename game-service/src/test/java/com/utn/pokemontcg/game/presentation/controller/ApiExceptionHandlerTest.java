package com.utn.pokemontcg.game.presentation.controller;

import com.utn.pokemontcg.game.application.service.GameNotFoundException;
import com.utn.pokemontcg.game.application.service.PlayerNotInGameException;
import com.utn.pokemontcg.game.domain.exception.GameRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage tests for {@link ApiExceptionHandler}.
 * <p>
 * Each @ExceptionHandler method is invoked directly (no Spring context) and the
 * ResponseEntity is verified for status, error phrase, code and message.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleGameRuleUsesExceptionStatusAndCode() {
        GameRuleException ex = new GameRuleException(
            "CUSTOM_CODE", "custom game rule message", HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<Map<String, Object>> response = handler.handleGameRule(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("Unprocessable Entity", response.getBody().get("error"));
        assertEquals(422, response.getBody().get("status"));
        assertEquals("CUSTOM_CODE", response.getBody().get("code"));
        assertEquals("custom game rule message", response.getBody().get("message"));
        assertNotNull(response.getBody().get("timestamp"));
        assertTrue(response.getBody().get("timestamp") instanceof Instant);
    }

    @Test
    void handleGameRuleWithBadRequestStatus() {
        GameRuleException ex = new GameRuleException(
            "INVALID_ACTION", "invalid", HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> response = handler.handleGameRule(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_ACTION", response.getBody().get("code"));
    }

    @Test
    void handleRuleViolationWithIllegalArgumentReturnsBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("illegal arg msg");

        ResponseEntity<Map<String, Object>> response = handler.handleRuleViolation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Bad Request", response.getBody().get("error"));
        assertEquals(400, response.getBody().get("status"));
        assertEquals("RULE_VIOLATION", response.getBody().get("code"));
        assertEquals("illegal arg msg", response.getBody().get("message"));
    }

    @Test
    void handleRuleViolationWithIllegalStateReturnsBadRequest() {
        IllegalStateException ex = new IllegalStateException("illegal state msg");

        ResponseEntity<Map<String, Object>> response = handler.handleRuleViolation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("illegal state msg", response.getBody().get("message"));
        assertEquals("RULE_VIOLATION", response.getBody().get("code"));
    }

    @Test
    void handleRuleViolationWithNullMessageDefaultsToInvalidRequest() {
        // ex.getMessage() is null -> badRequest() should default to "Request invalido"
        IllegalArgumentException ex = new IllegalArgumentException((String) null);

        ResponseEntity<Map<String, Object>> response = handler.handleRuleViolation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Request invalido", response.getBody().get("message"));
    }

    @Test
    void handleGameNotFoundReturnsNotFound() {
        UUID gameId = UUID.randomUUID();
        GameNotFoundException ex = new GameNotFoundException(gameId);

        ResponseEntity<Map<String, Object>> response = handler.handleGameNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Not Found", response.getBody().get("error"));
        assertEquals(404, response.getBody().get("status"));
        assertEquals("RULE_VIOLATION", response.getBody().get("code"));
        assertTrue(response.getBody().get("message").toString().contains(gameId.toString()));
    }

    @Test
    void handleForbiddenReturnsForbiddenForPlayerNotInGame() {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        PlayerNotInGameException ex = new PlayerNotInGameException(gameId, playerId);

        ResponseEntity<Map<String, Object>> response = handler.handleForbidden(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Forbidden", response.getBody().get("error"));
        assertEquals(403, response.getBody().get("status"));
        assertEquals("RULE_VIOLATION", response.getBody().get("code"));
        assertTrue(response.getBody().get("message").toString().contains(playerId.toString()));
        assertTrue(response.getBody().get("message").toString().contains(gameId.toString()));
    }

    @Test
    void handleAccessDeniedReturnsForbidden() {
        AccessDeniedException ex = new AccessDeniedException("denied msg");

        ResponseEntity<Map<String, Object>> response = handler.handleAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Forbidden", response.getBody().get("error"));
        assertEquals(403, response.getBody().get("status"));
        assertEquals("RULE_VIOLATION", response.getBody().get("code"));
        assertEquals("denied msg", response.getBody().get("message"));
    }

    @Test
    void handleUnreadableReturnsBadRequestWithFixedMessage() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("unparseable body");

        ResponseEntity<Map<String, Object>> response = handler.handleUnreadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Bad Request", response.getBody().get("error"));
        assertEquals(400, response.getBody().get("status"));
        assertEquals("RULE_VIOLATION", response.getBody().get("code"));
        // Fixed message regardless of ex.getMessage()
        assertEquals("Request invalido o valor no soportado", response.getBody().get("message"));
    }
}
