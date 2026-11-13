package com.utn.pokemontcg.realtime.presentation;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.realtime.dto.GameEventEnvelope;
import com.utn.pokemontcg.realtime.dto.GameEventMessage;
import com.utn.pokemontcg.realtime.dto.GameEventType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameServiceRealtimeFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldSendGameServiceObserverEventToRealtimeWebSocketSubscribers() throws Exception {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        ArrayBlockingQueue<GameEventEnvelope> received = new ArrayBlockingQueue<>(1);

        ResponseEntity<GameEventEnvelope> setupResponse = restTemplate.postForEntity(
            "/internal/events",
            new GameEventMessage(
                gameId,
                "GAME_CREATED",
                Map.of("playerOne", playerId.toString()),
                Instant.parse("2026-05-06T17:59:00Z")
            ),
            GameEventEnvelope.class
        );

        String jwtToken = createToken(playerId);

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter messageConverter = new MappingJackson2MessageConverter();
        messageConverter.setObjectMapper(new ObjectMapper().findAndRegisterModules());
        stompClient.setMessageConverter(messageConverter);

        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add("Authorization", "Bearer " + jwtToken);

        StompSession session = stompClient
            .connectAsync("ws://localhost:" + port + "/ws", handshakeHeaders, new StompSessionHandlerAdapter() {})
            .get(3, TimeUnit.SECONDS);

        StompHeaders subscribeHeaders = new StompHeaders();
        subscribeHeaders.setDestination("/topic/games/" + gameId + "/events");
        subscribeHeaders.add("playerId", playerId.toString());
        session.subscribe(subscribeHeaders, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GameEventEnvelope.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.offer((GameEventEnvelope) payload);
            }
        });
        Thread.sleep(300);

        ResponseEntity<GameEventEnvelope> response = restTemplate.postForEntity(
            "/internal/events",
            new GameEventMessage(
                gameId,
                "ENERGY_ATTACHED",
                Map.of("player", playerId.toString(), "attachedEnergy", 1),
                Instant.parse("2026-05-06T18:00:00Z")
            ),
            GameEventEnvelope.class
        );

        GameEventEnvelope event = received.poll(3, TimeUnit.SECONDS);

        assertEquals(200, setupResponse.getStatusCode().value());
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(event);
        assertEquals(gameId, event.gameId());
        assertEquals(GameEventType.ENERGY_ATTACHED, event.type());
        assertEquals(1, event.payload().get("attachedEnergy"));

        session.disconnect();
        stompClient.stop();
    }

    private String createToken(UUID playerId) {
        String secret = "your_very_long_secret_here_min_32_chars";
        return Jwts.builder()
            .setSubject(playerId.toString())
            .claim("playerId", playerId.toString())
            .setIssuedAt(Date.from(Instant.now()))
            .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
            .compact();
    }
}

