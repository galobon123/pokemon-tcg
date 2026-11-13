package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.engine.DamageCalculator;
import com.utn.pokemontcg.game.domain.model.GameCard;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageCalculatorTest {

    /*
     * JaCoCo gap analysis before this pass:
     * - weaknessMultiplier missed defender null, default null amount and amount < 1.
     * - resistanceReduction missed defender null, null amount and positive amount.
     * - typeMatches missed blank defender modifier and attacker without energyType.
     * - calculate needed explicit branches for base damage clamping and combined modifiers.
     */
    private final DamageCalculator calculator = new DamageCalculator();

    @Test
    void appliesWeaknessFromDefenderCard() {
        GameCard attacker = pokemon("fire-attacker", "Fire", null, null, null, null);
        GameCard defender = pokemon("grass-defender", "Grass", "Fire", 2, null, null);

        assertEquals(80, calculator.calculate(40, attacker, defender));
    }

    @Test
    void appliesResistanceFromDefenderCardWithoutGoingBelowZero() {
        GameCard attacker = pokemon("water-attacker", "Water", null, null, null, null);
        GameCard defender = pokemon("resistant-defender", "Colorless", null, null, "Water", -20);

        assertEquals(20, calculator.calculate(40, attacker, defender));
    }

    @Test
    void appliesWeaknessBeforeResistanceWhenBothMatch() {
        GameCard attacker = pokemon("fire-attacker", "Fire", null, null, null, null);
        GameCard defender = pokemon("mixed-defender", "Grass", "Fire", 2, "Fire", -20);

        assertEquals(60, calculator.calculate(40, attacker, defender));
    }

    @Test
    void leavesDamageUnchangedWhenDefenderHasNoWeaknessOrResistance() {
        GameCard attacker = pokemon("fire-attacker", "Fire", null, null, null, null);
        GameCard defender = pokemon("neutral-defender", "Water", null, null, null, null);

        assertEquals(40, calculator.calculate(40, attacker, defender));
    }

    @Test
    void resistanceCannotMakeDamageNegative() {
        GameCard attacker = pokemon("water-attacker", "Water", null, null, null, null);
        GameCard defender = pokemon("resistant-defender", "Colorless", null, null, "Water", -20);

        assertEquals(0, calculator.calculate(10, attacker, defender));
    }

    @Test
    void clampsNegativeBasePlusAttackerModifierBeforeDamageModifiers() {
        GameCard attacker = pokemon("fire-attacker", "Fire", null, null, null, null);
        GameCard defender = pokemon("neutral-defender", "Water", null, null, null, null);

        assertEquals(0, calculator.calculate(10, -30, attacker, defender, 0));
    }

    @Test
    void appliesAttackerAndDefenderModifiersAroundWeaknessAndResistance() {
        GameCard attacker = pokemon("fire-attacker", "Fire", null, null, null, null);
        GameCard defender = pokemon("mixed-defender", "Grass", "Fire", 2, "Fire", -20);

        assertEquals(70, calculator.calculate(30, 15, attacker, defender, 0));
        assertEquals(60, calculator.calculate(30, 15, attacker, defender, -10));
    }

    @Test
    void defaultsWeaknessMultiplierToTwoWhenAmountIsNullOrInvalid() {
        GameCard attacker = pokemon("fire-attacker", "Fire", null, null, null, null);

        assertEquals(2, calculator.weaknessMultiplier(attacker, pokemon("null-weakness", "Grass", "Fire", null, null, null)));
        assertEquals(2, calculator.weaknessMultiplier(attacker, pokemon("invalid-weakness", "Grass", "Fire", 0, null, null)));
    }

    @Test
    void ignoresWeaknessWhenDefenderIsNullOrTypeDoesNotMatch() {
        GameCard attacker = pokemon("fire-attacker", "Fire", null, null, null, null);
        GameCard defender = pokemon("water-weak-defender", "Grass", "Water", 2, null, null);

        assertEquals(1, calculator.weaknessMultiplier(attacker, null));
        assertEquals(1, calculator.weaknessMultiplier(attacker, defender));
    }

    @Test
    void defaultsResistanceToTwentyAndAcceptsPositiveAmount() {
        GameCard attacker = pokemon("water-attacker", "Water", null, null, null, null);

        assertEquals(20, calculator.resistanceReduction(attacker, pokemon("null-resistance", "Colorless", null, null, "Water", null)));
        assertEquals(30, calculator.resistanceReduction(attacker, pokemon("positive-resistance", "Colorless", null, null, "Water", 30)));
    }

    @Test
    void ignoresResistanceWhenDefenderIsNullBlankOrAttackerHasNoType() {
        GameCard attacker = pokemon("water-attacker", "Water", null, null, null, null);
        GameCard typelessAttacker = pokemon("typeless-attacker", null, null, null, null, null);

        assertEquals(0, calculator.resistanceReduction(attacker, null));
        assertEquals(0, calculator.resistanceReduction(attacker, pokemon("blank-resistance", "Colorless", null, null, " ", -20)));
        assertEquals(0, calculator.resistanceReduction(typelessAttacker, pokemon("resistant-defender", "Colorless", null, null, "Water", -20)));
    }

    @Test
    void legacyBooleanCalculationClampsNegativeDamage() {
        assertEquals(0, calculator.calculate(10, false, true, null));
    }

    @Test
    void keepsLegacyBooleanCalculationForExistingCallers() {
        assertEquals(60, calculator.calculate(40, true, true, null));
    }

    private GameCard pokemon(
        String id,
        String type,
        String weaknessType,
        Integer weaknessAmount,
        String resistanceType,
        Integer resistanceAmount
    ) {
        return new GameCard(
            id,
            id,
            "Pokemon",
            Set.of("Basic"),
            100,
            40,
            1,
            "Hit",
            null,
            type,
            java.util.List.of("Colorless"),
            1,
            null,
            java.util.List.of(),
            weaknessType,
            weaknessAmount,
            resistanceType,
            resistanceAmount,
            java.util.List.of()
        );
    }
}
