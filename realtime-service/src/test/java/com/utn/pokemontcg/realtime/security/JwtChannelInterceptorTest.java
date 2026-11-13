package com.utn.pokemontcg.realtime.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.realtime.application.EventAccessRegistry;
import com.utn.pokemontcg.realtime.application.GameAccessValidator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class JwtChannelInterceptorTest {

    private static final String SECRET = "this_is_a_test_secret_with_more_than_32_chars";

    @Test
    void preSendWithNullAccessorReturnsMessage() {
        JwtChannelInterceptor interceptor = interceptor();
        Message<?> message = mock(Message.class);
        try (var ignored = mockStatic(MessageHeaderAccessor.class)) {
            when(MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class)).thenReturn(null);

            Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

            assertSame(message, result);
        }
    }

    @Test
    void preSendWithNullCommandReturnsMessage() {
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = mock(StompHeaderAccessor.class);
        when(accessor.getCommand()).thenReturn(null);
        Message<?> message = mock(Message.class);
        try (var ignored = mockStatic(MessageHeaderAccessor.class)) {
            when(MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class)).thenReturn(accessor);

            Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

            assertSame(message, result);
        }
    }

    @Test
    void connectWithValidTokenStoresPlayerIdInSession() {
        UUID playerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(new HashMap<>());
        accessor.addNativeHeader("Authorization", "Bearer " + token(playerId));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertEquals(playerId, resultAccessor.getSessionAttributes().get(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE));
    }

    @Test
    void sendWithDifferentPayloadPlayerIdIsRejected() {
        UUID sessionPlayerId = UUID.randomUUID();
        UUID spoofedPlayerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        Message<byte[]> message = sendMessage(sessionPlayerId, "{\"playerId\":\"" + spoofedPlayerId + "\"}");

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void sendWithMatchingPayloadPlayerIdIsProcessed() {
        UUID sessionPlayerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        Message<byte[]> message = sendMessage(sessionPlayerId, "{\"playerId\":\"" + sessionPlayerId + "\"}");

        assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void connectWithInvalidTokenThrowsException() {
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(new HashMap<>());
        accessor.addNativeHeader("Authorization", "Bearer invalid-jwt-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void connectWithTokenHeaderOnlySucceeds() {
        UUID playerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(new HashMap<>());
        accessor.addNativeHeader("token", token(playerId));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertEquals(playerId, resultAccessor.getSessionAttributes().get(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE));
    }

    @Test
    void connectWithExistingSessionPlayerIdSucceeds() {
        UUID playerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, playerId);
        accessor.setSessionAttributes(attributes);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void connectWithFallbackPlayerIdHeaderSucceeds() {
        UUID playerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(new HashMap<>());
        accessor.addNativeHeader("playerId", playerId.toString());
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void connectWithNoAuthThrowsException() {
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(new HashMap<>());
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void connectWithValidTokenAndNullSessionAttributesCreatesNewMap() {
        UUID playerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = mock(StompHeaderAccessor.class);
        when(accessor.getCommand()).thenReturn(StompCommand.CONNECT);
        when(accessor.getFirstNativeHeader("Authorization")).thenReturn("Bearer " + token(playerId));

        Map<String, Object> capturedAttrs = new HashMap<>();
        when(accessor.getSessionAttributes())
            .thenReturn(null)           // first call: null → triggers creation
            .thenReturn(capturedAttrs); // subsequent calls: the created map
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = invocation.getArgument(0);
            capturedAttrs.putAll(map);
            return null;
        }).when(accessor).setSessionAttributes(any());

        Message<?> message = mock(Message.class);
        doReturn(new byte[0]).when(message).getPayload();
        try (var mockedAccessor = mockStatic(MessageHeaderAccessor.class)) {
            mockedAccessor.when(() -> MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class))
                .thenReturn(accessor);

            Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

            assertNotNull(result);
            assertEquals(playerId, capturedAttrs.get(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE));
        }
    }

    @Test
    void subscribeWithNonGameDestinationReturnsEarly() {
        UUID sessionPlayerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/not-a-game");
        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, sessionPlayerId);
        accessor.setSessionAttributes(attributes);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void sendWithNoSessionPlayerIdThrows() {
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionAttributes(new HashMap<>());
        String json = "{\"playerId\":\"" + UUID.randomUUID() + "\"}";
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        var message = MessageBuilder.createMessage(payload, accessor.getMessageHeaders());

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void subscribeFallsBackToGameServiceWhenRegistryHasNoGame() {
        UUID gameId = UUID.randomUUID();
        UUID sessionPlayerId = UUID.randomUUID();
        String token = token(sessionPlayerId);
        EventAccessRegistry registry = new EventAccessRegistry();
        GameAccessValidator validator = mock(GameAccessValidator.class);
        when(validator.canAccess(gameId, sessionPlayerId, token)).thenReturn(true);
        JwtChannelInterceptor interceptor = interceptor(registry, validator);
        Message<byte[]> message = subscribeMessage(gameId, sessionPlayerId, token);

        assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
        assertEquals(true, registry.canSubscribe(gameId, sessionPlayerId));
    }

    @Test
    void subscribeRejectsWhenRegistryAndGameServiceDenyAccess() {
        UUID gameId = UUID.randomUUID();
        UUID sessionPlayerId = UUID.randomUUID();
        String token = token(sessionPlayerId);
        GameAccessValidator validator = mock(GameAccessValidator.class);
        when(validator.canAccess(gameId, sessionPlayerId, token)).thenReturn(false);
        JwtChannelInterceptor interceptor = interceptor(new EventAccessRegistry(), validator);
        Message<byte[]> message = subscribeMessage(gameId, sessionPlayerId, token);

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void sendWithMapPayloadAndMatchingPlayerIdSucceeds() {
        UUID sessionPlayerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = mock(StompHeaderAccessor.class);
        when(accessor.getCommand()).thenReturn(StompCommand.SEND);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, sessionPlayerId);
        when(accessor.getSessionAttributes()).thenReturn(attrs);

        Message<?> message = mock(Message.class);
        doReturn(Map.of("playerId", sessionPlayerId.toString())).when(message).getPayload();

        try (var mocked = mockStatic(MessageHeaderAccessor.class)) {
            mocked.when(() -> MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class))
                .thenReturn(accessor);
            assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
        }
    }

    @Test
    void sendWithMapPayloadAndPlayerFallbackSucceeds() {
        UUID sessionPlayerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = mock(StompHeaderAccessor.class);
        when(accessor.getCommand()).thenReturn(StompCommand.SEND);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, sessionPlayerId);
        when(accessor.getSessionAttributes()).thenReturn(attrs);

        Message<?> message = mock(Message.class);
        // Use "player" key instead of "playerId" to test fallback
        doReturn(Map.of("player", sessionPlayerId.toString())).when(message).getPayload();

        try (var mocked = mockStatic(MessageHeaderAccessor.class)) {
            mocked.when(() -> MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class))
                .thenReturn(accessor);
            assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
        }
    }

    @Test
    void sendWithMapPayloadAndNonStringPlayerIdDoesNotThrow() {
        UUID sessionPlayerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = mock(StompHeaderAccessor.class);
        when(accessor.getCommand()).thenReturn(StompCommand.SEND);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, sessionPlayerId);
        when(accessor.getSessionAttributes()).thenReturn(attrs);

        Message<?> message = mock(Message.class);
        // Integer playerId → uuidFrom returns null → payloadPlayerId is null → no throw
        doReturn(Map.of("playerId", 42)).when(message).getPayload();

        try (var mocked = mockStatic(MessageHeaderAccessor.class)) {
            mocked.when(() -> MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class))
                .thenReturn(accessor);
            assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
        }
    }

    @Test
    void sendWithNonMappablePayloadDoesNotThrow() {
        UUID sessionPlayerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = mock(StompHeaderAccessor.class);
        when(accessor.getCommand()).thenReturn(StompCommand.SEND);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, sessionPlayerId);
        when(accessor.getSessionAttributes()).thenReturn(attrs);

        Message<?> message = mock(Message.class);
        // Integer payload is not Map, byte[], or String → payloadMap returns null
        doReturn(42).when(message).getPayload();

        try (var mocked = mockStatic(MessageHeaderAccessor.class)) {
            mocked.when(() -> MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class))
                .thenReturn(accessor);
            assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
        }
    }

    @Test
    void sendWithMalformedBytePayloadThrows() {
        UUID sessionPlayerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = mock(StompHeaderAccessor.class);
        when(accessor.getCommand()).thenReturn(StompCommand.SEND);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, sessionPlayerId);
        when(accessor.getSessionAttributes()).thenReturn(attrs);

        Message<?> message = mock(Message.class);
        // Malformed byte payload → IOException → IllegalArgumentException
        doReturn("{bad-json".getBytes(StandardCharsets.UTF_8)).when(message).getPayload();

        try (var mocked = mockStatic(MessageHeaderAccessor.class)) {
            mocked.when(() -> MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class))
                .thenReturn(accessor);
            assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, mock(MessageChannel.class)));
        }
    }

    @Test
    void sendWithSessionPlayerIdAsStringSucceeds() {
        UUID sessionPlayerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = mock(StompHeaderAccessor.class);
        when(accessor.getCommand()).thenReturn(StompCommand.SEND);
        Map<String, Object> attrs = new HashMap<>();
        // Store playerId as String, not UUID
        attrs.put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, sessionPlayerId.toString());
        when(accessor.getSessionAttributes()).thenReturn(attrs);

        Message<?> message = mock(Message.class);
        doReturn(Map.of("playerId", sessionPlayerId.toString())).when(message).getPayload();

        try (var mocked = mockStatic(MessageHeaderAccessor.class)) {
            mocked.when(() -> MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class))
                .thenReturn(accessor);
            assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
        }
    }

    @Test
    void subscribeWithGameDestinationAndNoPlayerIdThrows() {
        UUID gameId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/games/" + gameId + "/events");
        accessor.setSessionAttributes(new HashMap<>());
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void sendWithStringPayloadAndMatchingPlayerIdSucceeds() {
        UUID sessionPlayerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = mock(StompHeaderAccessor.class);
        when(accessor.getCommand()).thenReturn(StompCommand.SEND);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, sessionPlayerId);
        when(accessor.getSessionAttributes()).thenReturn(attrs);

        Message<?> message = mock(Message.class);
        // String payload → payloadMap String path
        doReturn("{\"playerId\":\"" + sessionPlayerId + "\"}").when(message).getPayload();

        try (var mocked = mockStatic(MessageHeaderAccessor.class)) {
            mocked.when(() -> MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class))
                .thenReturn(accessor);
            assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
        }
    }

    @Test
    void sendWithMapPayloadAndBlankPlayerIdDoesNotThrow() {
        UUID sessionPlayerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = mock(StompHeaderAccessor.class);
        when(accessor.getCommand()).thenReturn(StompCommand.SEND);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, sessionPlayerId);
        when(accessor.getSessionAttributes()).thenReturn(attrs);

        Message<?> message = mock(Message.class);
        // Blank string playerId → uuidFrom blank check → return null → payloadPlayerId null → no throw
        doReturn(Map.of("playerId", "  ")).when(message).getPayload();

        try (var mocked = mockStatic(MessageHeaderAccessor.class)) {
            mocked.when(() -> MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class))
                .thenReturn(accessor);
            assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
        }
    }

    @Test
    void sendWithMapPayloadAndInvalidUuidPlayerIdDoesNotThrow() {
        UUID sessionPlayerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = mock(StompHeaderAccessor.class);
        when(accessor.getCommand()).thenReturn(StompCommand.SEND);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, sessionPlayerId);
        when(accessor.getSessionAttributes()).thenReturn(attrs);

        Message<?> message = mock(Message.class);
        // Invalid UUID string → uuidFrom catches IllegalArgumentException → return null
        doReturn(Map.of("playerId", "not-a-uuid")).when(message).getPayload();

        try (var mocked = mockStatic(MessageHeaderAccessor.class)) {
            mocked.when(() -> MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class))
                .thenReturn(accessor);
            assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
        }
    }

    @Test
    void subscribeWithGameDestinationAndNativePlayerIdLogsWarning() {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        GameAccessValidator validator = mock(GameAccessValidator.class);
        when(validator.canAccess(gameId, playerId, token(playerId))).thenReturn(true);
        JwtChannelInterceptor interceptor = interceptor(new EventAccessRegistry(), validator);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/games/" + gameId + "/events");
        accessor.setSessionAttributes(new HashMap<>());
        // Set native header "playerId" so the orElseGet lambda finds a non-null fallback
        accessor.setNativeHeader("playerId", playerId.toString());
        // Also set TOKEN_ATTRIBUTE so canValidateAgainstGameService works
        accessor.getSessionAttributes().put(JwtHandshakeInterceptor.TOKEN_ATTRIBUTE, token(playerId));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
    }

    @Test
    void sendWithMapPayloadAndUuidValueInPlayerIdSucceeds() {
        UUID sessionPlayerId = UUID.randomUUID();
        JwtChannelInterceptor interceptor = interceptor();
        StompHeaderAccessor accessor = mock(StompHeaderAccessor.class);
        when(accessor.getCommand()).thenReturn(StompCommand.SEND);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, sessionPlayerId);
        when(accessor.getSessionAttributes()).thenReturn(attrs);

        Message<?> message = mock(Message.class);
        // Use same playerId as session → validation passes; UUID value → uuidFrom instanceof UUID path (line 217)
        doReturn(Map.of("playerId", sessionPlayerId)).when(message).getPayload();

        try (var mocked = mockStatic(MessageHeaderAccessor.class)) {
            mocked.when(() -> MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class))
                .thenReturn(accessor);
            assertDoesNotThrow(() -> interceptor.preSend(message, mock(MessageChannel.class)));
        }
    }

    private Message<byte[]> sendMessage(UUID sessionPlayerId, String json) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, sessionPlayerId);
        accessor.setSessionAttributes(attributes);
        return MessageBuilder.createMessage(json.getBytes(StandardCharsets.UTF_8), accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage(UUID gameId, UUID sessionPlayerId, String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/games/" + gameId + "/chat");
        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put(JwtHandshakeInterceptor.PLAYER_ID_ATTRIBUTE, sessionPlayerId);
        attributes.put(JwtHandshakeInterceptor.TOKEN_ATTRIBUTE, token);
        accessor.setSessionAttributes(attributes);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private JwtChannelInterceptor interceptor() {
        return interceptor(new EventAccessRegistry(), mock(GameAccessValidator.class));
    }

    private JwtChannelInterceptor interceptor(EventAccessRegistry registry, GameAccessValidator validator) {
        return new JwtChannelInterceptor(
            new JwtPlayerTokenService(SECRET),
            registry,
            validator,
            new ObjectMapper()
        );
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
