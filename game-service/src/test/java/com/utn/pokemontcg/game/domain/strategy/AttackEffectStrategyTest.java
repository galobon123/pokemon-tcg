package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the default {@code computeBaseDamage} method declared on
 * {@link AttackEffectStrategy} is covered. The default delegates to
 * {@link GameAggregate#activeAttackBaseDamage()}, so any implementation
 * (even an empty anonymous one) should expose the same value.
 */
class AttackEffectStrategyTest {

    @Test
    void computeBaseDamageDelegatesToGameAggregate() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setActiveAttackBaseDamage(70);

        AttackEffectStrategy strategy = new AttackEffectStrategy() {
            @Override
            public String effectName() {
                return "test";
            }

            @Override
            public boolean supports(AttackContext ctx) {
                return false;
            }

            @Override
            public void apply(AttackContext ctx) {
                // no-op
            }
        };

        assertEquals(70, strategy.computeBaseDamage(game));
    }

    @Test
    void computeBaseDamageReturnsDefaultValueWhenUnset() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        // The GameAggregate default for activeAttackBaseDamage is 30.

        AttackEffectStrategy strategy = new AttackEffectStrategy() {
            @Override
            public String effectName() {
                return "test";
            }

            @Override
            public boolean supports(AttackContext ctx) {
                return false;
            }

            @Override
            public void apply(AttackContext ctx) {
                // no-op
            }
        };

        assertEquals(30, strategy.computeBaseDamage(game));
    }
}
