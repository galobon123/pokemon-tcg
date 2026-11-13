package com.utn.pokemontcg.card.application.dto;

import java.util.List;

public record CardSummaryResponse(
    String id,
    String name,
    String setId,
    String supertype,
    List<String> subtypes,
    List<String> types,
    Integer hp,
    List<CardAttackResponse> attacks,
    List<TypeModifierResponse> weaknesses,
    List<TypeModifierResponse> resistances,
    Integer retreatCost,
    String evolvesFrom,
    List<CardAbilityResponse> abilities,
    List<String> rules,
    String imageUrl
) {
    public CardSummaryResponse(
        String id,
        String name,
        String setId,
        String supertype,
        List<String> subtypes,
        List<String> types,
        Integer hp,
        List<CardAttackResponse> attacks,
        List<TypeModifierResponse> weaknesses,
        List<TypeModifierResponse> resistances,
        String imageUrl
    ) {
        this(id, name, setId, supertype, subtypes, types, hp, attacks, weaknesses, resistances, null, null, List.of(), List.of(), imageUrl);
    }

    public CardSummaryResponse(
        String id,
        String name,
        String setId,
        String supertype,
        List<String> subtypes,
        List<String> types,
        Integer hp,
        List<CardAttackResponse> attacks,
        List<TypeModifierResponse> weaknesses,
        List<TypeModifierResponse> resistances,
        Integer retreatCost,
        String imageUrl
    ) {
        this(id, name, setId, supertype, subtypes, types, hp, attacks, weaknesses, resistances, retreatCost, null, List.of(), List.of(), imageUrl);
    }
}