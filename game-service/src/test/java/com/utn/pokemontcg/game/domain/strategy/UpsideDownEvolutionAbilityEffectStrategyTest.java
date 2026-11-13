package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UpsideDownEvolutionAbilityEffectStrategyTest {

    private GameAggregate createGameWithInkayAndEvolution() {
        UUID player = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        GameAggregate game = new GameAggregate(player);
        game.setPlayerTwo(opponent);
        game.setCurrentTurnPlayer(player);
        game.setFirstPlayer(player);
        game.registerCard(new GameCard("inkay-1", "Inkay", "Pokemon", Set.of("Basic"), 60, 10, 1,
            "Psychic", List.of("Psychic"), 1,
            null, List.of()));
        game.registerCard(new GameCard("malamar-1", "Malamar", "Pokemon", Set.of("Stage 1"), 90, 30, 2,
            "Psychic", List.of("Psychic", "Psychic"), 1,
            "Inkay", List.of()));
        List<String> deck = List.of("malamar-1", "c2", "c3");
        game.initializeZonesFor(player, deck);
        game.initializeZonesFor(opponent, List.of("o1"));
        game.placeSetupActive(player, "inkay-1");
        game.hand().get(player).add("h1");
        return game;
    }

    @Test
    void abilityNameReturnsUpsideDownEvolution() {
        assertEquals("Upside-Down Evolution", new UpsideDownEvolutionAbilityEffectStrategy().abilityName());
    }

    @Test
    void applyEvolvesInkayWhenEvolutionIsInDeck() {
        GameAggregate game = createGameWithInkayAndEvolution();
        UUID player = game.playerOne();
        var context = new AbilityEffectContext(game, player, game.cardCatalog().get("inkay-1"), "ACTIVE", 0, null);
        var result = new UpsideDownEvolutionAbilityEffectStrategy().apply(context);
        assertEquals("UPSIDE_DOWN_EVOLUTION", result.result());
        assertEquals("malamar-1", game.activePokemon().get(player));
    }

    @Test
    void applyReturnsNoCardWhenNoEvolutionFound() {
        GameAggregate game = createGameWithInkayAndEvolution();
        UUID player = game.playerOne();
        game.deck().get(player).remove("malamar-1");
        var context = new AbilityEffectContext(game, player, game.cardCatalog().get("inkay-1"), "ACTIVE", 0, null);
        var result = new UpsideDownEvolutionAbilityEffectStrategy().apply(context);
        assertEquals("UPSIDE_DOWN_EVOLUTION_NO_CARD", result.result());
    }

    @Test
    void applyThrowsWhenNotActiveSlot() {
        GameAggregate game = createGameWithInkayAndEvolution();
        UUID player = game.playerOne();
        var context = new AbilityEffectContext(game, player, game.cardCatalog().get("inkay-1"), "BENCH_0", 0, null);
        assertThrows(InvalidActionException.class, () ->
            new UpsideDownEvolutionAbilityEffectStrategy().apply(context));
    }

    @Test
    void applyShufflesDeckWhenEvolutionFound() {
        GameAggregate game = createGameWithInkayAndEvolution();
        UUID player = game.playerOne();
        var context = new AbilityEffectContext(game, player, game.cardCatalog().get("inkay-1"), "ACTIVE", 0, null);
        new UpsideDownEvolutionAbilityEffectStrategy().apply(context);
        assertEquals(2, game.deck().get(player).size());
    }

    @Test
    void applyShufflesDeckWhenNoEvolutionFound() {
        GameAggregate game = createGameWithInkayAndEvolution();
        UUID player = game.playerOne();
        game.deck().get(player).clear();
        game.deck().get(player).add("c1");
        var context = new AbilityEffectContext(game, player, game.cardCatalog().get("inkay-1"), "ACTIVE", 0, null);
        new UpsideDownEvolutionAbilityEffectStrategy().apply(context);
        assertEquals(1, game.deck().get(player).size());
    }
}
