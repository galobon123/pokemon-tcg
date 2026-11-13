package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the protected helpers in {@link AbstractAttackEffectStrategy} through a
 * package-private test-only subclass. The goal is to drive the missed branches and
 * instructions reported by JaCoCo (e.g. {@code selectedSlots} blank skip, {@code
 * defender} when the attacker is null or is player two, the {@code effectText}
 * normalization for accented characters and smart apostrophes, the
 * {@code hasCoinCondition} variants, the {@code coinConditionAllowsEffect}
 * branches that depend on the {@code coinFlip} supplier, and the
 * {@code firstDamageAmount} / {@code firstExplicitBonus} fallback paths).
 */
class AbstractAttackEffectStrategyCoverageTest {

    /**
     * Test-only subclass that exposes every protected method from the abstract
     * base so the tests can drive it directly. The {@code effectName}, {@code
     * supports} and {@code apply} members are simple stubs — the unit tests are
     * only concerned with the helper methods.
     */
    static class TestableStrategy extends AbstractAttackEffectStrategy {
        @Override
        public String effectName() {
            return "test";
        }

        @Override
        public boolean supports(AttackContext ctx) {
            return true;
        }

        @Override
        public void apply(AttackContext ctx) {
            // no-op
        }

        public List<String> exposedSelectedSlots(AttackContext ctx) {
            return selectedSlots(ctx);
        }

        public List<String> exposedSelectedNonSlotCardIds(AttackContext ctx) {
            return selectedNonSlotCardIds(ctx);
        }

        public UUID exposedAttacker(AttackContext ctx) {
            return attacker(ctx);
        }

        public UUID exposedDefender(AttackContext ctx) {
            return defender(ctx);
        }

        public String exposedEffectText(AttackContext ctx) {
            return effectText(ctx);
        }

        public boolean exposedContainsAny(String value, String... fragments) {
            return containsAny(value, fragments);
        }

        public boolean exposedHasCoinCondition(String text) {
            return hasCoinCondition(text);
        }

        public boolean exposedCoinConditionAllowsEffect(AttackContext ctx) {
            return coinConditionAllowsEffect(ctx);
        }

        public int exposedFirstDamageAmount(String value, int fallback) {
            return firstDamageAmount(value, fallback);
        }

        public int exposedFirstExplicitBonus(String value, int fallback) {
            return firstExplicitBonus(value, fallback);
        }
    }

