package com.utn.pokemontcg.game.presentation.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record PlayTrainerRequest(
    @NotBlank String cardInstanceId,
    String targetId,
    String targetSlot,
    String targetPlayerId,
    List<String> selectedCardIds,
    List<String> selectedEnergyCardIds,
    String selectedCondition,
    Boolean acceptedOptionalEffect,
    List<Boolean> coinFlipResults
) {
    public PlayTrainerRequest(String cardInstanceId, String targetId, String targetSlot) {
        this(cardInstanceId, targetId, targetSlot, null, List.of(), List.of(), null, null, List.of());
    }
}
