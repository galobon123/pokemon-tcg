package com.utn.pokemontcg.game.domain.chain;

import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public class AttackContext {
    private final GameAggregate game;
    private GameCard attackerCard;
    private GameCard defenderCard;
    private int requiredEnergy;
    private int attachedEnergy;
    private String attackName;
    private String attackText;
    private List<String> requiredEnergyCost = List.of();
    private List<AttachedEnergy> attachedEnergies = List.of();
    private boolean confused;
    private boolean cancelled;
    private boolean targetSelected;
    private String targetSlot;
    private UUID targetPlayerId;
    private List<String> selectedCardIds = List.of();
    private List<String> selectedEnergyCardIds = List.of();
    private String selectedCondition;
    private Boolean acceptedOptionalEffect;
    private int selectedOpponentAttackIndex = -1;
    private int attackIndex = -1;
    private List<Boolean> coinFlipResults = List.of();
    private BooleanSupplier coinFlip = () -> Math.random() < 0.5;
    private int baseDamage;
    private int preAttackBonus;
    private int attackerModifier;
    private int defenderModifier;
    private int weaknessMultiplier = 1;
    private int resistanceReduction;
    private int damage;
    private int selfDamage;
    private boolean postEffectsApplied;
    private final List<String> auditTrail = new ArrayList<>();
    private final List<String> revealedCardIds = new ArrayList<>();

    public AttackContext(GameAggregate game) {
        this.game = game;
    }

    public GameAggregate game() { return game; }
    public GameCard attackerCard() { return attackerCard; }
    public void setAttackerCard(GameCard attackerCard) { this.attackerCard = attackerCard; }
    public GameCard defenderCard() { return defenderCard; }
    public void setDefenderCard(GameCard defenderCard) { this.defenderCard = defenderCard; }
    public int requiredEnergy() { return requiredEnergy; }
    public void setRequiredEnergy(int requiredEnergy) { this.requiredEnergy = requiredEnergy; }
    public int attachedEnergy() { return attachedEnergy; }
    public void setAttachedEnergy(int attachedEnergy) { this.attachedEnergy = attachedEnergy; }
    public String attackName() { return attackName; }
    public void setAttackName(String attackName) { this.attackName = attackName; }
    public String attackText() { return attackText; }
    public void setAttackText(String attackText) { this.attackText = attackText; }
    public List<String> requiredEnergyCost() { return requiredEnergyCost; }
    public void setRequiredEnergyCost(List<String> requiredEnergyCost) {
        this.requiredEnergyCost = requiredEnergyCost == null ? List.of() : List.copyOf(requiredEnergyCost);
    }
    public List<AttachedEnergy> attachedEnergies() { return attachedEnergies; }
    public void setAttachedEnergies(List<AttachedEnergy> attachedEnergies) {
        this.attachedEnergies = attachedEnergies == null ? List.of() : List.copyOf(attachedEnergies);
    }
    public boolean confused() { return confused; }
    public void setConfused(boolean confused) { this.confused = confused; }
    public boolean cancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public boolean targetSelected() { return targetSelected; }
    public void setTargetSelected(boolean targetSelected) { this.targetSelected = targetSelected; }
    public String targetSlot() { return targetSlot; }
    public void setTargetSlot(String targetSlot) { this.targetSlot = targetSlot; }
    public UUID targetPlayerId() { return targetPlayerId; }
    public void setTargetPlayerId(UUID targetPlayerId) { this.targetPlayerId = targetPlayerId; }
    public List<String> selectedCardIds() { return selectedCardIds; }
    public void setSelectedCardIds(List<String> selectedCardIds) {
        this.selectedCardIds = selectedCardIds == null ? List.of() : List.copyOf(selectedCardIds);
    }
    public List<String> selectedEnergyCardIds() { return selectedEnergyCardIds; }
    public void setSelectedEnergyCardIds(List<String> selectedEnergyCardIds) {
        this.selectedEnergyCardIds = selectedEnergyCardIds == null ? List.of() : List.copyOf(selectedEnergyCardIds);
    }
    public String selectedCondition() { return selectedCondition; }
    public void setSelectedCondition(String selectedCondition) { this.selectedCondition = selectedCondition; }
    public Boolean acceptedOptionalEffect() { return acceptedOptionalEffect; }
    public void setAcceptedOptionalEffect(Boolean acceptedOptionalEffect) { this.acceptedOptionalEffect = acceptedOptionalEffect; }
    public int selectedOpponentAttackIndex() { return selectedOpponentAttackIndex; }
    public void setSelectedOpponentAttackIndex(int selectedOpponentAttackIndex) { this.selectedOpponentAttackIndex = selectedOpponentAttackIndex; }
    public int attackIndex() { return attackIndex; }
    public void setAttackIndex(int attackIndex) { this.attackIndex = attackIndex; }
    public List<Boolean> coinFlipResults() { return coinFlipResults; }
    public void setCoinFlipResults(List<Boolean> coinFlipResults) {
        this.coinFlipResults = coinFlipResults == null ? List.of() : List.copyOf(coinFlipResults);
    }
    public BooleanSupplier coinFlip() { return coinFlip; }
    public void setCoinFlip(BooleanSupplier coinFlip) {
        this.coinFlip = coinFlip == null ? () -> Math.random() < 0.5 : coinFlip;
    }
    public int baseDamage() { return baseDamage; }
    public void setBaseDamage(int baseDamage) { this.baseDamage = baseDamage; }
    public int preAttackBonus() { return preAttackBonus; }
    public void setPreAttackBonus(int preAttackBonus) { this.preAttackBonus = preAttackBonus; }
    public int attackerModifier() { return attackerModifier; }
    public void setAttackerModifier(int attackerModifier) { this.attackerModifier = attackerModifier; }
    public int defenderModifier() { return defenderModifier; }
    public void setDefenderModifier(int defenderModifier) { this.defenderModifier = defenderModifier; }
    public int weaknessMultiplier() { return weaknessMultiplier; }
    public void setWeaknessMultiplier(int weaknessMultiplier) { this.weaknessMultiplier = weaknessMultiplier; }
    public int resistanceReduction() { return resistanceReduction; }
    public void setResistanceReduction(int resistanceReduction) { this.resistanceReduction = resistanceReduction; }
    public int damage() { return damage; }
    public void setDamage(int damage) { this.damage = damage; }
    public int selfDamage() { return selfDamage; }
    public void setSelfDamage(int selfDamage) { this.selfDamage = selfDamage; }
    public boolean postEffectsApplied() { return postEffectsApplied; }
    public void setPostEffectsApplied(boolean postEffectsApplied) { this.postEffectsApplied = postEffectsApplied; }
    
    public List<String> auditTrail() { return List.copyOf(auditTrail); }
    public void audit(String message) { this.auditTrail.add(message); }
    public List<String> revealedCardIds() { return List.copyOf(revealedCardIds); }
    public void revealCard(String cardId) {
        if (cardId != null && !cardId.isBlank()) {
            this.revealedCardIds.add(cardId);
        }
    }
}
