package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForestCurseAbilityEffectStrategyTest {

    @Test
    void abilityNameReturnsForestsCurse() {
        assertEquals("Forest's Curse", new ForestCurseAbilityEffectStrategy().abilityName());
    }

    @Test
    void applyThrowsInvalidActionException() {
        assertThrows(InvalidActionException.class, () ->
            new ForestCurseAbilityEffectStrategy().apply(null));
    }
}
