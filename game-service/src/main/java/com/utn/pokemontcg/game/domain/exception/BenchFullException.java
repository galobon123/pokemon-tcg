package com.utn.pokemontcg.game.domain.exception;

import org.springframework.http.HttpStatus;

public class BenchFullException extends GameRuleException {
    public BenchFullException(String message) {
        super("BENCH_FULL", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
