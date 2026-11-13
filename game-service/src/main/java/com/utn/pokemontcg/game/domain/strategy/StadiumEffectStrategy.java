package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameAggregate;

import java.util.UUID;

public interface StadiumEffectStrategy {
    String cardName();
    void onPlayed(GameAggregate game, UUID playerId);
    void onRemoved(GameAggregate game);
}
