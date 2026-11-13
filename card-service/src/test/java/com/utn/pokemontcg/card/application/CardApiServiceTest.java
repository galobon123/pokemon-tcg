package com.utn.pokemontcg.card.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.card.application.dto.CardSearchResponse;
import com.utn.pokemontcg.card.application.dto.CardSummaryResponse;
import com.utn.pokemontcg.card.infrastructure.PokemonTcgClient;
import com.utn.pokemontcg.card.infrastructure.persistence.CardEntity;
import com.utn.pokemontcg.card.infrastructure.persistence.CardJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static java.util.Map.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class CardApiServiceTest {

    private PokemonTcgClient client;
    private CardJpaRepository cardJpaRepository;
    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;
    private CardApiService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(PokemonTcgClient.class);
        cardJpaRepository = mock(CardJpaRepository.class);
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new CardApiService(client, redisTemplate, new CardNormalizer(), cardJpaRepository, new ObjectMapper());
    }

    @Test
    void normalizesSearchResponsesForGameUse() {
        when(cardJpaRepository.findBySetId("xy1")).thenReturn(List.of());
        when(client.search("set.id:xy1", 20, 1)).thenReturn(searchPayload());

        CardSearchResponse response = service.search("set.id:xy1", 20, 1);

        assertEquals(1, response.count());
        assertEquals(1, response.cards().size());
        CardSummaryResponse card = response.cards().getFirst();
        assertEquals("xy1-1", card.id());
        assertEquals("Venusaur-EX", card.name());
        assertEquals("xy1", card.setId());
        assertEquals("Pokemon", card.supertype());
        assertEquals(List.of("Basic", "EX"), card.subtypes());
        assertEquals(List.of("Grass"), card.types());
        assertEquals(180, card.hp());
        assertEquals("Poison Powder", card.attacks().getFirst().name());
        assertEquals(3, card.attacks().getFirst().convertedEnergyCost());
        assertEquals("Fire", card.weaknesses().getFirst().type());
        verify(valueOperations).set(eq("cards:search:v2:set.id:xy1:20:1"), any(CardSearchResponse.class), any());
    }


    @Test
    void returnsSearchFromPostgresBeforeProvider() {
        when(cardJpaRepository.findBySetId("xy1")).thenReturn(List.of(cardEntity("xy1-2"), cardEntity("xy1-1")));

        CardSearchResponse response = service.search("set.id:xy1", 1, 1);

        assertEquals(1, response.count());
        assertEquals(2, response.totalCount());
        assertEquals("xy1-1", response.cards().getFirst().id());
        assertEquals("https://images.pokemontcg.io/xy1/1.png", response.cards().getFirst().imageUrl());
        verifyNoInteractions(client);
    }

    @Test
    void appliesCombinedFiltersToPostgresSetSearch() {
        CardEntity venusaur = cardEntity("xy1-1");
        CardEntity blastoise = cardEntity("xy1-2");
        blastoise.setName("Blastoise-EX");
        blastoise.setTypes("[\"Water\"]");
        when(cardJpaRepository.findBySetId("xy1")).thenReturn(List.of(venusaur, blastoise));

        CardSearchResponse response = service.search("set.id:xy1 name:venusaur supertype:Pokémon types:Grass", 20, 1);

        assertEquals(1, response.count());
        assertEquals(1, response.totalCount());
        assertEquals("xy1-1", response.cards().getFirst().id());
        verifyNoInteractions(client);
    }

    @Test
    void returnsCachedCardByIdWithoutCallingProvider() {
        CardSummaryResponse cached = new CardSummaryResponse(
            "xy1-1",
            "Venusaur-EX",
            "xy1",
            "Pokemon",
            List.of("Basic"),
            List.of("Grass"),
            180,
            List.of(),
            List.of(),
            List.of(),
            "https://images.pokemontcg.io/xy1/1.png"
        );
        when(valueOperations.get("cards:id:xy1-1")).thenReturn(cached);

        CardSummaryResponse response = service.byId("xy1-1");

        assertEquals(cached, response);
        verifyNoInteractions(client);
    }

    @Test
    void returnsCardByIdFromPostgresBeforeProvider() {
        when(cardJpaRepository.findById("xy1-1")).thenReturn(Optional.of(cardEntity("xy1-1")));

        CardSummaryResponse response = service.byId("xy1-1");

        assertEquals("xy1-1", response.id());
        assertEquals("Venusaur-EX", response.name());
        assertEquals("https://images.pokemontcg.io/xy1/1.png", response.imageUrl());
        verify(client, never()).getById("xy1-1");
    }

    @Test
    void stillCallsProviderWhenRedisIsUnavailable() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        when(cardJpaRepository.findById("xy1-1")).thenReturn(Optional.empty());
        when(client.getById("xy1-1")).thenReturn(byIdPayload());

        CardSummaryResponse response = service.byId("xy1-1");

        assertEquals("xy1-1", response.id());
        assertEquals("Venusaur-EX", response.name());
    }

    @Test
    void searchClampsPageSizeAbove250To250() {
        when(client.search("pikachu", 250, 1)).thenReturn(Map.of("count", 0, "totalCount", 0, "data", List.of()));

        CardSearchResponse response = service.search("pikachu", 9999, 1);

        assertEquals(0, response.count());
        verify(client).search("pikachu", 250, 1);
    }

    @Test
    void searchClampsPageSizeBelow1To1() {
        when(client.search("pikachu", 1, 1)).thenReturn(Map.of("count", 0, "totalCount", 0, "data", List.of()));

        CardSearchResponse response = service.search("pikachu", 0, 1);

        verify(client).search("pikachu", 1, 1);
    }

    @Test
    void searchClampsPageBelow1To1() {
        when(client.search("pikachu", 20, 1)).thenReturn(Map.of("count", 0, "totalCount", 0, "data", List.of()));

        service.search("pikachu", 20, 0);

        verify(client).search("pikachu", 20, 1);
    }

    @Test
    void searchFallsThroughToProviderWhenSetQueryHasNoLocalData() {
        when(cardJpaRepository.findBySetId("xy1")).thenReturn(List.of());
        when(client.search("set.id:xy1", 20, 1)).thenReturn(searchPayload());

        CardSearchResponse response = service.search("set.id:xy1", 20, 1);

        assertEquals(1, response.count());
    }

    @Test
    void searchFallsThroughToProviderWhenSetQueryReturnsNullFromPostgres() {
        when(cardJpaRepository.findBySetId("xy1")).thenReturn(null);
        when(client.search("set.id:xy1", 20, 1)).thenReturn(searchPayload());

        CardSearchResponse response = service.search("set.id:xy1", 20, 1);

        assertEquals(1, response.count());
    }

    @Test
    void searchReturnsCachedResponseWithoutCallingProviderOrDb() {
        CardSearchResponse cached = new CardSearchResponse("pikachu", 20, 1, 1, List.of());
        when(valueOperations.get("cards:search:v2:pikachu:20:1")).thenReturn(cached);

        CardSearchResponse response = service.search("pikachu", 20, 1);

        assertEquals(cached, response);
        verifyNoInteractions(client);
        verifyNoInteractions(cardJpaRepository);
    }

    @Test
    void searchStillWorksWhenRedisReadFailsAndLocalDataIsEmpty() {
        when(valueOperations.get(any())).thenThrow(new IllegalStateException("redis read failed"));
        when(cardJpaRepository.findBySetId("xy1")).thenReturn(List.of());
        when(client.search("set.id:xy1", 20, 1)).thenReturn(searchPayload());

        CardSearchResponse response = service.search("set.id:xy1", 20, 1);

        assertEquals(1, response.count());
    }

    @Test
    void searchStillWorksWhenRedisWriteFails() {
        when(cardJpaRepository.findBySetId("xy1")).thenReturn(List.of());
        when(client.search("set.id:xy1", 20, 1)).thenReturn(searchPayload());
        doThrow(new IllegalStateException("redis write failed")).when(valueOperations).set(any(), any(), any());

        CardSearchResponse response = service.search("set.id:xy1", 20, 1);

        assertEquals(1, response.count(), "search should still return results even when redis write fails");
    }

    @Test
    void byIdReturnsFromProviderWhenPostgresEmpty() {
        when(cardJpaRepository.findById("xy1-1")).thenReturn(Optional.empty());
        when(client.getById("xy1-1")).thenReturn(byIdPayload());

        CardSummaryResponse response = service.byId("xy1-1");

        assertEquals("xy1-1", response.id());
        assertEquals("Venusaur-EX", response.name());
        verify(valueOperations).set(eq("cards:id:xy1-1"), any(CardSummaryResponse.class), any());
    }

    @Test
    void byIdIgnoresRedisWriteFailure() {
        when(cardJpaRepository.findById("xy1-1")).thenReturn(Optional.empty());
        when(client.getById("xy1-1")).thenReturn(byIdPayload());
        doThrow(new IllegalStateException("redis write failed")).when(valueOperations).set(any(), any(), any());

        CardSummaryResponse response = service.byId("xy1-1");

        assertEquals("xy1-1", response.id());
    }

    @Test
    void byIdIgnoresRedisReadFailure() {
        when(valueOperations.get("cards:id:xy1-1")).thenThrow(new IllegalStateException("redis read failed"));
        when(cardJpaRepository.findById("xy1-1")).thenReturn(Optional.empty());
        when(client.getById("xy1-1")).thenReturn(byIdPayload());

        CardSummaryResponse response = service.byId("xy1-1");

        assertEquals("xy1-1", response.id());
    }

    @Test
    void typeFilterMatchesByTypesListOnPostgresCards() {
        CardEntity grass = cardEntity("xy1-1");
        CardEntity fire = cardEntity("xy1-2");
        fire.setName("Charizard-EX");
        fire.setTypes("[\"Fire\"]");
        when(cardJpaRepository.findBySetId("xy1")).thenReturn(List.of(grass, fire));

        CardSearchResponse response = service.search("set.id:xy1 types:Fire", 20, 1);

        assertEquals(1, response.count());
        assertEquals("xy1-2", response.cards().getFirst().id());
    }

    @Test
    void nameFilterIgnoresAccentMismatches() {
        CardEntity venusaur = cardEntity("xy1-1");
        venusaur.setName("Venusaur");
        CardEntity blastoise = cardEntity("xy1-2");
        blastoise.setName("Blastoise-EX");
        when(cardJpaRepository.findBySetId("xy1")).thenReturn(List.of(venusaur, blastoise));

        CardSearchResponse response = service.search("set.id:xy1 name:Venüsaur", 20, 1);

        assertEquals(1, response.count());
        assertEquals("xy1-1", response.cards().getFirst().id());
    }

    @Test
    void supertypeFilterMatchesExactAfterNormalization() {
        CardEntity pokemon = cardEntity("xy1-1");
        CardEntity energy = cardEntity("xy1-2");
        energy.setName("Fire Energy");
        energy.setSupertype("Energy");
        energy.setTypes("[\"Fire\"]");
        when(cardJpaRepository.findBySetId("xy1")).thenReturn(List.of(pokemon, energy));

        CardSearchResponse response = service.search("set.id:xy1 supertype:Pokemon", 20, 1);

        assertEquals(1, response.count());
        assertEquals("xy1-1", response.cards().getFirst().id());
    }

    @Test
    void emptyTypeFilterPassesAllCards() {
        CardEntity venusaur = cardEntity("xy1-1");
        when(cardJpaRepository.findBySetId("xy1")).thenReturn(List.of(venusaur));

        CardSearchResponse response = service.search("set.id:xy1 types:", 20, 1);

        assertEquals(1, response.count());
    }

    @Test
    void emptyNameFilterPassesAllCards() {
        CardEntity venusaur = cardEntity("xy1-1");
        when(cardJpaRepository.findBySetId("xy1")).thenReturn(List.of(venusaur));

        CardSearchResponse response = service.search("set.id:xy1 name:", 20, 1);

        assertEquals(1, response.count());
    }

    @Test
    void entityWithInvalidAbilitiesJsonProducesEmptyAbilities() {
        CardEntity entity = cardEntity("xy1-1");
        entity.setAbilities("{not-valid-json");
        when(cardJpaRepository.findById("xy1-1")).thenReturn(Optional.of(entity));

        CardSummaryResponse response = service.byId("xy1-1");

        assertEquals(0, response.abilities().size());
    }

    @Test
    void entityWithInvalidTypesJsonProducesEmptyTypes() {
        CardEntity entity = cardEntity("xy1-1");
        entity.setTypes("{not-valid-json");
        when(cardJpaRepository.findById("xy1-1")).thenReturn(Optional.of(entity));

        CardSummaryResponse response = service.byId("xy1-1");

        assertEquals(0, response.types().size());
    }

    @Test
    void entityWithBlankSubtypesJsonProducesEmptyList() {
        CardEntity entity = cardEntity("xy1-1");
        entity.setSubtypes("   ");
        when(cardJpaRepository.findById("xy1-1")).thenReturn(Optional.of(entity));

        CardSummaryResponse response = service.byId("xy1-1");

        assertEquals(0, response.subtypes().size());
    }

    @Test
    void entityWithNullAttacksJsonProducesEmptyList() {
        CardEntity entity = cardEntity("xy1-1");
        entity.setAttacks(null);
        when(cardJpaRepository.findById("xy1-1")).thenReturn(Optional.of(entity));

        CardSummaryResponse response = service.byId("xy1-1");

        assertEquals(0, response.attacks().size());
    }

    @Test
    void entityWithBlankNameIsStillReturned() {
        CardEntity entity = cardEntity("xy1-1");
        entity.setName("   ");
        when(cardJpaRepository.findBySetId("xy1")).thenReturn(List.of(entity));

        CardSearchResponse response = service.search("set.id:xy1", 20, 1);

        assertEquals(1, response.count());
    }

    @Test
    void searchByNonSetQueryBypassesPostgresCheck() {
        when(client.search("pikachu", 20, 1)).thenReturn(Map.of("count", 0, "totalCount", 0, "data", List.of()));

        CardSearchResponse response = service.search("pikachu", 20, 1);

        assertEquals(0, response.count());
        verify(cardJpaRepository, never()).findBySetId(any());
    }

    @Test
    void paginationSkipsAndLimitsResults() {
        when(cardJpaRepository.findBySetId("xy1")).thenReturn(List.of(
            cardEntity("xy1-1"), cardEntity("xy1-2"), cardEntity("xy1-3")
        ));

        CardSearchResponse page1 = service.search("set.id:xy1", 1, 1);
        assertEquals(1, page1.cards().size());
        assertEquals("xy1-1", page1.cards().getFirst().id());

        CardSearchResponse page2 = service.search("set.id:xy1", 1, 2);
        assertEquals("xy1-2", page2.cards().getFirst().id());

        CardSearchResponse page3 = service.search("set.id:xy1", 1, 3);
        assertEquals("xy1-3", page3.cards().getFirst().id());
    }

    private Map<String, Object> searchPayload() {
        return Map.of(
            "count", 1,
            "totalCount", 1,
            "data", List.of(cardPayload())
        );
    }

    private Map<String, Object> byIdPayload() {
        return Map.of("data", cardPayload());
    }

    private Map<String, Object> cardPayload() {
        return Map.ofEntries(
            entry("id", "xy1-1"),
            entry("name", "Venusaur-EX"),
            entry("set", Map.of("id", "xy1")),
            entry("supertype", "Pokemon"),
            entry("subtypes", List.of("Basic", "EX")),
            entry("types", List.of("Grass")),
            entry("hp", "180"),
            entry("attacks", List.of(Map.of(
                "name", "Poison Powder",
                "text", "Your opponent's Active Pokemon is now Poisoned.",
                "damage", "60",
                "cost", List.of("Grass", "Colorless", "Colorless"),
                "convertedEnergyCost", 3
            ))),
            entry("weaknesses", List.of(Map.of("type", "Fire", "value", "x2"))),
            entry("resistances", List.of(Map.of("type", "Water", "value", "-20"))),
            entry("images", Map.of("small", "https://images.pokemontcg.io/xy1/1.png"))
        );
    }

    private CardEntity cardEntity(String id) {
        CardEntity entity = new CardEntity();
        entity.setId(id);
        entity.setName("Venusaur-EX");
        entity.setSetId("xy1");
        entity.setSupertype("Pokemon");
        entity.setSubtypes("[\"Basic\",\"EX\"]");
        entity.setTypes("[\"Grass\"]");
        entity.setHp(180);
        entity.setAttacks("[{\"name\":\"Poison Powder\",\"text\":\"Your opponent's Active Pokemon is now Poisoned.\",\"damage\":\"60\",\"cost\":[\"Grass\",\"Colorless\",\"Colorless\"],\"convertedEnergyCost\":3}]");
        entity.setWeaknesses("[{\"type\":\"Fire\",\"value\":\"x2\"}]");
        entity.setResistances("[{\"type\":\"Water\",\"value\":\"-20\"}]");
        entity.setImageUrl("https://images.pokemontcg.io/xy1/1.png");
        return entity;
    }
}
