package com.utn.pokemontcg.realtime.dto;

public record ChatMessage(
    String playerId,
    String playerName,
    String text,
    String timestamp,
    String id
) {}
