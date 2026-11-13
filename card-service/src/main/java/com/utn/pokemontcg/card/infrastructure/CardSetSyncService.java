package com.utn.pokemontcg.card.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CardSetSyncService {

    private static final Logger log = LoggerFactory.getLogger(CardSetSyncService.class);

    private final PokemonTcgClient pokemonTcgClient;
    private final CardDiffChecker cardDiffChecker;
    private final CardImportService cardImportService;

    public CardSetSyncService(PokemonTcgClient pokemonTcgClient,
                              CardDiffChecker cardDiffChecker,
                              CardImportService cardImportService) {
        this.pokemonTcgClient = pokemonTcgClient;
        this.cardDiffChecker = cardDiffChecker;
        this.cardImportService = cardImportService;
    }

    @Scheduled(cron = "${card.sync.cron:0 0 3 * * MON}")
    public void scheduledSync() {
        syncSet("xy1");
    }

    public int syncSet(String setId) {
        log.info("Iniciando sync del set {}", setId);
        Map<String, Object> setData = setData(pokemonTcgClient.getSet(setId));
        String apiUpdatedAt = text(setData.get("updatedAt"));
        Integer apiTotal = integer(setData.get("total"));

        if (!cardDiffChecker.hasChanges(setId, apiUpdatedAt, apiTotal)) {
            log.info("Set {} sin cambios detectados, sync omitido", setId);
            return 0;
        }

        log.info("Cambios detectados en set {}, importando cartas", setId);
        int imported = cardImportService.importOrUpdateSet(setId, setData);
        log.info("Sync completado para set {} con {} cartas actualizadas", setId, imported);
        return imported;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> setData(Map<String, Object> response) {
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
