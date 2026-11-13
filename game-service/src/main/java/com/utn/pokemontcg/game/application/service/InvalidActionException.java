package com.utn.pokemontcg.game.application.service;

import com.utn.pokemontcg.game.domain.exception.GameRuleException;
import org.springframework.http.HttpStatus;

public class InvalidActionException extends GameRuleException {
    public InvalidActionException(String message) {
        super("INVALID_ACTION", message, HttpStatus.BAD_REQUEST);
    }
}
