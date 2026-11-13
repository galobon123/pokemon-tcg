package com.utn.pokemontcg.game.domain.exception;

import org.springframework.http.HttpStatus;

public class EvolutionNotAllowedException extends GameRuleException {
    public EvolutionNotAllowedException(String message) {
        super("EVOLUTION_NOT_ALLOWED", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
