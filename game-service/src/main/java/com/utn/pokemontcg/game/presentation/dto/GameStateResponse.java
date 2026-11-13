package com.utn.pokemontcg.game.presentation.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GameStateResponse(
    UUID gameId,
    String state,
    String phase,
    String setupPhase,
    UUID currentTurnPlayer,
    UUID firstPlayer,
    UUID winner,
    UUID coinFlipWinner,
    String coinResult,
    String coinFlipChoice,
    UUID playerOne,
    UUID playerTwo,
    Map<UUID, ?> setupByPlayer,
    Map<UUID, ?> activePokemonByPlayer,
    Map<UUID, ?> benchByPlayer,
    Map<UUID, ?> handSizesByPlayer,
    Map<UUID, ?> deckCardsRemainingByPlayer,
    Map<UUID, ?> prizeCardsRemainingByPlayer,
    Map<UUID, ?> discardPilesByPlayer,
    Map<UUID, ?> activeDamageCountersByPlayer,
    Map<UUID, ?> activeAttachedEnergyByPlayer,
    Map<UUID, ?> attachedEnergiesByPlayer,
    Object turnFlags,
    Map<UUID, ?> statusByPlayer,
    boolean suddenDeath,
    boolean waitingReplacement,
    UUID replacementPlayerId,
    UUID pendingForcedSwitchPlayerId,
    Map<UUID, String> playerNames,
    Map<UUID, String> playerAvatars,
    Map<UUID, VisibleCardInfo> activePokemonInfo,
    Map<UUID, List<VisibleCardInfo>> benchInfo,
    Map<UUID, List<VisibleCardInfo>> revealedHandCardsByPlayer,
    Instant countdownEndsAt,
    boolean firstTurn,
    Map<UUID, Integer> turnsStartedByPlayer,
    Map<UUID, Map<String, String>> toolsBySlot,
    Map<UUID, Map<String, Integer>> damageCountersBySlot,
    String activeStadium,
    UUID activeStadiumOwner,
    String activeStadiumName
) {
}
