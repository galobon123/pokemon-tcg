package com.utn.pokemontcg.game.presentation.controller;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.utn.pokemontcg.game.application.repository.ActionLogEntry;
import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.domain.attack.AttackPatternAnalyzer;
import com.utn.pokemontcg.game.domain.attack.AttackRequirementSpec;
import com.utn.pokemontcg.game.domain.attack.AttackSpec;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.PlayerSetupState;
import com.utn.pokemontcg.game.presentation.dto.AttackEffectPreview;
import com.utn.pokemontcg.game.presentation.dto.AttackInfo;
import com.utn.pokemontcg.game.presentation.dto.AttackRequirementStep;
import com.utn.pokemontcg.game.presentation.dto.AttackRequirementsResponse;
import com.utn.pokemontcg.game.presentation.dto.BenchPokemonRequest;
import com.utn.pokemontcg.game.presentation.dto.ChooseFirstRequest;
import com.utn.pokemontcg.game.presentation.dto.CoinFlipRequest;
import com.utn.pokemontcg.game.presentation.dto.CreateGameRequest;
import com.utn.pokemontcg.game.presentation.dto.DeckCardsResponse;
import com.utn.pokemontcg.game.presentation.dto.EvolvePokemonRequest;
import com.utn.pokemontcg.game.presentation.dto.GameActionRequest;
import com.utn.pokemontcg.game.presentation.dto.GameStateResponse;
import com.utn.pokemontcg.game.presentation.dto.GameSyncResponse;
import com.utn.pokemontcg.game.presentation.dto.HandResponse;
import com.utn.pokemontcg.game.presentation.dto.JoinGameRequest;
import com.utn.pokemontcg.game.presentation.dto.PlayTrainerRequest;
import com.utn.pokemontcg.game.presentation.dto.ReplaceActiveRequest;
import com.utn.pokemontcg.game.presentation.dto.SetupConfirmRequest;
import com.utn.pokemontcg.game.presentation.dto.SetupGameRequest;
import com.utn.pokemontcg.game.presentation.dto.SetupPlaceActiveRequest;
import com.utn.pokemontcg.game.presentation.dto.SetupPlaceBenchRequest;
import com.utn.pokemontcg.game.presentation.dto.UseAbilityRequest;
import com.utn.pokemontcg.game.presentation.dto.VisibleCardInfo;
import com.utn.pokemontcg.game.repository.UserRepository;
import com.utn.pokemontcg.game.security.CurrentUserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameApplicationService gameService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final AttackPatternAnalyzer attackPatternAnalyzer;

    public GameController(GameApplicationService gameService, CurrentUserService currentUserService, UserRepository userRepository, AttackPatternAnalyzer attackPatternAnalyzer) {
        this.gameService = gameService;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.attackPatternAnalyzer = attackPatternAnalyzer;
    }

    @PostMapping
    public ResponseEntity<GameStateResponse> create(@Valid @RequestBody CreateGameRequest request,
                                                    @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(toResponse(gameService.create(user.getPlayerId(), request.deckId()), user.getPlayerId()));
    }

    @PostMapping("/{gameId}/join")
    public ResponseEntity<GameStateResponse> join(@PathVariable UUID gameId,
                                                  @Valid @RequestBody JoinGameRequest request,
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(toResponse(gameService.join(gameId, user.getPlayerId(), request.deckId()), user.getPlayerId()));
    }

    @PostMapping("/{gameId}/coin-flip")
    public ResponseEntity<GameStateResponse> coinFlip(@PathVariable UUID gameId,
                                                      @Valid @RequestBody CoinFlipRequest request,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(toResponse(gameService.flipCoin(gameId, request.choice()), user.getPlayerId()));
    }

    @PostMapping("/{gameId}/choose-first")
    public ResponseEntity<GameStateResponse> chooseFirst(@PathVariable UUID gameId,
                                                         @Valid @RequestBody ChooseFirstRequest request,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(toResponse(gameService.chooseFirstPlayer(gameId, user.getPlayerId(), request.first()), user.getPlayerId()));
    }

    @PostMapping("/{gameId}/setup")
    public ResponseEntity<GameStateResponse> setup(@PathVariable UUID gameId,
                                                   @Valid @RequestBody SetupGameRequest request,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        boolean interactive = request.interactive() == null || request.interactive();
        GameAggregate game = interactive
            ? gameService.startInteractiveSetup(
                gameId,
                request.playerOneDeckSize(), request.playerOneBasicCount(),
                request.playerTwoDeckSize(), request.playerTwoBasicCount(),
                request.playerOneDeckId(), request.playerTwoDeckId()
            )
            : gameService.runInitialSetup(
                gameId,
                request.playerOneDeckSize(), request.playerOneBasicCount(),
                request.playerTwoDeckSize(), request.playerTwoBasicCount(),
                request.playerOneDeckId(), request.playerTwoDeckId()
            );
        return ResponseEntity.ok(toResponse(game, user.getPlayerId()));
    }

    @PostMapping("/{gameId}/setup/place-active")
    public ResponseEntity<GameStateResponse> placeSetupActive(@PathVariable UUID gameId,
                                                              @Valid @RequestBody SetupPlaceActiveRequest request,
                                                              @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(toResponse(gameService.placeSetupActive(gameId, user.getPlayerId(), request.cardInstanceId()), user.getPlayerId()));
    }

    @PostMapping("/{gameId}/setup/place-bench")
    public ResponseEntity<GameStateResponse> placeSetupBench(@PathVariable UUID gameId,
                                                             @Valid @RequestBody SetupPlaceBenchRequest request,
                                                             @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(toResponse(gameService.placeSetupBench(gameId, user.getPlayerId(), request.cardInstanceIds()), user.getPlayerId()));
    }

    @PostMapping("/{gameId}/setup/confirm")
    public ResponseEntity<GameStateResponse> confirmSetup(@PathVariable UUID gameId,
                                                          @Valid @RequestBody SetupConfirmRequest request,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(toResponse(gameService.confirmSetup(gameId, user.getPlayerId()), user.getPlayerId()));
    }

    @PostMapping("/{gameId}/actions")
    public ResponseEntity<GameStateResponse> action(@PathVariable UUID gameId,
                                                     @Valid @RequestBody GameActionRequest request,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(toResponse(gameService.executeAction(
            gameId,
            request.actionType(),
            request.condition(),
            request.energyCardId(),
            request.targetSlot(),
            request.energyCardIds(),
            request.attackIndex(),
            request.coinFlipResults(),
            parseUuid(request.targetPlayerId()),
            request.selectedCardIds(),
            request.selectedEnergyCardIds(),
            request.selectedCondition(),
            request.acceptedOptionalEffect(),
            request.selectedOpponentAttackIndex()
        ), user.getPlayerId()));
    }

    @PostMapping("/{gameId}/concede")
    public ResponseEntity<GameStateResponse> concede(@PathVariable UUID gameId,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(toResponse(gameService.concede(gameId, user.getPlayerId()), user.getPlayerId()));
    }

    @PostMapping("/{gameId}/actions/bench-pokemon")
    public ResponseEntity<GameStateResponse> benchPokemon(@PathVariable UUID gameId,
                                                          @Valid @RequestBody BenchPokemonRequest request,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(toResponse(gameService.benchPokemon(gameId, user.getPlayerId(), request.cardInstanceId(), request.targetSlot()), user.getPlayerId()));
    }

    @PostMapping("/{gameId}/actions/evolve")
    public ResponseEntity<GameStateResponse> evolvePokemon(@PathVariable UUID gameId,
                                                           @Valid @RequestBody EvolvePokemonRequest request,
                                                           @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(toResponse(gameService.evolvePokemon(
            gameId,
            user.getPlayerId(),
            request.fromCardInstanceId(),
            request.toCardInstanceId(),
            request.targetSlot()
        ), user.getPlayerId()));
    }

    @PostMapping("/{gameId}/actions/play-trainer")
    public ResponseEntity<GameStateResponse> playTrainer(@PathVariable UUID gameId,
                                                         @Valid @RequestBody PlayTrainerRequest request,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(toResponse(gameService.playTrainer(
            gameId,
            user.getPlayerId(),
            request.cardInstanceId(),
            request.targetSlot(),
            request.targetId(),
            parseUuid(request.targetPlayerId()),
            request.selectedCardIds(),
            request.selectedEnergyCardIds(),
            request.selectedCondition(),
            request.acceptedOptionalEffect(),
            request.coinFlipResults()
        ), user.getPlayerId()));
    }

    @GetMapping("/{gameId}/players/{playerId}/deck-preview")
    public ResponseEntity<com.utn.pokemontcg.game.presentation.dto.DeckPreviewResponse> deckPreview(
        @PathVariable UUID gameId,
        @PathVariable UUID playerId,
        @RequestParam(name = "limit", required = false, defaultValue = "7") int limit,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        // No authentication/ownership enforcement here beyond player belongs to game; controller trusts service to validate.
        var preview = gameService.deckPreview(gameId, playerId, limit);
        return ResponseEntity.ok(new com.utn.pokemontcg.game.presentation.dto.DeckPreviewResponse(preview));
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    @PostMapping("/{gameId}/actions/use-ability")
    public ResponseEntity<GameStateResponse> useAbility(@PathVariable UUID gameId,
                                                         @Valid @RequestBody UseAbilityRequest request,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(toResponse(gameService.useAbility(
            gameId,
            user.getPlayerId(),
            request.slotReference(),
            request.abilityIndex(),
            request.targetId()
        ), user.getPlayerId()));
    }

    @PostMapping("/{gameId}/actions/replace-active")
    public ResponseEntity<GameStateResponse> replaceActive(@PathVariable UUID gameId,
                                                           @Valid @RequestBody ReplaceActiveRequest request,
                                                           @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(toResponse(gameService.replaceActive(
            gameId,
            user.getPlayerId(),
            request.benchSlot()
        ), user.getPlayerId()));
    }

    @GetMapping("/{gameId}/actions")
    public ResponseEntity<Map<String, List<ActionLogEntry>>> actions(@PathVariable UUID gameId,
                                                                     @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(Map.of("entries", gameService.actionLog(gameId)));
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<GameStateResponse> state(@PathVariable UUID gameId,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        return ResponseEntity.ok(toResponse(gameService.get(gameId), user.getPlayerId()));
    }

    @GetMapping("/{gameId}/players/{playerId}/hand")
    public ResponseEntity<HandResponse> hand(@PathVariable UUID gameId,
                                              @PathVariable UUID playerId,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        List<String> cardIds = gameService.getHandCardIds(gameId, playerId);
        return ResponseEntity.ok(new HandResponse(
            gameId.toString(),
            playerId.toString(),
            cardIds,
            cardIds.size()
        ));
    }

    @GetMapping("/{gameId}/players/{playerId}/deck")
    public ResponseEntity<DeckCardsResponse> deck(@PathVariable UUID gameId,
                                                  @PathVariable UUID playerId,
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        if (!user.getPlayerId().equals(playerId)) {
            throw new org.springframework.security.access.AccessDeniedException("No tenes permiso para ver este mazo");
        }
        GameAggregate game = gameService.get(gameId);
        List<String> cardIds = gameService.getDeckCardIds(gameId, playerId);
        List<VisibleCardInfo> cards = cardIds.stream()
            .map(game.cardCatalog()::get)
            .filter(java.util.Objects::nonNull)
            .map(this::toVisibleCardInfo)
            .toList();
        return ResponseEntity.ok(new DeckCardsResponse(
            gameId.toString(),
            playerId.toString(),
            cardIds,
            cards,
            cardIds.size()
        ));
    }

    @GetMapping("/{gameId}/attacks/{attackIndex}/requirements")
    public ResponseEntity<AttackRequirementsResponse> attackRequirements(@PathVariable UUID gameId,
                                                                         @PathVariable int attackIndex,
                                                                         @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        GameAggregate game = gameService.get(gameId);
        UUID playerId = user.getPlayerId();
        if (!playerId.equals(game.currentTurnPlayer())) {
            throw new org.springframework.security.access.AccessDeniedException("No podes preparar ataques fuera de tu turno");
        }

        GameCard activeCard = game.cardCatalog().get(game.activePokemon().get(playerId));
        AttackText attackText = selectedAttackText(activeCard, attackIndex);
        String normalizedText = normalizeText(attackText.name() + " " + attackText.text());
        AttackSpec spec = attackPatternAnalyzer.analyze(attackText.name(), attackText.text(), attackText.baseDamage());
        List<AttackRequirementStep> steps = spec.requirements().stream()
            .map(requirement -> toRequirementStep(requirement, game, playerId, normalizedText))
            .toList();
        List<AttackEffectPreview> effects = spec.effects().stream()
            .map(effect -> new AttackEffectPreview(effect.type(), effect.label()))
            .toList();

        return ResponseEntity.ok(new AttackRequirementsResponse(
            gameId.toString(),
            playerId.toString(),
            attackIndex,
            attackText.name(),
            attackText.text(),
            steps,
            effects,
            spec.coinFlipCount()
        ));
    }

    @GetMapping("/{gameId}/logs")
    public ResponseEntity<Map<String, List<ActionLogEntry>>> actionLog(@PathVariable UUID gameId,
                                                                       @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(Map.of("entries", gameService.actionLog(gameId)));
    }

    @GetMapping("/{gameId}/sync")
    public ResponseEntity<GameSyncResponse> sync(@PathVariable UUID gameId,
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        var user = currentUserService.requireUser(userDetails);
        GameAggregate game = gameService.get(gameId);
        return ResponseEntity.ok(new GameSyncResponse(
            toResponse(game, user.getPlayerId()),
            gameService.actionLog(gameId),
            Instant.now()
        ));
    }

    private GameStateResponse toResponse(GameAggregate game, UUID requestingPlayer) {
        return new GameStateResponse(
            game.id(),
            game.gameState().name(),
            responsePhase(game),
            game.setupPhase() != null ? game.setupPhase().name() : null,
            game.currentTurnPlayer(),
            game.firstPlayer(),
            game.winner(),
            game.coinFlipWinner(),
            game.coinResult(),
            game.coinFlipChoice(),
            game.playerOne(),
            game.playerTwo(),
            setupSummary(game),
            visibleActivePokemon(game),
            visibleBench(game),
            handSizes(game),
            game.deckCardsRemaining(),
            game.prizeCardsRemaining(),
            game.discardPile(),
            game.activeDamageCounters(),
            game.activeAttachedEnergy(),
            game.attachedEnergies(),
            game.turnFlags(),
            game.statusByPlayer(),
            game.suddenDeath(),
            game.replacementPlayerId() != null,
            game.replacementPlayerId(),
            game.pendingForcedSwitchPlayerId(),
            playerNames(game),
            playerAvatars(game),
            visibleActivePokemonInfo(game, requestingPlayer),
            visibleBenchInfo(game, requestingPlayer),
            revealedHandCards(game),
            game.setupCountdownEndsAt(),
            game.firstTurn(),
            game.turnsStartedByPlayer(),
            game.toolsBySlot(),
            game.damageCountersBySlot(),
            game.activeStadium(),
            game.activeStadiumOwner(),
            resolveStadiumName(game)
        );
    }

    private Map<UUID, String> playerNames(GameAggregate game) {
        Map<UUID, String> names = new LinkedHashMap<>();
        if (game.playerOne() != null) {
            userRepository.findByPlayerId(game.playerOne())
                .ifPresent(user -> names.put(game.playerOne(), user.displayUsername()));
        }
        if (game.playerTwo() != null) {
            userRepository.findByPlayerId(game.playerTwo())
                .ifPresent(user -> names.put(game.playerTwo(), user.displayUsername()));
        }
        return names;
    }

    private String resolveStadiumName(GameAggregate game) {
        if (game.activeStadium() == null) return null;
        GameCard card = game.cardCatalog().get(game.activeStadium());
        return card != null ? card.name() : null;
    }

    private Map<UUID, String> playerAvatars(GameAggregate game) {
        Map<UUID, String> avatars = new LinkedHashMap<>();
        if (game.playerOne() != null) {
            userRepository.findByPlayerId(game.playerOne())
                .ifPresent(user -> avatars.put(game.playerOne(), user.getAvatar()));
        }
        if (game.playerTwo() != null) {
            userRepository.findByPlayerId(game.playerTwo())
                .ifPresent(user -> avatars.put(game.playerTwo(), user.getAvatar()));
        }
        return avatars;
    }

    private Map<UUID, VisibleCardInfo> visibleActivePokemonInfo(GameAggregate game, UUID requestingPlayer) {
        Map<UUID, VisibleCardInfo> info = new HashMap<>();
        boolean shouldHideOpponent = game.gameState() == GameState.SETUP || game.gameState() == GameState.COIN_FLIP;
        game.activePokemon().forEach((playerId, cardInstanceId) -> {
            if (shouldHideOpponent && requestingPlayer != null && !playerId.equals(requestingPlayer)) {
                return;
            }
            GameCard card = game.cardCatalog().get(cardInstanceId);
            if (card != null) {
                info.put(playerId, toVisibleCardInfo(card));
            }
        });
        return info;
    }

    private Map<UUID, List<VisibleCardInfo>> visibleBenchInfo(GameAggregate game, UUID requestingPlayer) {
        Map<UUID, List<VisibleCardInfo>> info = new HashMap<>();
        boolean shouldHideOpponent = game.gameState() == GameState.SETUP || game.gameState() == GameState.COIN_FLIP;
        game.bench().forEach((playerId, benchCards) -> {
            if (shouldHideOpponent && requestingPlayer != null && !playerId.equals(requestingPlayer)) {
                return;
            }
            List<VisibleCardInfo> visibleCards = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String cardInstanceId = i < benchCards.size() ? benchCards.get(i) : null;
                if (cardInstanceId != null) {
                    GameCard card = game.cardCatalog().get(cardInstanceId);
                    if (card != null) {
                        visibleCards.add(toVisibleCardInfo(card));
                    } else {
                        visibleCards.add(null);
                    }
                } else {
                    visibleCards.add(null);
                }
            }
            info.put(playerId, visibleCards);
        });
        return info;
    }

    private Map<UUID, List<VisibleCardInfo>> revealedHandCards(GameAggregate game) {
        Map<UUID, List<VisibleCardInfo>> result = new HashMap<>();
        game.setupByPlayer().forEach((player, setup) -> {
            List<String> revealedIds = setup.revealedHandCardIds();
            if (revealedIds == null || revealedIds.isEmpty()) return;
            List<VisibleCardInfo> cards = revealedIds.stream()
                .map(game.cardCatalog()::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toVisibleCardInfo)
                .toList();
            if (!cards.isEmpty()) {
                result.put(player, cards);
            }
        });
        return result;
    }

    private VisibleCardInfo toVisibleCardInfo(GameCard card) {
        String rawCardId = card.id();
        String cardId = rawCardId.contains(":")
            ? rawCardId.substring(0, rawCardId.indexOf(':'))
            : rawCardId;
        String setId = cardId.contains("-")
            ? cardId.substring(0, cardId.indexOf('-'))
            : "";
        List<AttackInfo> attackInfos = card.attacks().stream()
            .map(a -> new AttackInfo(a.name(), a.text(), a.damage(), a.cost(), a.convertedEnergyCost()))
            .toList();
        return new VisibleCardInfo(
            cardId,
            setId,
            card.name(),
            card.supertype(),
            card.subtypes(),
            card.hp(),
            card.attackName(),
            card.attackText(),
            card.attackDamage(),
            card.attackRequiredEnergy(),
            card.energyType(),
            card.attackCost(),
            card.retreatCost(),
            card.evolvesFrom(),
            card.abilities(),
            card.weaknessType(),
            card.weaknessAmount(),
            card.resistanceType(),
            card.resistanceAmount(),
            rawCardId,
            attackInfos
        );
    }

    private AttackText selectedAttackText(GameCard activeCard, int attackIndex) {
        if (activeCard == null) {
            return new AttackText("Ataque", "", 0);
        }
        if (!activeCard.attacks().isEmpty() && attackIndex >= 0 && attackIndex < activeCard.attacks().size()) {
            var attack = activeCard.attacks().get(attackIndex);
            return new AttackText(
                attack.name() == null || attack.name().isBlank() ? activeCard.name() : attack.name(),
                attack.text() == null ? "" : attack.text(),
                parseDamage(attack.damage())
            );
        }
        return new AttackText(
            activeCard.attackName() == null || activeCard.attackName().isBlank() ? activeCard.name() : activeCard.attackName(),
            activeCard.attackText() == null ? "" : activeCard.attackText(),
            activeCard.attackDamage()
        );
    }

    private AttackRequirementStep toRequirementStep(AttackRequirementSpec requirement, GameAggregate game, UUID playerId, String normalizedText) {
        UUID opponentId = playerId.equals(game.playerOne()) ? game.playerTwo() : game.playerOne();
        List<VisibleCardInfo> cards = switch (requirement.type()) {
            case "ORDER_DECK_TOP" -> visibleDeckCards(game, playerId, requirement.count(), normalizedText, true);
            case "SELECT_DECK_CARDS" -> visibleDeckCards(game, playerId, 0, normalizedText, false);
            case "VIEW_DECK_TOP" -> visibleDeckCards(game, opponentId, requirement.count(), normalizedText, true);
            case "VIEW_HAND", "SELECT_HAND_CARD" -> visibleHandCards(game, opponentId);
            case "SELECT_OPPONENT_ATTACK" -> visibleOpponentActiveCard(game, opponentId);
            default -> List.of();
        };
        int min = requirement.min();
        int max = requirement.max();
        if ("ORDER_DECK_TOP".equals(requirement.type()) || "VIEW_DECK_TOP".equals(requirement.type())) {
            min = cards.size();
            max = cards.size();
        }
        if ("VIEW_DECK_TOP".equals(requirement.type())) {
            min = 0;
        }
        if ("SELECT_DECK_CARDS".equals(requirement.type())) {
            min = cards.isEmpty() ? 0 : Math.min(requirement.min(), cards.size());
            max = Math.min(requirement.max(), cards.size());
        }
        if ("VIEW_HAND".equals(requirement.type())) {
            min = 0;
            max = cards.size();
        }
        if ("SELECT_HAND_CARD".equals(requirement.type())) {
            min = cards.isEmpty() ? 0 : Math.min(requirement.min(), cards.size());
            max = Math.min(requirement.max(), cards.size());
        }
        return new AttackRequirementStep(
            requirement.type(),
            requirement.scope(),
            min,
            max,
            requirement.count(),
            requirement.required(),
            cards,
            requirement.message(),
            requirement.options()
        );
    }

    private List<VisibleCardInfo> visibleDeckCards(GameAggregate game, UUID playerId, int limit, String text, boolean topOnly) {
        List<String> deck = game.deck().getOrDefault(playerId, List.of());
        List<String> source = topOnly
            ? deck.subList(0, Math.min(limit, deck.size()))
            : deck;
        return source.stream()
            .map(game.cardCatalog()::get)
            .filter(java.util.Objects::nonNull)
            .filter(card -> topOnly || cardMatchesDeckRequirement(card, text))
            .map(this::toVisibleCardInfo)
            .toList();
    }

    private List<VisibleCardInfo> visibleHandCards(GameAggregate game, UUID playerId) {
        return game.hand().getOrDefault(playerId, List.of()).stream()
            .map(game.cardCatalog()::get)
            .filter(java.util.Objects::nonNull)
            .map(this::toVisibleCardInfo)
            .toList();
    }

    private List<VisibleCardInfo> visibleOpponentActiveCard(GameAggregate game, UUID opponentId) {
        String activeCardId = game.activePokemon().get(opponentId);
        if (activeCardId == null) return List.of();
        GameCard card = game.cardCatalog().get(activeCardId);
        if (card == null) return List.of();
        return List.of(toVisibleCardInfo(card));
    }

    private boolean cardMatchesDeckRequirement(GameCard card, String text) {
        if (text.contains("fire energy")) return card.isEnergy() && energyType(card).equalsIgnoreCase("Fire");
        if (text.contains("water energy")) return card.isEnergy() && energyType(card).equalsIgnoreCase("Water");
        if (text.contains("grass energy")) return card.isEnergy() && energyType(card).equalsIgnoreCase("Grass");
        if (text.contains("lightning energy")) return card.isEnergy() && energyType(card).equalsIgnoreCase("Lightning");
        if (text.contains("psychic energy")) return card.isEnergy() && energyType(card).equalsIgnoreCase("Psychic");
        if (text.contains("fighting energy")) return card.isEnergy() && energyType(card).equalsIgnoreCase("Fighting");
        if (text.contains("darkness energy")) return card.isEnergy() && energyType(card).equalsIgnoreCase("Darkness");
        if (text.contains("metal energy")) return card.isEnergy() && energyType(card).equalsIgnoreCase("Metal");
        if (text.contains("energy")) return card.isEnergy();
        if (containsAny(text, "pokemon", "pokémon")) return card.isPokemon();
        if (text.contains("supporter")) return card.hasSubtype("Supporter");
        return true;
    }

    private String energyType(GameCard card) {
        if (card.energyType() != null && !card.energyType().isBlank() && !"Energy".equalsIgnoreCase(card.energyType().trim())) return card.energyType();
        return card.name() == null ? "" : card.name().replaceAll("(?i)\\s+energy$", "");
    }

    private boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) return true;
        }
        return false;
    }

    private String normalizeText(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }

    private int parseDamage(String damage) {
        if (damage == null || damage.isBlank()) return 0;
        try {
            return Integer.parseInt(damage.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record AttackText(String name, String text, int baseDamage) {}

    private Map<UUID, PlayerSetupState> setupSummary(GameAggregate game) {
        Map<UUID, PlayerSetupState> setup = new HashMap<>();
        game.setupByPlayer().forEach((player, original) -> {
            PlayerSetupState current = new PlayerSetupState();
            current.setMulligans(original.mulligans());
            current.setHandSize(game.hand().getOrDefault(player, List.of()).size());
            current.setBenchCount(game.benchOccupiedCount(player));
            current.setPrizeCount(game.prizeCards().getOrDefault(player, List.of()).size());
            current.setHasActive(game.activePokemon().get(player) != null);
            if (original.revealedHandCardIds() != null) {
                current.setRevealedHandCardIds(original.revealedHandCardIds());
            }
            setup.put(player, current);
        });
        return setup;
    }

    private Map<UUID, Integer> handSizes(GameAggregate game) {
        Map<UUID, Integer> sizes = new HashMap<>();
        game.hand().forEach((player, hand) -> sizes.put(player, hand.size()));
        return sizes;
    }

    private String responsePhase(GameAggregate game) {
        return game.gameState() == GameState.SETUP && game.setupPhase() != null
            ? game.setupPhase().name()
            : game.turnPhase().name();
    }

    private Map<UUID, ?> visibleActivePokemon(GameAggregate game) {
        return hideSetupCards(game) ? Map.of() : game.activePokemon();
    }

    private Map<UUID, List<String>> visibleBench(GameAggregate game) {
        if (hideSetupCards(game)) return Map.of();
        Map<UUID, List<String>> result = new HashMap<>();
        game.bench().forEach((playerId, benchCards) -> {
            result.put(playerId, benchCards.stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList()));
        });
        return result;
    }

    private boolean hideSetupCards(GameAggregate game) {
        return game.gameState() == GameState.SETUP || game.gameState() == GameState.COIN_FLIP;
    }
}
