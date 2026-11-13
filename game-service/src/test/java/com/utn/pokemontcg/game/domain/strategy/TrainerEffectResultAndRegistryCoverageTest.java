package com.utn.pokemontcg.game.domain.strategy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch/line coverage push for:
 *  - TrainerEffectResult   (91.7% line / 50% branch — null payload branch)
 *  - AbilityEffectRegistry (89.7% line / 50% branch — duplicate merge + null find)
 */
class TrainerEffectResultAndRegistryCoverageTest {

    // ====================================================================
    // TrainerEffectResult
    // ====================================================================

    @Test
    void ofCopiesPayloadWhenProvided() {
        Map<String, Object> source = Map.of("a", 1, "b", "two");
        TrainerEffectResult result = TrainerEffectResult.of("OK", source);

        assertEquals("OK", result.result());
        assertEquals(1, result.payload().get("a"));
        assertEquals("two", result.payload().get("b"));
    }

    @Test
    void ofNormalizesNullPayloadToEmptyMap() {
        TrainerEffectResult result = TrainerEffectResult.of("NULL_PAYLOAD", null);

        assertEquals("NULL_PAYLOAD", result.result());
        assertNotNull(result.payload());
        assertTrue(result.payload().isEmpty(),
            "null payload debe normalizarse a Map.of()");
    }

    @Test
    void noopReturnsEmptyPayload() {
        TrainerEffectResult result = TrainerEffectResult.noop("NOOP");

        assertEquals("NOOP", result.result());
        assertTrue(result.payload().isEmpty());
    }

    @Test
    void noopAndOfWithNullPayloadAreEquivalentInShape() {
        TrainerEffectResult noop = TrainerEffectResult.noop("X");
        TrainerEffectResult explicit = TrainerEffectResult.of("X", null);

        assertEquals(noop.result(), explicit.result());
        assertEquals(noop.payload().size(), explicit.payload().size());
    }

    // ====================================================================
    // AbilityEffectRegistry — find() with normal and null names
    // ====================================================================

    @Test
    void registryFindsStrategyByExactAbilityName() {
        AbilityEffectRegistry registry = new AbilityEffectRegistry(
            List.of(new NoOpAbilityEffectStrategy()));

        Optional<AbilityEffectStrategy> found = registry.find("NO_OP");

        assertTrue(found.isPresent());
        assertEquals("NO_OP", found.get().abilityName());
    }

    @Test
    void registryFindIsCaseInsensitiveAndTrimsWhitespace() {
        AbilityEffectRegistry registry = new AbilityEffectRegistry(
            List.of(new NoOpAbilityEffectStrategy()));

        Optional<AbilityEffectStrategy> found = registry.find("  no_op  ");

        assertTrue(found.isPresent(),
            "registry.find debe normalizar case + espacios antes de buscar");
    }

    @Test
    void registryFindWithNullAbilityNameReturnsEmpty() {
        AbilityEffectRegistry registry = new AbilityEffectRegistry(
            List.of(new NoOpAbilityEffectStrategy()));

        Optional<AbilityEffectStrategy> found = registry.find(null);

        assertTrue(found.isEmpty(),
            "registry.find(null) debe resolver a Optional.empty");
    }

    @Test
    void registryFindWithUnknownNameReturnsEmpty() {
        AbilityEffectRegistry registry = new AbilityEffectRegistry(
            List.of(new NoOpAbilityEffectStrategy()));

        Optional<AbilityEffectStrategy> found = registry.find("UNKNOWN_ABILITY");

        assertTrue(found.isEmpty());
    }

    // ====================================================================
    // AbilityEffectRegistry — duplicate strategies exercise merge function
    // ====================================================================

    @Test
    void registryDeduplicatesStrategiesWithSameAbilityName() {
        AbilityEffectStrategy first = new NoOpAbilityEffectStrategy();
        AbilityEffectStrategy second = new NoOpAbilityEffectStrategy();

        AbilityEffectRegistry registry = new AbilityEffectRegistry(List.of(first, second));

        // toUnmodifiableMap with merge (first, ignored) -> first keeps the first
        Optional<AbilityEffectStrategy> found = registry.find("NO_OP");

        assertTrue(found.isPresent());
        assertSame(first, found.get(),
            "La merge function (first, ignored) -> first debe conservar el primer strategy");
    }
}
