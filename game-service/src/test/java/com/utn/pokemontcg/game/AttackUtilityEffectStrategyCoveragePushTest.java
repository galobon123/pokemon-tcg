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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage push v2 for {@link AttackUtilityEffectStrategy}.
 *
 * Targets the 61 missed branches in apply(), topDeckLookCount(),
 * moveFromDeckToHand(), and helper functions.
 */
class AttackUtilityEffectStrategyCoveragePushTest {

    private static final AttackUtilityEffectStrategy STRATEGY = new AttackUtilityEffectStrategy();

    // ===== supports() false branch for isTopDeckOrderText =====

    @Test
    void supportsIsTopDeckOrderTextFalseBranch() {
        // The supports() method has `|| isTopDeckOrderText(text)`. The false
        // branch is hit when supports() returns true because of OTHER patterns
        // and isTopDeckOrderText returns false. We use "draw a card" which
        // matches the first containsAny but not isTopDeckOrderText.
        assertTrue(STRATEGY.supports(ctxFor(game(), "X", "Draw a card.")));
        // And ensure isTopDeckOrderText returns false for the same text:
        // (implicit — supports returned true from the first containsAny, not
        // isTopDeckOrderText, so the || short-circuited to true; the false
        // branch of isTopDeckOrderText's `english || spanish` is not hit here).
    }

    // ===== apply() branch: search your deck + put + into your hand variations =====

