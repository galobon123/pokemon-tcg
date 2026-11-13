package com.utn.pokemontcg.game.domain.model;

public record AttachedEnergy(
    String cardId,
    String energyType,
    int energyAmount
) {
    public AttachedEnergy(String cardId, String energyType) {
        this(cardId, energyType, 1);
    }

    public AttachedEnergy {
        if (energyAmount <= 0) {
            energyAmount = 1;
        }
    }
}
