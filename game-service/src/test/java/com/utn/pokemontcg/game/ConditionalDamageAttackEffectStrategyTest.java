package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.strategy.ConditionalDamageAttackEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConditionalDamageAttackEffectStrategyTest {

    @Test
    void supportsConditionalDamageText() {
        var s = new ConditionalDamageAttackEffectStrategy();
        assertTrue(s.supports(context(game(), "Test", "Does 20 more damage.")));
        assertTrue(s.supports(context(game(), "Test", "Does 20 damage times the amount of energy attached to this Pokemon.")));
        assertFalse(s.supports(context(game(), "Test", "Deal 30 damage.")));
    }

    @Test
    void applyAddsBonusForDamageCountersOnDefender() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerTwo(), "ACTIVE", 3);
        AttackContext ctx = context(game, "Second Bite", "Does 10 more damage for each damage counter on your opponent's Active Pokemon.");
        ctx.setDamage(20);
        s.apply(ctx);
        assertEquals(30, ctx.preAttackBonus());
    }

    @Test
    void applyAddsBonusForEnergyAttached() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e2", "Fire"));
        AttackContext ctx = context(game, "Fire Blast", "Does 20 more damage for each Fire Energy attached to this Pokemon.");
        s.apply(ctx);
        assertEquals(40, ctx.preAttackBonus());
    }

    @Test
    void applySetsBaseDamageForEnergyTimes() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e2", "Water"));
        AttackContext ctx = context(game, "Energy Blast", "Does 20 damage times the amount of energy attached to this Pokemon.");
        s.apply(ctx);
        assertEquals(40, ctx.baseDamage());
    }

    @Test
    void applyYouMayDoMoreDamageWhenAccepted() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Hypno Headbutt", "You may do 30 more damage. If you do, this Pokemon is now Asleep.");
        ctx.setDamage(50);
        ctx.setAcceptedOptionalEffect(true);
        s.apply(ctx);
        assertEquals(30, ctx.preAttackBonus());
    }

    @Test
    void applyNoBonusWhenDeclined() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Hypno Headbutt", "You may do 30 more damage. If you do, this Pokemon is now Asleep.");
        ctx.setDamage(50);
        ctx.setAcceptedOptionalEffect(false);
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    @Test
    void applyBonusWhenDefenderHasSpecialCondition() {
        GameAggregate game = game();
        game.statusByPlayer().put(game.playerTwo(), EnumSet.of(StatusCondition.ASLEEP));
        AttackContext ctx = context(game, "Status Strike", "If the Defending Pokemon is affected by a special condition, this attack does 60 more damage.");
        s.apply(ctx);
        assertEquals(60, ctx.preAttackBonus());
    }

    @Test
    void applyChecksCoinConditionFirst() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Lucky", "Flip a coin. If tails, this attack does nothing. Does 20 more damage.");
        ctx.setCoinFlipResults(List.of(false));
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    private final ConditionalDamageAttackEffectStrategy s = new ConditionalDamageAttackEffectStrategy();

    private AttackContext context(GameAggregate game, String name, String text) {
        AttackContext ctx = new AttackContext(game);
        ctx.setAttackName(name);
        ctx.setAttackText(text);
        return ctx;
    }

    private GameAggregate game() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(p1);
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());
        game.activePokemon().put(p1, "active-1");
        game.activePokemon().put(p2, "active-2");
        game.damageCountersBySlot().get(p1).put("ACTIVE", 0);
        game.damageCountersBySlot().get(p2).put("ACTIVE", 0);
        game.cardCatalog().put("active-1", new GameCard("active-1", "Pokemon", "Pokemon", Set.of("Basic"), 50, 10, 1));
        game.cardCatalog().put("active-2", new GameCard("active-2", "Pokemon", "Pokemon", Set.of("Basic"), 50, 10, 1));
        return game;
    }
}
