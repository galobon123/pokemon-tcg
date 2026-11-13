package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StadiumEffectRegistryTest {

    @Test
    void findReturnsStrategyForMatchingCard() {
        var registry = new StadiumEffectRegistry(List.of(
            new ShadowCircleStadiumEffect(),
            new FairyGardenStadiumEffect()
        ));
        GameCard card = new GameCard("s1", "Shadow Circle", "Trainer", Set.of("Stadium"), 0, 0, 0);
        var result = registry.find(card);
        assertTrue(result.isPresent());
        assertInstanceOf(ShadowCircleStadiumEffect.class, result.get());
    }

    @Test
    void findReturnsStrategyForFairyGarden() {
        var registry = new StadiumEffectRegistry(List.of(
            new ShadowCircleStadiumEffect(),
            new FairyGardenStadiumEffect()
        ));
        GameCard card = new GameCard("f1", "Fairy Garden", "Trainer", Set.of("Stadium"), 0, 0, 0);
        var result = registry.find(card);
        assertTrue(result.isPresent());
        assertInstanceOf(FairyGardenStadiumEffect.class, result.get());
    }

    @Test
    void findReturnsEmptyForUnknownCard() {
        var registry = new StadiumEffectRegistry(List.of(new ShadowCircleStadiumEffect()));
        GameCard card = new GameCard("u1", "Unknown", "Trainer", Set.of("Stadium"), 0, 0, 0);
        assertTrue(registry.find(card).isEmpty());
    }

    @Test
    void findReturnsEmptyForNullCard() {
        var registry = new StadiumEffectRegistry(List.of(new ShadowCircleStadiumEffect()));
        assertTrue(registry.find(null).isEmpty());
    }

    @Test
    void findReturnsEmptyForCardWithNullName() {
        var registry = new StadiumEffectRegistry(List.of(new ShadowCircleStadiumEffect()));
        GameCard card = new GameCard("c1", null, "Trainer", null, 0, 0, 0);
        assertTrue(registry.find(card).isEmpty());
    }

    @Test
    void findIsCaseInsensitive() {
        var registry = new StadiumEffectRegistry(List.of(new ShadowCircleStadiumEffect()));
        GameCard card = new GameCard("s1", "shadow circle", "Trainer", Set.of("Stadium"), 0, 0, 0);
        assertTrue(registry.find(card).isPresent());
    }

    @Test
    void findHandlesTrimmedNames() {
        var registry = new StadiumEffectRegistry(List.of(new ShadowCircleStadiumEffect()));
        GameCard card = new GameCard("s1", "  Shadow Circle  ", "Trainer", Set.of("Stadium"), 0, 0, 0);
        assertTrue(registry.find(card).isPresent());
    }

    @Test
    void findResolvesDuplicateStrategiesByKeepingFirst() {
        var first = new ShadowCircleStadiumEffect();
        var second = new ShadowCircleStadiumEffect();
        var registry = new StadiumEffectRegistry(List.of(first, second));
        GameCard card = new GameCard("s1", "Shadow Circle", "Trainer", Set.of("Stadium"), 0, 0, 0);
        var result = registry.find(card);
        assertTrue(result.isPresent());
    }

    @Test
    void findWithEmptyRegistryReturnsEmpty() {
        var registry = new StadiumEffectRegistry(List.of());
        GameCard card = new GameCard("s1", "Shadow Circle", "Trainer", Set.of("Stadium"), 0, 0, 0);
        assertTrue(registry.find(card).isEmpty());
    }
}