    private GameAggregate game() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(p1);
        game.setFirstPlayer(p1);
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());
        return game;
    }

    private AttackContext ctx(String name, String text) {
        AttackContext c = new AttackContext(game());
        c.setAttackName(name);
        c.setAttackText(text);
        return c;
    }

    private AttackContext ctx(GameAggregate g, String name, String text) {
        AttackContext c = new AttackContext(g);
        c.setAttackName(name);
        c.setAttackText(text);
        return c;
    }

    // ===================== selectedSlots =====================

    @Test
    void selectedSlotsSkipsBlankEntries() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Attack", "Pick 2 of your benched Pokemon.");
        c.setSelectedCardIds(List.of("", "   ", "BENCH_0", "BENCH_1"));
        assertEquals(List.of("BENCH_0", "BENCH_1"), s.exposedSelectedSlots(c));
    }

    @Test
    void selectedSlotsSkipsNullEntries() {
        // List.copyOf() rejects nulls, so we use reflection to inject a list that
        // contains a null sentinel and verify the selectedSlots(null) branch.
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Attack", "Pick 2 of your benched Pokemon.");
        injectSelectedCardIds(c, new NullLeadingList(List.of("BENCH_0", "BENCH_1")));
        // The strategy must skip the null entry and return only the slot references.
        assertEquals(List.of("BENCH_0", "BENCH_1"), s.exposedSelectedSlots(c));
    }

    /**
     * Test helper list that prepends a null at index 0 and delegates the rest of
     * the reads to a backing list. Only the read path used by the strategy under
     * test ({@code size()} and {@code get(int)}) is implemented.
     */
    private static final class NullLeadingList extends java.util.AbstractList<String> {
        private final List<String> backing;

        NullLeadingList(List<String> backing) {
            this.backing = new ArrayList<>(backing);
        }

        @Override
        public int size() {
            return backing.size() + 1;
        }

        @Override
        public String get(int index) {
            if (index == 0) {
                return null;
            }
            return backing.get(index - 1);
        }
    }

    /**
     * Replaces the {@code selectedCardIds} field on the given {@link AttackContext}
     * using reflection. This bypasses {@code setSelectedCardIds} which calls
     * {@code List.copyOf()} and therefore rejects null elements, allowing the test
     * to exercise the null-handling branch in {@code selectedSlots} /
     * {@code selectedNonSlotCardIds}.
     */
    private static void injectSelectedCardIds(AttackContext target, List<String> value) {
        try {
            java.lang.reflect.Field field = AttackContext.class.getDeclaredField("selectedCardIds");
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not inject selectedCardIds", e);
        }
    }

    @Test
    void selectedSlotsNormalizesLowercaseAndDedupes() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Attack", "Pick a benched Pokemon.");
        c.setSelectedCardIds(List.of("active", "ACTIVE", "bench_0"));
        assertEquals(List.of("ACTIVE", "BENCH_0"), s.exposedSelectedSlots(c));
    }

    @Test
    void selectedSlotsIgnoresNonSlotEntries() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Geomancy", "Choose 3 different Energy cards from your discard pile and attach them to your Pokemon.");
        c.setSelectedCardIds(List.of("energy-1", "energy-2", "ACTIVE"));
        // The "energy-1" and "energy-2" ids are not slot references and must be skipped.
        assertEquals(List.of("ACTIVE"), s.exposedSelectedSlots(c));
    }

    // ===================== selectedNonSlotCardIds =====================

    @Test
    void selectedNonSlotCardIdsSkipsBlank() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Geomancy", "Choose 3 different Energy cards from your discard pile and attach them to your Pokemon.");
        c.setSelectedCardIds(List.of("", "energy-1", "energy-2"));
        assertEquals(List.of("energy-1", "energy-2"), s.exposedSelectedNonSlotCardIds(c));
    }

    @Test
    void selectedNonSlotCardIdsSkipsNull() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Geomancy", "Choose 3 different Energy cards from your discard pile and attach them to your Pokemon.");
        injectSelectedCardIds(c, new NullLeadingList(List.of("energy-1", "energy-2")));
        assertEquals(List.of("energy-1", "energy-2"), s.exposedSelectedNonSlotCardIds(c));
    }

    @Test
    void selectedNonSlotCardIdsSkipsSlots() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Hydro Bombard", "This attack does 20 damage to 2 of your opponent's Benched Pokemon.");
        c.setSelectedCardIds(List.of("ACTIVE", "BENCH_0", "energy-1", "energy-2"));
        assertEquals(List.of("energy-1", "energy-2"), s.exposedSelectedNonSlotCardIds(c));
    }

    // ===================== attacker / defender =====================

    @Test
    void attackerReturnsCurrentTurnPlayer() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Attack", "Deal 30 damage.");
        assertEquals(c.game().currentTurnPlayer(), s.exposedAttacker(c));
    }

    @Test
    void defenderReturnsPlayerTwoWhenPlayerOneAttacks() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Attack", "Deal 30 damage.");
        UUID p1 = c.game().playerOne();
        UUID p2 = c.game().playerTwo();
        // currentTurnPlayer == playerOne → defender == playerTwo
        assertEquals(p1, s.exposedAttacker(c));
        assertEquals(p2, s.exposedDefender(c));
    }

    @Test
    void defenderReturnsPlayerOneWhenPlayerTwoAttacks() {
        TestableStrategy s = new TestableStrategy();
        GameAggregate g = game();
        g.setCurrentTurnPlayer(g.playerTwo());
        AttackContext c = ctx(g, "Attack", "Deal 30 damage.");
        // attacker == playerTwo → defender == playerOne
        assertEquals(g.playerTwo(), s.exposedAttacker(c));
        assertEquals(g.playerOne(), s.exposedDefender(c));
    }

    @Test
    void defenderReturnsPlayerOneWhenAttackerIsNull() {
        TestableStrategy s = new TestableStrategy();
        GameAggregate g = game();
        g.setCurrentTurnPlayer(null);
        AttackContext c = ctx(g, "Attack", "Deal 30 damage.");
        // attacker == null → the != null && equals(...) check is false → return playerOne
        assertEquals(null, s.exposedAttacker(c));
        assertEquals(g.playerOne(), s.exposedDefender(c));
    }

    // ===================== effectText =====================

    @Test
    void effectTextHandlesNullAttackText() {
        TestableStrategy s = new TestableStrategy();
        GameAggregate g = game();
        AttackContext c = new AttackContext(g);
        c.setAttackName("Attack");
        c.setAttackText(null);
        // When attackText is null, the text variable falls back to "" and the result
        // contains only the lowercased attack name. The call must not throw.
        String result = s.exposedEffectText(c);
        assertNotNull(result);
        assertTrue(result.contains("attack"));
    }

    @Test
    void effectTextHandlesNullAttackName() {
        TestableStrategy s = new TestableStrategy();
        GameAggregate g = game();
        AttackContext c = new AttackContext(g);
        c.setAttackName(null);
        c.setAttackText("Deal 30 damage.");
        String result = s.exposedEffectText(c);
        assertNotNull(result);
        // The text itself is present.
        assertTrue(result.contains("30 damage"));
    }

    @Test
    void effectTextNormalizesAccentedCharacters() {
        TestableStrategy s = new TestableStrategy();
        GameAggregate g = game();
        AttackContext c = new AttackContext(g);
        c.setAttackName("Pokémon");
        c.setAttackText("Daño: 30");
        // NFD normalization + removal of combining marks strips accents, then the
        // result is lowercased.
        String result = s.exposedEffectText(c);
        assertTrue(result.contains("pokemon"), "expected 'pokemon' in: " + result);
        assertTrue(result.contains("dano"), "expected 'dano' in: " + result);
        // The accented forms should be gone.
        assertFalse(result.contains("é"));
        assertFalse(result.contains("ñ"));
    }

    @Test
    void effectTextReplacesSmartApostropheWithAscii() {
        TestableStrategy s = new TestableStrategy();
        GameAggregate g = game();
        AttackContext c = new AttackContext(g);
        c.setAttackName("Pokémon’s Wrath");
        c.setAttackText("Deal 30 damage to Defending Pokémon’s Active.");
        String result = s.exposedEffectText(c);
        // ’ (U+2019) should be replaced with ' (U+0027).
        assertTrue(result.contains("pokemon's"), "expected 'pokemon's' in: " + result);
        assertFalse(result.contains("’"));
    }

    // ===================== containsAny =====================

    @Test
    void containsAnyFindsMatchingFragment() {
        TestableStrategy s = new TestableStrategy();
        assertTrue(s.exposedContainsAny("hello world", "foo", "world", "bar"));
    }

    @Test
    void containsAnyReturnsFalseWhenNoFragmentMatches() {
        TestableStrategy s = new TestableStrategy();
        assertFalse(s.exposedContainsAny("hello world", "foo", "bar", "baz"));
    }

    @Test
    void containsAnyWithEmptyFragmentsReturnsFalse() {
        TestableStrategy s = new TestableStrategy();
        assertFalse(s.exposedContainsAny("hello world"));
    }

    // ===================== hasCoinCondition =====================

    @Test
    void hasCoinConditionDetectsIfHeads() {
        TestableStrategy s = new TestableStrategy();
        // hasCoinCondition is called with the normalized (lowercased) attack text,
        // so the input must already be lowercase to hit the "if heads" branch.
        assertTrue(s.exposedHasCoinCondition("flip a coin. if heads, deal 30 damage."));
    }

    @Test
    void hasCoinConditionDetectsIfTails() {
        TestableStrategy s = new TestableStrategy();
        assertTrue(s.exposedHasCoinCondition("flip a coin. if tails, deal 30 damage."));
    }

    @Test
    void hasCoinConditionDetectsIfBoth() {
        TestableStrategy s = new TestableStrategy();
        assertTrue(s.exposedHasCoinCondition("flip 2 coins. if both are heads, deal 60 damage."));
    }

    @Test
    void hasCoinConditionDetectsIfAllHeads() {
        TestableStrategy s = new TestableStrategy();
        assertTrue(s.exposedHasCoinCondition("flip 2 coins. if all of them are heads, prevent all damage."));
    }

    @Test
    void hasCoinConditionReturnsFalseWhenNoCoinMention() {
        TestableStrategy s = new TestableStrategy();
        assertFalse(s.exposedHasCoinCondition("deal 30 damage to the defending pokemon."));
    }

    // ===================== coinConditionAllowsEffect =====================

    @Test
    void coinConditionAllowsEffectReturnsTrueWhenNoCondition() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Attack", "Deal 30 damage.");
        // No coin condition text → requiresHeads/requiresTails/requiresAllHeads all false
        // → short-circuit to return true without consulting coinFlipResults.
        assertTrue(s.exposedCoinConditionAllowsEffect(c));
    }

    @Test
    void coinConditionAllowsEffectAllHeadsTrueWhenAllHeads() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Attack", "Flip 2 coins. If all of them are heads, deal 60 damage.");
        c.setCoinFlipResults(List.of(true, true));
        assertTrue(s.exposedCoinConditionAllowsEffect(c));
    }

    @Test
    void coinConditionAllowsEffectAllHeadsFalseWhenMixed() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Attack", "Flip 2 coins. If all of them are heads, deal 60 damage.");
        c.setCoinFlipResults(List.of(true, false));
        assertFalse(s.exposedCoinConditionAllowsEffect(c));
    }

    @Test
    void coinConditionAllowsEffectTailsTrueWhenTails() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Attack", "Flip a coin. If tails, deal 30 damage.");
        c.setCoinFlipResults(List.of(false));
        assertTrue(s.exposedCoinConditionAllowsEffect(c));
    }

    @Test
    void coinConditionAllowsEffectTailsFalseWhenHeads() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Attack", "Flip a coin. If tails, deal 30 damage.");
        c.setCoinFlipResults(List.of(true));
        assertFalse(s.exposedCoinConditionAllowsEffect(c));
    }

    @Test
    void coinConditionAllowsEffectGeneratesViaSupplierWhenResultsEmpty() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Attack", "Flip a coin. If heads, deal 30 damage.");
        c.setCoinFlip(() -> true);
        // results is empty → strategy must invoke the supplier once and use the result.
        assertTrue(s.exposedCoinConditionAllowsEffect(c));
        // The generated results should be saved back to the context.
        assertEquals(1, c.coinFlipResults().size());
        assertTrue(c.coinFlipResults().get(0));
    }

    @Test
    void coinConditionAllowsEffectGeneratesViaSupplierTails() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Attack", "Flip a coin. If heads, deal 30 damage.");
        c.setCoinFlip(() -> false);
        // Generated tails → heads requirement not met → false.
        assertFalse(s.exposedCoinConditionAllowsEffect(c));
    }

    @Test
    void coinConditionAllowsEffectUsesExplicitFlipCount() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Attack", "Flip 3 coins. If heads, deal 30 damage.");
        c.setCoinFlip(() -> true);
        // "Flip 3 coins" → coinFlipCount returns 3 → the supplier is called 3 times.
        s.exposedCoinConditionAllowsEffect(c);
        assertEquals(3, c.coinFlipResults().size());
    }

    @Test
    void coinConditionAllowsEffectTailsWithDoesNothingIsIgnored() {
        TestableStrategy s = new TestableStrategy();
        // The text contains both "if tails" and "does nothing" — the latter suppresses
        // the tails requirement, so the result becomes true regardless of the flip.
        AttackContext c = ctx("Attack", "Flip a coin. If tails, does nothing.");
        c.setCoinFlipResults(List.of(true));
        assertTrue(s.exposedCoinConditionAllowsEffect(c));
    }

    @Test
    void coinConditionAllowsEffectHeadsAllowedWithAtLeastOneHead() {
        TestableStrategy s = new TestableStrategy();
        AttackContext c = ctx("Attack", "Flip 2 coins. If heads, deal 30 damage.");
        // The "if heads" branch uses hasHeads (anyMatch true). Mixed results still pass.
        c.setCoinFlipResults(List.of(true, false));
        assertTrue(s.exposedCoinConditionAllowsEffect(c));
    }

    // ===================== firstDamageAmount =====================

    @Test
    void firstDamageAmountReturnsFallbackWhenNoMatch() {
        TestableStrategy s = new TestableStrategy();
        // No "N damage" pattern → fallback is returned.
        assertEquals(7, s.exposedFirstDamageAmount("This attack paralyzes the Defending Pokemon.", 7));
    }

    @Test
    void firstDamageAmountParsesExplicitDamage() {
        TestableStrategy s = new TestableStrategy();
        assertEquals(50, s.exposedFirstDamageAmount("Does 50 damage to the Defending Pokemon.", 0));
    }

    // ===================== firstExplicitBonus =====================

    @Test
    void firstExplicitBonusParsesPlusPattern() {
        TestableStrategy s = new TestableStrategy();
        // The "+30" pattern: regex "\+\s*(\d+)" → group(1) = "30".
        // The pattern matches the +N form (sign then number), not the N+ form.
        assertEquals(30, s.exposedFirstExplicitBonus("This attack does +30 more damage.", 0));
    }

    @Test
    void firstExplicitBonusParsesMoreDamagePattern() {
        TestableStrategy s = new TestableStrategy();
        // The "20 more damage" pattern: regex "(\d+)\s+more\s+damage" → group(1) = "20".
        assertEquals(20, s.exposedFirstExplicitBonus("This attack does 20 more damage.", 0));
    }

    @Test
    void firstExplicitBonusReturnsFallbackWhenNoMatch() {
        TestableStrategy s = new TestableStrategy();
        // No "+N" and no "N more damage" pattern → fallback is returned.
        assertEquals(10, s.exposedFirstExplicitBonus("Some other effect.", 10));
    }
}
