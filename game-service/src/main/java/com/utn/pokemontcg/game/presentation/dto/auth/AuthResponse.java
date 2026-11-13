package com.utn.pokemontcg.game.presentation.dto.auth;

import java.util.UUID;

public record AuthResponse(
    String token,
    Long id,
    UUID playerId,
    String username,
    String email,
    String avatar
) {
}
