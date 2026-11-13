package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage push for {@link AbstractTrainerEffectStrategy} (66.7% branch).
 * Targets the 3/75 missed branches via a concrete test fixture that exposes
 * the protected helper methods.
 */
class AbstractTrainerEffectStrategyBranchCoverageTest {

    private final TestStrategy strategy = new TestStrategy();

    @Test
    void opponentOfReturnsPlayerTwoWhenPlayerIsPlayerOne() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p1 = game.playerOne();
        UUID p2 = UUID.randomUUID();
        game.setPlayerTwo(p2);

        UUID opponent = strategy.exposedOpponentOf(game, p1);

        assertEquals(p2, opponent);
    }

    @Test
    void opponentOfReturnsPlayerOneWhenPlayerIsPlayerTwo() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p1 = game.playerOne();
        UUID p2 = UUID.randomUUID();
        game.setPlayerTwo(p2);

        UUID opponent = strategy.exposedOpponentOf(game, p2);

        assertEquals(p1, opponent);
    }

    @Test
    void opponentOfReturnsPlayerOneWhenPlayerIsNull() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p1 = game.playerOne();

        UUID opponent = strategy.exposedOpponentOf(game, null);

        assertEquals(p1, opponent,
            "opponentOf(null) debe devolver playerOne como fallback");
    }

    @Test
    void drawReturnsEmptyWhenDeckIsEmpty() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p1 = game.playerOne();
        game.initializeZonesFor(p1, List.of());

        List<String> drawn = strategy.exposedDraw(game, p1, 5);

        assertTrue(drawn.isEmpty());
    }

    @Test
    void drawMovesCardsFromDeckToHand() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p1 = game.playerOne();
        game.initializeZonesFor(p1, new java.util.ArrayList<>(List.of("a", "b", "c")));

        List<String> drawn = strategy.exposedDraw(game, p1, 2);

        assertEquals(List.of("a", "b"), drawn);
        assertEquals(1, game.deckCardsRemaining().get(p1));
        assertTrue(game.hand().get(p1).containsAll(List.of("a", "b")));
    }

    @Test
    void searchBasicEnergyFindsFirstEnergyInDeck() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p1 = game.playerOne();
        game.initializeZonesFor(p1, new java.util.ArrayList<>(List.of("fire-1:1", "pokemon-1", "water-1:1")));
        game.registerCard(new GameCard("fire-1:1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.registerCard(new GameCard("pokemon-1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard("water-1:1", "Water Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Water", List.of(), 0));

        List<String> found = strategy.exposedSearchBasicEnergy(game, p1, 1);

        assertEquals(1, found.size());
        assertEquals("fire-1", found.get(0));
    }

    @Test
    void searchBasicEnergyFindsMultipleEnergies() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p1 = game.playerOne();
        game.initializeZonesFor(p1, new java.util.ArrayList<>(List.of("fire-1:1", "water-1:1")));
        game.registerCard(new GameCard("fire-1:1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.registerCard(new GameCard("water-1:1", "Water Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Water", List.of(), 0));

        List<String> found = strategy.exposedSearchBasicEnergy(game, p1, 2);

        assertEquals(2, found.size());
    }

    @Test
    void searchBasicEnergySkipsNonEnergyCards() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p1 = game.playerOne();
        game.initializeZonesFor(p1, new java.util.ArrayList<>(List.of("pokemon-1", "fire-1:1")));
        game.registerCard(new GameCard("pokemon-1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard("fire-1:1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));

        List<String> found = strategy.exposedSearchBasicEnergy(game, p1, 1);

        assertEquals(1, found.size());
        assertEquals("fire-1", found.get(0));
    }

    @Test
    void searchBasicEnergyReturnsEmptyWhenNoEnergies() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p1 = game.playerOne();
        game.initializeZonesFor(p1, new java.util.ArrayList<>(List.of("pokemon-1", "trainer-1")));
        game.registerCard(new GameCard("pokemon-1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard("trainer-1", "Potion", "Trainer", Set.of("Item"), 0, 0, 0));

        List<String> found = strategy.exposedSearchBasicEnergy(game, p1, 2);

        assertTrue(found.isEmpty());
    }

    @Test
    void shuffleHandIntoDeckMovesHandCardsAndShuffles() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p1 = game.playerOne();
        game.initializeZonesFor(p1, new java.util.ArrayList<>(List.of("d1", "d2", "d3")));
        game.hand().get(p1).addAll(List.of("h1", "h2"));

        strategy.exposedShuffleHandIntoDeck(game, p1);

        assertTrue(game.hand().get(p1).isEmpty());
        assertEquals(5, game.deck().get(p1).size());
    }

    @Test
    void discardHandMovesHandToDiscard() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p1 = game.playerOne();
        game.initializeZonesFor(p1, new java.util.ArrayList<>(List.of("d1", "d2")));
        game.hand().get(p1).addAll(List.of("h1", "h2"));

        strategy.exposedDiscardHand(game, p1);

        assertTrue(game.hand().get(p1).isEmpty());
        assertTrue(game.discardPile().get(p1).containsAll(List.of("h1", "h2")));
    }

    @Test
    void revealedCardsReturnsEmptyForNullInput() {
        List<java.util.Map<String, Object>> result = strategy.exposedRevealedCards(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void revealedCardsReturnsEmptyForEmptyInput() {
        List<java.util.Map<String, Object>> result = strategy.exposedRevealedCards(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void revealedCardsBuildsPayloadFromRegisteredCards() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.registerCard(new GameCard("xy1-1:1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard("xy1-2:1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));

        List<java.util.Map<String, Object>> result = strategy.exposedRevealedCards(game, List.of("xy1-1", "xy1-2"));

        assertEquals(2, result.size());
        java.util.Map<String, Object> first = result.get(0);
        assertEquals("xy1-1", first.get("cardId"));
        assertEquals("xy1", first.get("setId"));
        assertEquals("Pikachu", first.get("name"));
    }

    @Test
    void revealedCardsFallsBackToCardIdWhenNameIsNull() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.registerCard(new GameCard("xy1-1:1", null, "Pokemon", Set.of("Basic"), 60, 10, 1));

        List<java.util.Map<String, Object>> result = strategy.exposedRevealedCards(game, List.of("xy1-1"));

        assertEquals(1, result.size());
        assertEquals("xy1-1", result.get(0).get("name"),
            "Si name es null, revealedCards debe usar el cardId como name");
    }

    @Test
    void revealedCardsSkipsCardIdsNotInCatalog() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());

        List<java.util.Map<String, Object>> result = strategy.exposedRevealedCards(game,
            List.of("missing-id"));

        assertTrue(result.isEmpty(),
            "CardId que no esta en el catalogo debe ignorarse");
    }

    // ====================================================================
    // test fixture exposing protected helpers
    // ====================================================================

    static class TestStrategy extends AbstractTrainerEffectStrategy {
        @Override
        public String cardName() {
            return "Test";
        }

        @Override
        public com.utn.pokemontcg.game.domain.strategy.TrainerEffectResult apply(TrainerEffectContext context) {
            return com.utn.pokemontcg.game.domain.strategy.TrainerEffectResult.noop("noop");
        }

        UUID exposedOpponentOf(GameAggregate game, UUID player) {
            return opponentOf(game, player);
        }

        List<String> exposedDraw(GameAggregate game, UUID player, int n) {
            return draw(game, player, n);
        }

        List<String> exposedSearchBasicEnergy(GameAggregate game, UUID player, int max) {
            return searchBasicEnergy(game, player, max);
        }

        void exposedShuffleHandIntoDeck(GameAggregate game, UUID player) {
            shuffleHandIntoDeck(game, player);
        }

        void exposedDiscardHand(GameAggregate game, UUID player) {
            discardHand(game, player);
        }

        List<java.util.Map<String, Object>> exposedRevealedCards(GameAggregate game, List<String> ids) {
            return revealedCards(game, ids);
        }

        List<java.util.Map<String, Object>> exposedRevealedCards(List<String> ids) {
            return revealedCards(null, ids);
        }
    }
}
