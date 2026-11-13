package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Branch coverage push for {@link UpsideDownEvolutionAbilityEffectStrategy}
 * (99.3% line / 78.6% branch). Targets the 3 missed branches:
 *  - findEvolutionInDeck: card == null branch (deck has unknown id)
 *  - findEvolutionInDeck: card.evolvesFrom() == null branch (basic pokemon in deck)
 *  - findEvolutionInDeck: card.evolvesFrom() does NOT match inkayName
 * (the evolvedCard null branch is unreachable through the public API because
 *  findEvolutionInDeck already filters out unknown cards.)
 */
class UpsideDownEvolutionAbilityEffectStrategyBranchTest {

    @Test
    void skipsDeckCardsThatAreNotInCatalog() {
        GameAggregate game = newGame();
        UUID player = game.playerOne();
        game.registerCard(new GameCard("inkay-1", "Inkay", "Pokemon", Set.of("Basic"), 60, 10, 1,
            "Psychic", List.of("Psychic"), 1, null, List.of()));
        game.placeSetupActive(player, "inkay-1");
        // "malamar-1" is in the deck but NOT registered in the catalog
        game.deck().get(player).clear();
        game.deck().get(player).add("malamar-1");
        game.deckCardsRemaining().put(player, 1);

        GameCard inkay = game.cardCatalog().get("inkay-1");
        var ctx = new AbilityEffectContext(game, player, inkay, "ACTIVE", 0, null);
        var result = new UpsideDownEvolutionAbilityEffectStrategy().apply(ctx);

        // cardCatalog.get("malamar-1") returns null in findEvolutionInDeck → false branch
        assertEquals("UPSIDE_DOWN_EVOLUTION_NO_CARD", result.result());
    }

    @Test
    void skipsCardsWhoseEvolvesFromIsNull() {
        GameAggregate game = newGame();
        UUID player = game.playerOne();
        game.registerCard(new GameCard("inkay-1", "Inkay", "Pokemon", Set.of("Basic"), 60, 10, 1,
            "Psychic", List.of("Psychic"), 1, null, List.of()));
        // Basic pokemon with NO evolvesFrom field
        game.registerCard(new GameCard("basic-1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1,
            "Lightning", List.of("Lightning"), 0, null, List.of()));
        game.placeSetupActive(player, "inkay-1");
        game.deck().get(player).clear();
        game.deck().get(player).add("basic-1");
        game.deckCardsRemaining().put(player, 1);

        GameCard inkay = game.cardCatalog().get("inkay-1");
        var ctx = new AbilityEffectContext(game, player, inkay, "ACTIVE", 0, null);
        var result = new UpsideDownEvolutionAbilityEffectStrategy().apply(ctx);

        // card.evolvesFrom() == null branch → false branch
        assertEquals("UPSIDE_DOWN_EVOLUTION_NO_CARD", result.result());
    }

    @Test
    void skipsCardsWhoseEvolvesFromIsDifferentName() {
        GameAggregate game = newGame();
        UUID player = game.playerOne();
        game.registerCard(new GameCard("inkay-1", "Inkay", "Pokemon", Set.of("Basic"), 60, 10, 1,
            "Psychic", List.of("Psychic"), 1, null, List.of()));
        // evolvesFrom is "Pikachu", not "Inkay"
        game.registerCard(new GameCard("raichu-1", "Raichu", "Pokemon", Set.of("Stage 1"), 90, 30, 2,
            "Lightning", List.of("Lightning", "Colorless"), 1, "Pikachu", List.of()));
        game.placeSetupActive(player, "inkay-1");
        game.deck().get(player).clear();
        game.deck().get(player).add("raichu-1");
        game.deckCardsRemaining().put(player, 1);

        GameCard inkay = game.cardCatalog().get("inkay-1");
        var ctx = new AbilityEffectContext(game, player, inkay, "ACTIVE", 0, null);
        var result = new UpsideDownEvolutionAbilityEffectStrategy().apply(ctx);

        // card.evolvesFrom().equalsIgnoreCase("Inkay") is false branch
        assertEquals("UPSIDE_DOWN_EVOLUTION_NO_CARD", result.result());
    }

    @Test
    void findsEvolutionWhenEvolvesFromMatchesIgnoringCase() {
        GameAggregate game = newGame();
        UUID player = game.playerOne();
        game.registerCard(new GameCard("inkay-1", "Inkay", "Pokemon", Set.of("Basic"), 60, 10, 1,
            "Psychic", List.of("Psychic"), 1, null, List.of()));
        // evolvesFrom is "INKAY" (uppercase)
        game.registerCard(new GameCard("malamar-1", "Malamar", "Pokemon", Set.of("Stage 1"), 90, 30, 2,
            "Psychic", List.of("Psychic", "Psychic"), 1, "INKAY", List.of()));
        game.placeSetupActive(player, "inkay-1");
        game.deck().get(player).clear();
        game.deck().get(player).add("malamar-1");
        game.deckCardsRemaining().put(player, 1);

        GameCard inkay = game.cardCatalog().get("inkay-1");
        var ctx = new AbilityEffectContext(game, player, inkay, "ACTIVE", 0, null);
        var result = new UpsideDownEvolutionAbilityEffectStrategy().apply(ctx);

        assertEquals("UPSIDE_DOWN_EVOLUTION", result.result(),
            "equalsIgnoreCase debe encontrar la evolucion aunque difiera en case");
        assertEquals("Malamar", result.payload().get("evolvedTo"));
    }

