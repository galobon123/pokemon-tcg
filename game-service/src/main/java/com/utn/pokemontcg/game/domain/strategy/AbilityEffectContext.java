package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;

import java.util.UUID;

public record AbilityEffectContext(
    GameAggregate game,
    UUID player,
    GameCard pokemonCard,
    String slotReference,
    int abilityIndex,
    String targetId
) {
}
