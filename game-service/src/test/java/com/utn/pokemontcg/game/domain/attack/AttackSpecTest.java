package com.utn.pokemontcg.game.domain.attack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttackSpecTest {

    @Test
    void constructorNullSafesRequirements() {
        AttackSpec spec = new AttackSpec(null, List.of(), 0);
        assertEquals(List.of(), spec.requirements());
    }

    @Test
    void constructorNullSafesEffects() {
        AttackSpec spec = new AttackSpec(List.of(), null, 1);
        assertEquals(List.of(), spec.effects());
    }

    @Test
    void constructorPreservesNonNullValues() {
        var reqs = List.of(new AttackRequirementSpec("DECK", "Search", 0, 1, 1, true, "Select a Pokemon"));
        var effs = List.of(new AttackEffectSpec("damage", "30 damage"));
        AttackSpec spec = new AttackSpec(reqs, effs, 2);
        assertEquals(reqs, spec.requirements());
        assertEquals(effs, spec.effects());
        assertEquals(2, spec.coinFlipCount());
    }

    @Test
    void constructorWithAllNulls() {
        AttackSpec spec = new AttackSpec(null, null, 0);
        assertEquals(List.of(), spec.requirements());
        assertEquals(List.of(), spec.effects());
    }
}
