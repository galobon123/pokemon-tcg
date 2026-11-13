package com.utn.pokemontcg.game.presentation.dto;

import java.util.List;

public record SetupPlaceBenchRequest(
    List<String> cardInstanceIds
) {}
