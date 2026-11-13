package com.utn.pokemontcg.game.domain.exception;

import org.springframework.http.HttpStatus;

public class InvalidActionPhaseException extends GameRuleException {
    public InvalidActionPhaseException(String message) {
        super("INVALID_ACTION_PHASE", message, HttpStatus.BAD_REQUEST);
    }
}
