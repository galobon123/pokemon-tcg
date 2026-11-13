package com.utn.pokemontcg.card.presentation;

import com.utn.pokemontcg.card.infrastructure.CardSetSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/sync")
public class CardSyncController {

    private static final Logger log = LoggerFactory.getLogger(CardSyncController.class);

    private final CardSetSyncService cardSetSyncService;

    public CardSyncController(CardSetSyncService cardSetSyncService) {
        this.cardSetSyncService = cardSetSyncService;
    }

    @PostMapping("/sets/{setId}")
    public ResponseEntity<Map<String, Object>> triggerSetSync(@PathVariable String setId) {
        log.info("Sync manual solicitado para set {}", setId);
        int imported = cardSetSyncService.syncSet(setId);
        return ResponseEntity.ok(Map.of(
            "setId", setId,
            "imported", imported,
            "message", imported == 0
                ? "Set sin cambios detectados"
                : "Sync completado para set: " + setId
        ));
    }
}
