package com.utn.pokemontcg.game.application.service;

import java.util.UUID;

public class PlayerNotInGameException extends RuntimeException {

    public PlayerNotInGameException(UUID gameId, UUID playerId) {
        super("Player " + playerId + " does not belong to game " + gameId);
    }
}
