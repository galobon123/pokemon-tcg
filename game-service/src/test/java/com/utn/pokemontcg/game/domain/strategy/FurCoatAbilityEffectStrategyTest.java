package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FurCoatAbilityEffectStrategyTest {

    @Test
    void abilityNameReturnsFurCoat() {
        assertEquals("Fur Coat", new FurCoatAbilityEffectStrategy().abilityName());
    }

    @Test
    void applyThrowsInvalidActionException() {
        assertThrows(InvalidActionException.class, () ->
            new FurCoatAbilityEffectStrategy().apply(null));
    }
}
