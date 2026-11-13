package com.utn.pokemontcg.game.domain.exception;

import org.springframework.http.HttpStatus;

public class InsufficientEnergyException extends GameRuleException {
    public InsufficientEnergyException(String message) {
        super("INSUFFICIENT_ENERGY", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
