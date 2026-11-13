package com.utn.pokemontcg.game.presentation.dto;

import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GameActionRequest(
    @NotNull GameActionType actionType,
    StatusCondition condition,
    String energyCardId,
    String targetSlot,
    List<String> energyCardIds,
    Integer attackIndex,
    List<Boolean> coinFlipResults,
    String targetPlayerId,
    List<String> selectedCardIds,
    List<String> selectedEnergyCardIds,
    String selectedCondition,
    Boolean acceptedOptionalEffect,
    Integer selectedOpponentAttackIndex
) {
    public GameActionRequest(
        GameActionType actionType,
        StatusCondition condition,
        String energyCardId,
        String targetSlot,
        List<String> energyCardIds,
        Integer attackIndex,
        List<Boolean> coinFlipResults
    ) {
        this(actionType, condition, energyCardId, targetSlot, energyCardIds, attackIndex, coinFlipResults, null, List.of(), List.of(), null, null, null);
    }
}
