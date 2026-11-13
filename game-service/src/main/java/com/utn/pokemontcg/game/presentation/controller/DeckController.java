package com.utn.pokemontcg.game.presentation.controller;

import com.utn.pokemontcg.game.application.service.deck.DeckService;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckUpsertSelfRequest;
import com.utn.pokemontcg.game.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/decks")
public class DeckController {

    private final DeckService deckService;
    private final CurrentUserService currentUserService;

    public DeckController(DeckService deckService, CurrentUserService currentUserService) {
        this.deckService = deckService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<DeckResponse> create(@Valid @RequestBody DeckUpsertSelfRequest request,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(deckService.createForPlayer(user.getPlayerId(), request));
    }

    @PutMapping("/{deckId}")
    public ResponseEntity<DeckResponse> update(@PathVariable("deckId") UUID deckId,
                                               @Valid @RequestBody DeckUpsertSelfRequest request,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(deckService.updateForPlayer(user.getPlayerId(), deckId, request));
    }

    @GetMapping("/{deckId}")
    public ResponseEntity<DeckResponse> get(@PathVariable("deckId") UUID deckId,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(deckService.getForPlayer(user.getPlayerId(), deckId));
    }

    @DeleteMapping("/{deckId}")
    public ResponseEntity<Void> delete(@PathVariable("deckId") UUID deckId,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        deckService.deleteForPlayer(user.getPlayerId(), deckId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<List<DeckResponse>> listMine(@AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(deckService.listByPlayer(user.getPlayerId()));
    }
}
