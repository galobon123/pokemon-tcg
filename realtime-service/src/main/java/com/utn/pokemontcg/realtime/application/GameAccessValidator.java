package com.utn.pokemontcg.realtime.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Service
public class GameAccessValidator {

    private final RestClient restClient;

    public GameAccessValidator(@Value("${game.service.url:http://localhost:8081}") String gameServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(gameServiceUrl).build();
    }

    public boolean canAccess(UUID gameId, UUID playerId, String token) {
        if (gameId == null || playerId == null || token == null || token.isBlank()) {
            return false;
        }
        try {
            Map<?, ?> state = restClient.get()
                .uri("/api/games/{gameId}", gameId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(Map.class);
            if (state == null) {
                return false;
            }
            return playerId.equals(uuidFrom(state.get("playerOne")))
                || playerId.equals(uuidFrom(state.get("playerTwo")));
        } catch (RuntimeException ex) {
            return false;
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
