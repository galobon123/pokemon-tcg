package com.utn.pokemontcg.game.presentation.dto.deck;

import com.utn.pokemontcg.game.infrastructure.client.CardServiceClient.AttackData;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DeckCardInput(
    @NotBlank String cardId,
    @NotBlank String name,
    String setId,
    @Min(1) int quantity,
    @NotBlank String type,
    String subtype,
    @NotNull Boolean basicEnergy,
    @NotNull Boolean basicPokemon,
    @NotNull Boolean aceSpec,
    Integer hp,
    Integer attackDamage,
    Integer attackRequiredEnergy,
    String attackName,
    String attackText,
    String energyType,
    List<String> attackCost,
    Integer retreatCost,
    String evolvesFrom,
    List<String> abilities,
    String weaknessType,
    Integer weaknessAmount,
    String resistanceType,
    Integer resistanceAmount,
    List<AttackData> attacks
) {
    public DeckCardInput(
        String cardId,
        String name,
        String setId,
        int quantity,
        String type,
        String subtype,
        Boolean basicEnergy,
        Boolean basicPokemon,
        Boolean aceSpec,
        Integer hp,
        Integer attackDamage,
        Integer attackRequiredEnergy
    ) {
        this(
            cardId,
            name,
            setId,
            quantity,
            type,
            subtype,
            basicEnergy,
            basicPokemon,
            aceSpec,
            hp,
            attackDamage,
            attackRequiredEnergy,
            null,
            null,
            null,
            List.of(),
            0,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            List.of()
        );
    }

    public DeckCardInput(
        String cardId,
        String name,
        String setId,
        int quantity,
        String type,
        String subtype,
        Boolean basicEnergy,
        Boolean basicPokemon,
        Boolean aceSpec,
        Integer hp,
        Integer attackDamage,
        Integer attackRequiredEnergy,
        String attackName,
        String attackText,
        String energyType,
        List<String> attackCost,
        Integer retreatCost,
        String evolvesFrom,
        List<String> abilities
    ) {
        this(
            cardId,
            name,
            setId,
            quantity,
            type,
            subtype,
            basicEnergy,
            basicPokemon,
            aceSpec,
            hp,
            attackDamage,
            attackRequiredEnergy,
            attackName,
            attackText,
            energyType,
            attackCost,
            retreatCost,
            evolvesFrom,
            abilities,
            null,
            null,
            null,
            null,
            List.of()
        );
    }

    public DeckCardInput {
        attackCost = attackCost == null ? List.of() : List.copyOf(attackCost);
        abilities = abilities == null ? List.of() : List.copyOf(abilities);
        attacks = attacks == null ? List.of() : List.copyOf(attacks);
    }
}
