package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MysticalFireAbilityEffectStrategyTest {

    private GameAggregate createGame() {
        UUID player = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        GameAggregate game = new GameAggregate(player);
        game.setPlayerTwo(opponent);
        game.setCurrentTurnPlayer(player);
        game.setFirstPlayer(player);
        game.initializeZonesFor(player, List.of("c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10"));
        game.initializeZonesFor(opponent, List.of("o1", "o2"));
        return game;
    }

    @Test
    void abilityNameReturnsMysticalFire() {
        assertEquals("Mystical Fire", new MysticalFireAbilityEffectStrategy().abilityName());
    }

    @Test
    void drawsCardsUpToMaxHandSize() {
        GameAggregate game = createGame();
        UUID player = game.playerOne();
        var context = new AbilityEffectContext(game, player, null, "ACTIVE", 0, null);
        var result = new MysticalFireAbilityEffectStrategy().apply(context);
        assertEquals("MYSTICAL_FIRE_DRAW", result.result());
        assertEquals(6, game.hand().get(player).size());
        assertEquals(4, game.deck().get(player).size());
        assertEquals(4, game.deckCardsRemaining().get(player));
    }

    @Test
    void drawsPartialWhenDeckHasFewerCardsThanNeeded() {
        GameAggregate game = createGame();
        UUID player = game.playerOne();
        game.hand().get(player).add("h1");
        game.hand().get(player).add("h2");
        game.hand().get(player).add("h3");
        List<String> deck = game.deck().get(player);
        deck.clear();
        deck.add("d1");
        deck.add("d2");
        var context = new AbilityEffectContext(game, player, null, "ACTIVE", 0, null);
        var result = new MysticalFireAbilityEffectStrategy().apply(context);
        assertEquals("MYSTICAL_FIRE_DRAW", result.result());
        assertEquals(5, game.hand().get(player).size());
    }

    @Test
    void returnsNoopWhenHandAlreadyAtMaxSize() {
        GameAggregate game = createGame();
        UUID player = game.playerOne();
        game.hand().get(player).addAll(List.of("h1", "h2", "h3", "h4", "h5", "h6"));
        var context = new AbilityEffectContext(game, player, null, "ACTIVE", 0, null);
        var result = new MysticalFireAbilityEffectStrategy().apply(context);
        assertEquals("MYSTICAL_FIRE_NO_DRAW", result.result());
        assertEquals(6, game.hand().get(player).size());
    }

    @Test
    void returnsNoopWhenHandExceedsMaxSize() {
        GameAggregate game = createGame();
        UUID player = game.playerOne();
        game.hand().get(player).addAll(List.of("h1", "h2", "h3", "h4", "h5", "h6", "h7"));
        var context = new AbilityEffectContext(game, player, null, "ACTIVE", 0, null);
        var result = new MysticalFireAbilityEffectStrategy().apply(context);
        assertEquals("MYSTICAL_FIRE_NO_DRAW", result.result());
        assertEquals(7, game.hand().get(player).size());
    }

    @Test
    void drawsOneCardWhenOneSlotAvailable() {
        GameAggregate game = createGame();
        UUID player = game.playerOne();
        game.hand().get(player).addAll(List.of("h1", "h2", "h3", "h4", "h5"));
        int initialDeckSize = game.deck().get(player).size();
        var context = new AbilityEffectContext(game, player, null, "ACTIVE", 0, null);
        var result = new MysticalFireAbilityEffectStrategy().apply(context);
        assertEquals("MYSTICAL_FIRE_DRAW", result.result());
        assertEquals(6, game.hand().get(player).size());
        assertEquals(initialDeckSize - 1, game.deck().get(player).size());
    }
}
