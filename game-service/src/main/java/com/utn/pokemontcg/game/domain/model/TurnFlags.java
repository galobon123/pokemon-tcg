package com.utn.pokemontcg.game.domain.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.HashSet;
import java.util.Set;

@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class TurnFlags {

    private boolean energyAttached;
    private boolean retreated;
    private boolean supporterPlayed;
    private boolean stadiumPlayed;
    private boolean attacked;
    private Set<String> abilitiesUsed = new HashSet<>();
    private Set<String> evolvedSlots = new HashSet<>();

    // Getters & Setters
    public boolean energyAttached() { return energyAttached; }
    public void setEnergyAttached(boolean energyAttached) { this.energyAttached = energyAttached; }

    public boolean retreated() { return retreated; }
    public void setRetreated(boolean retreated) { this.retreated = retreated; }

    public boolean supporterPlayed() { return supporterPlayed; }
    public void setSupporterPlayed(boolean supporterPlayed) { this.supporterPlayed = supporterPlayed; }

    public boolean stadiumPlayed() { return stadiumPlayed; }
    public void setStadiumPlayed(boolean stadiumPlayed) { this.stadiumPlayed = stadiumPlayed; }

    public boolean attacked() { return attacked; }
    public void setAttacked(boolean attacked) { this.attacked = attacked; }

    public Set<String> abilitiesUsed() { return abilitiesUsed; }
    public Set<String> evolvedSlots() { return evolvedSlots; }

    public void markAbilityUsed(String abilityKey) {
        this.abilitiesUsed.add(abilityKey);
    }

    public boolean abilityUsed(String abilityKey) {
        return this.abilitiesUsed.contains(abilityKey);
    }

    public void markSlotEvolved(String slot) {
        this.evolvedSlots.add(slot);
    }

    public boolean slotEvolved(String slot) {
        return this.evolvedSlots.contains(slot);
    }

    /**
     * Reinicia todas las flags al finalizar o comenzar un turno.
     */
    public void reset() {
        this.energyAttached = false;
        this.retreated = false;
        this.supporterPlayed = false;
        this.stadiumPlayed = false;
        this.attacked = false;
        this.abilitiesUsed.clear();
        this.evolvedSlots.clear();
    }
}
