package com.utn.pokemontcg.game.domain.exception;

import org.springframework.http.HttpStatus;

public class GameRuleException extends IllegalStateException {

    private final String code;
    private final HttpStatus status;

    public GameRuleException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