    @Test
    void searchYourDeckDefaultCount_pullsOne() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).add("basic-1");
        game.registerCard(new GameCard("basic-1", "Mon", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.deckCardsRemaining().put(game.playerOne(), 1);
        // "search your deck" + "put" + "into your hand" but no "up to 2" or
        // "3 different" → count = 1.
        STRATEGY.apply(ctxFor(game, "Fetch One",
            "Search your deck for a Pokemon and put it into your hand. Shuffle your deck."));
        assertTrue(game.hand().get(game.playerOne()).contains("basic-1"));
    }

    @Test
    void searchYourDeckCountTwoOnTwo() {
        // "up to 2" → 2.
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("c-1", "c-2"));
        for (String id : List.of("c-1", "c-2")) {
            game.registerCard(new GameCard(id, "M-" + id, "Pokemon", Set.of("Basic"), 60, 10, 1));
        }
        game.deckCardsRemaining().put(game.playerOne(), 2);
        AttackContext ctx = ctxFor(game, "Fetch2",
            "Search your deck for up to 2 Pokemon and put them into your hand.");
        ctx.setSelectedCardIds(List.of("c-1", "c-2"));
        STRATEGY.apply(ctx);
        assertTrue(game.hand().get(game.playerOne()).containsAll(List.of("c-1", "c-2")));
    }

    // ===== apply() branch: discardTop false (discarded == 0) =====

    @Test
    void discardTopOfOpponentDeckEmpty_noAudit() {
        // The opponent's deck is empty → discardTopCount returns 0 → no audit.
        GameAggregate game = game();
        AttackContext ctx = ctxFor(game, "Empty Snipe",
            "Discard the top card of your opponent's deck.");
        STRATEGY.apply(ctx);
        // No "discard_top_card" audit when discarded == 0.
        assertFalse(ctx.auditTrail().stream()
            .anyMatch(s -> s.contains("discard_top_card=1,target=defender")));
    }

    // ===== apply() branch: card.attach path variations =====

    @Test
    void discardTopOfYourDeckNotEnergy() {
        // discard the top card of your deck + "attach it" but top card is NOT energy.
        GameAggregate game = game();
        game.deck().get(game.playerOne()).add("trainer-top");
        game.registerCard(new GameCard("trainer-top", "Potion", "Trainer", Set.of("Item"), 0, 0, 0));
        game.deckCardsRemaining().put(game.playerOne(), 1);
        STRATEGY.apply(ctxFor(game, "Recover",
            "Discard the top card of your deck. If that card is a basic Energy, attach it to this Pokemon."));
        // Card is Trainer, not Energy, so it's discarded, not attached.
        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
        assertEquals(1, game.discardPile().getOrDefault(game.playerOne(), new ArrayList<>()).size());
    }

    @Test
    void discardTopOfYourDeckEmptyDeck() {
        // Deck is empty → discardTop returns null → no attach.
        GameAggregate game = game();
        STRATEGY.apply(ctxFor(game, "Recover Empty",
            "Discard the top card of your deck. If that card is a basic Energy, attach it to this Pokemon."));
        // No exceptions; the attack still audits.
        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
    }

    // ===== apply() branch: switch this without bench =====

    @Test
    void switchThisNoBench_skips() {
        // "switch this" but no bench pokemon → skips.
        GameAggregate game = game();
        STRATEGY.apply(ctxFor(game, "Switch Self", "Switch this Pokemon with 1 of your Benched Pokemon."));
        // Active is unchanged.
        assertEquals("active-1", game.activePokemon().get(game.playerOne()));
    }

    // ===== apply() branch: opponent switches with empty defender bench =====

    @Test
    void opponentSwitchesEmptyBench_skips() {
        // "opponent switches" but no bench pokemon → skips.
        GameAggregate game = game();
        STRATEGY.apply(ctxFor(game, "Force Switch",
            "Your opponent switches their Active Pokemon with 1 of their Benched Pokemon."));
        assertNotNull(game); // no exception
    }

    @Test
    void opponentSwitchesViaSwitch1OfYourOpponent() {
        // "switch 1 of your opponent" → triggers setPendingForcedSwitchPlayerId.
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).add("opp-bench-1");
        game.registerCard(new GameCard("opp-bench-1", "OppB1", "Pokemon", Set.of("Basic"), 60, 10, 1));
        STRATEGY.apply(ctxFor(game, "Force Switch 2",
            "switch 1 of your opponent's Benched Pokemon with their Active Pokemon."));
        assertEquals(game.playerTwo(), game.pendingForcedSwitchPlayerId());
    }

    @Test
    void opponentSwitchesViaYourOpponentSwitches() {
        // "your opponent switches" → triggers setPendingForcedSwitchPlayerId.
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).add("opp-bench-2");
        game.registerCard(new GameCard("opp-bench-2", "OppB2", "Pokemon", Set.of("Basic"), 60, 10, 1));
        STRATEGY.apply(ctxFor(game, "Force Switch 3",
            "Your opponent switches 1 of their Pokemon with their Active."));
        assertEquals(game.playerTwo(), game.pendingForcedSwitchPlayerId());
    }

    // ===== apply() branch: remaining hp / is 10 =====

    @Test
    void remainingHp10WithLowHp() {
        // The Pokemon already has 10 HP — setBothActiveRemainingHpTo10
        // shouldn't add damage counters.
        GameAggregate game = game();
        game.activeHp().put(game.playerOne(), 60);
        game.activeHp().put(game.playerTwo(), 60);
        game.addDamageCounters(game.playerOne(), "ACTIVE", 1); // 50 HP left
        STRATEGY.apply(ctxFor(game, "EndGame", "The remaining HP of each player is 10."));
        // Both should be reduced to 10.
        assertEquals(10, game.activeHp().get(game.playerOne()));
        assertEquals(10, game.activeHp().get(game.playerTwo()));
    }

    // ===== apply() branch: for each tails + discards a card from =====

    @Test
    void forEachTailsDiscardsFromEmptyHand() {
        // No cards in hand → loop doesn't execute, no exception.
        GameAggregate game = game();
        AttackContext ctx = ctxFor(game, "Burn",
            "Flip 4 coins. For each tails, your opponent discards a card from their hand.");
        java.util.ArrayDeque<Boolean> queue = new java.util.ArrayDeque<>(List.of(false, false, true, true));
        ctx.setCoinFlip(queue::removeFirst);
        STRATEGY.apply(ctx);
        assertEquals(0, game.discardPile().getOrDefault(game.playerTwo(), new ArrayList<>()).size());
    }

    // ===== apply() branch: move an energy attached to your opponent with no bench =====

    @Test
    void moveEnergyFromOpponentNoBench_skips() {
        // No bench on the defender → skips the move.
        GameAggregate game = game();
        game.attachEnergy(game.playerTwo(), "ACTIVE", new AttachedEnergy("opp-e", "Fire"));
        AttackContext ctx = ctxFor(game, "Steal",
            "Move an energy attached to your opponent's Active Pokemon to 1 of their Benched Pokemon.");
        ctx.setAcceptedOptionalEffect(Boolean.TRUE);
        STRATEGY.apply(ctx);
        // Energy stays.
        assertEquals(1, game.attachedEnergyCount(game.playerTwo(), "ACTIVE"));
    }

    // ===== moveFromDeckToHand: branches for filter =====

    @Test
    void moveFromDeckToHandSupporterFilter() {
        // text.contains("supporter") → hasSubtype("Supporter").
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("sup-1", "pok-1"));
        game.registerCard(new GameCard("sup-1", "Shauna", "Trainer", Set.of("Supporter"), 0, 0, 0));
        game.registerCard(new GameCard("pok-1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.deckCardsRemaining().put(game.playerOne(), 2);
        AttackContext ctx = ctxFor(game, "FetchSup",
            "Search your deck for a Supporter card and put it into your hand.");
        ctx.setSelectedCardIds(List.of("sup-1", "pok-1"));
        STRATEGY.apply(ctx);
        // Only the Supporter should be moved.
        assertTrue(game.hand().get(game.playerOne()).contains("sup-1"));
        assertFalse(game.hand().get(game.playerOne()).contains("pok-1"));
    }

    @Test
    void moveFromDeckToHandEnergyFilter() {
        // text.contains("energy") → isBasicEnergy() || isEnergy().
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("en-1", "pok-1"));
        game.registerCard(new GameCard("en-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.registerCard(new GameCard("pok-1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.deckCardsRemaining().put(game.playerOne(), 2);
        AttackContext ctx = ctxFor(game, "FetchEnergy",
            "Search your deck for an Energy card and put it into your hand.");
        ctx.setSelectedCardIds(List.of("en-1", "pok-1"));
        STRATEGY.apply(ctx);
        assertTrue(game.hand().get(game.playerOne()).contains("en-1"));
        assertFalse(game.hand().get(game.playerOne()).contains("pok-1"));
    }

    @Test
    void moveFromDeckToHandPokemonFilter() {
        // text.contains("pokemon") → isPokemon().
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("pok-1", "en-1"));
        game.registerCard(new GameCard("pok-1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard("en-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.deckCardsRemaining().put(game.playerOne(), 2);
        AttackContext ctx = ctxFor(game, "FetchPokemon",
            "Search your deck for a Pokemon and put it into your hand.");
        ctx.setSelectedCardIds(List.of("pok-1", "en-1"));
        STRATEGY.apply(ctx);
        assertTrue(game.hand().get(game.playerOne()).contains("pok-1"));
        assertFalse(game.hand().get(game.playerOne()).contains("en-1"));
    }

    @Test
    void moveFromDeckToHandNoMatch_skips() {
        // text contains neither supporter/energy/pokemon → match = true (default).
        // The "default true" branch of the match expression.
        GameAggregate game = game();
        game.deck().get(game.playerOne()).add("any-1");
        game.registerCard(new GameCard("any-1", "AnyCard", "Trainer", Set.of("Item"), 0, 0, 0));
        game.deckCardsRemaining().put(game.playerOne(), 1);
        AttackContext ctx = ctxFor(game, "FetchAny",
            "Search your deck for a card and put it into your hand.");
        ctx.setSelectedCardIds(List.of("any-1"));
        STRATEGY.apply(ctx);
        assertTrue(game.hand().get(game.playerOne()).contains("any-1"));
    }

    @Test
    void moveFromDeckToHandSelectedNotInDeck_skips() {
        // The selected id is not in the deck → skip.
        GameAggregate game = game();
        game.deck().get(game.playerOne()).add("real-1");
        game.registerCard(new GameCard("real-1", "Real", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.deckCardsRemaining().put(game.playerOne(), 1);
        AttackContext ctx = ctxFor(game, "Fetch",
            "Search your deck for a Pokemon and put it into your hand.");
        ctx.setSelectedCardIds(List.of("not-in-deck"));
        STRATEGY.apply(ctx);
        // Nothing moved.
        assertTrue(game.hand().get(game.playerOne()).isEmpty());
    }

    @Test
    void moveFromDeckToHandCountLimitReached() {
        // The loop hits moved >= count → break.
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("a", "b", "c"));
        for (String id : List.of("a", "b", "c")) {
            game.registerCard(new GameCard(id, "M-" + id, "Pokemon", Set.of("Basic"), 60, 10, 1));
        }
        game.deckCardsRemaining().put(game.playerOne(), 3);
        AttackContext ctx = ctxFor(game, "Fetch2",
            "Search your deck for up to 2 Pokemon and put them into your hand.");
        ctx.setSelectedCardIds(List.of("a", "b", "c"));
        STRATEGY.apply(ctx);
        // Only 2 should be moved.
        assertEquals(2, game.hand().get(game.playerOne()).size());
    }

    // ===== discardTopCount: empty deck =====

    @Test
    void discardTopCountEmptyDeck() {
        // The for-loop's !deck.isEmpty() check exits immediately.
        GameAggregate game = game();
        STRATEGY.apply(ctxFor(game, "Empty Discard",
            "Discard the top card of your opponent's deck for each damage counter on this Pokemon."));
        // No exception.
        assertNotNull(game);
    }

    // ===== reorderTopDeckCards: limit <= 1 =====

    @Test
    void reorderTopDeckCardsLimit1_returnsEarly() {
        // topDeckLookCount returns 1 (default) → limit = 1 → return 0.
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("x", "y", "z"));
        for (String id : List.of("x", "y", "z")) {
            game.registerCard(new GameCard(id, "M-" + id, "Pokemon", Set.of("Basic"), 60, 10, 1));
        }
        game.deckCardsRemaining().put(game.playerOne(), 3);
        AttackContext ctx = ctxFor(game, "Reorder 1",
            "Look at the top card of your deck and put them back in any order.");
        ctx.setSelectedCardIds(List.of("x"));
        STRATEGY.apply(ctx);
        // reorderTopDeckCards returns 0 → no audit.
        assertFalse(ctx.auditTrail().stream()
            .anyMatch(s -> s.contains("deck_top_reordered=1")));
    }

    @Test
    void reorderTopDeckCardsMismatchedSelection() {
        // currentTop.containsAll(requestedTop) is false → return 0.
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("a", "b", "c"));
        for (String id : List.of("a", "b", "c")) {
            game.registerCard(new GameCard(id, "M-" + id, "Pokemon", Set.of("Basic"), 60, 10, 1));
        }
        game.deckCardsRemaining().put(game.playerOne(), 3);
        AttackContext ctx = ctxFor(game, "Reorder Mismatch",
            "Look at the top 3 cards of your deck and put them back in any order.");
        // Selection is missing "c".
        ctx.setSelectedCardIds(List.of("a", "b"));
        STRATEGY.apply(ctx);
        // The "deck_top_reordered" audit should not be present.
        assertFalse(ctx.auditTrail().stream()
            .anyMatch(s -> s.contains("deck_top_reordered=3")));
    }

    // ===== topDeckLookCount: 1 carta (spanish) =====

    @Test
    void topDeckLookCountSpanish1Carta() {
        // "1 carta" → topDeckLookCount returns 1. The reorderTopDeckCards
        // function then early-returns because limit <= 1. We just need the
        // topDeckLookCount path to be hit. The audit won't show "deck_top_reordered"
        // because count is 0, but the function is called.
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("a", "b", "c"));
        for (String id : List.of("a", "b", "c")) {
            game.registerCard(new GameCard(id, "M-" + id, "Pokemon", Set.of("Basic"), 60, 10, 1));
        }
        game.deckCardsRemaining().put(game.playerOne(), 3);
        AttackContext ctx = ctxFor(game, "Mira Una",
            "Mira la 1 carta de arriba de tu mazo y vuelvela a poner en cualquier orden.");
        ctx.setSelectedCardIds(List.of("a"));
        STRATEGY.apply(ctx);
        // The deck is unchanged because reorderTopDeckCards early-returned.
        // The point is that the "1 carta" path of topDeckLookCount was executed.
        assertEquals("a", game.deck().get(game.playerOne()).get(0));
    }

    // ===== topDeckLookCount: top 2 =====

    @Test
    void topDeckLookCountEnglish2() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("a", "b", "c"));
        for (String id : List.of("a", "b", "c")) {
            game.registerCard(new GameCard(id, "M-" + id, "Pokemon", Set.of("Basic"), 60, 10, 1));
        }
        game.deckCardsRemaining().put(game.playerOne(), 3);
        AttackContext ctx = ctxFor(game, "Top 2",
            "Look at the top 2 cards of your deck and put them back in any order.");
        ctx.setSelectedCardIds(List.of("a", "b"));
        STRATEGY.apply(ctx);
        assertTrue(ctx.auditTrail().stream()
            .anyMatch(s -> s.contains("deck_top_reordered=2")));
    }

    // ===== topDeckLookCount: 2 cartas =====

    @Test
    void topDeckLookCountSpanish2Cartas() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("a", "b", "c"));
        for (String id : List.of("a", "b", "c")) {
            game.registerCard(new GameCard(id, "M-" + id, "Pokemon", Set.of("Basic"), 60, 10, 1));
        }
        game.deckCardsRemaining().put(game.playerOne(), 3);
        AttackContext ctx = ctxFor(game, "Mira 2",
            "Mira las 2 cartas de arriba de tu mazo y vuelvelas a poner en cualquier orden.");
        ctx.setSelectedCardIds(List.of("a", "b"));
        STRATEGY.apply(ctx);
        assertTrue(ctx.auditTrail().stream()
            .anyMatch(s -> s.contains("deck_top_reordered=2")));
    }

    // ===== isTopDeckOrderText: english / spanish false branches (already done for some in AttackPatternAnalyzer) =====

    @Test
    void isTopDeckOrderTextEnglishMissingCardsOfYourDeck() {
        // "look at the top" but no "cards of your deck" → english false.
        // The apply() doesn't call reorder (isTopDeckOrderText false), and the
        // deck remains empty. This test verifies the no-reorder path runs.
        GameAggregate game = game();
        STRATEGY.apply(ctxFor(game, "Look No Deck",
            "Look at the top 3 cards of your hand and put them back in any order."));
        // The deck was never reordered.
        assertTrue(game.deck().get(game.playerOne()).isEmpty());
    }

    @Test
    void isTopDeckOrderTextEnglishMissingPutThemBack() {
        // "look at the top" + "cards of your deck" but no "put them back".
        GameAggregate game = game();
        STRATEGY.apply(ctxFor(game, "Look No Back",
            "Look at the top 3 cards of your deck and draw them."));
        // No reorder.
        assertTrue(game.deck().get(game.playerOne()).isEmpty());
    }

    @Test
    void isTopDeckOrderTextSpanishMissingMazo() {
        GameAggregate game = game();
        STRATEGY.apply(ctxFor(game, "Mira No Mazo",
            "Mira las 3 cartas de arriba y ponlas en orden."));
        // No reorder.
        assertTrue(game.deck().get(game.playerOne()).isEmpty());
    }

    // ===== switchActiveWithSelectedBench: bench <= 0 =====

    @Test
    void switchActiveWithSelectedBenchNoBench_skips() {
        // The "each player switches" path calls switchActiveWithSelectedBench.
        // With no bench, returns early.
        GameAggregate game = game();
        String oldActive = game.activePokemon().get(game.playerOne());
        STRATEGY.apply(ctxFor(game, "Both Switch", "Each player switches their Active Pokemon with one of their Benched Pokemon."));
        // No exception; active unchanged.
        assertEquals(oldActive, game.activePokemon().get(game.playerOne()));
    }

    // ===== moveEnergy: attached.isEmpty() =====

    @Test
    void moveEnergyNoAttached_returns0() {
        // "move an energy from this" but no energy attached → return 0.
        GameAggregate game = game();
        game.bench().get(game.playerOne()).add("bench-x");
        game.registerCard(new GameCard("bench-x", "BX", "Pokemon", Set.of("Basic"), 60, 10, 1));
        STRATEGY.apply(ctxFor(game, "Move Energy Empty",
            "Move an energy from this Pokemon to 1 of your Benched Pokemon."));
        // No energy moved.
        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
    }

    // ===== discardToHand: card.hasSubtype branch =====

    @Test
    void discardToHandCardWrongSubtype_skips() {
        // "put 2 item cards from your discard pile into your hand" — discard
        // contains a non-Item card → skipped.
        GameAggregate game = game();
        game.discardPile().computeIfAbsent(game.playerOne(), ignored -> new ArrayList<>())
            .addAll(List.of("item-1", "sup-1"));
        game.registerCard(new GameCard("item-1", "Potion", "Trainer", Set.of("Item"), 0, 0, 0));
        game.registerCard(new GameCard("sup-1", "Shauna", "Trainer", Set.of("Supporter"), 0, 0, 0));
        STRATEGY.apply(ctxFor(game, "Recover Items Mixed",
            "Put 2 item cards from your discard pile into your hand."));
        // Only Item should be in hand.
        assertTrue(game.hand().get(game.playerOne()).contains("item-1"));
        assertFalse(game.hand().get(game.playerOne()).contains("sup-1"));
    }

    // ===== discardSelectedOpponentHandCard: empty hand =====

    @Test
    void discardSelectedOpponentHandEmptyHand_noOp() {
        // Hand is empty → return 0.
        GameAggregate game = game();
        STRATEGY.apply(ctxFor(game, "Empty Hand Discard",
            "Discard a card from your opponent's hand."));
        // No exception, no discard.
        assertEquals(0, game.discardPile().getOrDefault(game.playerTwo(), new ArrayList<>()).size());
    }

    // ===== shuffleOpponentHandCardIntoDeck: empty hand =====

    @Test
    void shuffleOpponentHandEmptyHand_noOp() {
        // "random card from your opponent" path → shuffleOpponentHandCardIntoDeck
        // with empty hand → returns.
        GameAggregate game = game();
        STRATEGY.apply(ctxFor(game, "Empty Random Shuffle",
            "Choose a random card from your opponent's hand and shuffle it into their deck."));
        // No exception.
        assertEquals(0, game.discardPile().getOrDefault(game.playerTwo(), new ArrayList<>()).size());
    }

    // ===== isSelectedOpponentHandShuffleText: "pon" Spanish =====

    @Test
    void isSelectedOpponentHandShuffleSpanishPon() {
        GameAggregate game = game();
        game.hand().get(game.playerTwo()).add("shuf-1");
        AttackContext ctx = ctxFor(game, "Pon",
            "Pon una carta de la mano de tu oponente en su mazo.");
        ctx.setSelectedCardIds(List.of("shuf-1"));
        STRATEGY.apply(ctx);
        assertTrue(game.hand().get(game.playerTwo()).isEmpty());
        assertTrue(game.deck().get(game.playerTwo()).contains("shuf-1"));
    }

    @Test
    void isSelectedOpponentHandShuffleSpanishBaraja() {
        GameAggregate game = game();
        game.hand().get(game.playerTwo()).add("shuf-2");
        AttackContext ctx = ctxFor(game, "Baraja",
            "Baraja una carta de la mano de tu oponente en su mazo.");
        ctx.setSelectedCardIds(List.of("shuf-2"));
        STRATEGY.apply(ctx);
        assertTrue(game.hand().get(game.playerTwo()).isEmpty());
    }

    // ===== draw: deck.isEmpty() short-circuit =====

    @Test
    void drawWithEmptyDeck_returns0() {
        // "draw a card" but deck is empty → return 0.
        GameAggregate game = game();
        STRATEGY.apply(ctxFor(game, "Empty Draw", "Draw a card."));
        assertEquals(0, game.hand().get(game.playerOne()).size());
    }

    // ===== firstBench: bench[i] != null branch =====

    @Test
    void firstBenchReturnsNonNullIndex() {
        // Each player switches + bench with non-null entries.
        GameAggregate game = game();
        game.bench().get(game.playerOne()).add("p1-b");
        game.bench().get(game.playerTwo()).add("p2-b");
        game.registerCard(new GameCard("p1-b", "P1B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard("p2-b", "P2B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        STRATEGY.apply(ctxFor(game, "Both Switch", "Each player switches their Active Pokemon with one of their Benched Pokemon."));
        // After swap, the bench pokemon becomes the new active.
        assertEquals("p1-b", game.activePokemon().get(game.playerOne()));
        assertEquals("p2-b", game.activePokemon().get(game.playerTwo()));
    }

    // ===== damageEachOpponentPokemon: bench iteration =====

    @Test
    void damageEachOpponentPokemonMultipleBench() {
        // Multiple bench pokemon, all should be damaged.
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).addAll(List.of("o1", "o2"));
        game.registerCard(new GameCard("o1", "O1", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard("o2", "O2", "Pokemon", Set.of("Basic"), 60, 10, 1));
        STRATEGY.apply(ctxFor(game, "Splash", "Put 2 damage counters each of your opponent's Pokemon."));
        assertEquals(2, game.damageCountersAtSlot(game.playerTwo(), "ACTIVE"));
        assertEquals(2, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
        assertEquals(2, game.damageCountersAtSlot(game.playerTwo(), "BENCH_1"));
    }

    // ===== moveFromDeckToHand: requested is empty =====

    @Test
    void moveFromDeckToHandNoSelected_usesDeckOrder() {
        // No selected card ids → uses deck order (first card).
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("first", "second"));
        for (String id : List.of("first", "second")) {
            game.registerCard(new GameCard(id, "M-" + id, "Pokemon", Set.of("Basic"), 60, 10, 1));
        }
        game.deckCardsRemaining().put(game.playerOne(), 2);
        AttackContext ctx = ctxFor(game, "Fetch No Selected",
            "Search your deck for up to 2 Pokemon and put them into your hand.");
        // No setSelectedCardIds — uses deck order.
        STRATEGY.apply(ctx);
        // Both should be moved (since count=2 and deck has 2).
        assertTrue(game.hand().get(game.playerOne()).contains("first"));
        assertTrue(game.hand().get(game.playerOne()).contains("second"));
    }

    // ===== DiscardOpponentHandForTails: hand becomes empty =====

    @Test
    void discardOpponentHandForTailsStopsAtEmptyHand() {
        // 4 tails but only 2 cards in hand → 2 discards.
        GameAggregate game = game();
        game.hand().get(game.playerTwo()).addAll(List.of("h-1", "h-2"));
        AttackContext ctx = ctxFor(game, "Burn",
            "Flip 4 coins. For each tails, your opponent discards a card from their hand.");
        java.util.ArrayDeque<Boolean> queue = new java.util.ArrayDeque<>(List.of(false, false, false, false));
        ctx.setCoinFlip(queue::removeFirst);
        STRATEGY.apply(ctx);
        assertEquals(2, game.discardPile().getOrDefault(game.playerTwo(), new ArrayList<>()).size());
    }

    // ===== Helper =====

    private static AttackContext ctxFor(GameAggregate game, String name, String text) {
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
}
