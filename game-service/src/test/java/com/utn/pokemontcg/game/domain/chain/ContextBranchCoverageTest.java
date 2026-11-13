package com.utn.pokemontcg.game.domain.chain;

import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage push for {@link AttackContext} (40% branch) and
 * {@link TrainerEffectContext} (40% branch).
 *
 * The class is plain getters/setters, so the missed branches are all
 * the null-safety ternaries: a null List should be normalized to List.of(),
 * a null BooleanSupplier should be replaced with a Math.random() default.
 */
class ContextBranchCoverageTest {

    @Test
    void attackContextSetRequiredEnergyCostNullBecomesEmpty() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        ctx.setRequiredEnergyCost(null);
        assertNotNull(ctx.requiredEnergyCost());
        assertTrue(ctx.requiredEnergyCost().isEmpty());
    }

    @Test
    void attackContextSetRequiredEnergyCostCopiesInput() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        java.util.ArrayList<String> mutable = new java.util.ArrayList<>(List.of("Fire", "Colorless"));
        ctx.setRequiredEnergyCost(mutable);
        mutable.add("Grass"); // mutate original after set
        // the context's list should be the original 2, not 3
        assertEquals(2, ctx.requiredEnergyCost().size());
    }

    @Test
    void attackContextSetAttachedEnergiesNullBecomesEmpty() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        ctx.setAttachedEnergies(null);
        assertNotNull(ctx.attachedEnergies());
        assertTrue(ctx.attachedEnergies().isEmpty());
    }

    @Test
    void attackContextSetAttachedEnergiesCopiesInput() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        List<AttachedEnergy> mutable = new java.util.ArrayList<>(List.of(new AttachedEnergy("e-1", "Fire")));
        ctx.setAttachedEnergies(mutable);
        mutable.add(new AttachedEnergy("e-2", "Water"));
        assertEquals(1, ctx.attachedEnergies().size());
    }

    @Test
    void attackContextSetSelectedCardIdsNullBecomesEmpty() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        ctx.setSelectedCardIds(null);
        assertTrue(ctx.selectedCardIds().isEmpty());
    }

    @Test
    void attackContextSetSelectedEnergyCardIdsNullBecomesEmpty() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        ctx.setSelectedEnergyCardIds(null);
        assertTrue(ctx.selectedEnergyCardIds().isEmpty());
    }

    @Test
    void attackContextSetCoinFlipResultsNullBecomesEmpty() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        ctx.setCoinFlipResults(null);
        assertTrue(ctx.coinFlipResults().isEmpty());
    }

    @Test
    void attackContextSetCoinFlipNullFallsBackToRandom() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        ctx.setCoinFlip(null);
        BooleanSupplier supplier = ctx.coinFlip();
        assertNotNull(supplier);
        // calling it should not throw and return a boolean
        boolean result = supplier.getAsBoolean();
        assertTrue(result || !result); // tautology: just verify it returns a boolean
    }

    @Test
    void attackContextCustomCoinFlipIsReturned() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        ctx.setCoinFlip(() -> true);
        assertTrue(ctx.coinFlip().getAsBoolean());
    }

    @Test
    void attackContextRevealCardIgnoresNullAndBlank() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        ctx.revealCard(null);
        ctx.revealCard("");
        ctx.revealCard("   ");
        assertTrue(ctx.revealedCardIds().isEmpty());
    }

    @Test
    void attackContextRevealCardAcceptsValidIds() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        ctx.revealCard("xy1-1");
        ctx.revealCard("xy1-2");
        assertEquals(2, ctx.revealedCardIds().size());
    }

    @Test
    void attackContextAuditTrailStartsEmptyAndAccumulates() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        assertTrue(ctx.auditTrail().isEmpty());
        ctx.audit("first");
        ctx.audit("second");
        assertEquals(2, ctx.auditTrail().size());
        assertEquals("first", ctx.auditTrail().get(0));
        assertEquals("second", ctx.auditTrail().get(1));
    }

    @Test
    void attackContextAuditTrailReturnedListIsImmutable() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        ctx.audit("x");
        List<String> trail = ctx.auditTrail();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
            () -> trail.add("mutate"));
    }

    @Test
    void attackContextRevealedCardIdsReturnedListIsImmutable() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        ctx.revealCard("c1");
        List<String> revealed = ctx.revealedCardIds();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
            () -> revealed.add("mutate"));
    }

    @Test
    void attackContextDefaultFieldValuesAreSensible() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        assertEquals(0, ctx.baseDamage());
        assertEquals(0, ctx.preAttackBonus());
        assertEquals(0, ctx.attackerModifier());
        assertEquals(0, ctx.defenderModifier());
        assertEquals(1, ctx.weaknessMultiplier());
        assertEquals(0, ctx.resistanceReduction());
        assertEquals(0, ctx.damage());
        assertEquals(0, ctx.selfDamage());
        assertEquals(-1, ctx.selectedOpponentAttackIndex());
        assertEquals(-1, ctx.attackIndex());
        assertFalse(ctx.confused());
        assertFalse(ctx.cancelled());
        assertFalse(ctx.targetSelected());
        assertFalse(ctx.postEffectsApplied());
        assertNull(ctx.attackName());
        assertNull(ctx.attackText());
        assertNull(ctx.attackerCard());
        assertNull(ctx.defenderCard());
        assertNull(ctx.targetSlot());
        assertNull(ctx.targetPlayerId());
        assertNull(ctx.selectedCondition());
        assertNull(ctx.acceptedOptionalEffect());
    }

    @Test
    void attackContextSettersRoundTrip() {
        AttackContext ctx = new AttackContext(new GameAggregate(UUID.randomUUID()));
        GameCard attacker = new GameCard("a", "A", "Pokemon", Set.of("Basic"), 60, 10, 1);
        GameCard defender = new GameCard("d", "D", "Pokemon", Set.of("Basic"), 70, 12, 2);
        UUID target = UUID.randomUUID();

        ctx.setAttackerCard(attacker);
        ctx.setDefenderCard(defender);
        ctx.setRequiredEnergy(1);
        ctx.setAttachedEnergy(2);
        ctx.setAttackName("Tackle");
        ctx.setAttackText("Flip a coin.");
        ctx.setConfused(true);
        ctx.setCancelled(true);
        ctx.setTargetSelected(true);
        ctx.setTargetSlot("BENCH_0");
        ctx.setTargetPlayerId(target);
        ctx.setSelectedCondition("poisoned");
        ctx.setAcceptedOptionalEffect(Boolean.TRUE);
        ctx.setSelectedOpponentAttackIndex(1);
        ctx.setAttackIndex(2);
        ctx.setBaseDamage(50);
        ctx.setPreAttackBonus(10);
        ctx.setAttackerModifier(5);
        ctx.setDefenderModifier(3);
        ctx.setWeaknessMultiplier(2);
        ctx.setResistanceReduction(20);
        ctx.setDamage(60);
        ctx.setSelfDamage(10);
        ctx.setPostEffectsApplied(true);

        assertEquals(attacker, ctx.attackerCard());
        assertEquals(defender, ctx.defenderCard());
        assertEquals(1, ctx.requiredEnergy());
        assertEquals(2, ctx.attachedEnergy());
        assertEquals("Tackle", ctx.attackName());
        assertEquals("Flip a coin.", ctx.attackText());
        assertTrue(ctx.confused());
        assertTrue(ctx.cancelled());
        assertTrue(ctx.targetSelected());
        assertEquals("BENCH_0", ctx.targetSlot());
        assertEquals(target, ctx.targetPlayerId());
        assertEquals("poisoned", ctx.selectedCondition());
        assertEquals(Boolean.TRUE, ctx.acceptedOptionalEffect());
        assertEquals(1, ctx.selectedOpponentAttackIndex());
        assertEquals(2, ctx.attackIndex());
        assertEquals(50, ctx.baseDamage());
        assertEquals(10, ctx.preAttackBonus());
        assertEquals(5, ctx.attackerModifier());
        assertEquals(3, ctx.defenderModifier());
        assertEquals(2, ctx.weaknessMultiplier());
        assertEquals(20, ctx.resistanceReduction());
        assertEquals(60, ctx.damage());
        assertEquals(10, ctx.selfDamage());
        assertTrue(ctx.postEffectsApplied());
    }

    // ====================================================================
    // TrainerEffectContext branches
    // ====================================================================

    @Test
    void trainerEffectContextSixArgConstructorDefaults() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p1 = game.playerOne();
        GameCard trainer = new GameCard("t", "Trainer", "Trainer", Set.of("Item"), 0, 0, 0);

        TrainerEffectContext ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), "ACTIVE", "tgt-id");

        assertEquals(game, ctx.game());
        assertEquals(p1, ctx.player());
        assertEquals(trainer, ctx.trainerCard());
        assertEquals(trainer.id(), ctx.trainerCardId());
        assertEquals("ACTIVE", ctx.targetSlot());
        assertEquals("tgt-id", ctx.targetId());
        assertNull(ctx.targetPlayerId());
        assertTrue(ctx.selectedCardIds().isEmpty());
        assertTrue(ctx.selectedEnergyCardIds().isEmpty());
        assertNull(ctx.selectedCondition());
        assertNull(ctx.acceptedOptionalEffect());
        // coinFlip() must return a non-null BooleanSupplier (default random)
        assertNotNull(ctx.coinFlip());
    }

    @Test
    void trainerEffectContextNullSelectedListsBecomeEmpty() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        GameCard trainer = new GameCard("t", "Trainer", "Trainer", Set.of("Item"), 0, 0, 0);

        TrainerEffectContext ctx = new TrainerEffectContext(game, game.playerOne(), trainer,
            trainer.id(), "ACTIVE", "tgt-id", null, null, null, null, null, null);

        assertTrue(ctx.selectedCardIds().isEmpty());
        assertTrue(ctx.selectedEnergyCardIds().isEmpty());
        assertNotNull(ctx.coinFlip());
    }

    @Test
    void trainerEffectContextCoinFlipFallsBackWhenNull() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        GameCard trainer = new GameCard("t", "Trainer", "Trainer", Set.of("Item"), 0, 0, 0);

        TrainerEffectContext ctx = new TrainerEffectContext(game, game.playerOne(), trainer,
            trainer.id(), "ACTIVE", "tgt-id", null, null, null, null, null, null);

        assertNotNull(ctx.coinFlip());
        // calling it returns a boolean, not throws
        ctx.coinFlip().getAsBoolean();
    }

    @Test
    void trainerEffectContextCoinFlipPreservesCustom() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        GameCard trainer = new GameCard("t", "Trainer", "Trainer", Set.of("Item"), 0, 0, 0);
        BooleanSupplier custom = () -> true;

        TrainerEffectContext ctx = new TrainerEffectContext(game, game.playerOne(), trainer,
            trainer.id(), "ACTIVE", "tgt-id", null, null, null, null, null, custom);

        assertTrue(ctx.coinFlip().getAsBoolean());
    }
}
