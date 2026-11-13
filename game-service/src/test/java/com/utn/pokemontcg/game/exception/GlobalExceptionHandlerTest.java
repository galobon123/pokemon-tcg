package com.utn.pokemontcg.game.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAccessDeniedReturnsForbidden() {
        ResponseEntity<Map<String, String>> res = handler.handleAccessDenied(new AccessDeniedException("denied"));
        assertEquals(403, res.getStatusCode().value());
        assertEquals("Acceso denegado", res.getBody().get("error"));
    }

    @Test
    void handleAuthenticationReturnsUnauthorized() {
        ResponseEntity<Map<String, String>> res = handler.handleAuthentication(new AuthenticationException("no auth") {});
        assertEquals(401, res.getStatusCode().value());
        assertEquals("No autorizado", res.getBody().get("error"));
    }

    @Test
    void handleValidationReturnsBadRequest() {
        BindingResult bindingResult = new org.springframework.validation.BeanPropertyBindingResult(new Object(), "obj");
        bindingResult.addError(new FieldError("obj", "fieldName", "must not be null"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> res = handler.handleValidation(ex);

        assertEquals(400, res.getStatusCode().value());
        List<Map<String, String>> errors = (List<Map<String, String>>) res.getBody().get("errors");
        assertEquals(1, errors.size());
        assertEquals("fieldName", errors.get(0).get("field"));
    }
}
