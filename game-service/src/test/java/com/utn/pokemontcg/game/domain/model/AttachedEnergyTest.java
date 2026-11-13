package com.utn.pokemontcg.game.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttachedEnergyTest {

    @Test
    void fullConstructorCreatesCorrectly() {
        AttachedEnergy e = new AttachedEnergy("fire-1", "Fire", 2);
        assertEquals("fire-1", e.cardId());
        assertEquals("Fire", e.energyType());
        assertEquals(2, e.energyAmount());
    }

    @Test
    void twoArgConstructorDefaultsToSingleEnergy() {
        AttachedEnergy e = new AttachedEnergy("water-1", "Water");
        assertEquals("water-1", e.cardId());
        assertEquals("Water", e.energyType());
        assertEquals(1, e.energyAmount());
    }

    @Test
    void compactConstructorClampsZeroToMinimum() {
        AttachedEnergy e = new AttachedEnergy("grass-1", "Grass", 0);
        assertEquals(1, e.energyAmount());
    }

    @Test
    void compactConstructorClampsNegativeToMinimum() {
        AttachedEnergy e = new AttachedEnergy("dark-1", "Darkness", -3);
        assertEquals(1, e.energyAmount());
    }

    @Test
    void compactConstructorPreservesPositiveAmount() {
        AttachedEnergy e = new AttachedEnergy("double-1", "Colorless", 2);
        assertEquals(2, e.energyAmount());
    }
}
