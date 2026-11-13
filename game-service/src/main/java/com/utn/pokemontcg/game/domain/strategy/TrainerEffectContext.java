package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public record TrainerEffectContext(
    GameAggregate game,
    UUID player,
    GameCard trainerCard,
    String trainerCardId,
    String targetSlot,
    String targetId,
    UUID targetPlayerId,
    List<String> selectedCardIds,
    List<String> selectedEnergyCardIds,
    String selectedCondition,
    Boolean acceptedOptionalEffect,
    BooleanSupplier coinFlip
) {
    public TrainerEffectContext(GameAggregate game, UUID player, GameCard trainerCard, String trainerCardId, String targetSlot, String targetId) {
        this(game, player, trainerCard, trainerCardId, targetSlot, targetId, null, List.of(), List.of(), null, null, () -> Math.random() < 0.5);
    }

    public List<String> selectedCardIds() {
        return selectedCardIds == null ? List.of() : selectedCardIds;
    }

    public List<String> selectedEnergyCardIds() {
        return selectedEnergyCardIds == null ? List.of() : selectedEnergyCardIds;
    }

    public BooleanSupplier coinFlip() {
        return coinFlip == null ? () -> Math.random() < 0.5 : coinFlip;
    }
}