    @Test
    void noCardBranchUsesDefaultDeckWhenDeckIsEmpty() {
        GameAggregate game = newGame();
        UUID player = game.playerOne();
        game.registerCard(new GameCard("inkay-1", "Inkay", "Pokemon", Set.of("Basic"), 60, 10, 1,
            "Psychic", List.of("Psychic"), 1, null, List.of()));
        game.placeSetupActive(player, "inkay-1");
        // empty deck
        game.deck().get(player).clear();
        game.deckCardsRemaining().put(player, 0);

        GameCard inkay = game.cardCatalog().get("inkay-1");
        var ctx = new AbilityEffectContext(game, player, inkay, "ACTIVE", 0, null);
        var result = new UpsideDownEvolutionAbilityEffectStrategy().apply(ctx);

        assertEquals("UPSIDE_DOWN_EVOLUTION_NO_CARD", result.result());
        assertEquals("No se encontro evolucion valida en el mazo", result.payload().get("reason"));
    }

    @Test
    void evolvedCardNullBranchFallsBackToEvolutionId() {
        // The evolvedCard null branch is normally unreachable, but we can hit it by
        // wrapping cardCatalog() in a counting proxy that returns null the SECOND
        // time the same id is requested — which is exactly when the payload is built.
        GameAggregate game = newGame();
        UUID player = game.playerOne();
        GameCard inkay = new GameCard("inkay-1", "Inkay", "Pokemon", Set.of("Basic"), 60, 10, 1,
            "Psychic", List.of("Psychic"), 1, null, List.of());
        GameCard malamar = new GameCard("malamar-1", "Malamar", "Pokemon", Set.of("Stage 1"), 90, 30, 2,
            "Psychic", List.of("Psychic", "Psychic"), 1, "Inkay", List.of());
        game.registerCard(inkay);
        game.registerCard(malamar);
        game.placeSetupActive(player, "inkay-1");

        // Wrap the card catalog in a one-shot map: first get() returns the real card,
        // subsequent get() with the same key return null.
        Map<String, GameCard> flakeyCatalog = new FlakeyOneShotCatalog(game.cardCatalog());
        ((FlakeyGameAggregate) game).overrideCardCatalog(flakeyCatalog);

        // deck must reference "malamar-1" (which the flakey catalog hides on the 2nd lookup)
        game.deck().get(player).clear();
        game.deck().get(player).add("malamar-1");
        game.deckCardsRemaining().put(player, 1);

        var ctx = new AbilityEffectContext(game, player, inkay, "ACTIVE", 0, null);
        var result = new UpsideDownEvolutionAbilityEffectStrategy().apply(ctx);

        assertEquals("UPSIDE_DOWN_EVOLUTION", result.result());
        // Second call to cardCatalog().get(evolutionId) returned null → fallback to id
        assertEquals("malamar-1", result.payload().get("evolvedTo"),
            "evolvedCard == null branch: payload debe usar el evolutionId como fallback");
    }

    // ====================================================================
    // helpers
    // ====================================================================

    private GameAggregate newGame() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new FlakeyGameAggregate(p1);
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(p1);
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());
        return game;
    }

    /** A {@link GameAggregate} that lets tests swap the cardCatalog map. */
    static class FlakeyGameAggregate extends GameAggregate {
        private Map<String, GameCard> cardCatalogOverride;

        FlakeyGameAggregate(UUID playerOne) {
            super(playerOne);
        }

        void overrideCardCatalog(Map<String, GameCard> override) {
            this.cardCatalogOverride = override;
        }

        @Override
        public Map<String, GameCard> cardCatalog() {
            return cardCatalogOverride != null ? cardCatalogOverride : super.cardCatalog();
        }
    }

    /**
     * Map that returns the underlying value on the first {@code get(id)} call,
     * but returns {@code null} for every subsequent call (only per-id counter).
     * Used to simulate the "evolvedCard == null" branch in the payload.
     */
    static class FlakeyOneShotCatalog extends HashMap<String, GameCard> {
        private final Map<String, GameCard> backing;
        private final Set<String> consumed = new java.util.HashSet<>();

        FlakeyOneShotCatalog(Map<String, GameCard> backing) {
            this.backing = backing;
        }

        @Override
        public GameCard get(Object key) {
            if (key instanceof String s) {
                if (consumed.add(s)) {
                    return backing.get(s);
                }
                return null;
            }
            return backing.get(key);
        }

        @Override
        public Set<Map.Entry<String, GameCard>> entrySet() {
            return backing.entrySet();
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean containsKey(Object key) {
            return backing.containsKey(key);
        }
    }
}
