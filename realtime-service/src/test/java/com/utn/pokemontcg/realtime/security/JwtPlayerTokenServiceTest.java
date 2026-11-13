package com.utn.pokemontcg.realtime.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtPlayerTokenServiceTest {

    private static final String SECRET = "this_is_a_test_secret_with_more_than_32_chars";
    private JwtPlayerTokenService service;

    @BeforeEach
    void setUp() {
        service = new JwtPlayerTokenService(SECRET);
    }

    @Test
    void shouldExtractPlayerIdFromValidToken() {
        UUID playerId = UUID.randomUUID();
        String token = createToken(playerId);

        var result = service.extractPlayerId(token);

        assertTrue(result.isPresent());
        assertEquals(playerId, result.get());
    }

    @Test
    void shouldReturnEmptyForNullToken() {
        var result = service.extractPlayerId(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForBlankToken() {
        var result = service.extractPlayerId("   ");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForInvalidToken() {
        var result = service.extractPlayerId("invalid-token");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForExpiredToken() {
        UUID playerId = UUID.randomUUID();
        String token = Jwts.builder()
            .setSubject(playerId.toString())
            .claim("playerId", playerId.toString())
            .setIssuedAt(Date.from(Instant.now().minusSeconds(7200)))
            .setExpiration(Date.from(Instant.now().minusSeconds(3600)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
            .compact();

        var result = service.extractPlayerId(token);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForTokenWithoutPlayerIdClaim() {
        String token = Jwts.builder()
            .setSubject("test@example.com")
            .setIssuedAt(Date.from(Instant.now()))
            .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
            .compact();

        var result = service.extractPlayerId(token);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForTokenWithNonUuidPlayerId() {
        String token = Jwts.builder()
            .setSubject("test@example.com")
            .claim("playerId", "not-a-uuid")
            .setIssuedAt(Date.from(Instant.now()))
            .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
            .compact();

        var result = service.extractPlayerId(token);

        assertTrue(result.isEmpty());
    }

    private String createToken(UUID playerId) {
        return Jwts.builder()
            .setSubject(playerId.toString())
            .claim("playerId", playerId.toString())
            .setIssuedAt(Date.from(Instant.now()))
            .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
            .compact();
    }
}
