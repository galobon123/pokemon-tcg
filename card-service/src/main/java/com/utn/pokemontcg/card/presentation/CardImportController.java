package com.utn.pokemontcg.card.presentation;

import com.utn.pokemontcg.card.infrastructure.CardImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/cards/import")
public class CardImportController {

    private final CardImportService cardImportService;

    public CardImportController(CardImportService cardImportService) {
        this.cardImportService = cardImportService;
    }

    @PostMapping("/{setId}")
    public ResponseEntity<Map<String, Object>> importSet(@PathVariable String setId) {
        int count = cardImportService.importSet(setId);
        return ResponseEntity.ok(Map.of(
            "setId", setId,
            "imported", count,
            "message", count + " cartas importadas correctamente"
        ));
    }
}
