package com.utn.pokemontcg.game.presentation.dto;

import java.util.List;
import java.util.Set;

public record VisibleCardInfo(
    String cardId,
    String setId,
    String name,
    String supertype,
    Set<String> subtypes,
    int hp,
    String attackName,
    String attackText,
    int attackDamage,
    int attackRequiredEnergy,
    String energyType,
    List<String> attackCost,
    int retreatCost,
    String evolvesFrom,
    List<String> abilities,
    String weaknessType,
    Integer weaknessAmount,
    String resistanceType,
    Integer resistanceAmount,
    String instanceId,
    List<AttackInfo> attacks
) {
}
