package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.ConditionalDamageAttackEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage for {@link ConditionalDamageAttackEffectStrategy}.
 *
 * The strategy is a long if/else-if chain over the normalized attack text. Each
 * test focuses on exactly one branch by crafting the attack text and the
 * defender card / board state to satisfy that branch's preconditions and then
 * asserts the resulting {@code preAttackBonus} (or {@code baseDamage} when the
 * branch writes through {@code setBaseDamage} and returns early).
 */
class ConditionalDamageAttackEffectStrategyCoverageTest {

    private final ConditionalDamageAttackEffectStrategy s = new ConditionalDamageAttackEffectStrategy();

    // -----------------------------------------------------------------------
    // (a) "active pokemon is a grass" → defender with energyType="Grass"
    //     The strategy literally matches "active Pokemon" (not "defending"), so the
    //     test text must use that exact phrase.
    // -----------------------------------------------------------------------
    @Test
    void applyBonusWhenDefenderIsGrassByEnergyType() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Leaf Pulse",
            "If the active Pokemon is a Grass Pokemon, this attack does 20 more damage.");
        ctx.setDefenderCard(pokemon("grass-def", "Bulbasaur", "Grass", List.of("Grass")));
        s.apply(ctx);
        assertEquals(20, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (b) "active pokemon is a pokemon-ex" → defender.isPokemonEx() == true
    // -----------------------------------------------------------------------
    @Test
    void applyBonusWhenDefenderIsPokemonEx() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Exploit Weakness",
            "If the active Pokemon is a Pokemon-ex, this attack does 60 more damage.");
        // Name ends in "-EX" → isPokemonEx() returns true
        ctx.setDefenderCard(pokemon("ex-def", "Mewtwo-EX", "Psychic", List.of()));
        s.apply(ctx);
        assertEquals(60, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (c) "for each damage counter on your opponent" → counters * multiplier
    // -----------------------------------------------------------------------
    @Test
    void applyBonusForEachDamageCounterOnOpponent() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerTwo(), "ACTIVE", 3);
        AttackContext ctx = context(game, "Second Bite",
            "Does 10 more damage for each damage counter on your opponent's Active Pokemon.");
        s.apply(ctx);
        assertEquals(30, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (d) "times the amount of energy attached to both active"
    //     → (attackerEnergy + defenderEnergy) * firstExplicitBonus
    // -----------------------------------------------------------------------
    @Test
    void applyBonusTimesEnergyAttachedToBothActive() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e2", "Water"));
        game.attachEnergy(game.playerTwo(), "ACTIVE", new AttachedEnergy("e3", "Lightning"));
        // total attached = 3; firstExplicitBonus falls back to 20 (no `+X` or `X more damage`)
        AttackContext ctx = context(game, "Energy Storm",
            "Does 20 damage times the amount of energy attached to both Active Pokemon.");
        s.apply(ctx);
        assertEquals(60, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (e) "times the number of your benched" → sets baseDamage (not preAttackBonus)
    // -----------------------------------------------------------------------
    @Test
    void applySetsBaseDamageForTimesYourBenched() {
        GameAggregate game = game();
        game.bench().get(game.playerOne()).addAll(List.of("b1", "b2"));
        // firstDamageAmount picks up "20 damage" → 20; benchOccupiedCount = 2
        // → baseDamage = 40, and the method returns before touching preAttackBonus
        AttackContext ctx = context(game, "Bench Boost",
            "Does 20 damage times the number of your Benched Pokemon.");
        s.apply(ctx);
        assertEquals(40, ctx.baseDamage());
        assertEquals(0, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (f) "you may do" + "more damage" with acceptedOptionalEffect = null → bonus applies
    //     The check is `!= Boolean.FALSE`, so null counts as "accepted".
    // -----------------------------------------------------------------------
    @Test
    void applyOptionalMoreDamageWithNullAccepted() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Hypno Headbutt",
            "You may do 30 more damage. If you do, this Pokemon is now Asleep.");
        // acceptedOptionalEffect is left null intentionally
        s.apply(ctx);
        assertEquals(30, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (g) "discard" + "energy" + optional + acceptedOptionalEffect = null → bonus applies
    //     The discard branch reads the same `!= Boolean.FALSE` rule.
    // -----------------------------------------------------------------------
    @Test
    void applyDiscardEnergyOptionalWithNullAccepted() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Electron Crush",
            "You may discard a Fire Energy attached to this Pokemon. If you do, this attack does 30 more damage.");
        // acceptedOptionalEffect is left null intentionally
        s.apply(ctx);
        assertEquals(30, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (h) defenderHasType → attackCost contains the type (energyType does NOT)
    //     This exercises the second half of the OR in {@code defenderHasType}.
    // -----------------------------------------------------------------------
    @Test
    void applyBonusWhenDefenderTypeByAttackCost() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Color Crush",
            "If the active Pokemon is a Grass Pokemon, this attack does 20 more damage.");
        // energyType is a non-Grass string, but attackCost contains "Grass"
        ctx.setDefenderCard(pokemon("mixed-def", "Tangela", "Colorless", List.of("Colorless", "Grass")));
        s.apply(ctx);
        assertEquals(20, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (i) "for each different type of basic energy" → set of unique types * multiplier
    // -----------------------------------------------------------------------
    @Test
    void applyBonusForEachDifferentTypeOfBasicEnergy() {
        GameAggregate game = game();
        // 3 energies, 2 unique types → types.size() = 2; fallback multiplier = 30
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e2", "Water"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e3", "Fire"));
        AttackContext ctx = context(game, "Diverse Energy",
            "Does 20 damage for each different type of basic Energy attached to this Pokemon.");
        s.apply(ctx);
        assertEquals(60, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (j) "for each different type of basic energy" with NO energy attached
    //     The set of types is empty, so types.size() = 0 → bonus = 0.
    // -----------------------------------------------------------------------
    @Test
    void applyZeroBonusForEachDifferentTypeWithNoEnergyAttached() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Diverse Energy",
            "Does 20 damage for each different type of basic Energy attached to this Pokemon.");
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (k) "times the amount of energy attached to both active" — asymmetric
    //     (attacker has energy, defender has none). Verifies the sum is 0 + N.
    // -----------------------------------------------------------------------
    @Test
    void applyBonusTimesEnergyAttachedToBothActiveAsymmetric() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e2", "Water"));
        // defender has 0 energy; total = 2 + 0 = 2; firstExplicitBonus falls back to 20
        AttackContext ctx = context(game, "Energy Storm",
            "Does 20 damage times the amount of energy attached to both Active Pokemon.");
        s.apply(ctx);
        assertEquals(40, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (l) "if the active Pokemon is a Grass Pokemon" + defender is NOT Grass
    //     Exercises the false branch of {@code defenderHasType} at line 41.
    // -----------------------------------------------------------------------
    @Test
    void applyNoGrassBonusWhenDefenderIsNotGrass() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Leaf Pulse",
            "If the active Pokemon is a Grass Pokemon, this attack does 20 more damage.");
        ctx.setDefenderCard(pokemon("water-def", "Squirtle", "Water", List.of("Water")));
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (m) "lunatone is on your bench" when there is nothing on the bench
    //     Exercises the false branch of {@code benchContains} at line 43.
    // -----------------------------------------------------------------------
    @Test
    void applyNoLunatoneBonusWhenBenchIsEmpty() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Lunar Crush",
            "If Lunatone is on your Bench, this attack does 30 more damage.");
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (n) "lunatone is on your bench" with a non-Lunatone card on the bench
    //     Exercises the false branch of the anyMatch inside benchContains
    //     (the card != null sub-condition is true, name match is false).
    // -----------------------------------------------------------------------
    @Test
    void applyNoLunatoneBonusWhenOtherPokemonOnBench() {
        GameAggregate game = game();
        game.benchPokemon(game.playerOne(), "pikachu-1", "BENCH_0");
        game.registerCard(pokemon("pikachu-1", "Pikachu", "Lightning", List.of("Lightning")));
        AttackContext ctx = context(game, "Lunar Crush",
            "If Lunatone is on your Bench, this attack does 30 more damage.");
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (o) "already has any damage counters" when defender has zero counters
    //     Exercises the false branch of the damageCountersAtSlot > 0 check.
    // -----------------------------------------------------------------------
    @Test
    void applyNoBonusForAlreadyHasDamageCountersWhenNoCounters() {
        GameAggregate game = game();
        // ensure defender has 0 damage counters (default is 0)
        AttackContext ctx = context(game, "Painful Bite",
            "If the Defending Pokemon already has any damage counters on it, this attack does 40 more damage.");
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (p) "if the active Pokemon is a Pokemon-ex" with defenderCard == null
    //     Exercises the null-guard in the && chain at line 47.
    // -----------------------------------------------------------------------
    @Test
    void applyNoExBonusWhenDefenderCardIsNull() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Exploit Weakness",
            "If the active Pokemon is a Pokemon-ex, this attack does 60 more damage.");
        // defenderCard left null intentionally
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (q) "if the active Pokemon is a Pokemon-ex" with a non-EX defender
    //     Exercises the false branch of defenderCard().isPokemonEx() at line 47.
    // -----------------------------------------------------------------------
    @Test
    void applyNoExBonusWhenDefenderIsNotEx() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Exploit Weakness",
            "If the active Pokemon is a Pokemon-ex, this attack does 60 more damage.");
        ctx.setDefenderCard(pokemon("vanilla", "Snorlax", "Colorless", List.of("Colorless")));
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (r) "affected by a special condition" when the defender has NO status
    //     Exercises the false branch of the isEmpty() check at line 49 and
    //     the false branch of the affected ? bonus : 0 at line 50.
    // -----------------------------------------------------------------------
    @Test
    void applyNoSpecialConditionBonusWhenNotAffected() {
        GameAggregate game = game();
        // ensure no status on the defender
        game.statusByPlayer().put(game.playerTwo(), java.util.EnumSet.noneOf(com.utn.pokemontcg.game.domain.model.StatusCondition.class));
        AttackContext ctx = context(game, "Status Strike",
            "If the Defending Pokemon is affected by a special condition, this attack does 60 more damage.");
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (s) "affected by a special condition" + "remove all special conditions"
    //     Exercises the inner && at line 51 (affected && text contains clause).
    // -----------------------------------------------------------------------
    @Test
    void applyRemoveAllSpecialConditionsWhenAffected() {
        GameAggregate game = game();
        game.statusByPlayer().put(game.playerTwo(),
            java.util.EnumSet.of(com.utn.pokemontcg.game.domain.model.StatusCondition.ASLEEP));
        AttackContext ctx = context(game, "Status Strike",
            "If the Defending Pokemon is affected by a special condition, this attack does 60 more damage. "
                + "Remove all special conditions from the Defending Pokemon.");
        s.apply(ctx);
        assertEquals(60, ctx.preAttackBonus());
        // the remove-all branch wipes the status — the source code does a Map.remove,
        // so we check via getOrDefault to model "no entry == no status"
        assertTrue(game.statusByPlayer()
            .getOrDefault(game.playerTwo(), java.util.EnumSet.noneOf(com.utn.pokemontcg.game.domain.model.StatusCondition.class))
            .isEmpty());
    }

    // -----------------------------------------------------------------------
    // (t) "has any Psychic energy attached" when no energy is attached
    //     Exercises the false branch of energyCount("Psychic") > 0 at line 61.
    // -----------------------------------------------------------------------
    @Test
    void applyNoPsychicBonusWhenNoPsychicEnergy() {
        GameAggregate game = game();
        // attach a non-Psychic energy to be sure
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        AttackContext ctx = context(game, "Mind Reader",
            "If this Pokemon has any Psychic energy attached to it, this attack does 30 more damage.");
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (u) "you may do" + "more damage" with acceptedOptionalEffect = true
    //     Confirms the explicit-true path adds the bonus (reaffirms the C=true
    //     branch at line 81).
    // -----------------------------------------------------------------------
    @Test
    void applyYouMayDoMoreDamageWithExplicitTrue() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Hypno Headbutt",
            "You may do 30 more damage. If you do, this Pokemon is now Asleep.");
        ctx.setAcceptedOptionalEffect(true);
        s.apply(ctx);
        assertEquals(30, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (v) "discard an Energy" with the discard mandatory (no "you may")
    //     Exercises the L91 branch where !optional is true, so discardHappens
    //     is true regardless of acceptedOptionalEffect.
    // -----------------------------------------------------------------------
    @Test
    void applyMandatoryDiscardEnergyAppliesBonus() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Energy Crush",
            "Discard a Fire Energy attached to this Pokemon. This attack does 30 more damage.");
        // acceptedOptionalEffect is null — the mandatory path does not care
        s.apply(ctx);
        assertEquals(30, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (w) optional discard with acceptedOptionalEffect = false
    //     Exercises the L92 false branch (discardHappens == false).
    // -----------------------------------------------------------------------
    @Test
    void applyOptionalDiscardEnergyWithFalseSkipsBonus() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Electron Crush",
            "You may discard a Fire Energy attached to this Pokemon. If you do, this attack does 30 more damage.");
        ctx.setAcceptedOptionalEffect(false);
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (x) optional discard with acceptedOptionalEffect = true
    //     Exercises the L91 true branch via the acceptedOptionalEffect != FALSE
    //     sub-condition (the optional path is taken when the player accepts).
    // -----------------------------------------------------------------------
    @Test
    void applyOptionalDiscardEnergyWithTrueAppliesBonus() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Electron Crush",
            "You may discard a Fire Energy attached to this Pokemon. If you do, this attack does 30 more damage.");
        ctx.setAcceptedOptionalEffect(true);
        s.apply(ctx);
        assertEquals(30, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (y) discard text that does NOT include "more damage" (no bonus applies)
    //     Exercises the false branch of Pattern find() at line 89.
    // -----------------------------------------------------------------------
    @Test
    void applyNoDiscardBonusWhenPatternMissing() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Energy Flick",
            "Discard a Fire Energy attached to this Pokemon. This attack does 30 damage.");
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (z) coin condition that fails → early return at L36
    //     The coin condition is recognized (text "If heads") and the recorded
    //     flip is tails, so !coinConditionAllowsEffect is true and apply() must
    //     return BEFORE evaluating the if-else chain. We confirm by attaching
    //     Fire Energy that would otherwise drive a 40-damage bonus at L56; if
    //     the early return is skipped the bonus would be 40.
    // -----------------------------------------------------------------------
    @Test
    void applyEarlyReturnWhenCoinConditionFails() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e2", "Fire"));
        AttackContext ctx = context(game, "Lucky Strike",
            "Flip a coin. If heads, this attack does 20 more damage for each Fire Energy attached to this Pokemon.");
        // No "does nothing" → the strategy DOES recognize the heads requirement.
        // Recorded flip is tails → allowed = false → !coinConditionAllowsEffect = true → return.
        ctx.setCoinFlipResults(List.of(false));
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (aa) discard text where "discard" is present but "energy" is not
    //      Exercises the A=true / B=false outcome of the && at L88
    //      (the `text.contains("energy")` sub-condition is false).
    //      We use a discard-a-tool attack whose text still triggers supports()
    //      via the "you may do" + "more damage" branch (L29).
    // -----------------------------------------------------------------------
    @Test
    void applyNoDiscardBonusWhenTextHasDiscardButNoEnergy() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Tool Drop",
            "You may do 20 more damage. If you do, discard a tool card from this Pokemon.");
        ctx.setAcceptedOptionalEffect(true);
        s.apply(ctx);
        // The optional "you may do more damage" path adds 20, but the discard
        // branch at L88 stays false (text has "discard" but not "energy") → 20 total.
        assertEquals(20, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (bb) supports() — text that hits L26 ("damage times the number of your benched")
    //      L24 (does/do N more damage) does not match because the text never
    //      puts "more damage" right after the number.
    // -----------------------------------------------------------------------
    @Test
    void supportsTimesNumberOfBenched() {
        AttackContext ctx = context(game(), "Bench Boost",
            "Does 30 damage times the number of your Benched Pokemon.");
        assertTrue(s.supports(ctx));
    }

    // -----------------------------------------------------------------------
    // (cc) supports() — text that hits L27 ("for each different type")
    // -----------------------------------------------------------------------
    @Test
    void supportsForEachDifferentType() {
        AttackContext ctx = context(game(), "Diverse Energy",
            "Does 20 damage for each different type of basic Energy attached to this Pokemon.");
        assertTrue(s.supports(ctx));
    }

    // -----------------------------------------------------------------------
    // (dd) supports() — text that hits L28 ("more damage for each damage counter")
    //      The phrase lacks the "(does|do) N" prefix that L24 looks for.
    // -----------------------------------------------------------------------
    @Test
    void supportsMoreDamageForEachDamageCounter() {
        AttackContext ctx = context(game(), "Painful Bite",
            "10 more damage for each damage counter on this Pokemon.");
        assertTrue(s.supports(ctx));
    }

    // -----------------------------------------------------------------------
    // (ee) supports() — text that hits L29 ("you may do" + "more damage")
    //      without matching L24. We use a phrase where "more damage" is not
    //      preceded by "(does|do) <number>".
    // -----------------------------------------------------------------------
    @Test
    void supportsYouMayDoMoreDamageWithoutDoesPrefix() {
        AttackContext ctx = context(game(), "Headbutt",
            "You may do 20 more damage. If you do, this Pokemon is now Asleep.");
        assertTrue(s.supports(ctx));
    }

    // -----------------------------------------------------------------------
    // (ff) supports() — text that hits L30 ("you may" + "additional damage")
    // -----------------------------------------------------------------------
    @Test
    void supportsYouMayAdditionalDamage() {
        AttackContext ctx = context(game(), "Crushing Blow",
            "You may do 20 additional damage to the Defending Pokemon.");
        assertTrue(s.supports(ctx));
    }

    // -----------------------------------------------------------------------
    // (gg) Grass check with defenderCard == null
    //     This is the ONLY call site of {@code defenderHasType}, so a null
    //     defender here covers the A-false (and the D-false on the || arm)
    //     branches at lines 103 / 104.
    // -----------------------------------------------------------------------
    @Test
    void applyNoGrassBonusWhenDefenderCardIsNull() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Leaf Pulse",
            "If the active Pokemon is a Grass Pokemon, this attack does 20 more damage.");
        // defenderCard left null intentionally → defenderHasType() short-circuits
        // on its first guard and returns false
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // (hh) Grass check with defenderCard having a NULL energyType
    //     Exercises the B-false branch in the && chain at line 103
    //     (defenderCard is non-null, energyType is null → second guard is false).
    //     The attack cost must NOT contain "Grass" either, otherwise the ||
    //     arm on line 104 would still match.
    // -----------------------------------------------------------------------
    @Test
    void applyNoGrassBonusWhenDefenderEnergyTypeIsNull() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Leaf Pulse",
            "If the active Pokemon is a Grass Pokemon, this attack does 20 more damage.");
        GameCard nullType = new GameCard("null-type", "Mystery Pokemon", "Pokemon",
            Set.of("Basic"), 100, 10, 1, null, List.of("Colorless"), 1);
        ctx.setDefenderCard(nullType);
        s.apply(ctx);
        assertEquals(0, ctx.preAttackBonus());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private AttackContext context(GameAggregate game, String name, String text) {
        AttackContext ctx = new AttackContext(game);
        ctx.setAttackName(name);
        ctx.setAttackText(text);
        return ctx;
    }

    private GameCard pokemon(String id, String name, String energyType, List<String> attackCost) {
        return new GameCard(id, name, "Pokemon", Set.of("Basic"),
            100, 10, 1, energyType, attackCost, 1);
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
        game.activePokemon().put(p1, "active-1");
        game.activePokemon().put(p2, "active-2");
        game.registerCard(new GameCard("active-1", "Attacker", "Pokemon", Set.of("Basic"), 100, 10, 1));
        game.registerCard(new GameCard("active-2", "Defender", "Pokemon", Set.of("Basic"), 100, 10, 1));
        game.damageCountersBySlot().get(p1).put("ACTIVE", 0);
        game.damageCountersBySlot().get(p2).put("ACTIVE", 0);
        return game;
    }
}
