package com.utn.pokemontcg.realtime.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtHandshakeInterceptorTest {

    private static final String SECRET = "this_is_a_test_secret_with_more_than_32_chars";

    @Test
    void acceptsHandshakeWithValidBearerToken() {
        UUID playerId = UUID.randomUUID();
        JwtHandshakeInterceptor interceptor = interceptor();
        ServerHttpRequest request = request("Bearer " + token(playerId), "ws://localhost/ws");
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), attributes);

        assertTrue(accepted);
        assertEquals(playerId, attributes.get(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE));
    }

    @Test
    void acceptsHandshakeWithValidQueryToken() {
        UUID playerId = UUID.randomUUID();
        JwtHandshakeInterceptor interceptor = interceptor();
        ServerHttpRequest request = request(null, "ws://localhost/ws?token=" + token(playerId));
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean accepted = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), new HashMap<>());

        assertTrue(accepted);
    }

    @Test
    void rejectsHandshakeWithInvalidToken() {
        JwtHandshakeInterceptor interceptor = interceptor();
        ServerHttpRequest request = request("Bearer invalid-token", "ws://localhost/ws");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean accepted = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), new HashMap<>());

        assertFalse(accepted);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsHandshakeWithoutToken() {
        JwtHandshakeInterceptor interceptor = interceptor();
        ServerHttpRequest request = request(null, "ws://localhost/ws");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean accepted = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), new HashMap<>());

        assertFalse(accepted);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void acceptsHandshakeWithFallbackPlayerIdHeader() {
        UUID playerId = UUID.randomUUID();
        JwtHandshakeInterceptor interceptor = interceptor();
        HttpHeaders headers = new HttpHeaders();
        headers.add("playerId", playerId.toString());
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws"));
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), attributes);

        assertTrue(accepted);
        assertEquals(playerId, attributes.get(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE));
    }

    @Test
    void rejectsHandshakeWithInvalidFallbackPlayerId() {
        JwtHandshakeInterceptor interceptor = interceptor();
        ServerHttpRequest request = request(null, "ws://localhost/ws?playerId=not-a-uuid");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean accepted = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), new HashMap<>());

        assertFalse(accepted);
    }

    @Test
    void acceptsHandshakeWithFallbackPlayerIdQueryParam() {
        UUID playerId = UUID.randomUUID();
        JwtHandshakeInterceptor interceptor = interceptor();
        ServerHttpRequest request = request(null, "ws://localhost/ws?playerId=" + playerId);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), attributes);

        assertTrue(accepted);
        assertEquals(playerId, attributes.get(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE));
    }

    private JwtHandshakeInterceptor interceptor() {
        return new JwtHandshakeInterceptor(new JwtPlayerTokenService(SECRET));
    }

    private ServerHttpRequest request(String authorization, String uri) {
        HttpHeaders headers = new HttpHeaders();
        if (authorization != null) {
            headers.add("Authorization", authorization);
        }
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create(uri));
        return request;
    }

    private String token(UUID playerId) {
        return Jwts.builder()
            .setSubject("ash@example.com")
            .claim("playerId", playerId.toString())
            .setIssuedAt(Date.from(Instant.now()))
            .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
            .compact();
    }
}
