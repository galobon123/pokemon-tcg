package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.attack.AttackPatternAnalyzer;
import com.utn.pokemontcg.game.domain.attack.AttackSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackPatternAnalyzerTest {

    private final AttackPatternAnalyzer analyzer = new AttackPatternAnalyzer();

    @Test
    void classifiesCombinedCoinStatusAndEnergyDiscardAttack() {
        AttackSpec spec = analyzer.analyze(
            "Poison Powder",
            "Flip a coin. If heads, your opponent's Active Pokemon is now Poisoned. Discard an Energy attached to this Pokemon.",
            60
        );

        assertEquals(1, spec.coinFlipCount());
        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("SELECT_ATTACHED_ENERGY") && step.scope().equals("OWN_ACTIVE")));
        assertTrue(spec.effects().stream().anyMatch(effect -> effect.type().equals("DAMAGE")));
        assertTrue(spec.effects().stream().anyMatch(effect -> effect.type().equals("COIN_FLIP")));
        assertTrue(spec.effects().stream().anyMatch(effect -> effect.type().equals("STATUS")));
        assertTrue(spec.effects().stream().anyMatch(effect -> effect.type().equals("DISCARD_ENERGY")));
    }

    @Test
    void classifiesTargetAndDeckOrderingRequirements() {
        AttackSpec spec = analyzer.analyze(
            "Mind Bend",
            "Choose 1 of your opponent's Benched Pokemon. Look at the top 3 cards of your deck and put them back in any order.",
            20
        );

        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("SELECT_TARGET") && step.scope().equals("OPPONENT_BENCH")));
        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("ORDER_DECK_TOP") && step.count() == 3));
        assertTrue(spec.effects().stream().anyMatch(effect -> effect.type().equals("DAMAGE")));
    }

    @Test
    void classifiesOptionalConditionChoice() {
        AttackSpec spec = analyzer.analyze(
            "Mystery Gas",
            "You may choose either Burned or Poisoned. The Defending Pokemon is now affected by that Special Condition.",
            0
        );

        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("SELECT_CONDITION")));
        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("CONFIRM_OPTIONAL_EFFECT")));
        assertTrue(spec.effects().stream().anyMatch(effect -> effect.type().equals("STATUS")));
    }

    @Test
    void classifiesOpponentDeckTopViewRequirement() {
        AttackSpec spec = analyzer.analyze(
            "Scout",
            "Discard the top card of your opponent's deck.",
            0
        );

        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("VIEW_DECK_TOP") && step.scope().equals("OPPONENT_DECK_TOP")));
        assertTrue(spec.effects().stream().anyMatch(effect -> effect.type().equals("OPPONENT_DECK")));
    }

    @Test
    void classifiesOpponentHandViewRequirement() {
        AttackSpec spec = analyzer.analyze(
            "Look",
            "Look at your opponent's hand.",
            0
        );

        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("VIEW_HAND") && step.scope().equals("OPPONENT_HAND")));
        assertTrue(spec.effects().stream().anyMatch(effect -> effect.type().equals("OPPONENT_HAND")));
    }

    @Test
    void classifiesOpponentHandSelectionRequirement() {
        AttackSpec spec = analyzer.analyze(
            "Sabotear",
            "Elige 1 carta de la mano de tu oponente y descártala.",
            0
        );

        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("SELECT_HAND_CARD") && step.scope().equals("OPPONENT_HAND")));
        assertTrue(spec.effects().stream().anyMatch(effect -> effect.type().equals("OPPONENT_HAND")));
    }

    @Test
    void classifiesOpponentHandConfirmationForRandomEffect() {
        AttackSpec spec = analyzer.analyze(
            "Sleight of Hand",
            "Shuffle a random card from your opponent's hand into their deck.",
            0
        );

        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("CONFIRM_HAND_EFFECT") && step.scope().equals("OPPONENT_HAND")));
        assertTrue(spec.effects().stream().anyMatch(effect -> effect.type().equals("OPPONENT_HAND")));
    }

    @Test
    void doesNotTreatRandomHandDiscardAsSelectable() {
        AttackSpec spec = analyzer.analyze(
            "Tails Gambit",
            "Flip 4 coins. For each tails, discard a random card from your opponent's hand.",
            0
        );

        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("CONFIRM_HAND_EFFECT")));
        assertTrue(spec.requirements().stream().noneMatch(step -> step.type().equals("SELECT_HAND_CARD")));
    }

    @Test
    void classifiesOpponentAttackSelectionForBlock() {
        AttackSpec spec = analyzer.analyze(
            "Smokescreen",
            "Choose 1 of your opponent's attacks. During your opponent's next turn, that attack can't be used.",
            20
        );

        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("SELECT_OPPONENT_ATTACK") && step.scope().equals("OPPONENT_ACTIVE")));
        assertTrue(spec.requirements().stream().noneMatch(step -> step.type().equals("SELECT_TARGET")));
        assertTrue(spec.effects().stream().anyMatch(effect -> effect.type().equals("PERSISTENT_EFFECT")));
    }

    @Test
    void classifiesOpponentAttackSelectionSpanishVariant() {
        AttackSpec spec = analyzer.analyze(
            "Niebla",
            "Elige 1 ataque del Pokemon Activo rival. Durante su proximo turno, no puede usar ese ataque.",
            0
        );

        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("SELECT_OPPONENT_ATTACK")));
    }

    @Test
    void classifiesOpponentAttackSelectionActiveVoice() {
        AttackSpec spec = analyzer.analyze(
            "Torment",
            "Choose 1 of your opponent's Active Pokémon's attacks. That Pokémon can't use that attack during your opponent's next turn.",
            0
        );

        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("SELECT_OPPONENT_ATTACK") && step.scope().equals("OPPONENT_ACTIVE")));
        assertTrue(spec.requirements().stream().noneMatch(step -> step.type().equals("SELECT_TARGET")));
        assertTrue(spec.effects().stream().anyMatch(effect -> effect.type().equals("PERSISTENT_EFFECT")));
    }

    @Test
    void classifiesOpponentAttackSelectionCannotUse() {
        AttackSpec spec = analyzer.analyze(
            "King's Shield",
            "If your opponent's Active Pokémon tries to attack during your opponent's next turn, that Pokémon cannot use that attack.",
            50
        );

        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("SELECT_OPPONENT_ATTACK")));
    }

    @Test
    void doesNotRequireTargetForBenchedPokemonCountDamage() {
        AttackSpec spec = analyzer.analyze(
            "Circle Circuit",
            "This attack does 20 damage times the number of your Benched Pokemon.",
            20
        );

        assertTrue(spec.requirements().stream().noneMatch(step -> step.type().equals("SELECT_TARGET")));
    }

    @Test
    void doesNotPreviewAttachedEnergyDiscardAsEnergyAttach() {
        AttackSpec spec = analyzer.analyze(
            "Flamethrower",
            "Discard an Energy attached to this Pokemon.",
            90
        );

        assertTrue(spec.effects().stream().noneMatch(effect -> effect.type().equals("ATTACH_ENERGY")));
        assertTrue(spec.effects().stream().anyMatch(effect -> effect.type().equals("DISCARD_ENERGY")));
    }

    @Test
    void doesNotRequireEnergySelectionWhenTextMentionsPokemonTool() {
        AttackSpec spec = analyzer.analyze(
            "Joust",
            "Discard a Pokemon Tool card attached to your opponent's Active Pokemon.",
            40
        );

        assertTrue(spec.requirements().stream().noneMatch(step -> step.type().equals("SELECT_ATTACHED_ENERGY")));
    }

    @Test
    void classifiesTopDeckOrderWithFourCards() {
        AttackSpec spec = analyzer.analyze(
            "Scout",
            "Look at the top four cards of your deck and put them back in any order.",
            0
        );
        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("ORDER_DECK_TOP") && step.count() == 4));
    }

    @Test
    void classifiesTopDeckOrderWithFiveCards() {
        AttackSpec spec = analyzer.analyze(
            "Foresight",
            "Look at the top five cards of your deck and put them back in any order.",
            0
        );
        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("ORDER_DECK_TOP") && step.count() == 5));
    }

    @Test
    void classifiesTopDeckOrderSpanishVariant() {
        AttackSpec spec = analyzer.analyze(
            "Vision",
            "Mira las 3 cartas superiores de tu mazo y vuelve a ordenarlas.",
            0
        );
        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("ORDER_DECK_TOP") && step.count() == 3));
    }

    @Test
    void classifiesOpponentDeckTopWithMultipleCardsSpanish() {
        AttackSpec spec = analyzer.analyze(
            "Investigacion",
            "Mira las 2 cartas superiores del mazo de tu oponente y descartalas.",
            0
        );
        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("VIEW_DECK_TOP") && step.count() == 2));
    }

    @Test
    void classifiesSelfHealTargetRequirement() {
        AttackSpec spec = analyzer.analyze(
            "Sweet Scent",
            "Heal 30 damage from 1 of your Pokemon.",
            0
        );
        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("SELECT_TARGET") && step.scope().equals("OWN_POKEMON")));
    }

    @Test
    void doesNotRequireEnergySelectionForAllEnergyDiscard() {
        AttackSpec spec = analyzer.analyze(
            "Crush",
            "Discard all Energy attached to your opponent's Active Pokemon.",
            80
        );
        assertTrue(spec.requirements().stream().noneMatch(step -> step.type().equals("SELECT_ATTACHED_ENERGY")));
    }

    @Test
    void classifiesDeckAndHandSelectionInSpanish() {
        AttackSpec spec = analyzer.analyze(
            "Busqueda",
            "Busca 1 carta de Energia en tu mazo y una cartas de la mano de tu oponente.",
            0
        );
        assertTrue(spec.requirements().stream().anyMatch(step -> step.type().equals("SELECT_HAND_CARD")));
    }
}
