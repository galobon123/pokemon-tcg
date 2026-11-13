package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShadowCircleStadiumEffectTest {

    @Test
    void cardNameReturnsShadowCircle() {
        assertEquals("Shadow Circle", new ShadowCircleStadiumEffect().cardName());
    }

    @Test
    void onPlayedAddsEffectToBothPlayers() {
        var effect = new ShadowCircleStadiumEffect();
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        effect.onPlayed(game, game.playerOne());
        assertTrue(game.hasActiveEffect(game.playerOne(), ShadowCircleStadiumEffect.EFFECT_KEY));
        assertTrue(game.hasActiveEffect(game.playerTwo(), ShadowCircleStadiumEffect.EFFECT_KEY));
        assertEquals(1, game.activeEffectValue(game.playerOne(), ShadowCircleStadiumEffect.EFFECT_KEY));
        assertEquals(1, game.activeEffectValue(game.playerTwo(), ShadowCircleStadiumEffect.EFFECT_KEY));
    }

    @Test
    void onRemovedRemovesEffectFromBothPlayers() {
        var effect = new ShadowCircleStadiumEffect();
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        effect.onPlayed(game, game.playerOne());
        effect.onRemoved(game);
        assertFalse(game.hasActiveEffect(game.playerOne(), ShadowCircleStadiumEffect.EFFECT_KEY));
        assertFalse(game.hasActiveEffect(game.playerTwo(), ShadowCircleStadiumEffect.EFFECT_KEY));
    }

    @Test
    void onRemovedDoesNothingWhenNoEffect() {
        var effect = new ShadowCircleStadiumEffect();
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        effect.onRemoved(game);
        assertFalse(game.hasActiveEffect(game.playerOne(), ShadowCircleStadiumEffect.EFFECT_KEY));
    }
}
