package com.utn.pokemontcg.game.presentation.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.utn.pokemontcg.game.application.repository.GameStateRepository;
import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.presentation.dto.GameHistoryResponse;
import com.utn.pokemontcg.game.repository.UserRepository;
import com.utn.pokemontcg.game.security.CurrentUserService;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final GameApplicationService gameService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final GameStateRepository gameStateRepository;

    public HistoryController(GameApplicationService gameService, CurrentUserService currentUserService, UserRepository userRepository, GameStateRepository gameStateRepository) {
        this.gameService = gameService;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.gameStateRepository = gameStateRepository;
    }

    @DeleteMapping
    public ResponseEntity<Void> clearHistory(@AuthenticationPrincipal UserDetails userDetails) {
        currentUserService.requireUser(userDetails);
        gameStateRepository.deleteAll();
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<GameHistoryResponse>> history(@AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        List<GameAggregate> finishedGames = gameService.getHistory(user.getPlayerId());
        List<GameHistoryResponse> history = finishedGames.stream()
            .map(game -> {
                boolean isPlayerOne = user.getPlayerId().equals(game.playerOne());
                UUID opponentId = isPlayerOne ? game.playerTwo() : game.playerOne();
                boolean won = user.getPlayerId().equals(game.winner());
                String opponentName = "Unknown";
                String opponentAvatar = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/25.png";
                if (opponentId != null) {
                    var opponent = userRepository.findByPlayerId(opponentId);
                    if (opponent.isPresent()) {
                        opponentName = opponent.get().displayUsername();
                        opponentAvatar = opponent.get().getAvatar();
                    }
                }
                return new GameHistoryResponse(
                    game.id(),
                    opponentName,
                    opponentAvatar,
                    won,
                    resolveFinishReason(game),
                    game.updatedAt()
                );
            })
            .sorted((a, b) -> b.finishedAt().compareTo(a.finishedAt()))
            .toList();
        return ResponseEntity.ok(history);
    }

    private String resolveFinishReason(GameAggregate game) {
        if (game.winner() == null) return "UNKNOWN";
        UUID loser = game.winner().equals(game.playerOne()) ? game.playerTwo() : game.playerOne();
        if (loser == null) return "UNKNOWN";
        if (game.prizeCardsRemaining().getOrDefault(game.winner(), 1) <= 0) return "PRIZES";
        if (!game.hasPokemonInPlay(loser)) return "KO_TOTAL";
        if (game.deck().getOrDefault(loser, List.of()).isEmpty()) return "DECK_OUT";
        return "UNKNOWN";
    }
}
