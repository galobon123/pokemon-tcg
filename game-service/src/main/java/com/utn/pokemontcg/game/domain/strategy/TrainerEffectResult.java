package com.utn.pokemontcg.game.domain.strategy;

import java.util.Map;

public record TrainerEffectResult(String result, Map<String, Object> payload) {
    public static TrainerEffectResult of(String result, Map<String, Object> payload) {
        return new TrainerEffectResult(result, payload == null ? Map.of() : Map.copyOf(payload));
    }

    public static TrainerEffectResult noop(String result) {
        return of(result, Map.of());
    }
}
