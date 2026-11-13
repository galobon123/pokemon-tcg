package com.utn.pokemontcg.game.presentation.dto;

import java.util.List;

public record AttackInfo(
    String name,
    String text,
    String damage,
    List<String> cost,
    int convertedEnergyCost
) {
    public AttackInfo {
        cost = cost == null ? List.of() : List.copyOf(cost);
    }
}
