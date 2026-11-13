package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.AttackUtilityEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage tests for {@link AttackUtilityEffectStrategy}.
 *
 * The existing AttackEffectStrategyTest/UncoveredPatternsTest covers most
 * branches, but the following utility patterns remain untested:
 *   - "draw 4 cards" (defender), "draw 2 cards" / "draw a card" (attacker)
 *   - "search your deck" + "put" + "into your hand" with up to 2 / 3 different
 *   - "discard the top card of your opponent" (for each damage counter)
 *   - "discard the top card of your deck" + attach from discard
 *   - isTopDeckOrderText (look at the top X cards of your deck)
 *   - "does N damage to itself" (10/20/30)
 *   - "each player switches", "switch this", "opponent switches"
 *   - "move an energy from this", "put a card from your discard pile on
 *     top of your deck", "put 2 item cards from your discard pile into
 *     your hand", "put 2 damage counters each of your opponent",
 *   - "remaining hp of each... is 10"
 *   - "opponent's hand" discard & shuffle
 *   - "random card from your opponent"
 *   - "for each tails" + "discards a card from"
 *   - "move an energy attached to your opponent" (with acceptedOptional=true)
 */
class AttackUtilityEffectStrategyCoverageTest {

    private static final AttackUtilityEffectStrategy STRATEGY = new AttackUtilityEffectStrategy();

