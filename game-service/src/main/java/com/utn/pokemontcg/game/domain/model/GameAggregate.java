package com.utn.pokemontcg.game.domain.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class GameAggregate {

    private UUID id;

    private UUID playerOne;
    private UUID playerTwo;
    private UUID currentTurnPlayer;

    private GameState gameState;
    private TurnPhase turnPhase;
    private SetupPhase setupPhase;
    private Instant setupCountdownEndsAt;

    private Instant updatedAt;

    private Map<UUID, PlayerSetupState> setupByPlayer = new HashMap<>();

    private UUID firstPlayer;
    private boolean firstTurn = true;
    private int turnNumber = 1;

    private TurnFlags turnFlags = new TurnFlags();

    private Map<UUID, Integer> prizeCardsRemaining = new HashMap<>();
    private Map<UUID, Integer> activeHp = new HashMap<>();
    private Map<UUID, Integer> activeDamageCounters = new HashMap<>();
    private Map<UUID, Integer> activeAttachedEnergy = new HashMap<>();
    private Map<UUID, Integer> deckCardsRemaining = new HashMap<>();
    private Map<UUID, EnumSet<StatusCondition>> statusByPlayer = new HashMap<>();

    private Map<UUID, Boolean> activePokemonEx = new HashMap<>();
    private Map<UUID, UUID> deckIdsByPlayer = new HashMap<>();
    private Map<UUID, String> activePokemon = new HashMap<>();
    private Map<UUID, List<String>> bench = new HashMap<>();
    private Map<UUID, List<String>> hand = new HashMap<>();
    private Map<UUID, List<String>> deck = new HashMap<>();
    private Map<UUID, List<String>> prizeCards = new HashMap<>();
    private Map<UUID, List<String>> discardPile = new HashMap<>();
    private Map<UUID, List<String>> originalDecks = new HashMap<>();
    private Map<String, GameCard> cardCatalog = new HashMap<>();
    private Map<UUID, Map<String, List<AttachedEnergy>>> attachedEnergies = new HashMap<>();
    private Map<UUID, Map<String, Integer>> damageCountersBySlot = new HashMap<>();
    private Map<UUID, Map<String, String>> toolsBySlot = new HashMap<>();
    private Map<UUID, Map<String, Integer>> slotEnteredTurn = new HashMap<>();
    private Map<UUID, Map<String, Integer>> activeEffects = new HashMap<>();
    private Map<UUID, Map<String, Integer>> activeEffectExpiresAfterTurn = new HashMap<>();
    private Map<UUID, Integer> turnsStartedByPlayer = new HashMap<>();
    private Set<UUID> setupConfirmedByPlayer = new HashSet<>();
    private String activeStadium;
    private UUID activeStadiumOwner;

    private int activeAttackBaseDamage = 30;
    private int activeAttackRequiredEnergy = 1;
    private boolean defendingPokemonWeakToAttack;
    private boolean defendingPokemonResistsAttack;

    private UUID winner;
    private boolean suddenDeath;
    private UUID replacementPlayerId;
    private UUID pendingForcedSwitchPlayerId;
    private UUID coinFlipWinner;
    private String coinResult;
    private String coinFlipChoice;

    // ========================
    // Constructors
    // ========================

    public GameAggregate() {
        this.id = UUID.randomUUID();
        this.gameState = GameState.WAITING;
        this.turnPhase = TurnPhase.DRAW;
        this.updatedAt = Instant.now();
    }

    public GameAggregate(UUID playerOne) {
        this();
        this.playerOne = playerOne;
    }

    // ========================
    // Getters
    // ========================

    public UUID id() { return id; }

    public UUID playerOne() { return playerOne; }

    public UUID playerTwo() { return playerTwo; }

    public UUID currentTurnPlayer() { return currentTurnPlayer; }

    public GameState gameState() { return gameState; }

    public TurnPhase turnPhase() { return turnPhase; }

    public SetupPhase setupPhase() { return setupPhase; }

    public Instant setupCountdownEndsAt() { return setupCountdownEndsAt; }

    public Instant updatedAt() { return updatedAt; }

    public Map<UUID, PlayerSetupState> setupByPlayer() { return setupByPlayer; }

    public UUID firstPlayer() { return firstPlayer; }

    public boolean firstTurn() { return firstTurn; }

    public int turnNumber() { return turnNumber; }

    public TurnFlags turnFlags() { return turnFlags; }

    public Map<UUID, Integer> prizeCardsRemaining() { return prizeCardsRemaining; }

    public Map<UUID, Integer> activeHp() { return activeHp; }

    public Map<UUID, Integer> activeDamageCounters() { return activeDamageCounters; }

    public Map<UUID, Integer> activeAttachedEnergy() { return activeAttachedEnergy; }

    public Map<UUID, Integer> deckCardsRemaining() { return deckCardsRemaining; }

    public Map<UUID, EnumSet<StatusCondition>> statusByPlayer() { return statusByPlayer; }

    public Map<UUID, Boolean> activePokemonEx() { return activePokemonEx; }

    public Map<UUID, UUID> deckIdsByPlayer() { return deckIdsByPlayer; }

    public Map<UUID, String> activePokemon() { return activePokemon; }

    public Map<UUID, List<String>> bench() { return bench; }

    public Map<UUID, List<String>> hand() { return hand; }

    public Map<UUID, List<String>> deck() { return deck; }

    public Map<UUID, List<String>> prizeCards() { return prizeCards; }

    public Map<UUID, List<String>> discardPile() { return discardPile; }

    public Map<UUID, List<String>> originalDecks() { return originalDecks; }

    public Map<String, GameCard> cardCatalog() { return cardCatalog; }

    public Map<UUID, Map<String, List<AttachedEnergy>>> attachedEnergies() { return attachedEnergies; }

    public Map<UUID, Map<String, Integer>> damageCountersBySlot() { return damageCountersBySlot; }

    public Map<UUID, Map<String, String>> toolsBySlot() { return toolsBySlot; }

    public Map<UUID, Map<String, Integer>> slotEnteredTurn() { return slotEnteredTurn; }

    public Map<UUID, Map<String, Integer>> activeEffects() { return activeEffects; }

    public Map<UUID, Map<String, Integer>> activeEffectExpiresAfterTurn() { return activeEffectExpiresAfterTurn; }

    public Map<UUID, Integer> turnsStartedByPlayer() { return turnsStartedByPlayer; }

    public Set<UUID> setupConfirmedByPlayer() { return setupConfirmedByPlayer; }

    public String activeStadium() { return activeStadium; }

    public UUID activeStadiumOwner() { return activeStadiumOwner; }

    public int activeAttackBaseDamage() { return activeAttackBaseDamage; }

    public int activeAttackRequiredEnergy() { return activeAttackRequiredEnergy; }

    public boolean defendingPokemonWeakToAttack() { return defendingPokemonWeakToAttack; }

    public boolean defendingPokemonResistsAttack() { return defendingPokemonResistsAttack; }

    public UUID winner() { return winner; }

    public boolean suddenDeath() { return suddenDeath; }

    public UUID replacementPlayerId() { return replacementPlayerId; }

    public UUID pendingForcedSwitchPlayerId() { return pendingForcedSwitchPlayerId; }

    public UUID coinFlipWinner() { return coinFlipWinner; }
    public String coinResult() { return coinResult; }
    public String coinFlipChoice() { return coinFlipChoice; }

    // ========================
    // Setters
    // ========================

    public void setPlayerOne(UUID playerOne) {
        this.playerOne = playerOne;
        touch();
    }

    public void setPlayerTwo(UUID playerTwo) {
        this.playerTwo = playerTwo;
        touch();
    }

    public void setCurrentTurnPlayer(UUID currentTurnPlayer) {
        this.currentTurnPlayer = currentTurnPlayer;
        touch();
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
        touch();
    }

    public void setSetupPhase(SetupPhase setupPhase) {
        this.setupPhase = setupPhase;
        touch();
    }

    public void setSetupCountdownEndsAt(Instant endsAt) {
        this.setupCountdownEndsAt = endsAt;
        touch();
    }

    public void setTurnPhase(TurnPhase turnPhase) {
        this.turnPhase = turnPhase;
        touch();
    }

    public void setFirstPlayer(UUID firstPlayer) {
        this.firstPlayer = firstPlayer;
        touch();
    }

    public void setFirstTurn(boolean firstTurn) {
        this.firstTurn = firstTurn;
        touch();
    }

    public void setTurnNumber(int turnNumber) {
        this.turnNumber = turnNumber;
        touch();
    }

    public void setWinner(UUID winner) {
        this.winner = winner;
        touch();
    }

    public void setSuddenDeath(boolean suddenDeath) {
        this.suddenDeath = suddenDeath;
        touch();
    }

    public void setReplacementPlayerId(UUID replacementPlayerId) {
        this.replacementPlayerId = replacementPlayerId;
        touch();
    }

    public void setPendingForcedSwitchPlayerId(UUID pendingForcedSwitchPlayerId) {
        this.pendingForcedSwitchPlayerId = pendingForcedSwitchPlayerId;
        touch();
    }

    public void setCoinFlipWinner(UUID coinFlipWinner) {
        this.coinFlipWinner = coinFlipWinner;
        touch();
    }

    public void setCoinResult(String coinResult) {
        this.coinResult = coinResult;
        touch();
    }

    public void setCoinFlipChoice(String coinFlipChoice) {
        this.coinFlipChoice = coinFlipChoice;
        touch();
    }

    public void rememberOriginalDeck(UUID player, List<String> orderedDeck) {
        originalDecks.put(player, new ArrayList<>(orderedDeck));
        touch();
    }

    public void setActiveAttackBaseDamage(int activeAttackBaseDamage) {
        this.activeAttackBaseDamage = activeAttackBaseDamage;
        touch();
    }

    public void setActiveAttackRequiredEnergy(int activeAttackRequiredEnergy) {
        this.activeAttackRequiredEnergy = activeAttackRequiredEnergy;
        touch();
    }

    public void setDefendingPokemonWeakToAttack(boolean defendingPokemonWeakToAttack) {
        this.defendingPokemonWeakToAttack = defendingPokemonWeakToAttack;
        touch();
    }

    public void setDefendingPokemonResistsAttack(boolean defendingPokemonResistsAttack) {
        this.defendingPokemonResistsAttack = defendingPokemonResistsAttack;
        touch();
    }

    public void initializeZonesFor(UUID player, List<String> orderedDeck) {
        deck.put(player, new ArrayList<>(orderedDeck));
        hand.put(player, new ArrayList<>());
        prizeCards.put(player, new ArrayList<>());
        discardPile.put(player, new ArrayList<>());
        bench.put(player, new ArrayList<>());
        attachedEnergies.put(player, new HashMap<>());
        damageCountersBySlot.put(player, new HashMap<>());
        toolsBySlot.put(player, new HashMap<>());
        slotEnteredTurn.put(player, new HashMap<>());
        activeEffects.put(player, new HashMap<>());
        activeEffectExpiresAfterTurn.put(player, new HashMap<>());
        turnsStartedByPlayer.put(player, 0);
        activePokemon.remove(player);
        activeHp.remove(player);
        activePokemonEx.remove(player);
        activeDamageCounters.put(player, 0);
        damageCountersFor(player).put("ACTIVE", 0);
        activeAttachedEnergy.put(player, 0);
        deckCardsRemaining.put(player, orderedDeck.size());
        touch();
    }

    public void placeSetupActive(UUID player, String cardId) {
        activePokemon.put(player, cardId);
        markSlotEnteredOnSetup(player, "ACTIVE");
        activeHp.put(player, activeMaxHp(player));
        activeDamageCounters.put(player, 0);
        damageCountersFor(player).put("ACTIVE", 0);
        activeAttachedEnergy.put(player, 0);
        GameCard card = cardCatalog.get(cardId);
        activePokemonEx.put(player, card != null && card.isPokemonEx());
        touch();
    }

    public void placeSetupBench(UUID player, List<String> cardIds) {
        List<String> playerBench = bench.computeIfAbsent(player, ignored -> new ArrayList<>(java.util.Arrays.asList(null, null, null, null, null)));
        ensureBenchSize(playerBench);
        for (String cardId : cardIds == null ? List.<String>of() : cardIds) {
            int slotIndex = -1;
            for (int i = 0; i < 5; i++) {
                if (playerBench.get(i) == null) {
                    slotIndex = i;
                    break;
                }
            }
            if (slotIndex >= 0) {
                playerBench.set(slotIndex, cardId);
                markSlotEnteredOnSetup(player, "BENCH_" + slotIndex);
            }
        }
        touch();
    }

    public void confirmSetup(UUID player) {
        setupConfirmedByPlayer.add(player);
        touch();
    }

    public boolean setupConfirmed(UUID player) {
        return setupConfirmedByPlayer.contains(player);
    }

    public void registerCard(GameCard card) {
        cardCatalog.put(card.id(), card);
        touch();
    }

    public int activeMaxHp(UUID player) {
        GameCard card = cardCatalog.get(activePokemon.get(player));
        return card != null && card.hp() > 0 ? card.hp() : 120;
    }

    public boolean hasPokemonInPlay(UUID player) {
        return activePokemon.get(player) != null
            || benchOccupiedCount(player) > 0;
    }

    public int benchOccupiedCount(UUID player) {
        List<String> playerBench = bench.getOrDefault(player, List.of());
        int count = 0;
        for (String entry : playerBench) {
            if (entry != null) count++;
        }
        return count;
    }

    private void ensureBenchSize(List<String> playerBench) {
        while (playerBench.size() < 5) {
            playerBench.add(null);
        }
    }

    public List<AttachedEnergy> attachedEnergiesFor(UUID player, String targetSlot) {
        return attachedEnergies
            .computeIfAbsent(player, ignored -> new HashMap<>())
            .computeIfAbsent(normalizeSlot(targetSlot), ignored -> new ArrayList<>());
    }

    public void attachEnergy(UUID player, String targetSlot, AttachedEnergy energy) {
        attachedEnergiesFor(player, targetSlot).add(energy);
        refreshActiveAttachedEnergy(player);
        touch();
    }

    public int damageCountersAtSlot(UUID player, String targetSlot) {
        String slot = normalizeSlot(targetSlot);
        if ("ACTIVE".equals(slot)) {
            return activeDamageCounters.getOrDefault(player, 0);
        }
        return damageCountersFor(player).getOrDefault(slot, 0);
    }

    public void addDamageCounters(UUID player, String targetSlot, int counters) {
        if (counters <= 0) {
            return;
        }
        String slot = normalizeSlot(targetSlot);
        int updated = damageCountersAtSlot(player, slot) + counters;
        damageCountersFor(player).put(slot, updated);
        if ("ACTIVE".equals(slot)) {
            activeDamageCounters.put(player, updated);
            activeHp.put(player, Math.max(0, activeMaxHp(player) - (updated * 10)));
        }
        touch();
    }

    public void healDamageCounters(UUID player, String targetSlot, int counters) {
        if (counters <= 0) {
            return;
        }
        String slot = normalizeSlot(targetSlot);
        int updated = Math.max(0, damageCountersAtSlot(player, slot) - counters);
        damageCountersFor(player).put(slot, updated);
        if ("ACTIVE".equals(slot)) {
            activeDamageCounters.put(player, updated);
            activeHp.put(player, Math.max(0, activeMaxHp(player) - (updated * 10)));
        }
        touch();
    }

    public List<AttachedEnergy> removeAttachedEnergies(UUID player, String targetSlot, List<String> energyCardIds) {
        List<AttachedEnergy> slotEnergies = attachedEnergiesFor(player, targetSlot);
        List<AttachedEnergy> removed = new ArrayList<>();
        for (String energyCardId : energyCardIds == null ? List.<String>of() : energyCardIds) {
            AttachedEnergy match = slotEnergies.stream()
                .filter(energy -> energy.cardId().equals(energyCardId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La energia " + energyCardId + " no esta adjunta a " + normalizeSlot(targetSlot)));
            slotEnergies.remove(match);
            removed.add(match);
        }
        refreshActiveAttachedEnergy(player);
        touch();
        return removed;
    }

    public List<AttachedEnergy> discardAllAttachedEnergies(UUID player, String targetSlot) {
        List<AttachedEnergy> slotEnergies = attachedEnergiesFor(player, targetSlot);
        List<AttachedEnergy> removed = new ArrayList<>(slotEnergies);
        slotEnergies.clear();
        refreshActiveAttachedEnergy(player);
        touch();
        return removed;
    }

    public int attachedEnergyCount(UUID player, String targetSlot) {
        return attachedEnergiesFor(player, targetSlot).size();
    }

    public String cardAtSlot(UUID player, String targetSlot) {
        String slot = normalizeSlot(targetSlot);
        if ("ACTIVE".equals(slot)) {
            return activePokemon.get(player);
        }
        int benchIndex = benchIndex(slot);
        if (benchIndex < 0) {
            return null;
        }
        List<String> playerBench = bench.getOrDefault(player, List.of());
        if (benchIndex >= playerBench.size()) {
            return null;
        }
        return playerBench.get(benchIndex);
    }

    public boolean isOccupiedSlot(UUID player, String targetSlot) {
        return cardAtSlot(player, targetSlot) != null;
    }

    public void benchPokemon(UUID player, String cardId, String targetSlot) {
        List<String> playerBench = bench.computeIfAbsent(player, ignored -> new ArrayList<>(java.util.Arrays.asList(null, null, null, null, null)));
        ensureBenchSize(playerBench);
        if (benchOccupiedCount(player) >= 5) {
            throw new IllegalArgumentException("La banca ya esta llena");
        }
        int targetIndex;
        if (targetSlot != null && !targetSlot.isBlank()) {
            String normalized = normalizeSlot(targetSlot);
            targetIndex = benchIndex(normalized);
            if (targetIndex < 0 || targetIndex > 4) {
                targetIndex = -1;
            } else if (playerBench.get(targetIndex) != null) {
                targetIndex = -1;
            }
        } else {
            targetIndex = -1;
        }
        if (targetIndex < 0) {
            for (int i = 0; i < playerBench.size(); i++) {
                if (playerBench.get(i) == null) {
                    targetIndex = i;
                    break;
                }
            }
        }
        if (targetIndex < 0) {
            throw new IllegalArgumentException("La banca ya esta llena");
        }
        playerBench.set(targetIndex, cardId);
        String slot = "BENCH_" + targetIndex;
        slotEnteredTurnFor(player).put(slot, turnNumber);
        touch();
    }

    public void markSlotEnteredThisTurn(UUID player, String targetSlot) {
        slotEnteredTurnFor(player).put(normalizeSlot(targetSlot), turnNumber);
        touch();
    }

    public void markSlotEnteredOnSetup(UUID player, String targetSlot) {
        slotEnteredTurnFor(player).put(normalizeSlot(targetSlot), 0);
        touch();
    }

    public boolean slotEnteredThisTurn(UUID player, String targetSlot) {
        return slotEnteredTurnFor(player).getOrDefault(normalizeSlot(targetSlot), -1) == turnNumber;
    }

    public void addActiveEffect(UUID player, String key, int value) {
        activeEffectsFor(player).put(key, value);
        touch();
    }

    public void addActiveEffectUntilTurn(UUID player, String key, int value, int expiresAfterTurn) {
        activeEffectsFor(player).put(key, value);
        activeEffectExpirationsFor(player).put(key, expiresAfterTurn);
        touch();
    }

    public boolean hasActiveEffect(UUID player, String key) {
        return activeEffectsFor(player).containsKey(key);
    }

    public int activeEffectValue(UUID player, String key) {
        return activeEffectsFor(player).getOrDefault(key, 0);
    }

    public int consumeActiveEffect(UUID player, String key) {
        Integer value = activeEffectsFor(player).remove(key);
        if (value != null) {
            activeEffectExpirationsFor(player).remove(key);
            touch();
        }
        return value == null ? 0 : value;
    }

    public void removeActiveEffect(UUID player, String key) {
        if (activeEffectsFor(player).remove(key) != null) {
            activeEffectExpirationsFor(player).remove(key);
            touch();
        }
    }

    public void clearExpiredActiveEffects(int resolvedTurnNumber) {
        boolean changed = false;
        for (Map.Entry<UUID, Map<String, Integer>> playerEntry : activeEffectExpiresAfterTurn.entrySet()) {
            UUID player = playerEntry.getKey();
            List<String> expired = playerEntry.getValue().entrySet().stream()
                .filter(entry -> entry.getValue() <= resolvedTurnNumber)
                .map(Map.Entry::getKey)
                .toList();
            for (String key : expired) {
                playerEntry.getValue().remove(key);
                activeEffectsFor(player).remove(key);
                changed = true;
            }
        }
        if (changed) {
            touch();
        }
    }

    public String toolAtSlot(UUID player, String targetSlot) {
        return toolsFor(player).get(normalizeSlot(targetSlot));
    }

    public void equipTool(UUID player, String targetSlot, String toolCardId) {
        toolsFor(player).put(normalizeSlot(targetSlot), toolCardId);
        touch();
    }

    public void clearTool(UUID player, String targetSlot) {
        toolsFor(player).remove(normalizeSlot(targetSlot));
        touch();
    }

    public void putStadium(UUID owner, String stadiumCardId) {
        this.activeStadium = stadiumCardId;
        this.activeStadiumOwner = owner;
        touch();
    }

    public String clearStadium() {
        String previous = this.activeStadium;
        this.activeStadium = null;
        this.activeStadiumOwner = null;
        touch();
        return previous;
    }

    public void evolveAtSlot(UUID player, String targetSlot, String evolvedCardId) {
        String slot = normalizeSlot(targetSlot);
        if ("ACTIVE".equals(slot)) {
            activePokemon.put(player, evolvedCardId);
            GameCard card = cardCatalog.get(evolvedCardId);
            activePokemonEx.put(player, card != null && card.isPokemonEx());
            activeHp.put(player, Math.max(0, activeMaxHp(player) - (activeDamageCounters.getOrDefault(player, 0) * 10)));
            damageCountersFor(player).put("ACTIVE", activeDamageCounters.getOrDefault(player, 0));
        } else {
            int benchIndex = benchIndex(slot);
            List<String> playerBench = bench.getOrDefault(player, List.of());
            if (benchIndex < 0 || benchIndex >= playerBench.size() || playerBench.get(benchIndex) == null) {
                throw new IllegalArgumentException("Slot de banca invalido: " + slot);
            }
            playerBench.set(benchIndex, evolvedCardId);
        }
        turnFlags.markSlotEvolved(slot);
        slotEnteredTurnFor(player).put(slot, turnNumber);
        touch();
    }

    public void markTurnStartedFor(UUID player) {
        turnsStartedByPlayer.put(player, turnsStartedByPlayer.getOrDefault(player, 0) + 1);
        touch();
    }

    public void swapActiveWithHand(UUID player, String handCardId) {
        List<String> playerHand = hand.get(player);
        if (playerHand == null || !playerHand.contains(handCardId)) {
            throw new IllegalArgumentException("La carta no esta en la mano del jugador");
        }
        GameCard handCard = cardCatalog.get(handCardId);
        if (handCard == null || !"Aegislash".equalsIgnoreCase(handCard.name())) {
            throw new IllegalArgumentException("Stance Change requiere un Aegislash en la mano");
        }

        String oldActive = activePokemon.get(player);
        activePokemon.put(player, handCardId);
        playerHand.remove(handCardId);
        if (oldActive != null) {
            playerHand.add(oldActive);
        }
        refreshActivePokemonDerivedState(player);
        touch();
    }

    public void swapActiveWithBench(UUID player, int benchIndex) {
        List<String> playerBench = bench.getOrDefault(player, List.of());
        if (benchIndex < 0 || benchIndex >= 5) {
            throw new IllegalArgumentException("Slot de banca invalido: BENCH_" + benchIndex);
        }
        ensureBenchSize(playerBench);
        if (playerBench.get(benchIndex) == null) {
            throw new IllegalArgumentException("No hay Pokemon en el slot de banca: BENCH_" + benchIndex);
        }

        String oldActive = activePokemon.get(player);
        String newActive = playerBench.get(benchIndex);
        playerBench.set(benchIndex, oldActive);
        activePokemon.put(player, newActive);

        Map<String, List<AttachedEnergy>> playerEnergies = attachedEnergies.computeIfAbsent(player, ignored -> new HashMap<>());
        List<AttachedEnergy> activeEnergy = new ArrayList<>(playerEnergies.getOrDefault("ACTIVE", List.of()));
        String benchSlot = "BENCH_" + benchIndex;
        List<AttachedEnergy> benchEnergy = new ArrayList<>(playerEnergies.getOrDefault(benchSlot, List.of()));
        playerEnergies.put("ACTIVE", benchEnergy);
        playerEnergies.put(benchSlot, activeEnergy);

        swapSlotValue(toolsFor(player), "ACTIVE", benchSlot);
        swapSlotValue(slotEnteredTurnFor(player), "ACTIVE", benchSlot);
        swapSlotValue(damageCountersFor(player), "ACTIVE", benchSlot);
        activeDamageCounters.put(player, damageCountersFor(player).getOrDefault("ACTIVE", 0));
        refreshActivePokemonDerivedState(player);

        refreshActiveAttachedEnergy(player);
        touch();
    }

    public String promoteBenchToActive(UUID player, int benchIndex) {
        List<String> playerBench = bench.getOrDefault(player, List.of());
        if (benchIndex < 0 || benchIndex >= 5) {
            return null;
        }
        ensureBenchSize(playerBench);
        if (playerBench.get(benchIndex) == null) {
            return null;
        }
        String promoted = playerBench.get(benchIndex);
        playerBench.set(benchIndex, null);
        activePokemon.put(player, promoted);

        Map<String, List<AttachedEnergy>> playerEnergies = attachedEnergies.computeIfAbsent(player, ignored -> new HashMap<>());
        playerEnergies.put("ACTIVE", new ArrayList<>(playerEnergies.getOrDefault("BENCH_" + benchIndex, List.of())));
        playerEnergies.remove("BENCH_" + benchIndex);
        Map<String, String> playerTools = toolsFor(player);
        playerTools.put("ACTIVE", playerTools.get("BENCH_" + benchIndex));
        playerTools.remove("BENCH_" + benchIndex);
        Map<String, Integer> playerEnteredTurns = slotEnteredTurnFor(player);
        playerEnteredTurns.put("ACTIVE", playerEnteredTurns.getOrDefault("BENCH_" + benchIndex, turnNumber));
        playerEnteredTurns.remove("BENCH_" + benchIndex);
        Map<String, Integer> playerDamageCounters = damageCountersFor(player);
        playerDamageCounters.put("ACTIVE", playerDamageCounters.getOrDefault("BENCH_" + benchIndex, 0));
        playerDamageCounters.remove("BENCH_" + benchIndex);
        activeDamageCounters.put(player, playerDamageCounters.getOrDefault("ACTIVE", 0));
        refreshActivePokemonDerivedState(player);

        refreshActiveAttachedEnergy(player);
        touch();
        return promoted;
    }

    public String removeCardAtSlot(UUID player, String targetSlot) {
        String slot = normalizeSlot(targetSlot);
        if ("ACTIVE".equals(slot)) {
            return activePokemon.remove(player);
        }
        int benchIndex = benchIndex(slot);
        List<String> playerBench = bench.getOrDefault(player, List.of());
        if (benchIndex < 0 || benchIndex >= playerBench.size() || playerBench.get(benchIndex) == null) {
            return null;
        }
        String removed = playerBench.get(benchIndex);
        playerBench.set(benchIndex, null);
        clearBenchSlotState(player, benchIndex);
        touch();
        return removed;
    }

    public int benchIndex(String targetSlot) {
        String slot = normalizeSlot(targetSlot);
        if (!slot.startsWith("BENCH_")) {
            return -1;
        }
        try {
            return Integer.parseInt(slot.substring("BENCH_".length()));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public String normalizeSlot(String targetSlot) {
        return targetSlot == null || targetSlot.isBlank()
            ? "ACTIVE"
            : targetSlot.trim().toUpperCase();
    }

    public void refreshActiveAttachedEnergy(UUID player) {
        activeAttachedEnergy.put(player, attachedEnergiesFor(player, "ACTIVE").size());
    }

    public void refreshActivePokemonDerivedState(UUID player) {
        GameCard active = cardCatalog.get(activePokemon.get(player));
        activePokemonEx.put(player, active != null && active.isPokemonEx());
        int counters = activeDamageCounters.getOrDefault(player, 0);
        activeHp.put(player, Math.max(0, activeMaxHp(player) - (counters * 10)));
    }

    private Map<String, String> toolsFor(UUID player) {
        return toolsBySlot.computeIfAbsent(player, ignored -> new HashMap<>());
    }

    private Map<String, Integer> damageCountersFor(UUID player) {
        return damageCountersBySlot.computeIfAbsent(player, ignored -> new HashMap<>());
    }

    private Map<String, Integer> slotEnteredTurnFor(UUID player) {
        return slotEnteredTurn.computeIfAbsent(player, ignored -> new HashMap<>());
    }

    private Map<String, Integer> activeEffectsFor(UUID player) {
        return activeEffects.computeIfAbsent(player, ignored -> new HashMap<>());
    }

    private Map<String, Integer> activeEffectExpirationsFor(UUID player) {
        return activeEffectExpiresAfterTurn.computeIfAbsent(player, ignored -> new HashMap<>());
    }

    private void clearBenchSlotState(UUID player, int slotIndex) {
        String slot = "BENCH_" + slotIndex;
        Map<String, List<AttachedEnergy>> playerEnergies = attachedEnergies.computeIfAbsent(player, ignored -> new HashMap<>());
        playerEnergies.remove(slot);
        toolsFor(player).remove(slot);
        slotEnteredTurnFor(player).remove(slot);
        damageCountersFor(player).remove(slot);
    }

    private <T> void swapSlotValue(Map<String, T> values, String firstSlot, String secondSlot) {
        T first = values.get(firstSlot);
        T second = values.get(secondSlot);
        if (second == null) {
            values.remove(firstSlot);
        } else {
            values.put(firstSlot, second);
        }
        if (first == null) {
            values.remove(secondSlot);
        } else {
            values.put(secondSlot, first);
        }
    }

    // ========================
    // Internal
    // ========================

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
