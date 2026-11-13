package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.chain.EnergyValidationStep;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FinalCoveragePushTest {

    // === AttackUtilityEffectStrategy uncovered paths ===

    @Test
    void utility_shuffleOpponentHandIntoDeckSelected() {
        GameAggregate game = game();
        game.hand().get(game.playerTwo()).add("h1");
        AttackContext context = context(game, "Red Card",
            "Choose a card from your opponent's hand and shuffle it into your opponent's deck.");
        context.setSelectedCardIds(List.of("h1"));
        new AttackUtilityEffectStrategy().apply(context);
        assertTrue(game.hand().get(game.playerTwo()).isEmpty());
    }

    @Test
    void utility_searchDeckAndPutIntoHandByType() {
        GameAggregate game = game();
        game.registerCard(new GameCard("poke-1", "Charmander", "Pokemon", Set.of("Basic"), 50, 10, 1));
        game.deck().get(game.playerOne()).add("poke-1");
        AttackContext context = context(game, "Pokemon Search",
            "Search your deck for a Pokemon and put it into your hand.");
        context.setSelectedCardIds(List.of("poke-1"));
        new AttackUtilityEffectStrategy().apply(context);
        assertEquals(1, game.hand().get(game.playerOne()).size());
    }

    @Test
    void utility_searchDeckWithoutSelectedCardsPicksFirst() {
        GameAggregate game = game();
        game.registerCard(new GameCard("poke-1", "Charmander", "Pokemon", Set.of("Basic"), 50, 10, 1));
        game.deck().get(game.playerOne()).add("poke-1");
        AttackContext context = context(game, "Pokemon Search",
            "Search your deck for a Pokemon and put it into your hand.");
        new AttackUtilityEffectStrategy().apply(context);
        assertEquals(1, game.hand().get(game.playerOne()).size());
    }

    @Test
    void utility_searchDeckForSupporter() {
        GameAggregate game = game();
        game.registerCard(new GameCard("supp-1", "Shauna", "Trainer", Set.of("Supporter"), 0, 0, 0));
        game.deck().get(game.playerOne()).add("supp-1");
        AttackContext context = context(game, "Supporter Search",
            "Search your deck for a Supporter and put it into your hand.");
        context.setSelectedCardIds(List.of("supp-1"));
        new AttackUtilityEffectStrategy().apply(context);
        assertEquals(1, game.hand().get(game.playerOne()).size());
    }

    @Test
    void utility_searchDeckForEnergy() {
        GameAggregate game = game();
        game.registerCard(new GameCard("fire-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.deck().get(game.playerOne()).add("fire-1");
        AttackContext context = context(game, "Energy Search",
            "Search your deck for an Energy and put it into your hand.");
        context.setSelectedCardIds(List.of("fire-1"));
        new AttackUtilityEffectStrategy().apply(context);
        assertEquals(1, game.hand().get(game.playerOne()).size());
    }

    @Test
    void utility_returnItemCardsFromDiscard() {
        GameAggregate game = game();
        game.registerCard(new GameCard("item-1", "Potion", "Trainer", Set.of("Item"), 0, 0, 0));
        game.discardPile().get(game.playerOne()).add("item-1");
        AttackContext context = context(game, "Item Retrieval",
            "Put 2 Item cards from your discard pile into your hand.");
        new AttackUtilityEffectStrategy().apply(context);
        assertEquals(1, game.hand().get(game.playerOne()).size());
    }

    @Test
    void utility_remainingHpTo10() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerOne(), "ACTIVE", 0);
        game.registerCard(new GameCard("active-1", "Attacker", "Pokemon", Set.of("Basic"), 100, 10, 1));
        AttackContext context = context(game, "Final Blow",
            "If the remaining HP of each player's Active Pokemon is 10, this attack does 100 more damage.");
        new AttackUtilityEffectStrategy().apply(context);
    }

    @Test
    void utility_topDeckOrderTest() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("c1", "c2", "c3"));
        AttackContext context = context(game, "Arrange",
            "Look at the top 3 cards of your deck and put them back in any order.");
        context.setSelectedCardIds(List.of("c1", "c2", "c3"));
        new AttackUtilityEffectStrategy().apply(context);
        assertEquals(3, game.deck().get(game.playerOne()).size());
    }

    // === PersistentAttackEffectStrategy uncovered paths ===

    @Test
    void persistent_cannotRetreatDuringOpponent() {
        GameAggregate game = game();
        game.setTurnNumber(3);
        AttackContext context = context(game, "Root", "The Defending Pokemon can't retreat during your opponent's next turn.");
        context.setAttackIndex(0);
        new PersistentAttackEffectStrategy().apply(context);
        assertTrue(game.hasActiveEffect(game.playerTwo(), "CANNOT_RETREAT"));
    }

    @Test
    void persistent_cannotRetreatSelf() {
        GameAggregate game = game();
        game.setTurnNumber(3);
        AttackContext context = context(game, "Iron Wall", "This Pokemon can't retreat during your next turn.");
        context.setAttackIndex(0);
        new PersistentAttackEffectStrategy().apply(context);
        assertTrue(game.hasActiveEffect(game.playerOne(), "CANNOT_RETREAT"));
    }

    @Test
    void persistent_supporterLock() {
        GameAggregate game = game();
        game.setTurnNumber(3);
        AttackContext context = context(game, "Silence", "Your opponent can't play any Supporter card during his or her next turn.");
        new PersistentAttackEffectStrategy().apply(context);
        assertTrue(game.hasActiveEffect(game.playerTwo(), "SUPPORTER_LOCK"));
    }

    @Test
    void persistent_abilityLock() {
        GameAggregate game = game();
        game.setTurnNumber(3);
        AttackContext context = context(game, "Neutralize", "Your opponent's Active Pokemon has no Abilities during your opponent's next turn.");
        new PersistentAttackEffectStrategy().apply(context);
        assertTrue(game.hasActiveEffect(game.playerTwo(), "ABILITY_LOCK"));
    }

    // === CoinBonusDamageAttackEffectStrategy ===

    @Test
    void coinBonus_withCoinConditionHeads() {
        AttackContext context = context(game(), "Bonus",
            "Flip a coin. If heads, this attack does 20 more damage.");
        context.setCoinFlip(sequence(true));
        context.setCoinFlipResults(List.of());
        new CoinBonusDamageAttackEffectStrategy(() -> true).apply(context);
        assertEquals(20, context.preAttackBonus());
    }

    @Test
    void coinBonus_headsConditionWithTailsResult() {
        AttackContext context = context(game(), "Bonus",
            "Flip a coin. If heads, this attack does 20 more damage.");
        context.setCoinFlip(sequence(false));
        context.setCoinFlipResults(List.of());
        new CoinBonusDamageAttackEffectStrategy(() -> false).apply(context);
        assertEquals(0, context.preAttackBonus());
    }

    // === EnergyDiscardAttackEffectStrategy uncovered paths ===

    @Test
    void energyDiscard_optionalDeclined() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        AttackContext context = context(game, "Optional Discard",
            "You may discard an Energy attached to this Pokemon. If you do, this attack does 30 more damage.");
        context.setAcceptedOptionalEffect(false);
        new EnergyDiscardAttackEffectStrategy().apply(context);
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
    }

    @Test
    void energyDiscard_requiredType() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("fire-1", "Fire"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("water-1", "Water"));
        AttackContext context = context(game, "Fire Discard",
            "Discard a [R] Energy attached to this Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(context);
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
    }

    @Test
    void energyDiscard_skipWhenNoEnergy() {
        GameAggregate game = game();
        AttackContext context = context(game, "Discard", "Discard an Energy attached to this Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(context);
    }

    // === EnergyValidationStep uncovered paths ===

    @Test
    void energyValidation_skipsWhenCancelled() {
        AttackContext context = context(game(), "Test", "Does nothing.");
        context.setCancelled(true);
        assertDoesNotThrow(() -> new EnergyValidationStep().execute(context));
    }

    @Test
    void energyValidation_passesWhenEnoughEnergy() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        AttackContext context = context(game, "Test", "Does 10 damage.");
        context.setRequiredEnergy(1);
        context.setAttachedEnergy(1);
        assertDoesNotThrow(() -> new EnergyValidationStep().execute(context));
    }

    @Test
    void energyValidatio_cancelsWhenInsufficientEnergy() {
        AttackContext context = context(game(), "Test", "Does 10 damage.");
        context.setRequiredEnergy(2);
        context.setAttachedEnergy(1);
        new EnergyValidationStep().execute(context);
        assertTrue(context.cancelled());
    }

    @Test
    void energyValidation_passesWithExactCost() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e2", "Colorless"));
        AttackContext context = context(game, "Test", "Does 10 damage.");
        context.setRequiredEnergyCost(List.of("Fire", "Colorless"));
        context.setAttachedEnergies(game.attachedEnergiesFor(game.playerOne(), "ACTIVE"));
        assertDoesNotThrow(() -> new EnergyValidationStep().execute(context));
    }

    @Test
    void energyValidation_passesWithRainbowEnergy() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("rain-1", "Rainbow"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("rain-2", "Rainbow"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("fire-1", "Fire"));
        AttackContext context = context(game, "Test", "Does 10 damage.");
        context.setRequiredEnergyCost(List.of("Fire", "Water", "Grass"));
        context.setAttachedEnergies(game.attachedEnergiesFor(game.playerOne(), "ACTIVE"));
        assertDoesNotThrow(() -> new EnergyValidationStep().execute(context));
    }

    @Test
    void energyValidation_failsWithMissingColorless() {
        AttackContext context = context(game(), "Test", "Does 10 damage.");
        context.setRequiredEnergyCost(List.of("Fire", "Colorless", "Colorless"));
        context.setAttachedEnergies(List.of());
        assertThrows(com.utn.pokemontcg.game.domain.exception.InsufficientEnergyException.class,
            () -> new EnergyValidationStep().execute(context));
    }

    @Test
    void energyValidation_failsWithMissingSpecificType() {
        AttackContext context = context(game(), "Test", "Does 10 damage.");
        context.setRequiredEnergyCost(List.of("Fire", "Water"));
        context.setAttachedEnergies(List.of(new AttachedEnergy("e1", "Fire")));
        assertThrows(com.utn.pokemontcg.game.domain.exception.InsufficientEnergyException.class,
            () -> new EnergyValidationStep().execute(context));
    }

    private java.util.function.BooleanSupplier sequence(boolean... values) {
        java.util.ArrayDeque<Boolean> queue = new java.util.ArrayDeque<>();
        for (boolean value : values) queue.add(value);
        return queue::removeFirst;
    }

    private AttackContext context(GameAggregate game, String attackName, String attackText) {
        AttackContext context = new AttackContext(game);
        context.setAttackName(attackName);
        context.setAttackText(attackText);
        context.setAttachedEnergies(game.attachedEnergiesFor(game.currentTurnPlayer(), "ACTIVE"));
        return context;
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
