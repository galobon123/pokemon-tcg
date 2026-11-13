package com.utn.pokemontcg.realtime.presentation;

import com.utn.pokemontcg.realtime.application.InvalidGameEventException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeExceptionHandlerTest {

    private final RealtimeExceptionHandler handler = new RealtimeExceptionHandler();

    @Test
    void shouldReturnBadRequestForInvalidGameEvent() {
        ResponseEntity<Map<String, Object>> response = handler.invalidEvent(
            new InvalidGameEventException("Invalid event type: UNKNOWN")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("Invalid event type: UNKNOWN", response.getBody().get("message"));
    }

    @Test
    void shouldReturnBadRequestForMethodArgumentNotValid() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("target", "type", "must not be null");
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.invalidRequest(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("type: must not be null", response.getBody().get("message"));
    }

    @Test
    void shouldFallbackToDefaultMessageWhenNoFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of());

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.invalidRequest(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Evento invalido", response.getBody().get("message"));
    }
}
