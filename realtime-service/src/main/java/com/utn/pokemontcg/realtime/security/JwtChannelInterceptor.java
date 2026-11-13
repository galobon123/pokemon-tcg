package com.utn.pokemontcg.realtime.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.realtime.application.EventAccessRegistry;
import com.utn.pokemontcg.realtime.application.GameAccessValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtChannelInterceptor.class);

    private final JwtPlayerTokenService tokenService;
    private final EventAccessRegistry accessRegistry;
    private final GameAccessValidator gameAccessValidator;
    private final ObjectMapper objectMapper;

    public JwtChannelInterceptor(
        JwtPlayerTokenService tokenService,
        EventAccessRegistry accessRegistry,
        GameAccessValidator gameAccessValidator,
        ObjectMapper objectMapper
    ) {
        this.tokenService = tokenService;
        this.accessRegistry = accessRegistry;
        this.gameAccessValidator = gameAccessValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            validateConnect(accessor);
        }
        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            validateGameTopicSubscription(accessor);
        }
        if (accessor.getCommand() == StompCommand.SEND) {
            validateSendPayloadOwner(accessor, message.getPayload());
        }
        return message;
    }

    private void validateConnect(StompHeaderAccessor accessor) {
        String token = tokenFrom(accessor);
        if (token != null) {
            UUID playerId = tokenService.extractPlayerId(token)
                .orElseThrow(() -> new IllegalArgumentException("Token JWT WebSocket invalido"));
            sessionAttributes(accessor).put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, playerId);
            sessionAttributes(accessor).put(JwtHandshakeInterceptor.TOKEN_ATTRIBUTE, token);
            return;
        }

        Optional<UUID> existingPlayerId = sessionPlayerId(accessor);
        if (existingPlayerId.isPresent()) {
            LOGGER.info("STOMP CONNECT autenticado via session attributes para playerId={}", existingPlayerId.get());
            return;
        }

        UUID fallbackPlayerId = uuidFrom(accessor.getFirstNativeHeader("playerId"));
        if (fallbackPlayerId != null) {
            LOGGER.warn("STOMP CONNECT usando fallback deprecado playerId sin JWT");
            sessionAttributes(accessor).put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, fallbackPlayerId);
            return;
        }

        LOGGER.error("STOMP CONNECT rechazado: no se encontro token JWT ni playerId en session");
        throw new IllegalArgumentException("Token JWT WebSocket requerido");
    }

    private void validateGameTopicSubscription(StompHeaderAccessor accessor) {
        UUID gameId = gameIdFrom(accessor.getDestination());
        if (gameId == null) {
            return;
        }

        UUID playerId = sessionPlayerId(accessor).orElseGet(() -> {
            UUID fallbackPlayerId = uuidFrom(accessor.getFirstNativeHeader("playerId"));
            if (fallbackPlayerId != null) {
                LOGGER.warn("STOMP SUBSCRIBE usando fallback deprecado playerId sin JWT");
            }
            return fallbackPlayerId;
        });
        if (playerId == null) {
            LOGGER.error("STOMP SUBSCRIBE rechazado: playerId nulo para gameId={}", gameId);
            throw new IllegalArgumentException("El jugador no puede suscribirse a esta partida");
        }
        if (!accessRegistry.canSubscribe(gameId, playerId) && !canValidateAgainstGameService(gameId, playerId, accessor)) {
            LOGGER.error("STOMP SUBSCRIBE rechazado: playerId={} no registrado en gameId={}", playerId, gameId);
            throw new IllegalArgumentException("El jugador no puede suscribirse a esta partida");
        }
        LOGGER.info("STOMP SUBSCRIBE autorizado: playerId={} suscrito a gameId={}", playerId, gameId);
    }

    private boolean canValidateAgainstGameService(UUID gameId, UUID playerId, StompHeaderAccessor accessor) {
        Object rawToken = sessionAttributes(accessor).get(JwtHandshakeInterceptor.TOKEN_ATTRIBUTE);
        String token = rawToken instanceof String text ? text : tokenFrom(accessor);
        if (!gameAccessValidator.canAccess(gameId, playerId, token)) {
            return false;
        }
        accessRegistry.registerPlayer(gameId, playerId);
        return true;
    }

    private void validateSendPayloadOwner(StompHeaderAccessor accessor, Object payload) {
        Optional<UUID> sessionPlayerId = sessionPlayerId(accessor);
        if (sessionPlayerId.isEmpty()) {
            throw new IllegalArgumentException("No se pudo identificar al jugador WebSocket");
        }

        UUID payloadPlayerId = playerIdFromPayload(payload);
        if (payloadPlayerId != null && !sessionPlayerId.get().equals(payloadPlayerId)) {
            throw new IllegalArgumentException("El playerId del mensaje STOMP no coincide con el JWT");
        }
    }

    private String tokenFrom(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null) {
            authorization = accessor.getFirstNativeHeader("authorization");
        }
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        String token = accessor.getFirstNativeHeader("token");
        return token == null || token.isBlank() ? null : token;
    }

    private Optional<UUID> sessionPlayerId(StompHeaderAccessor accessor) {
        Object rawPlayerId = sessionAttributes(accessor).get(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE);
        if (rawPlayerId instanceof UUID uuid) {
            return Optional.of(uuid);
        }
        if (rawPlayerId instanceof String text) {
            return Optional.ofNullable(uuidFrom(text));
        }
        return Optional.empty();
    }

    private Map<String, Object> sessionAttributes(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            accessor.setSessionAttributes(new java.util.HashMap<>());
            attributes = accessor.getSessionAttributes();
        }
        return attributes;
    }

    private UUID gameIdFrom(String destination) {
        if (destination == null || !destination.startsWith("/topic/games/")) {
            return null;
        }
        String rawGameId = destination.replace("/topic/games/", "");
        int slash = rawGameId.indexOf('/');
        if (slash != -1) {
            rawGameId = rawGameId.substring(0, slash);
        }
        return uuidFrom(rawGameId);
    }

    private UUID playerIdFromPayload(Object payload) {
        Map<String, Object> body = payloadMap(payload);
        if (body == null) {
            return null;
        }
        Object rawPlayerId = body.get("playerId");
        if (rawPlayerId == null) {
            rawPlayerId = body.get("player");
        }
        return uuidFrom(rawPlayerId);
    }

    private Map<String, Object> payloadMap(Object payload) {
        try {
            if (payload instanceof Map<?, ?> map) {
                return map.entrySet().stream()
                    .filter(entry -> entry.getKey() instanceof String)
                    .collect(java.util.stream.Collectors.toMap(entry -> (String) entry.getKey(), Map.Entry::getValue));
            }
            if (payload instanceof byte[] bytes && bytes.length > 0) {
                return objectMapper.readValue(bytes, MAP_TYPE);
            }
            if (payload instanceof String text && !text.isBlank()) {
                return objectMapper.readValue(text.getBytes(StandardCharsets.UTF_8), MAP_TYPE);
            }
            return null;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Payload STOMP invalido", ex);
        }
    }

    private UUID uuidFrom(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
