package com.utn.pokemontcg.realtime.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtPlayerTokenService {

    private static final String PLAYER_ID_CLAIM = "playerId";

    private final Key signingKey;

    public JwtPlayerTokenService(@Value("${app.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<UUID> extractPlayerId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
            Object rawPlayerId = claims.get(PLAYER_ID_CLAIM);
            if (rawPlayerId == null) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(rawPlayerId.toString()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
