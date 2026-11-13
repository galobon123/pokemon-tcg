package com.utn.pokemontcg.card.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.card.infrastructure.persistence.CardEntity;
import com.utn.pokemontcg.card.infrastructure.persistence.CardJpaRepository;
import com.utn.pokemontcg.card.infrastructure.persistence.CardSetEntity;
import com.utn.pokemontcg.card.infrastructure.persistence.CardSetJpaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CardImportServiceTest {

    private final PokemonTcgClient client = mock(PokemonTcgClient.class);
    private final CardJpaRepository cardRepository = mock(CardJpaRepository.class);
    private final CardSetJpaRepository setRepository = mock(CardSetJpaRepository.class);
    private final CardImportService service = new CardImportService(
        client,
        cardRepository,
        setRepository,
        new ObjectMapper()
    );

    @Test
    void importOrUpdateSetUpsertsCardsAndUpdatesSetFingerprint() {
        CardEntity existing = new CardEntity();
        existing.setId("xy1-1");
        existing.setName("Old Name");

        Map<String, Object> setData = Map.of(
            "id", "xy1",
            "name", "XY",
            "updatedAt", "2024/01/01",
            "total", 146
        );
        when(client.search("set.id:xy1", 250)).thenReturn(Map.of("data", List.of(cardPayload())));
        when(cardRepository.findById("xy1-1")).thenReturn(Optional.of(existing));
        when(setRepository.findById("xy1")).thenReturn(Optional.empty());

        int imported = service.importOrUpdateSet("xy1", setData);

        assertEquals(1, imported);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CardEntity>> cardsCaptor = ArgumentCaptor.forClass(List.class);
        verify(cardRepository).saveAll(cardsCaptor.capture());
        CardEntity savedCard = cardsCaptor.getValue().getFirst();
        assertEquals("xy1-1", savedCard.getId());
        assertEquals("Venusaur-EX", savedCard.getName());
        assertEquals("xy1", savedCard.getSetId());
        assertEquals(180, savedCard.getHp());
        assertNotNull(savedCard.getImportedAt());

        ArgumentCaptor<CardSetEntity> setCaptor = ArgumentCaptor.forClass(CardSetEntity.class);
        verify(setRepository).save(setCaptor.capture());
        CardSetEntity savedSet = setCaptor.getValue();
        assertEquals("xy1", savedSet.getId());
        assertEquals("XY", savedSet.getName());
        assertEquals("2024/01/01", savedSet.getApiUpdatedAt());
        assertEquals(146, savedSet.getTotalCardsApi());
        assertNotNull(savedSet.getLastSyncAt());
    }

    @Test
    void importSetDelegatesToImportOrUpdateSetWithExternalData() {
        when(client.getSet("xy1")).thenReturn(Map.of("data", Map.of(
            "id", "xy1", "name", "XY", "updatedAt", "2024/01/01", "total", 146
        )));
        when(client.search("set.id:xy1", 250)).thenReturn(Map.of("data", List.of()));
        when(setRepository.findById("xy1")).thenReturn(Optional.empty());

        int imported = service.importSet("xy1");

        assertEquals(0, imported);
        verify(client).getSet("xy1");
        verify(client).search("set.id:xy1", 250);
    }

    @Test
    void importOrUpdateSetReturnsZeroWhenNoCardsFound() {
        Map<String, Object> setData = Map.of("id", "xy1", "name", "XY", "updatedAt", "2024/01/01", "total", 0);
        when(client.search("set.id:xy1", 250)).thenReturn(Map.of("data", List.of()));

        int imported = service.importOrUpdateSet("xy1", setData);

        assertEquals(0, imported);
        verify(cardRepository, never()).saveAll(any());
        verify(setRepository, never()).save(any());
    }

    @Test
    void importOrUpdateSetReturnsZeroWhenDataIsNotAList() {
        Map<String, Object> setData = Map.of("id", "xy1", "name", "XY", "updatedAt", "2024/01/01", "total", 0);
        when(client.search("set.id:xy1", 250)).thenReturn(Map.of("data", "not-a-list"));

        int imported = service.importOrUpdateSet("xy1", setData);

        assertEquals(0, imported);
    }

    @Test
    void importOrUpdateSetSkipsNonMapEntriesInData() {
        Map<String, Object> setData = Map.of("id", "xy1", "name", "XY", "updatedAt", "2024/01/01", "total", 1);
        when(client.search("set.id:xy1", 250)).thenReturn(Map.of("data", List.of("not-a-map", cardPayload())));
        when(cardRepository.findById("xy1-1")).thenReturn(Optional.empty());
        when(setRepository.findById("xy1")).thenReturn(Optional.empty());

        int imported = service.importOrUpdateSet("xy1", setData);

        assertEquals(1, imported);
    }

    @Test
    void importOrUpdateSetCreatesNewEntityWhenCardDoesNotExistLocally() {
        Map<String, Object> setData = Map.of("id", "xy1", "name", "XY", "updatedAt", "2024/01/01", "total", 1);
        when(client.search("set.id:xy1", 250)).thenReturn(Map.of("data", List.of(cardPayload())));
        when(cardRepository.findById("xy1-1")).thenReturn(Optional.empty());
        when(setRepository.findById("xy1")).thenReturn(Optional.empty());

        int imported = service.importOrUpdateSet("xy1", setData);

        assertEquals(1, imported);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CardEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(cardRepository).saveAll(captor.capture());
        CardEntity saved = captor.getValue().getFirst();
        assertEquals("xy1-1", saved.getId());
    }

    @Test
    void importOrUpdateSetReusesExistingSetFingerprint() {
        CardSetEntity existing = new CardSetEntity();
        existing.setId("xy1");
        existing.setName("Old Name");

        Map<String, Object> setData = Map.of("id", "xy1", "name", "XY", "updatedAt", "2024/01/01", "total", 1);
        when(client.search("set.id:xy1", 250)).thenReturn(Map.of("data", List.of(cardPayload())));
        when(cardRepository.findById("xy1-1")).thenReturn(Optional.empty());
        when(setRepository.findById("xy1")).thenReturn(Optional.of(existing));

        service.importOrUpdateSet("xy1", setData);

        ArgumentCaptor<CardSetEntity> captor = ArgumentCaptor.forClass(CardSetEntity.class);
        verify(setRepository).save(captor.capture());
        CardSetEntity saved = captor.getValue();
        assertEquals("xy1", saved.getId());
        assertEquals("XY", saved.getName());
        assertEquals("2024/01/01", saved.getApiUpdatedAt());
        assertEquals(1, saved.getTotalCardsApi());
    }

    @Test
    void importOrUpdateSetFallsBackToImportedCountWhenTotalIsMissing() {
        Map<String, Object> setData = Map.of("id", "xy1", "name", "XY", "updatedAt", "2024/01/01");
        when(client.search("set.id:xy1", 250)).thenReturn(Map.of("data", List.of(cardPayload(), cardPayload())));
        when(cardRepository.findById("xy1-1")).thenReturn(Optional.empty());
        when(setRepository.findById("xy1")).thenReturn(Optional.empty());

        service.importOrUpdateSet("xy1", setData);

        ArgumentCaptor<CardSetEntity> captor = ArgumentCaptor.forClass(CardSetEntity.class);
        verify(setRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getTotalCardsApi());
    }

    @Test
    void importOrUpdateSetHandlesNullHpByStoringNull() {
        Map<String, Object> setData = Map.of("id", "xy1", "name", "XY", "updatedAt", "2024/01/01", "total", 1);
        when(client.search("set.id:xy1", 250)).thenReturn(Map.of(
            "data", List.of(Map.of("id", "xy1-1", "name", "Energy", "set", Map.of("id", "xy1"), "supertype", "Energy"))
        ));
        when(cardRepository.findById("xy1-1")).thenReturn(Optional.empty());
        when(setRepository.findById("xy1")).thenReturn(Optional.empty());

        service.importOrUpdateSet("xy1", setData);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CardEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(cardRepository).saveAll(captor.capture());
        assertNull(captor.getValue().getFirst().getHp());
    }

    @Test
    void importOrUpdateSetParsesHpAsInteger() {
        Map<String, Object> setData = Map.of("id", "xy1", "name", "XY", "updatedAt", "2024/01/01", "total", 1);
        when(client.search("set.id:xy1", 250)).thenReturn(Map.of(
            "data", List.of(Map.ofEntries(
                entry("id", "xy1-1"),
                entry("name", "Card"),
                entry("set", Map.of("id", "xy1")),
                entry("supertype", "Pokemon"),
                entry("hp", 200)
            ))
        ));
        when(cardRepository.findById("xy1-1")).thenReturn(Optional.empty());
        when(setRepository.findById("xy1")).thenReturn(Optional.empty());

        service.importOrUpdateSet("xy1", setData);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CardEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(cardRepository).saveAll(captor.capture());
        assertEquals(200, captor.getValue().getFirst().getHp());
    }

    @Test
    void importOrUpdateSetFallsBackToNullWhenHpIsUnparseableString() {
        Map<String, Object> setData = Map.of("id", "xy1", "name", "XY", "updatedAt", "2024/01/01", "total", 1);
        when(client.search("set.id:xy1", 250)).thenReturn(Map.of(
            "data", List.of(Map.ofEntries(
                entry("id", "xy1-1"),
                entry("name", "Card"),
                entry("set", Map.of("id", "xy1")),
                entry("supertype", "Pokemon"),
                entry("hp", "not-a-number")
            ))
        ));
        when(cardRepository.findById("xy1-1")).thenReturn(Optional.empty());
        when(setRepository.findById("xy1")).thenReturn(Optional.empty());

        service.importOrUpdateSet("xy1", setData);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CardEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(cardRepository).saveAll(captor.capture());
        assertNull(captor.getValue().getFirst().getHp());
    }

    @Test
    void importOrUpdateSetHandlesUnparseableRetreatCostAsNull() {
        Map<String, Object> setData = Map.of("id", "xy1", "name", "XY", "updatedAt", "2024/01/01", "total", 1);
        when(client.search("set.id:xy1", 250)).thenReturn(Map.of(
            "data", List.of(Map.ofEntries(
                entry("id", "xy1-1"),
                entry("name", "Card"),
                entry("set", Map.of("id", "xy1")),
                entry("supertype", "Pokemon"),
                entry("convertedRetreatCost", "not-a-number")
            ))
        ));
        when(cardRepository.findById("xy1-1")).thenReturn(Optional.empty());
        when(setRepository.findById("xy1")).thenReturn(Optional.empty());

        service.importOrUpdateSet("xy1", setData);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CardEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(cardRepository).saveAll(captor.capture());
        assertNull(captor.getValue().getFirst().getRetreatCost());
    }

    @Test
    void importOrUpdateSetSerializesAttacksAndSubtypesAsJson() {
        Map<String, Object> setData = Map.of("id", "xy1", "name", "XY", "updatedAt", "2024/01/01", "total", 1);
        when(client.search("set.id:xy1", 250)).thenReturn(Map.of(
            "data", List.of(Map.ofEntries(
                entry("id", "xy1-1"),
                entry("name", "Card"),
                entry("set", Map.of("id", "xy1")),
                entry("supertype", "Pokemon"),
                entry("subtypes", List.of("Basic", "EX")),
                entry("attacks", List.of(Map.of("name", "A", "damage", "10")))
            ))
        ));
        when(cardRepository.findById("xy1-1")).thenReturn(Optional.empty());
        when(setRepository.findById("xy1")).thenReturn(Optional.empty());

        service.importOrUpdateSet("xy1", setData);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CardEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(cardRepository).saveAll(captor.capture());
        CardEntity saved = captor.getValue().getFirst();
        assertNotNull(saved.getSubtypes());
        assertTrue(saved.getSubtypes().contains("Basic"));
        assertNotNull(saved.getAttacks());
    }

    @Test
    void importOrUpdateSetHandlesNullNestedMaps() {
        Map<String, Object> setData = Map.of("id", "xy1", "name", "XY", "updatedAt", "2024/01/01", "total", 1);
        when(client.search("set.id:xy1", 250)).thenReturn(Map.of(
            "data", List.of(Map.of("id", "xy1-1", "name", "Card"))
        ));
        when(cardRepository.findById("xy1-1")).thenReturn(Optional.empty());
        when(setRepository.findById("xy1")).thenReturn(Optional.empty());

        service.importOrUpdateSet("xy1", setData);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CardEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(cardRepository).saveAll(captor.capture());
        CardEntity saved = captor.getValue().getFirst();
        assertNull(saved.getSetId());
        assertNull(saved.getImageUrl());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cardPayload() {
        return Map.ofEntries(
            entry("id", "xy1-1"),
            entry("name", "Venusaur-EX"),
            entry("set", Map.of("id", "xy1")),
            entry("supertype", "Pokemon"),
            entry("subtypes", List.of("Basic", "EX")),
            entry("types", List.of("Grass")),
            entry("hp", "180"),
            entry("images", Map.of("small", "https://images.pokemontcg.io/xy1/1.png"))
        );
    }
}
