package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.attack.AttackPatternAnalyzer;
import com.utn.pokemontcg.game.domain.attack.AttackSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage push for {@link AttackPatternAnalyzer}.
 * The existing {@link AttackPatternAnalyzerTest} covers most patterns; this
 * file targets the less-common branches: target counts of 2 (Hydro Bombard /
 * Geomancy), OWN_POKEMON / OWN_BENCH / OPPONENT_BENCH / ANY_POKEMON scopes,
 * additional effect previews (SELF_DAMAGE, OPPONENT_DAMAGE_ALL, HP_SET,
 * DISCARD_RECOVERY, SWITCH, MOVE_ENERGY, DRAW, SEARCH, CONDITIONAL_DAMAGE
 * via for each heads / different types, PERSISTENT_EFFECT variants), and
 * deck / discard count variations.
 */
class AttackPatternAnalyzerBranchesTest {

    private final AttackPatternAnalyzer analyzer = new AttackPatternAnalyzer();

    // ----- target counts -----

    @Test
    void targetCountTwoOfYourOpponentBenched() {
        AttackSpec spec = analyzer.analyze("Hydro Bombard",
            "Choose 2 of your opponent's Benched Pokemon. This attack does 30 damage to each of them.",
            30);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_TARGET")
                && step.scope().equals("OPPONENT_BENCH")
                && step.max() == 2));
    }

    @Test
    void targetCountAttachUpToTwoBenched() {
        AttackSpec spec = analyzer.analyze("Geomancy",
            "Attach up to 2 Fairy Energy cards from your hand to your Benched Pokemon.",
            0);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_TARGET")
                && step.scope().equals("OWN_BENCH")
                && step.max() == 2));
    }

    // ----- target scope: OWN_POKEMON (heal one of your own) -----

    @Test
    void scopeOwnPokemonViaAttachEnergyToOneOfYourPokemon() {
        AttackSpec spec = analyzer.analyze("Gather Energy",
            "Attach a Lightning Energy card from your discard pile to 1 of your Pokemon.",
            0);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_TARGET")
                && step.scope().equals("OWN_POKEMON")));
    }

    // ----- target scope: OWN_BENCH variants -----

    @Test
    void scopeOwnBenchFromTuBanca() {
        AttackSpec spec = analyzer.analyze("Cambia",
            "Elige 1 Pokemon de tu banca para que sea el nuevo Activo.",
            0);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_TARGET")
                && step.scope().equals("OWN_BENCH")));
    }

    // ----- target scope: OPPONENT_BENCH variants -----

    @Test
    void scopeOpponentBenchFromBancaDeTuOponente() {
        AttackSpec spec = analyzer.analyze("Drenaje",
            "Elige 1 Pokemon en banca de tu oponente y hazle 30 dano.",
            30);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_TARGET")
                && step.scope().equals("OPPONENT_BENCH")));
    }

    @Test
    void scopeOpponentBenchFromSwitchOpponent() {
        AttackSpec spec = analyzer.analyze("Forced Switch",
            "Switch 1 of your opponent's Benched Pokemon with their Active Pokemon.",
            0);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_TARGET")
                && step.scope().equals("OPPONENT_BENCH")));
    }

    @Test
    void scopeOpponentBenchFromMoveEnergyAttachedToOpponent() {
        AttackSpec spec = analyzer.analyze("Energy Snatch",
            "Move an Energy attached to your opponent's Active Pokemon to 1 of your Pokemon.",
            0);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_TARGET")
                && step.scope().equals("OPPONENT_BENCH")));
    }

    // ----- target scope: ANY_POKEMON -----

    @Test
    void scopeAnyPokemonFromChoose1Pokemon() {
        AttackSpec spec = analyzer.analyze("Shift",
            "Choose 1 Pokemon. Move an Energy from that Pokemon to another of your Pokemon.",
            0);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_TARGET")
                && step.scope().equals("ANY_POKEMON")));
    }

    // ----- target scope: hitsAll special cases -----

    @Test
    void hitsAllReturnsNullScopeForNumberOfBenched() {
        AttackSpec spec = analyzer.analyze("Circle Circuit",
            "20 damage times the number of your Benched Pokemon.",
            20);
        assertTrue(spec.requirements().stream()
            .noneMatch(step -> step.type().equals("SELECT_TARGET")),
            "'number of your Benched' debe bypassear addTargetRequirement");
    }

    // ----- opponent hand selection counts -----

    @Test
    void opponentHandSelectionCountTwoCards() {
        // "Choose a card" (no digit) and "Discard two cards" — the count regex
        // matches "two cards from your opponent's hand" and returns 2.
        AttackSpec spec = analyzer.analyze("Sabotage",
            "Look at your opponent's hand. Choose a card from your opponent's hand. Discard two cards from your opponent's hand.",
            0);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_HAND_CARD")
                && step.count() == 2),
            "two cards from your opponent's hand debe matchear el regex y devolver 2");
    }

    @Test
    void opponentHandSelectionCountFromSpanish() {
        // "Elige una carta" (no digit) + "Descarta 3 cartas de la mano" → 3.
        AttackSpec spec = analyzer.analyze("Saqueo",
            "Mira la mano de tu oponente. Elige una carta de la mano. Descarta 3 cartas de la mano.",
            0);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_HAND_CARD")
                && step.count() == 3),
            "3 cartas de la mano debe matchear el regex en espanol y devolver 3");
    }

    // ----- deck selection counts -----

    @Test
    void deckSelectionCountTwo() {
        AttackSpec spec = analyzer.analyze("Search Two",
            "Search your deck for up to 2 cards and put them into your hand.",
            0);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_DECK_CARDS")
                && step.count() == 2));
    }

    @Test
    void deckSelectionCountThree() {
        AttackSpec spec = analyzer.analyze("Search Three",
            "Search your deck for 3 different types of Pokemon and put them into your hand.",
            0);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_DECK_CARDS")
                && step.count() == 3));
    }

    // ----- condition selection options -----

    @Test
    void conditionSelectionAsleepOption() {
        AttackSpec spec = analyzer.analyze("Sleep Pick",
            "You may choose either Sleep or Paralyzed for the Defending Pokemon.",
            0);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_CONDITION")));
    }

    @Test
    void conditionSelectionConfusedOption() {
        AttackSpec spec = analyzer.analyze("Confusion Pick",
            "You may choose either Confused or Burned. The Defending Pokemon is now affected by that Special Condition.",
            0);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_CONDITION")));
    }

    // ----- effect previews: SELF_DAMAGE / OPPONENT_DAMAGE_ALL / HP_SET / RECOVERY -----

    @Test
    void selfDamageEffectPreview() {
        AttackSpec spec = analyzer.analyze("Recoil",
            "This attack does 100 damage to the Defending Pokemon. This Pokemon does 30 damage to itself.",
            100);
        assertTrue(spec.effects().stream()
            .anyMatch(effect -> effect.type().equals("SELF_DAMAGE")));
    }

    @Test
    void opponentDamageAllEffectPreview() {
        AttackSpec spec = analyzer.analyze("Earthquake",
            "Does 20 damage to each of your opponent's Benched Pokemon and Active Pokemon. Put 2 damage counters each of your opponent's Pokemon.",
            20);
        assertTrue(spec.effects().stream()
            .anyMatch(effect -> effect.type().equals("OPPONENT_DAMAGE_ALL")));
    }

    @Test
    void remainingHpEffectPreview() {
        AttackSpec spec = analyzer.analyze("Final Gambit",
            "This attack does damage equal to the remaining HP of the Defending Pokemon.",
            0);
        assertTrue(spec.effects().stream()
            .anyMatch(effect -> effect.type().equals("HP_SET")));
    }

    @Test
    void discardRecoveryEffectPreview() {
        AttackSpec spec = analyzer.analyze("Salvage",
            "Put a card from your discard pile into your hand.",
            0);
        assertTrue(spec.effects().stream()
            .anyMatch(effect -> effect.type().equals("DISCARD_RECOVERY")));
    }

    // ----- effect previews: SWITCH / MOVE_ENERGY / DRAW / SEARCH -----

    @Test
    void switchEffectPreview() {
        AttackSpec spec = analyzer.analyze("Escape",
            "Switch this Pokemon with 1 of your Benched Pokemon.",
            0);
        assertTrue(spec.effects().stream()
            .anyMatch(effect -> effect.type().equals("SWITCH")));
    }

    @Test
    void moveEnergyEffectPreview() {
        AttackSpec spec = analyzer.analyze("Shuffle Energy",
            "Move an Energy from this Pokemon to 1 of your Benched Pokemon.",
            0);
        assertTrue(spec.effects().stream()
            .anyMatch(effect -> effect.type().equals("MOVE_ENERGY")));
    }

    @Test
    void drawEffectPreview() {
        AttackSpec spec = analyzer.analyze("Professor Draw",
            "Draw 3 cards.",
            0);
        assertTrue(spec.effects().stream()
            .anyMatch(effect -> effect.type().equals("DRAW")));
    }

    @Test
    void searchEffectPreview() {
        AttackSpec spec = analyzer.analyze("Card Search",
            "Search your deck for a Pokemon and put it into your hand.",
            0);
        assertTrue(spec.effects().stream()
            .anyMatch(effect -> effect.type().equals("SEARCH")));
    }

    // ----- effect previews: CONDITIONAL_DAMAGE variants -----

    @Test
    void conditionalDamageForEachHeadsAddsSecondEntry() {
        // The COIN_FLIP effect is the only one when there's no baseDamage;
        // with baseDamage > 0 we also get a CONDITIONAL_DAMAGE entry.
        AttackSpec spec = analyzer.analyze("Lucky Strike",
            "Flip 2 coins. This attack does 50 more damage for each heads.",
            50);
        assertTrue(spec.effects().stream()
            .anyMatch(effect -> effect.type().equals("CONDITIONAL_DAMAGE")));
    }

    @Test
    void conditionalDamageForEachDifferentType() {
        AttackSpec spec = analyzer.analyze("Type Mix",
            "This attack does 20 more damage for each different type of Energy attached to this Pokemon.",
            30);
        assertTrue(spec.effects().stream()
            .anyMatch(effect -> effect.type().equals("CONDITIONAL_DAMAGE")));
    }

    // ----- effect previews: PERSISTENT_EFFECT variants -----

    @Test
    void persistentEffectCannotPlaySupporter() {
        AttackSpec spec = analyzer.analyze("Lock Supporter",
            "During your opponent's next turn, your opponent can't play any Supporter card.",
            0);
        assertTrue(spec.effects().stream()
            .anyMatch(effect -> effect.type().equals("PERSISTENT_EFFECT")));
    }

    @Test
    void persistentEffectPreventAllDamage() {
        AttackSpec spec = analyzer.analyze("Bodyguard",
            "Prevent all damage done to this Pokemon by attacks during your opponent's next turn.",
            0);
        assertTrue(spec.effects().stream()
            .anyMatch(effect -> effect.type().equals("PERSISTENT_EFFECT")));
    }

    // ----- coin flip count -----

    @Test
    void coinFlipCountZeroWhenNoFlipInText() {
        AttackSpec spec = analyzer.analyze("Tackle", "This attack does 30 damage.", 30);
        assertEquals(0, spec.coinFlipCount());
    }

    @Test
    void coinFlipCountDefaultOneWhenFlipPresentButNoCount() {
        AttackSpec spec = analyzer.analyze("Flip Strike",
            "Flip a coin. If heads, this attack does 20 more damage.",
            20);
        assertEquals(1, spec.coinFlipCount());
    }

    @Test
    void coinFlipCountExplicit() {
        AttackSpec spec = analyzer.analyze("Triple Flip",
            "Flip 3 coins. This attack does 30 damage for each heads.",
            30);
        assertEquals(3, spec.coinFlipCount());
    }

    // ----- discard count variations -----

    @Test
    void discardCountFour() {
        AttackSpec spec = analyzer.analyze("Energy Burst",
            "Discard 4 Energy attached to this Pokemon. This attack does 100 damage.",
            100);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_ATTACHED_ENERGY")
                && step.count() == 4));
    }

    @Test
    void discardCountTwo() {
        AttackSpec spec = analyzer.analyze("Double Discard",
            "Discard 2 Energy attached to this Pokemon. This attack does 80 damage.",
            80);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_ATTACHED_ENERGY")
                && step.count() == 2));
    }

    // ----- damage preview appears with baseDamage > 0 -----

    @Test
    void damageEffectPreviewPresentWithBaseDamage() {
        AttackSpec spec = analyzer.analyze("Tackle", "Tackle the Defending Pokemon.", 40);
        assertTrue(spec.effects().stream()
            .anyMatch(effect -> effect.type().equals("DAMAGE")));
    }

    @Test
    void benchDamageEffectPreview() {
        AttackSpec spec = analyzer.analyze("Bench Hit",
            "This attack does 30 damage to 1 of your opponent's Benched Pokemon.",
            0);
        assertTrue(spec.effects().stream()
            .anyMatch(effect -> effect.type().equals("BENCH_DAMAGE")));
    }

    // ----- energy discard on opponent's pokemon -----

    @Test
    void energySelectionFromOpponentActive() {
        AttackSpec spec = analyzer.analyze("Strip",
            "Discard an Energy attached to your opponent's Active Pokemon.",
            60);
        assertTrue(spec.requirements().stream()
            .anyMatch(step -> step.type().equals("SELECT_ATTACHED_ENERGY")
                && step.scope().equals("OPPONENT_ACTIVE")));
    }
}
