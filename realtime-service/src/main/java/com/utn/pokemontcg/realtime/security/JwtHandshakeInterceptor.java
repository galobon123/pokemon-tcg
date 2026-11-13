package com.utn.pokemontcg.realtime.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String PLAYER_ID_ATTRIBUTE = "playerId";
    public static final String TOKEN_ATTRIBUTE = "jwt";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final JwtPlayerTokenService tokenService;

    public JwtHandshakeInterceptor(JwtPlayerTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   @NonNull Map<String, Object> attributes) {
        String token = tokenFrom(request);
        if (token != null) {
            Optional<UUID> playerId = tokenService.extractPlayerId(token);
            if (playerId.isPresent()) {
                attributes.put(PLAYER_ID_ATTRIBUTE, playerId.get());
                attributes.put(TOKEN_ATTRIBUTE, token);
                return true;
            }
            LOGGER.warn("WebSocket handshake rechazado: token JWT invalido");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        UUID fallbackPlayerId = fallbackPlayerIdFrom(request);
        if (fallbackPlayerId != null) {
            LOGGER.warn("WebSocket handshake usando fallback deprecado playerId sin JWT");
            attributes.put(PLAYER_ID_ATTRIBUTE, fallbackPlayerId);
            return true;
        }

        LOGGER.warn("WebSocket handshake rechazado: no se encontro token JWT");
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
                               @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private String tokenFrom(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        String queryToken = UriComponentsBuilder.fromUri(request.getURI())
            .build()
            .getQueryParams()
            .getFirst("token");
        return queryToken == null || queryToken.isBlank() ? null : queryToken;
    }

    private UUID fallbackPlayerIdFrom(ServerHttpRequest request) {
        String rawPlayerId = request.getHeaders().getFirst("playerId");
        if (rawPlayerId == null || rawPlayerId.isBlank()) {
            rawPlayerId = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("playerId");
        }
        if (rawPlayerId == null || rawPlayerId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawPlayerId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
