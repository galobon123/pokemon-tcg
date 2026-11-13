package com.utn.pokemontcg.game.presentation.controller;

import com.utn.pokemontcg.game.application.service.GameNotFoundException;
import com.utn.pokemontcg.game.application.service.PlayerNotInGameException;
import com.utn.pokemontcg.game.domain.exception.GameRuleException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(GameRuleException.class)
    public ResponseEntity<Map<String, Object>> handleGameRule(GameRuleException ex) {
        return error(ex.status(), ex.status().getReasonPhrase(), ex.getMessage(), ex.code());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleRuleViolation(RuntimeException ex) {
        return badRequest(ex.getMessage());
    }

    @ExceptionHandler(GameNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleGameNotFound(GameNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(PlayerNotInGameException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(PlayerNotInGameException ex) {
        return error(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return badRequest("Request invalido o valor no soportado");
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return error(HttpStatus.BAD_REQUEST, "Bad Request", message == null ? "Request invalido" : message);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String error, String message) {
        return error(status, error, message, "RULE_VIOLATION");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String error, String message, String code) {
        return ResponseEntity.status(status).body(Map.of(
            "timestamp", Instant.now(),
            "status", status.value(),
            "error", error,
            "code", code,
            "message", message
        ));
    }
}
