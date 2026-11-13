package com.utn.pokemontcg.card.application;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.card.infrastructure.PokemonTcgClient;
import com.utn.pokemontcg.card.infrastructure.persistence.CardEntity;
import com.utn.pokemontcg.card.infrastructure.persistence.CardJpaRepository;

class CardPerformanceEvidenceTest {

    private PokemonTcgClient client;
    private CardJpaRepository cardJpaRepository;
    private CardApiService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(PokemonTcgClient.class);
        cardJpaRepository = mock(CardJpaRepository.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new CardApiService(client, redisTemplate, new CardNormalizer(), cardJpaRepository, new ObjectMapper());
    }

    @Test
    void localSetSearchAverageUnderFiveHundredMillisecondsWithoutExternalApi() {
        when(cardJpaRepository.findBySetId("xy1")).thenReturn(localXy1Cards());
        int samples = 20;
        long totalNanos = 0;

        for (int i = 0; i < samples; i++) {
            long startedAt = System.nanoTime();
            service.search("set.id:xy1", 146, 1);
            totalNanos += System.nanoTime() - startedAt;
        }

        double averageMillis = totalNanos / 1_000_000.0 / samples;
        System.out.printf("PERF-EVIDENCE card-service local search average: %.3f ms%n", averageMillis);
        assertTrue(
            averageMillis < 500,
            () -> "Promedio de busqueda local esperado < 500ms, actual: " + averageMillis + "ms"
        );
        verifyNoInteractions(client);
    }

    private List<CardEntity> localXy1Cards() {
        return IntStream.rangeClosed(1, 146)
            .mapToObj(this::cardEntity)
            .toList();
    }

    private CardEntity cardEntity(int number) {
        CardEntity entity = new CardEntity();
        entity.setId("xy1-" + number);
        entity.setName("XY1 Card " + number);
        entity.setSetId("xy1");
        entity.setSupertype(number % 5 == 0 ? "Energy" : "Pokemon");
        entity.setSubtypes(number % 5 == 0 ? "[\"Basic\"]" : "[\"Basic\"]");
        entity.setTypes(number % 5 == 0 ? "[\"Fire\"]" : "[\"Grass\"]");
        entity.setHp(number % 5 == 0 ? null : 60 + number);
        entity.setAttacks("[]");
        entity.setWeaknesses("[]");
        entity.setResistances("[]");
        entity.setImageUrl("https://images.pokemontcg.io/xy1/" + number + ".png");
        return entity;
    }
}
