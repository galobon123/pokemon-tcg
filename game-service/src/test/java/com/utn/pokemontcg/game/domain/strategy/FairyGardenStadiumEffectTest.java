package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FairyGardenStadiumEffectTest {

    @Test
    void cardNameReturnsFairyGarden() {
        assertEquals("Fairy Garden", new FairyGardenStadiumEffect().cardName());
    }

    @Test
    void onPlayedAddsEffectToBothPlayers() {
        var effect = new FairyGardenStadiumEffect();
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        effect.onPlayed(game, game.playerOne());
        assertTrue(game.hasActiveEffect(game.playerOne(), FairyGardenStadiumEffect.EFFECT_KEY));
        assertTrue(game.hasActiveEffect(game.playerTwo(), FairyGardenStadiumEffect.EFFECT_KEY));
    }

    @Test
    void onRemovedRemovesEffectFromBothPlayers() {
        var effect = new FairyGardenStadiumEffect();
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        effect.onPlayed(game, game.playerOne());
        effect.onRemoved(game);
        assertFalse(game.hasActiveEffect(game.playerOne(), FairyGardenStadiumEffect.EFFECT_KEY));
        assertFalse(game.hasActiveEffect(game.playerTwo(), FairyGardenStadiumEffect.EFFECT_KEY));
    }

    @Test
    void onRemovedDoesNothingWhenNoEffect() {
        var effect = new FairyGardenStadiumEffect();
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        effect.onRemoved(game);
        assertFalse(game.hasActiveEffect(game.playerOne(), FairyGardenStadiumEffect.EFFECT_KEY));
    }
}
