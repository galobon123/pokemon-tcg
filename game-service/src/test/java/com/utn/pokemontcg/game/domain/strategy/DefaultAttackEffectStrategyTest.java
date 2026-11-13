package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAttackEffectStrategyTest {

    private final DefaultAttackEffectStrategy strategy = new DefaultAttackEffectStrategy();

    @Test
    void effectNameReturnsDefault() {
        assertEquals("DEFAULT", strategy.effectName());
    }

    @Test
    void supportsAlwaysReturnsTrue() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        AttackContext ctx = new AttackContext(game);
        assertTrue(strategy.supports(ctx));
    }

    @Test
    void applyAuditsNoSpecialEffect() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        AttackContext ctx = new AttackContext(game);
        strategy.apply(ctx);
        assertTrue(ctx.auditTrail().stream()
                .anyMatch(a -> a.contains("no_special_effect")));
    }

    @Test
    void computeBaseDamageReturnsTen() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        assertEquals(10, strategy.computeBaseDamage(game));
    }
}
