package com.utn.pokemontcg.card.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CardSetSyncServiceTest {

    private final PokemonTcgClient client = mock(PokemonTcgClient.class);
    private final CardDiffChecker diffChecker = mock(CardDiffChecker.class);
    private final CardImportService importService = mock(CardImportService.class);
    private final CardSetSyncService syncService = new CardSetSyncService(client, diffChecker, importService);

    @Test
    void skipsImportWhenSetHasNoChanges() {
        when(client.getSet("xy1")).thenReturn(setPayload("2024/01/01", 146));
        when(diffChecker.hasChanges("xy1", "2024/01/01", 146)).thenReturn(false);

        int imported = syncService.syncSet("xy1");

        assertEquals(0, imported);
        verify(importService, never()).importOrUpdateSet("xy1", Map.of());
    }

    @Test
    void importsWhenSetChanged() {
        Map<String, Object> setData = Map.of("id", "xy1", "name", "XY", "updatedAt", "2024/01/01", "total", 146);
        when(client.getSet("xy1")).thenReturn(Map.of("data", setData));
        when(diffChecker.hasChanges("xy1", "2024/01/01", 146)).thenReturn(true);
        when(importService.importOrUpdateSet("xy1", setData)).thenReturn(146);

        int imported = syncService.syncSet("xy1");

        assertEquals(146, imported);
        verify(importService).importOrUpdateSet("xy1", setData);
    }

    @Test
    void scheduledSyncTriggersSyncForXy1Set() {
        when(client.getSet("xy1")).thenReturn(setPayload("2024/01/01", 146));
        when(diffChecker.hasChanges("xy1", "2024/01/01", 146)).thenReturn(false);

        syncService.scheduledSync();

        verify(client).getSet("xy1");
        verify(diffChecker).hasChanges("xy1", "2024/01/01", 146);
    }

    @Test
    void setDataReturnsEmptyMapWhenDataIsNotAMap() {
        when(client.getSet("xy1")).thenReturn(Map.of("data", "not-a-map"));
        when(diffChecker.hasChanges("xy1", null, null)).thenReturn(false);

        int imported = syncService.syncSet("xy1");

        assertEquals(0, imported);
        verify(importService, never()).importOrUpdateSet("xy1", Map.of());
    }

    @Test
    void setDataReturnsEmptyMapWhenDataKeyIsAbsent() {
        when(client.getSet("xy1")).thenReturn(Map.of("other", "value"));
        when(diffChecker.hasChanges("xy1", null, null)).thenReturn(false);

        int imported = syncService.syncSet("xy1");

        assertEquals(0, imported);
        verify(importService, never()).importOrUpdateSet("xy1", Map.of());
    }

    @Test
    void totalAsStringIsParsedToInteger() {
        Map<String, Object> setData = Map.of("id", "xy1", "updatedAt", "2024/01/01", "total", "146");
        when(client.getSet("xy1")).thenReturn(Map.of("data", setData));
        when(diffChecker.hasChanges("xy1", "2024/01/01", 146)).thenReturn(true);
        when(importService.importOrUpdateSet("xy1", setData)).thenReturn(146);

        int imported = syncService.syncSet("xy1");

        assertEquals(146, imported);
        verify(diffChecker).hasChanges("xy1", "2024/01/01", 146);
    }

    @Test
    void totalAsUnparseableStringFallsBackToNull() {
        Map<String, Object> setData = Map.of("id", "xy1", "updatedAt", "2024/01/01", "total", "not-a-number");
        when(client.getSet("xy1")).thenReturn(Map.of("data", setData));
        when(diffChecker.hasChanges("xy1", "2024/01/01", null)).thenReturn(false);

        int imported = syncService.syncSet("xy1");

        assertEquals(0, imported);
        verify(diffChecker).hasChanges("xy1", "2024/01/01", null);
    }

    @Test
    void totalAsNullIsPassedAsNull() {
        Map<String, Object> setData = Map.of("id", "xy1", "updatedAt", "2024/01/01");
        when(client.getSet("xy1")).thenReturn(Map.of("data", setData));
        when(diffChecker.hasChanges("xy1", "2024/01/01", null)).thenReturn(false);

        int imported = syncService.syncSet("xy1");

        assertEquals(0, imported);
        verify(diffChecker).hasChanges(eq("xy1"), eq("2024/01/01"), isNull());
    }

    @Test
    void totalAsNonNumberNonStringIsPassedAsNull() {
        Map<String, Object> setData = Map.of("id", "xy1", "updatedAt", "2024/01/01", "total", true);
        when(client.getSet("xy1")).thenReturn(Map.of("data", setData));
        when(diffChecker.hasChanges("xy1", "2024/01/01", null)).thenReturn(false);

        int imported = syncService.syncSet("xy1");

        assertEquals(0, imported);
    }

    private Map<String, Object> setPayload(String updatedAt, int total) {
        return Map.of("data", Map.of("id", "xy1", "updatedAt", updatedAt, "total", total));
    }
}
