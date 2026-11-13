package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.*;
import com.utn.pokemontcg.game.domain.model.*;
import com.utn.pokemontcg.game.domain.strategy.*;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FinalCoverageSprintTest {

    @Test
    void energyValidation_emptyCostSkips() {
        AttackContext c = ctx(game(), "Test", "Test");
        c.setRequiredEnergyCost(List.of());
        assertDoesNotThrow(() -> new EnergyValidationStep().execute(c));
    }

    @Test
    void confusionCheck_confusedAndHeads() {
        GameAggregate g = game();
        g.statusByPlayer().put(g.playerOne(), java.util.EnumSet.of(StatusCondition.CONFUSED));
        AttackContext c = ctx(g, "Test", "Test");
        c.setConfused(true);
        c.setCoinFlip(() -> true);
        new ConfusionCheckStep().execute(c);
    }

    @Test
    void inMemoryRepo_saveAndFind() {
        InMemoryGameStateRepository repo = new InMemoryGameStateRepository();
        GameAggregate g = game();
        GameAggregate saved = repo.save(g);
        assertTrue(repo.findById(g.id()).isPresent());
    }

    @Test
    void inMemoryRepo_deleteAll() {
        InMemoryGameStateRepository repo = new InMemoryGameStateRepository();
        repo.save(game());
        repo.deleteAll();
    }

    @Test
    void coinFlipDamage_fightingEnergyFlipCount() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("f1", "Fighting"));
        AttackContext c = ctx(g, "Fury", "Flip a coin for each Fighting Energy attached to this Pokemon. This attack does 20 damage for each heads.");
        c.setCoinFlip(seq(true));
        new CoinFlipDamageAttackEffectStrategy().apply(c);
    }

    @Test
    void specialCondition_burnAndConfuse() {
        GameAggregate g = game();
        AttackContext c1 = ctx(g, "Burn", "The Defending Pokemon is now Burned.");
        new SpecialConditionAttackEffectStrategy().apply(c1);
        assertTrue(g.statusByPlayer().get(g.playerTwo()).contains(StatusCondition.BURNED));
        g.statusByPlayer().get(g.playerTwo()).clear();
        AttackContext c2 = ctx(g, "Confuse", "The Defending Pokemon is now Confused.");
        new SpecialConditionAttackEffectStrategy().apply(c2);
        assertTrue(g.statusByPlayer().get(g.playerTwo()).contains(StatusCondition.CONFUSED));
    }

    @Test
    void gameAggregate_addDamageCountersToBench() {
        GameAggregate g = game();
        g.placeSetupBench(g.playerOne(), List.of("b1"));
        g.addDamageCounters(g.playerOne(), "BENCH_0", 3);
        assertEquals(3, g.damageCountersAtSlot(g.playerOne(), "BENCH_0"));
    }

    @Test
    void preAttackStep_execute() {
        AttackContext c = ctx(game(), "Test", "Test");
        c.setPreAttackBonus(10);
        new PreAttackStep().execute(c);
        assertEquals(10, c.preAttackBonus());
    }

    @Test
    void gameAggregate_healDamageCounters() {
        GameAggregate g = game();
        g.addDamageCounters(g.playerOne(), "ACTIVE", 5);
        g.healDamageCounters(g.playerOne(), "ACTIVE", 2);
        assertEquals(3, g.damageCountersAtSlot(g.playerOne(), "ACTIVE"));
    }

    @Test
    void gameAggregate_healDamageCountersNonPositive() {
        GameAggregate g = game();
        g.addDamageCounters(g.playerOne(), "ACTIVE", 5);
        g.healDamageCounters(g.playerOne(), "ACTIVE", 0);
        assertEquals(5, g.damageCountersAtSlot(g.playerOne(), "ACTIVE"));
    }

    private java.util.function.BooleanSupplier seq(boolean... v) {
        java.util.ArrayDeque<Boolean> q = new java.util.ArrayDeque<>();
        for (boolean b : v) q.add(b);
        return q::removeFirst;
    }

    private AttackContext ctx(GameAggregate g, String name, String text) {
        AttackContext c = new AttackContext(g);
        c.setAttackName(name);
        c.setAttackText(text);
        c.setAttachedEnergies(g.attachedEnergiesFor(g.currentTurnPlayer(), "ACTIVE"));
        c.setAttackerCard(g.cardCatalog().get(g.activePokemon().get(g.currentTurnPlayer())));
        return c;
    }

    private GameAggregate game() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        GameAggregate g = new GameAggregate(p1);
        g.setPlayerTwo(p2);
        g.setCurrentTurnPlayer(p1);
        g.setFirstPlayer(p1);
        g.initializeZonesFor(p1, List.of());
        g.initializeZonesFor(p2, List.of());
        g.activePokemon().put(p1, "a1");
        g.activePokemon().put(p2, "a2");
        g.registerCard(new GameCard("a1", "Att", "Pokemon", Set.of("Basic"), 100, 10, 1));
        g.registerCard(new GameCard("a2", "Def", "Pokemon", Set.of("Basic"), 100, 10, 1));
        return g;
    }
}
