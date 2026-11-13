package com.utn.pokemontcg.game.domain.exception;

import org.springframework.http.HttpStatus;

public class SupporterAlreadyPlayedException extends GameRuleException {
    public SupporterAlreadyPlayedException(String message) {
        super("SUPPORTER_ALREADY_PLAYED", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