    @Test
    void utilitySupports_returnsTrueForAllKnownPatterns() {
        assertTrue(STRATEGY.supports(ctx(game(), "x", "draw 4 cards")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "draw 2 cards")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "draw a card")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "search your deck for a card and put it into your hand")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "search your deck for up to 2 cards and put them into your hand")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "search your deck for 3 different cards and put them into your hand")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "discard the top card of your opponent's deck")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "discard the top card of your deck. If that card is an Energy, attach it to this Pokemon.")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "look at the top 5 cards of your deck and put them back in any order")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "does 10 damage to itself")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "does 20 damage to itself")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "does 30 damage to itself")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "each player switches their Active Pokemon")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "switch this Pokemon with 1 of your Benched Pokemon")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "your opponent switches their Active Pokemon")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "move an energy from this Pokemon to 1 of your Benched Pokemon")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "put a card from your discard pile on top of your deck")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "put 2 item cards from your discard pile into your hand")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "put 2 damage counters on each of your opponent's Pokemon")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "the remaining HP of each player is 10")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "discard a card from your opponent's hand")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "choose a random card from your opponent's hand and shuffle it into their deck")));
        assertTrue(STRATEGY.supports(ctx(game(), "x", "flip 4 coins. For each tails, your opponent discards a card from their hand")));
    }

    @Test
    void drawFourCards_movesToDefender() {
        GameAggregate game = game();
        game.deck().get(game.playerTwo()).add("d1");
        game.deck().get(game.playerTwo()).add("d2");
        game.deck().get(game.playerTwo()).add("d3");
        game.deck().get(game.playerTwo()).add("d4");
        game.deckCardsRemaining().put(game.playerTwo(), 4);

        STRATEGY.apply(ctx(game, "Hurricane", "The Defending Pokemon: draw 4 cards."));

        assertEquals(4, game.hand().get(game.playerTwo()).size());
        assertTrue(game.deck().get(game.playerTwo()).isEmpty());
    }

    @Test
    void drawTwoCards_movesToAttacker() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).add("a1");
        game.deck().get(game.playerOne()).add("a2");
        game.deckCardsRemaining().put(game.playerOne(), 2);

        STRATEGY.apply(ctx(game, "Research", "The Attacking Pokemon: draw 2 cards."));

        assertEquals(2, game.hand().get(game.playerOne()).size());
    }

    @Test
    void drawACard_movesToAttacker() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).add("a1");
        game.deckCardsRemaining().put(game.playerOne(), 1);

        STRATEGY.apply(ctx(game, "Investigate", "Draw a card."));

        assertEquals(1, game.hand().get(game.playerOne()).size());
    }

    @Test
    void searchYourDeck_putsBasicIntoHand() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).add("basic-deck-1");
        game.registerCard(new GameCard("basic-deck-1", "DeckMon", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.deckCardsRemaining().put(game.playerOne(), 1);

        STRATEGY.apply(ctx(game, "Fetch", "Search your deck for a Pokemon and put it into your hand. Shuffle your deck."));

        assertTrue(game.hand().get(game.playerOne()).contains("basic-deck-1"));
    }

    @Test
    void searchYourDeck_upTo2_pullsTwoCards() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("up2-1", "up2-2", "up2-3"));
        for (String id : List.of("up2-1", "up2-2", "up2-3")) {
            game.registerCard(new GameCard(id, "Mon-" + id, "Pokemon", Set.of("Basic"), 60, 10, 1));
        }
        game.deckCardsRemaining().put(game.playerOne(), 3);
        AttackContext ctx = ctx(game, "Fetch2", "Search your deck for up to 2 Pokemon and put them into your hand.");
        ctx.setSelectedCardIds(List.of("up2-1", "up2-2"));

        STRATEGY.apply(ctx);

        assertTrue(game.hand().get(game.playerOne()).contains("up2-1"));
        assertTrue(game.hand().get(game.playerOne()).contains("up2-2"));
        assertEquals(1, game.deck().get(game.playerOne()).size());
    }

    @Test
    void searchYourDeck_3DifferentEnergy_pullsThree() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("e-a", "e-b", "e-c"));
        for (String id : List.of("e-a", "e-b", "e-c")) {
            game.registerCard(new GameCard(id, "Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        }
        game.deckCardsRemaining().put(game.playerOne(), 3);
        AttackContext ctx = ctx(game, "Tri", "Search your deck for 3 different energy cards and put them into your hand.");
        ctx.setSelectedCardIds(List.of("e-a", "e-b", "e-c"));

        STRATEGY.apply(ctx);

        assertTrue(game.hand().get(game.playerOne()).containsAll(List.of("e-a", "e-b", "e-c")));
    }

    @Test
    void discardTopCardOfOpponent_single() {
        GameAggregate game = game();
        game.deck().get(game.playerTwo()).add("opp-1");
        game.deckCardsRemaining().put(game.playerTwo(), 1);

        STRATEGY.apply(ctx(game, "Snipe", "Discard the top card of your opponent's deck."));

        assertEquals(1, game.discardPile().get(game.playerTwo()).size());
        assertEquals("opp-1", game.discardPile().get(game.playerTwo()).get(0));
    }

    @Test
    void discardTopCardOfOpponent_perDamageCounter() {
        GameAggregate game = game();
        game.deck().get(game.playerTwo()).addAll(List.of("opp-1", "opp-2", "opp-3"));
        game.deckCardsRemaining().put(game.playerTwo(), 3);
        game.addDamageCounters(game.playerOne(), "ACTIVE", 3);

        STRATEGY.apply(ctx(game, "Heavy", "Discard the top card of your opponent's deck for each damage counter on this Pokemon."));

        assertEquals(3, game.discardPile().get(game.playerTwo()).size());
    }

    @Test
    void discardTopCardOfYourDeck_andAttachEnergyFromIt() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).add("energy-top");
        game.registerCard(new GameCard("energy-top", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.deckCardsRemaining().put(game.playerOne(), 1);

        STRATEGY.apply(ctx(game, "Recover", "Discard the top card of your deck. If that card is a Fire Energy, attach it to this Pokemon."));

        // The card should be attached, not discarded.
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
        assertEquals(0, game.discardPile().getOrDefault(game.playerOne(), new ArrayList<>()).size());
    }

    @Test
    void isTopDeckOrderText_reordered() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("t1", "t2", "t3"));
        for (String id : List.of("t1", "t2", "t3")) {
            game.registerCard(new GameCard(id, "T-" + id, "Pokemon", Set.of("Basic"), 60, 10, 1));
        }
        game.deckCardsRemaining().put(game.playerOne(), 3);
        AttackContext ctx = ctx(game, "Peek", "Look at the top 3 cards of your deck and put them back in any order.");
        ctx.setSelectedCardIds(List.of("t1", "t2", "t3"));

        STRATEGY.apply(ctx);

        // The deck ordering changed/audited — at minimum the audit must
        // include a deck_top_reordered entry.
        assertTrue(ctx.auditTrail().stream().anyMatch(s -> s.contains("deck_top_reordered")));
    }

    @Test
    void doesTenDamageToItself_addsDamageCounter() {
        GameAggregate game = game();
        STRATEGY.apply(ctx(game, "Pain", "Does 10 damage to itself."));
        assertEquals(1, game.damageCountersAtSlot(game.playerOne(), "ACTIVE"));
    }

    @Test
    void doesTwentyDamageToItself_addsTwoDamageCounters() {
        GameAggregate game = game();
        STRATEGY.apply(ctx(game, "Pain2", "Does 20 damage to itself."));
        assertEquals(2, game.damageCountersAtSlot(game.playerOne(), "ACTIVE"));
    }

    @Test
    void doesThirtyDamageToItself_addsThreeDamageCounters() {
        GameAggregate game = game();
        STRATEGY.apply(ctx(game, "Pain3", "Does 30 damage to itself."));
        assertEquals(3, game.damageCountersAtSlot(game.playerOne(), "ACTIVE"));
    }

    @Test
    void eachPlayerSwitches_swapsActives() {
        GameAggregate game = game();
        // Add a bench pokemon for both players so swap has a target.
        String p1Bench = "p1-bench";
        String p2Bench = "p2-bench";
        game.registerCard(new GameCard(p1Bench, "P1Bench", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard(p2Bench, "P2Bench", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.bench().get(game.playerOne()).add(p1Bench);
        game.bench().get(game.playerTwo()).add(p2Bench);

        STRATEGY.apply(ctx(game, "Switcheroo", "Each player switches their Active Pokemon with one of their Benched Pokemon."));

        // After both swaps, the original active pokes end up on the bench
        // (or the other way around); the new actives are the bench ones.
        assertEquals(p1Bench, game.activePokemon().get(game.playerOne()));
        assertEquals(p2Bench, game.activePokemon().get(game.playerTwo()));
    }

    @Test
    void switchThis_movesAttackerActiveToBench() {
        GameAggregate game = game();
        String benchId = "p1-bench-switch";
        game.registerCard(new GameCard(benchId, "P1BenchS", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.bench().get(game.playerOne()).add(benchId);

        STRATEGY.apply(ctx(game, "Shift", "Switch this Pokemon with 1 of your Benched Pokemon."));

        assertEquals(benchId, game.activePokemon().get(game.playerOne()));
    }

    @Test
    void opponentSwitches_marksForcedSwitch() {
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).add("opp-bench");
        game.registerCard(new GameCard("opp-bench", "OppBench", "Pokemon", Set.of("Basic"), 60, 10, 1));

        STRATEGY.apply(ctx(game, "Force", "Your opponent switches their Active Pokemon with 1 of their Benched Pokemon."));

        assertEquals(game.playerTwo(), game.pendingForcedSwitchPlayerId());
    }

    @Test
    void moveAnEnergyFromThis_attackerToBench() {
        GameAggregate game = game();
        String benchId = "p1-bench-energy";
        game.registerCard(new GameCard(benchId, "P1BenchE", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.bench().get(game.playerOne()).add(benchId);
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        AttackContext ctx = ctx(game, "Recycle", "Move an energy from this Pokemon to 1 of your Benched Pokemon.");
        ctx.setSelectedEnergyCardIds(List.of("e1"));

        STRATEGY.apply(ctx);

        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
    }

    @Test
    void putACardFromDiscardPileOnTopOfDeck_moves() {
        GameAggregate game = game();
        game.discardPile().computeIfAbsent(game.playerOne(), ignored -> new ArrayList<>()).add("rec-1");

        STRATEGY.apply(ctx(game, "Salvage", "Put a card from your discard pile on top of your deck."));

        assertEquals("rec-1", game.deck().get(game.playerOne()).get(0));
        assertTrue(game.discardPile().get(game.playerOne()).isEmpty());
    }

    @Test
    void putTwoItemCardsFromDiscardPileIntoHand_movesItems() {
        GameAggregate game = game();
        game.discardPile().computeIfAbsent(game.playerOne(), ignored -> new ArrayList<>()).addAll(List.of("item-1", "item-2"));
        game.registerCard(new GameCard("item-1", "Potion", "Trainer", Set.of("Item"), 0, 0, 0));
        game.registerCard(new GameCard("item-2", "Switch", "Trainer", Set.of("Item"), 0, 0, 0));

        STRATEGY.apply(ctx(game, "Recover Items", "Put 2 item cards from your discard pile into your hand."));

        assertTrue(game.hand().get(game.playerOne()).containsAll(List.of("item-1", "item-2")));
    }

    @Test
    void putTwoDamageCountersEachOfOpponent_damagesActiveAndBench() {
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).add("opp-bench-1");
        game.registerCard(new GameCard("opp-bench-1", "OppB", "Pokemon", Set.of("Basic"), 60, 10, 1));

        STRATEGY.apply(ctx(game, "Splash", "Put 2 damage counters each of your opponent's Pokemon."));

        assertEquals(2, game.damageCountersAtSlot(game.playerTwo(), "ACTIVE"));
        assertEquals(2, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
    }

    @Test
    void remainingHpOfEachIs10_lowersBothActivesTo10() {
        GameAggregate game = game();
        // Set starting HPs higher so we can observe the reduction.
        game.activeHp().put(game.playerOne(), 100);
        game.activeHp().put(game.playerTwo(), 100);

        STRATEGY.apply(ctx(game, "EndGame", "The remaining HP of each player is 10."));

        assertEquals(10, game.activeHp().get(game.playerOne()));
        assertEquals(10, game.activeHp().get(game.playerTwo()));
    }

    @Test
    void opponentHandDiscard_selectedCardMovesToDiscard() {
        GameAggregate game = game();
        game.hand().get(game.playerTwo()).add("opp-hand-1");
        AttackContext ctx = ctx(game, "Robbery", "Discard a card from your opponent's hand.");
        ctx.setSelectedCardIds(List.of("opp-hand-1"));

        STRATEGY.apply(ctx);

        assertEquals(1, game.discardPile().getOrDefault(game.playerTwo(), new ArrayList<>()).size());
    }

    @Test
    void randomCardFromOpponent_movesHandCardIntoDeck() {
        GameAggregate game = game();
        game.hand().get(game.playerTwo()).add("opp-hand-rand");
        game.deck().get(game.playerTwo()).add("opp-deck-1");
        game.deckCardsRemaining().put(game.playerTwo(), 1);

        STRATEGY.apply(ctx(game, "Snatch", "Choose a random card from your opponent's hand and shuffle it into their deck."));

        assertTrue(game.hand().get(game.playerTwo()).isEmpty());
        assertEquals(2, game.deck().get(game.playerTwo()).size());
    }

    @Test
    void forEachTailsDiscardsOpponentHand_discardsTails() {
        GameAggregate game = game();
        game.hand().get(game.playerTwo()).addAll(List.of("h1", "h2", "h3", "h4"));
        // Two tails out of four flips → two discards. discardOpponentHandForTails
        // uses the coinFlip supplier, not the results list.
        java.util.ArrayDeque<Boolean> queue = new java.util.ArrayDeque<>(List.of(false, false, true, true));
        AttackContext ctx = ctx(game, "Burn", "Flip 4 coins. For each tails, your opponent discards a card from their hand.");
        ctx.setCoinFlip(queue::removeFirst);

        STRATEGY.apply(ctx);

        assertEquals(2, game.discardPile().getOrDefault(game.playerTwo(), new ArrayList<>()).size());
    }

    @Test
    void moveAnEnergyFromOpponent_acceptedOptionalTrue() {
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).add("def-bench");
        game.registerCard(new GameCard("def-bench", "DefBench", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.attachEnergy(game.playerTwo(), "ACTIVE", new AttachedEnergy("opp-e1", "Fire"));
        AttackContext ctx = ctx(game, "Steal", "Move an energy attached to your opponent's Active Pokemon to 1 of their Benched Pokemon.");
        ctx.setAcceptedOptionalEffect(Boolean.TRUE);
        ctx.setSelectedEnergyCardIds(List.of("opp-e1"));

        STRATEGY.apply(ctx);

        assertEquals(0, game.attachedEnergyCount(game.playerTwo(), "ACTIVE"));
        assertEquals(1, game.attachedEnergyCount(game.playerTwo(), "BENCH_0"));
    }

    @Test
    void moveAnEnergyFromOpponent_acceptedOptionalFalse_skips() {
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).add("def-bench-2");
        game.registerCard(new GameCard("def-bench-2", "DefBench2", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.attachEnergy(game.playerTwo(), "ACTIVE", new AttachedEnergy("opp-e2", "Fire"));
        AttackContext ctx = ctx(game, "Steal", "Move an energy attached to your opponent's Active Pokemon to 1 of their Benched Pokemon.");
        ctx.setAcceptedOptionalEffect(Boolean.FALSE);

        STRATEGY.apply(ctx);

        // Energy stays on the active.
        assertEquals(1, game.attachedEnergyCount(game.playerTwo(), "ACTIVE"));
    }

    @Test
    void utilityAppliedAudit_isAlwaysAppended() {
        GameAggregate game = game();
        AttackContext ctx = ctx(game, "Misc", "Draw a card.");
        game.deck().get(game.playerOne()).add("audit-1");
        game.deckCardsRemaining().put(game.playerOne(), 1);

        STRATEGY.apply(ctx);

        assertTrue(ctx.auditTrail().stream().anyMatch(s -> s.contains("utility_applied")));
    }

    @Test
    void utilityCancelled_skipsAllEffects() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).add("c-1");
        game.deckCardsRemaining().put(game.playerOne(), 1);
        AttackContext ctx = ctx(game, "X", "Draw a card.");
        ctx.setCancelled(true);

        STRATEGY.apply(ctx);

        assertEquals(0, game.hand().get(game.playerOne()).size());
    }

    @Test
    void supports_opponentHandShuffleText() {
        GameAggregate game = game();
        // Add a single card to the hand so the "shuffle" branch is engaged.
        game.hand().get(game.playerTwo()).add("shuf-1");
        AttackContext ctx = ctx(game, "Confusion",
            "Shuffle a card from your opponent's hand into their deck.");
        ctx.setSelectedCardIds(List.of("shuf-1"));
        assertTrue(STRATEGY.supports(ctx));
        STRATEGY.apply(ctx);
        // The card should be in the deck and not in the hand.
        assertTrue(game.hand().get(game.playerTwo()).isEmpty());
        assertTrue(game.deck().get(game.playerTwo()).contains("shuf-1"));
    }

    private static AttackContext ctx(GameAggregate game, String name, String text) {
        AttackContext ctx = new AttackContext(game);
        ctx.setAttackName(name);
        ctx.setAttackText(text);
        return ctx;
    }

    private static GameAggregate game() {
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

    @SuppressWarnings("unused")
    private static AttackContext assertNotNullCtx(AttackContext ctx) {
        assertNotNull(ctx);
        return ctx;
    }
}
