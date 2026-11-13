package com.utn.pokemontcg.game.application.service;

import com.utn.pokemontcg.game.application.repository.ActionLogEntry;
import com.utn.pokemontcg.game.application.repository.GameStateRepository;
import com.utn.pokemontcg.game.application.service.deck.DeckService;
import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.engine.DamageCalculator;
import com.utn.pokemontcg.game.domain.engine.RuleValidator;
import com.utn.pokemontcg.game.domain.engine.StatusEffectManager;
import com.utn.pokemontcg.game.domain.event.GameEventType;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.PlayerSetupState;
import com.utn.pokemontcg.game.domain.model.SetupPhase;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectContext;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.FairyGardenStadiumEffect;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectContext;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.StadiumEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectResult;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectStrategy;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckCardInput;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

@Service
public class GameApplicationService {

    private static final Logger log = LoggerFactory.getLogger(GameApplicationService.class);

    private final GameStateRepository gameStateRepository;
    private final GameEngineFacade gameEngineFacade;
    private final SetupEngineService setupEngineService;
    private final TurnActionValidator turnActionValidator;
    private final VictoryService victoryService;
    private final RuleValidator ruleValidator;
    private final DamageCalculator damageCalculator;
    private final StatusEffectManager statusEffectManager;
    private final DeckService deckService;
    private final TrainerEffectRegistry trainerEffectRegistry;
    private final AbilityEffectRegistry abilityEffectRegistry;
    private final StadiumEffectRegistry stadiumEffectRegistry;

    public GameApplicationService(
        GameStateRepository gameStateRepository,
        GameEngineFacade gameEngineFacade,
        SetupEngineService setupEngineService,
        TurnActionValidator turnActionValidator,
        VictoryService victoryService,
        RuleValidator ruleValidator,
        DamageCalculator damageCalculator,
        StatusEffectManager statusEffectManager
    ) {
        this(
            gameStateRepository,
            gameEngineFacade,
            setupEngineService,
            turnActionValidator,
            victoryService,
            ruleValidator,
            damageCalculator,
            statusEffectManager,
            null,
            null,
            null,
            null
        );
    }

    public GameApplicationService(
        GameStateRepository gameStateRepository,
        GameEngineFacade gameEngineFacade,
        SetupEngineService setupEngineService,
        TurnActionValidator turnActionValidator,
        VictoryService victoryService,
        RuleValidator ruleValidator,
        DamageCalculator damageCalculator,
        StatusEffectManager statusEffectManager,
        DeckService deckService
    ) {
        this(
            gameStateRepository,
            gameEngineFacade,
            setupEngineService,
            turnActionValidator,
            victoryService,
            ruleValidator,
            damageCalculator,
            statusEffectManager,
            deckService,
            null,
            null,
            null
        );
    }

    @Autowired
    public GameApplicationService(
        GameStateRepository gameStateRepository,
        GameEngineFacade gameEngineFacade,
        SetupEngineService setupEngineService,
        TurnActionValidator turnActionValidator,
        VictoryService victoryService,
        RuleValidator ruleValidator,
        DamageCalculator damageCalculator,
        StatusEffectManager statusEffectManager,
        DeckService deckService,
        TrainerEffectRegistry trainerEffectRegistry,
        AbilityEffectRegistry abilityEffectRegistry,
        StadiumEffectRegistry stadiumEffectRegistry
    ) {
        this.gameStateRepository = gameStateRepository;
        this.gameEngineFacade = gameEngineFacade;
        this.setupEngineService = setupEngineService;
        this.turnActionValidator = turnActionValidator;
        this.victoryService = victoryService;
        this.ruleValidator = ruleValidator;
        this.damageCalculator = damageCalculator;
        this.statusEffectManager = statusEffectManager;
        this.deckService = deckService;
        this.trainerEffectRegistry = trainerEffectRegistry;
        this.abilityEffectRegistry = abilityEffectRegistry;
        this.stadiumEffectRegistry = stadiumEffectRegistry;
    }

    public GameAggregate create(UUID playerId) {
        return create(playerId, null);
    }

    public GameAggregate create(UUID playerId, UUID deckId) {
        GameAggregate game = new GameAggregate(playerId);
        if (deckId != null) {
            game.deckIdsByPlayer().put(playerId, deckId);
        }
        GameAggregate saved = gameStateRepository.save(game);
        gameEngineFacade.publish(saved, GameEventType.GAME_CREATED, eventPayloadWithDeck("playerOne", playerId, deckId));
        return saved;
    }

    public GameAggregate flipCoin(UUID gameId, String choice) {
        GameAggregate game = get(gameId);
        if (game.playerTwo() == null) {
            throw new IllegalStateException("Se necesitan dos jugadores para lanzar la moneda");
        }
        require(game.gameState() == GameState.COIN_FLIP, "La partida no esta en fase de lanzamiento de moneda");
        if (game.coinFlipWinner() != null) {
            return game;
        }

        boolean heads = Math.random() < 0.5;
        String coinResult = heads ? "HEADS" : "TAILS";
        game.setCoinResult(coinResult);

        UUID coinFlipWinner;
        if (choice != null) {
            game.setCoinFlipChoice(choice);
            coinFlipWinner = coinResult.equals(choice) ? game.playerOne() : game.playerTwo();
        } else {
            coinFlipWinner = heads ? game.playerOne() : game.playerTwo();
        }
        game.setCoinFlipWinner(coinFlipWinner);
        gameStateRepository.save(game);

        Map<String, Object> coinFlipPayload = new java.util.HashMap<>();
        coinFlipPayload.put("coinResult", coinResult);
        coinFlipPayload.put("coinFlipWinner", coinFlipWinner);
        coinFlipPayload.put("coinFlipChoice", game.coinFlipChoice());
        coinFlipPayload.put("playerOne", game.playerOne());
        coinFlipPayload.put("playerTwo", game.playerTwo());
        gameEngineFacade.publish(game, GameEventType.COIN_FLIP, coinFlipPayload);

        return game;
    }

    public List<String> deckPreview(UUID gameId, UUID playerId, int limit) {
        GameAggregate game = get(gameId);
        if (!playerBelongsToGame(game, playerId)) {
            throw new IllegalArgumentException("El jugador no pertenece a la partida");
        }
        List<String> deck = game.deck().getOrDefault(playerId, List.of());
        int end = Math.min(limit, deck.size());
        return new ArrayList<>(deck.subList(0, end));
    }

    public GameAggregate chooseFirstPlayer(UUID gameId, UUID playerId, boolean chooseFirst) {
        GameAggregate game = get(gameId);
        require(game.gameState() == GameState.COIN_FLIP, "La partida no esta en fase de lanzamiento de moneda");
        require(game.coinFlipWinner() != null, "Se debe lanzar la moneda primero");
        require(playerId.equals(game.coinFlipWinner()), "Solo el ganador del lanzamiento puede elegir");
        require(game.firstPlayer() == null, "Ya se eligio el jugador inicial");

        UUID first = chooseFirst ? playerId : opponentOf(game, playerId);
        game.setFirstPlayer(first);
        startGameAfterCoinFlip(game);

        gameEngineFacade.publish(game, GameEventType.FIRST_PLAYER_CHOSEN, Map.of(
            "firstPlayer", game.firstPlayer(),
            "chosenBy", playerId
        ));

        return gameStateRepository.save(game);
    }

    public GameAggregate startInteractiveSetup(
        UUID gameId,
        Integer playerOneDeckSize,
        Integer playerOneBasicCount,
        Integer playerTwoDeckSize,
        Integer playerTwoBasicCount,
        UUID playerOneDeckId,
        UUID playerTwoDeckId
    ) {
        GameAggregate game = get(gameId);

        if (game.gameState() == GameState.SETUP) {
            return game;
        }

        if (game.playerTwo() == null) {
            throw new IllegalStateException("Two players are required before setup");
        }

        registerRequestedDeckIds(game, playerOneDeckId, playerTwoDeckId);

        UUID resolvedPlayerOneDeckId = game.deckIdsByPlayer().get(game.playerOne());
        UUID resolvedPlayerTwoDeckId = game.deckIdsByPlayer().get(game.playerTwo());

        var p1 = hasBothDecks(resolvedPlayerOneDeckId, resolvedPlayerTwoDeckId)
            ? setupEngineService.preparePlayerOpeningHand(
                game.playerOne(),
                realDeckCardsFor(game.playerOne(), resolvedPlayerOneDeckId),
                0,
                game
            )
            : setupEngineService.preparePlayerOpeningHand(
                game.playerOne(),
                require(playerOneDeckSize, "playerOneDeckSize"),
                require(playerOneBasicCount, "playerOneBasicCount"),
                0,
                game
            );

        var p2 = hasBothDecks(resolvedPlayerOneDeckId, resolvedPlayerTwoDeckId)
            ? setupEngineService.preparePlayerOpeningHand(
                game.playerTwo(),
                realDeckCardsFor(game.playerTwo(), resolvedPlayerTwoDeckId),
                p1.mulligans(),
                game
            )
            : setupEngineService.preparePlayerOpeningHand(
                game.playerTwo(),
                require(playerTwoDeckSize, "playerTwoDeckSize"),
                require(playerTwoBasicCount, "playerTwoBasicCount"),
                p1.mulligans(),
                game
            );

        drawOptionalMulliganCards(game, game.playerOne(), p2.mulligans());
        drawOptionalMulliganCards(game, game.playerTwo(), p1.mulligans());
        p1.setHandSize(game.hand().get(game.playerOne()).size());
        p2.setHandSize(game.hand().get(game.playerTwo()).size());

        game.setupByPlayer().put(game.playerOne(), p1);
        game.setupByPlayer().put(game.playerTwo(), p2);
        game.setupConfirmedByPlayer().clear();
        game.setCurrentTurnPlayer(null);
        game.setSetupPhase(SetupPhase.SETUP_WAITING_ACTIVE);
        game.setSetupCountdownEndsAt(Instant.now().plusSeconds(60));
        gameEngineFacade.startSetup(game);

        GameAggregate saved = gameStateRepository.save(game);
        gameEngineFacade.publish(saved, GameEventType.GAME_SETUP, Map.of(
            "setupPhase", saved.setupPhase().name()
        ));
        return saved;
    }

    public GameAggregate placeSetupActive(UUID gameId, UUID playerId, String cardInstanceId) {
        GameAggregate game = get(gameId);
        requireSetupPlayer(game, playerId);
        require(game.setupPhase() != null && game.setupPhase() != SetupPhase.SETUP_REVEAL, "La partida no esta en fase de preparacion");
        require(game.activePokemon().get(playerId) == null, "El jugador ya eligio Pokemon Activo");
        require(game.hand().getOrDefault(playerId, List.of()).contains(cardInstanceId), "La carta no esta en la mano");
        GameCard card = game.cardCatalog().get(cardInstanceId);
        require(card != null && card.isBasicPokemon(), "El Activo inicial debe ser un Pokemon Basico");

        game.hand().get(playerId).remove(cardInstanceId);
        game.placeSetupActive(playerId, cardInstanceId);
        refreshSetupSummary(game, playerId);

        if (game.activePokemon().get(game.playerOne()) != null && game.activePokemon().get(game.playerTwo()) != null) {
            game.setSetupPhase(SetupPhase.SETUP_WAITING_BENCH);
        }

        gameEngineFacade.publish(game, GameEventType.SETUP_ACTIVE_PLACED, Map.of(
            "playerId", playerId,
            "setupPhase", game.setupPhase().name(),
            "remainingHandSize", game.hand().getOrDefault(playerId, List.of()).size()
        ));
        appendAction(game, GameActionType.SETUP_PLACE_ACTIVE, playerId);
        return gameStateRepository.save(game);
    }

    public GameAggregate placeSetupBench(UUID gameId, UUID playerId, List<String> cardInstanceIds) {
        GameAggregate game = get(gameId);
        requireSetupPlayer(game, playerId);
        require(game.setupPhase() != null && game.setupPhase() != SetupPhase.SETUP_REVEAL, "La partida no esta en fase de preparacion");
        require(game.activePokemon().get(playerId) != null, "Primero hay que elegir Pokemon Activo");
        List<String> cards = cardInstanceIds == null ? List.of() : cardInstanceIds;
        require(game.benchOccupiedCount(playerId) + cards.size() <= 5, "La banca solo admite hasta 5 Pokemon");

        for (String cardInstanceId : cards) {
            require(game.hand().getOrDefault(playerId, List.of()).contains(cardInstanceId), "La carta " + cardInstanceId + " no esta en la mano");
            GameCard card = game.cardCatalog().get(cardInstanceId);
            require(card != null && card.isBasicPokemon(), "Solo se pueden poner Pokemon Basicos en Banca durante setup");
        }

        game.hand().get(playerId).removeAll(cards);
        game.placeSetupBench(playerId, cards);
        refreshSetupSummary(game, playerId);

        gameEngineFacade.publish(game, GameEventType.SETUP_BENCH_PLACED, Map.of(
            "playerId", playerId,
            "benchCount", game.benchOccupiedCount(playerId),
            "remainingHandSize", game.hand().getOrDefault(playerId, List.of()).size()
        ));
        appendAction(game, GameActionType.SETUP_PLACE_BENCH, playerId);
        return gameStateRepository.save(game);
    }

    public GameAggregate confirmSetup(UUID gameId, UUID playerId) {
        GameAggregate game = get(gameId);
        requireSetupPlayer(game, playerId);
        require(game.setupPhase() != null && game.setupPhase() != SetupPhase.SETUP_REVEAL, "La partida no esta en fase de preparacion");
        require(game.activePokemon().get(playerId) != null, "No se puede confirmar sin Pokemon Activo");

        game.confirmSetup(playerId);
        gameEngineFacade.publish(game, GameEventType.SETUP_CONFIRMED, Map.of(
            "playerId", playerId,
            "confirmedPlayers", game.setupConfirmedByPlayer().size()
        ));
        appendAction(game, GameActionType.SETUP_CONFIRM, playerId);

        if (game.setupConfirmed(game.playerOne()) && game.setupConfirmed(game.playerTwo())) {
            transitionToCoinFlip(game);
        }

        return gameStateRepository.save(game);
    }

    public GameAggregate join(UUID gameId, UUID playerId) {
        return join(gameId, playerId, null);
    }

    public GameAggregate join(UUID gameId, UUID playerId, UUID deckId) {
        GameAggregate game = get(gameId);
        game.setPlayerTwo(playerId);
        if (deckId != null) {
            game.deckIdsByPlayer().put(playerId, deckId);
        }
        GameAggregate saved = gameStateRepository.save(game);
        gameEngineFacade.publish(saved, GameEventType.PLAYER_JOINED, eventPayloadWithDeck("playerTwo", playerId, deckId));
        return saved;
    }

    public GameAggregate runInitialSetup(
        UUID gameId,
        int playerOneDeckSize,
        int playerOneBasicCount,
        int playerTwoDeckSize,
        int playerTwoBasicCount
    ) {
        return runInitialSetup(
            gameId,
            playerOneDeckSize,
            playerOneBasicCount,
            playerTwoDeckSize,
            playerTwoBasicCount,
            null,
            null
        );
    }

    public GameAggregate runInitialSetup(
        UUID gameId,
        Integer playerOneDeckSize,
        Integer playerOneBasicCount,
        Integer playerTwoDeckSize,
        Integer playerTwoBasicCount,
        UUID playerOneDeckId,
        UUID playerTwoDeckId
    ) {
        GameAggregate game = get(gameId);

        if (game.playerTwo() == null) {
            throw new IllegalStateException("Two players are required before setup");
        }

        registerRequestedDeckIds(game, playerOneDeckId, playerTwoDeckId);

        UUID resolvedPlayerOneDeckId = game.deckIdsByPlayer().get(game.playerOne());
        UUID resolvedPlayerTwoDeckId = game.deckIdsByPlayer().get(game.playerTwo());

        var p1 = hasBothDecks(resolvedPlayerOneDeckId, resolvedPlayerTwoDeckId)
            ? setupEngineService.preparePlayerBoard(
                game.playerOne(),
                realDeckCardsFor(game.playerOne(), resolvedPlayerOneDeckId),
                0,
                game
            )
            : setupEngineService.preparePlayerBoard(
                game.playerOne(),
                require(playerOneDeckSize, "playerOneDeckSize"),
                require(playerOneBasicCount, "playerOneBasicCount"),
                0,
                game
            );

        var p2 = hasBothDecks(resolvedPlayerOneDeckId, resolvedPlayerTwoDeckId)
            ? setupEngineService.preparePlayerBoard(
                game.playerTwo(),
                realDeckCardsFor(game.playerTwo(), resolvedPlayerTwoDeckId),
                p1.mulligans(),
                game
            )
            : setupEngineService.preparePlayerBoard(
                game.playerTwo(),
                require(playerTwoDeckSize, "playerTwoDeckSize"),
                require(playerTwoBasicCount, "playerTwoBasicCount"),
                p1.mulligans(),
                game
            );

        drawOptionalMulliganCards(game, game.playerOne(), p2.mulligans());
        p1.setHandSize(game.hand().get(game.playerOne()).size());

        game.setupByPlayer().put(game.playerOne(), p1);
        game.setupByPlayer().put(game.playerTwo(), p2);

        game.setFirstPlayer(
            Math.random() < 0.5
                ? game.playerOne()
                : game.playerTwo()
        );

        game.setCurrentTurnPlayer(game.firstPlayer());
        game.markTurnStartedFor(game.firstPlayer());

        gameEngineFacade.startSetup(game);
        gameEngineFacade.startActive(game);

        GameAggregate saved = gameStateRepository.save(game);

        gameStateRepository.appendActionLog(
            game.id(),
            GameActionType.DRAW,
            game.currentTurnPlayer(),
            "SETUP_COMPLETED",
            Instant.now(),
            baseActionPayload(game, GameActionType.DRAW, game.currentTurnPlayer(), Map.of("setupCompleted", true))
        );

        gameEngineFacade.publish(saved, GameEventType.SETUP_COMPLETED, Map.of(
            "firstPlayer", saved.firstPlayer(),
            "currentTurnPlayer", saved.currentTurnPlayer(),
            "phase", saved.turnPhase().name()
        ));

        return saved;
    }

    public GameAggregate executeAction(UUID gameId, GameActionType actionType) {
        return executeAction(gameId, actionType, StatusCondition.POISONED);
    }

    public GameAggregate executeAction(UUID gameId, GameActionType actionType, StatusCondition condition) {
        return executeAction(gameId, actionType, condition, null, null, List.of(), null, List.of(), null, List.of(), List.of(), null, null, null);
    }

    public GameAggregate executeAction(
        UUID gameId,
        GameActionType actionType,
        StatusCondition condition,
        String energyCardId,
        String targetSlot,
        List<String> energyCardIds
    ) {
        return executeAction(gameId, actionType, condition, energyCardId, targetSlot, energyCardIds, null, List.of(), null, List.of(), List.of(), null, null, null);
    }

    public GameAggregate executeAction(
        UUID gameId,
        GameActionType actionType,
        StatusCondition condition,
        String energyCardId,
        String targetSlot,
        List<String> energyCardIds,
        Integer attackIndex,
        List<Boolean> coinFlipResults
    ) {
        return executeAction(gameId, actionType, condition, energyCardId, targetSlot, energyCardIds, attackIndex, coinFlipResults, null, List.of(), List.of(), null, null, null);
    }

    public GameAggregate executeAction(
        UUID gameId,
        GameActionType actionType,
        StatusCondition condition,
        String energyCardId,
        String targetSlot,
        List<String> energyCardIds,
        Integer attackIndex,
        List<Boolean> coinFlipResults,
        UUID targetPlayerId,
        List<String> selectedCardIds,
        List<String> selectedEnergyCardIds,
        String selectedCondition,
        Boolean acceptedOptionalEffect,
        Integer selectedOpponentAttackIndex
    ) {
        GameAggregate game = get(gameId);
        UUID actingPlayer = game.currentTurnPlayer();
        StatusCondition conditionToApply = condition == null ? StatusCondition.POISONED : condition;
        String normalizedTargetSlot = normalizeActionTarget(game, actionType, targetSlot);
        List<String> actionEnergyCardIds = energyCardIds == null ? List.of() : energyCardIds;
        AttackContext attackContext = null;

        turnActionValidator.validate(game, actionType, energyCardId, normalizedTargetSlot, actionEnergyCardIds, attackIndex);
        ruleValidator.validate(game, actionType);

        switch (actionType) {
            case DRAW -> {
                drawForTurn(game, game.currentTurnPlayer());
                UUID drawer = game.currentTurnPlayer();
                if (drawer.equals(game.pendingForcedSwitchPlayerId()) && game.benchOccupiedCount(drawer) > 0) {
                    game.setReplacementPlayerId(drawer);
                    game.setGameState(GameState.WAITING_REPLACEMENT);
                    gameEngineFacade.publish(game, GameEventType.CARD_DRAWN, Map.of(
                        "player", drawer,
                        "handSize", game.hand().getOrDefault(drawer, List.of()).size(),
                        "deckRemaining", game.deckCardsRemaining().getOrDefault(drawer, 0),
                        "phase", game.turnPhase().name(),
                        "currentTurnPlayer", drawer
                    ));
                } else {
                    if (drawer.equals(game.pendingForcedSwitchPlayerId())) {
                        game.setPendingForcedSwitchPlayerId(null);
                    }
                    gameEngineFacade.advanceTurnPhase(game);
                    gameEngineFacade.publish(game, GameEventType.CARD_DRAWN, Map.of(
                        "player", drawer,
                        "handSize", game.hand().getOrDefault(drawer, List.of()).size(),
                        "deckRemaining", game.deckCardsRemaining().getOrDefault(drawer, 0),
                        "phase", game.turnPhase().name(),
                        "currentTurnPlayer", drawer
                    ));
                }
            }

            case ATTACH_ENERGY -> {
                AttachedEnergy attachedEnergy = attachEnergy(game, game.currentTurnPlayer(), energyCardId, normalizedTargetSlot);
                gameEngineFacade.publish(game, GameEventType.ENERGY_ATTACHED, Map.of(
                    "playerId", game.currentTurnPlayer(),
                    "targetSlot", normalizedTargetSlot,
                    "energyType", attachedEnergy.energyType(),
                    "remainingHandSize", game.hand().getOrDefault(game.currentTurnPlayer(), List.of()).size(),
                    "phase", game.turnPhase().name(),
                    "currentTurnPlayer", game.currentTurnPlayer()
                ));
            }

            case PLAY_SUPPORTER -> {
                game.turnFlags().setSupporterPlayed(true);
                gameEngineFacade.publish(game, GameEventType.SUPPORTER_PLAYED, Map.of(
                    "player", game.currentTurnPlayer(),
                    "phase", game.turnPhase().name(),
                    "currentTurnPlayer", game.currentTurnPlayer()
                ));
            }

            case RETREAT -> {
                retreatActivePokemon(game, game.currentTurnPlayer(), normalizedTargetSlot, actionEnergyCardIds);
                game.turnFlags().setRetreated(true);
                gameEngineFacade.publish(game, GameEventType.RETREAT_DECLARED, Map.of(
                    "player", game.currentTurnPlayer(),
                    "targetSlot", normalizedTargetSlot,
                    "discardedEnergies", actionEnergyCardIds.size()
                ));
            }

            case ATTACK -> {
                game.turnFlags().setAttacked(true);
                attackContext = gameEngineFacade.resolveAttack(
                    game,
                    attackIndex,
                    List.of(),
                    normalizedTargetSlot,
                    targetPlayerId,
                    selectedCardIds,
                    selectedEnergyCardIds == null || selectedEnergyCardIds.isEmpty() ? List.of() : selectedEnergyCardIds,
                    selectedCondition,
                    acceptedOptionalEffect,
                    selectedOpponentAttackIndex
                );

                UUID attacker = game.currentTurnPlayer();
                UUID defender = opponentOf(game, attacker);

                int damage = damageCalculator.calculate(
                    attackContext.damage(),
                    false,
                    false,
                    game.statusByPlayer().getOrDefault(
                        attacker,
                        EnumSet.noneOf(StatusCondition.class)
                    )
                );

                applyDamage(game, defender, damage);
                applyDamage(game, attacker, attackContext.selfDamage());

                boolean spikyShieldTriggered = triggerSpikyShield(game, attacker, defender, damage);
                boolean destinyBurstHeads = triggerDestinyBurst(game, attacker, defender);

                VictoryService.KnockoutResult knockout = victoryService.applyKnockoutAndPrizes(
                    game,
                    attacker,
                    defender
                );
                publishKnockoutResult(game, knockout);
                victoryService.applyBenchKnockoutsAndPrizes(game, attacker, defender)
                    .forEach(result -> publishKnockoutResult(game, result));

                if (spikyShieldTriggered
                    && game.activeHp().getOrDefault(attacker, 1) <= 0
                    && game.gameState() != GameState.WAITING_REPLACEMENT) {
                    VictoryService.KnockoutResult attackerKO = victoryService.applyKnockoutAndPrizes(
                        game,
                        defender,
                        attacker
                    );
                    publishKnockoutResult(game, attackerKO);
                    victoryService.applyBenchKnockoutsAndPrizes(game, defender, attacker)
                        .forEach(result -> publishKnockoutResult(game, result));
                }

                if (destinyBurstHeads
                    && game.activeHp().getOrDefault(attacker, 1) <= 0
                    && game.gameState() != GameState.WAITING_REPLACEMENT) {
                    VictoryService.KnockoutResult attackerKO = victoryService.applyKnockoutAndPrizes(
                        game,
                        defender,
                        attacker
                    );
                    publishKnockoutResult(game, attackerKO);
                    victoryService.applyBenchKnockoutsAndPrizes(game, defender, attacker)
                        .forEach(result -> publishKnockoutResult(game, result));
                }

                closeGameAndPublishIfNeeded(game);
            }

            case END_TURN -> {
                TurnPhase previousPhase = game.turnPhase();
                gameEngineFacade.advanceTurnPhase(game);
                if (previousPhase == TurnPhase.ATTACK) {
                    completeBetweenTurnsAndStartNextTurn(game, false);
                }
            }

            case TAKE_PRIZE -> {
                UUID player = game.currentTurnPlayer();

                game.prizeCardsRemaining().put(
                    player,
                    game.prizeCardsRemaining().getOrDefault(player, 6) - 1
                );
                gameEngineFacade.publish(game, GameEventType.PRIZE_TAKEN, Map.of(
                    "player", player,
                    "prizeCardsRemaining", game.prizeCardsRemaining().getOrDefault(player, 0),
                    "phase", game.turnPhase().name(),
                    "currentTurnPlayer", game.currentTurnPlayer()
                ));
            }

            case APPLY_SPECIAL_CONDITION -> {
                statusEffectManager.applyCondition(
                    game,
                    opponentOf(game, game.currentTurnPlayer()),
                    conditionToApply
                );
                gameEngineFacade.publish(game, GameEventType.STATUS_APPLIED, Map.of(
                    "target", opponentOf(game, game.currentTurnPlayer()),
                    "condition", conditionToApply.name()
                ));
            }

            case RESOLVE_BETWEEN_TURNS -> {
                completeBetweenTurnsAndStartNextTurn(game, false);
            }

            case SETUP_PLACE_ACTIVE, SETUP_PLACE_BENCH, SETUP_CONFIRM,
                 BENCH_POKEMON, EVOLVE_POKEMON, PLAY_TRAINER, EQUIP_TOOL, USE_ABILITY -> throw new InvalidActionException(
                actionType + " usa un endpoint dedicado"
            );
        }

        closeGameAndPublishIfNeeded(game);

        gameStateRepository.appendActionLog(
            game.id(),
            actionType,
            actingPlayer,
            game.gameState().name() + "/" + game.turnPhase().name(),
            Instant.now(),
            baseActionPayload(game, actionType, actingPlayer, Map.of(
                "targetSlot", normalizedTargetSlot == null ? "" : normalizedTargetSlot,
                "energyCardId", energyCardId == null ? "" : energyCardId,
                "selectedEnergyCardIds", actionEnergyCardIds
            ))
        );

        GameAggregate saved = gameStateRepository.save(game);
        if (attackContext != null) {
            publishAttackResolved(saved, attackContext);
        }
        return saved;
    }

    public List<ActionLogEntry> actionLog(UUID gameId) {
        return gameStateRepository.actionLog(gameId);
    }

    public List<GameAggregate> getHistory(UUID playerId) {
        return gameStateRepository.findFinishedByPlayerId(playerId);
    }

    public GameAggregate concede(UUID gameId, UUID playerId) {
        GameAggregate game = get(gameId);
        require(game.gameState() != GameState.FINISHED, "La partida ya finalizó");
        require(playerBelongsToGame(game, playerId), "El jugador no pertenece a la partida");

        UUID opponent = opponentOf(game, playerId);
        game.setWinner(opponent);
        game.setGameState(GameState.FINISHED);

        GameAggregate saved = gameStateRepository.save(game);

        gameEngineFacade.publish(game, GameEventType.GAME_FINISHED, Map.of(
            "winner", game.winner(),
            "reason", "CONCEDE"
        ));

        return saved;
    }

    public GameAggregate get(UUID gameId) {
        GameAggregate game = gameStateRepository.latestSnapshot(gameId)
            .orElseThrow(() -> new GameNotFoundException(gameId));
        finishCountdownIfExpired(game);
        return game;
    }

    private void finishCountdownIfExpired(GameAggregate game) {
        if (game.gameState() == GameState.SETUP
            && game.setupCountdownEndsAt() != null
            && Instant.now().isAfter(game.setupCountdownEndsAt())) {
            boolean p1NoActive = game.activePokemon().get(game.playerOne()) == null;
            boolean p2NoActive = game.activePokemon().get(game.playerTwo()) == null;

            if (p1NoActive && p2NoActive) {
                game.setWinner(null);
                game.setGameState(GameState.FINISHED);
                game.setSetupPhase(null);
                game.setSetupCountdownEndsAt(null);
                Map<String, Object> payload = new HashMap<>();
                payload.put("winner", null);
                payload.put("reason", "INACTIVITY");
                gameEngineFacade.publish(game, GameEventType.GAME_FINISHED, payload);
            } else {
                transitionToCoinFlip(game);
            }
            gameStateRepository.save(game);
        }
    }

    public List<String> getHandCardIds(UUID gameId, UUID playerId) {
        GameAggregate game = get(gameId);
        if (!playerBelongsToGame(game, playerId)) {
            throw new PlayerNotInGameException(gameId, playerId);
        }
        return List.copyOf(game.hand().getOrDefault(playerId, List.of()));
    }

    public List<String> getDeckCardIds(UUID gameId, UUID playerId) {
        GameAggregate game = get(gameId);
        if (!playerBelongsToGame(game, playerId)) {
            throw new PlayerNotInGameException(gameId, playerId);
        }
        return List.copyOf(game.deck().getOrDefault(playerId, List.of()));
    }

    public GameAggregate replaceActive(UUID gameId, UUID playerId, int benchSlot) {
        GameAggregate game = get(gameId);
        require(game.gameState() == GameState.WAITING_REPLACEMENT, "La partida no esta esperando reemplazo de Activo");
        require(playerId != null && playerId.equals(game.replacementPlayerId()), "Solo el jugador pendiente puede elegir el reemplazo");
        require(benchSlot >= 0 && benchSlot <= 4, "benchSlot debe estar entre 0 y 4");
        require(game.cardAtSlot(playerId, "BENCH_" + benchSlot) != null, "El benchSlot indicado esta vacio");

        boolean isForcedSwitch = playerId.equals(game.pendingForcedSwitchPlayerId());
        String promoted;
        if (isForcedSwitch) {
            game.swapActiveWithBench(playerId, benchSlot);
            game.setPendingForcedSwitchPlayerId(null);
            promoted = game.cardAtSlot(playerId, "ACTIVE");
        } else {
            promoted = game.promoteBenchToActive(playerId, benchSlot);
            require(promoted != null, "No hay Pokemon en el benchSlot indicado");
        }
        statusEffectManager.clearSpecialConditions(game, playerId, "ACTIVE");
        game.setReplacementPlayerId(null);
        if (game.winner() == null) {
            game.setGameState(GameState.ACTIVE);
        }
        game.refreshActivePokemonDerivedState(playerId);

        gameEngineFacade.publish(game, GameEventType.ACTIVE_REPLACED, Map.of(
            "playerId", playerId,
            "benchSlot", benchSlot,
            "promotedCard", promoted
        ));
        closeGameAndPublishIfNeeded(game);
        completeBetweenTurnsAndStartNextTurn(game, true);
        return gameStateRepository.save(game);
    }

    public GameAggregate benchPokemon(UUID gameId, UUID playerId, String cardInstanceId, String targetSlot) {
        GameAggregate game = get(gameId);
        turnActionValidator.validateBenchPokemon(game, playerId, cardInstanceId);

        game.hand().getOrDefault(playerId, List.of()).remove(cardInstanceId);
        game.benchPokemon(playerId, cardInstanceId, targetSlot);

        gameEngineFacade.publish(game, GameEventType.POKEMON_BENCHED, Map.of(
            "playerId", playerId,
            "cardId", cardInstanceId,
            "benchSize", game.benchOccupiedCount(playerId),
            "remainingHandSize", game.hand().getOrDefault(playerId, List.of()).size(),
            "phase", game.turnPhase().name(),
            "currentTurnPlayer", game.currentTurnPlayer()
        ));
        appendAction(game, GameActionType.BENCH_POKEMON, playerId);
        return gameStateRepository.save(game);
    }

    public GameAggregate benchPokemon(UUID gameId, UUID playerId, String cardInstanceId) {
        return benchPokemon(gameId, playerId, cardInstanceId, null);
    }

    public GameAggregate evolvePokemon(
        UUID gameId,
        UUID playerId,
        String fromCardInstanceId,
        String toCardInstanceId,
        String targetSlot
    ) {
        GameAggregate game = get(gameId);
        String slot = game.normalizeSlot(targetSlot);
        turnActionValidator.validateEvolution(game, playerId, fromCardInstanceId, toCardInstanceId, slot);

        game.hand().getOrDefault(playerId, List.of()).remove(toCardInstanceId);
        game.discardPile().computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(fromCardInstanceId);
        game.evolveAtSlot(playerId, slot, toCardInstanceId);
        statusEffectManager.clearSpecialConditions(game, playerId, slot);

        GameCard evolvedCard = game.cardCatalog().get(toCardInstanceId);
        gameEngineFacade.publish(game, GameEventType.POKEMON_EVOLVED, Map.of(
            "playerId", playerId,
            "targetSlot", slot,
            "fromCardId", fromCardInstanceId,
            "toCardId", toCardInstanceId,
            "toCardName", evolvedCard == null ? toCardInstanceId : evolvedCard.name(),
            "phase", game.turnPhase().name(),
            "currentTurnPlayer", game.currentTurnPlayer()
        ));
        appendAction(game, GameActionType.EVOLVE_POKEMON, playerId);
        return gameStateRepository.save(game);
    }

    public GameAggregate playTrainer(
        UUID gameId,
        UUID playerId,
        String cardInstanceId,
        String targetSlot,
        String targetId
    ) {
        return playTrainer(gameId, playerId, cardInstanceId, targetSlot, targetId, null, List.of(), List.of(), null, null, List.of());
    }

    public GameAggregate playTrainer(
        UUID gameId,
        UUID playerId,
        String cardInstanceId,
        String targetSlot,
        String targetId,
        UUID targetPlayerId,
        List<String> selectedCardIds,
        List<String> selectedEnergyCardIds,
        String selectedCondition,
        Boolean acceptedOptionalEffect,
        List<Boolean> coinFlipResults
    ) {
        GameAggregate game = get(gameId);
        turnActionValidator.validateTrainer(game, playerId, cardInstanceId, targetSlot);
        GameCard trainerCard = game.cardCatalog().get(cardInstanceId);
        String slot = game.normalizeSlot(targetSlot);

        boolean stadium = trainerCard.hasSubtype("Stadium") || trainerCard.hasRuleContaining("Stadium");
        boolean tool = trainerCard.hasSubtype("Pokemon Tool") || trainerCard.hasSubtype("Tool") || trainerCard.hasRuleContaining("Pokemon Tool");
        boolean supporter = trainerCard.hasSubtype("Supporter") || trainerCard.hasRuleContaining("Supporter");

        TrainerEffectResult result = TrainerEffectResult.noop("NO_SPECIAL_EFFECT");
        if (tool) {
            game.hand().getOrDefault(playerId, List.of()).remove(cardInstanceId);
            game.equipTool(playerId, slot, cardInstanceId);
            gameEngineFacade.publish(game, GameEventType.TOOL_EQUIPPED, Map.of(
                "playerId", playerId,
                "cardId", cardInstanceId,
                "targetSlot", slot,
                "phase", game.turnPhase().name(),
                "currentTurnPlayer", game.currentTurnPlayer()
            ));
            appendAction(game, GameActionType.EQUIP_TOOL, playerId);
            return gameStateRepository.save(game);
        }

        if (stadium) {
            game.hand().getOrDefault(playerId, List.of()).remove(cardInstanceId);
            UUID previousOwner = game.activeStadiumOwner();
            String previous = game.clearStadium();
            if (previous != null) {
                GameCard oldCard = game.cardCatalog().get(previous);
                if (oldCard != null && stadiumEffectRegistry != null) {
                    stadiumEffectRegistry.find(oldCard)
                        .ifPresent(s -> s.onRemoved(game));
                }
                if (previousOwner != null) {
                    game.discardPile().computeIfAbsent(previousOwner, ignored -> new ArrayList<>()).add(previous);
                }
            }
            game.putStadium(playerId, cardInstanceId);
            game.turnFlags().setStadiumPlayed(true);
            if (stadiumEffectRegistry != null) {
                stadiumEffectRegistry.find(trainerCard)
                    .ifPresent(s -> s.onPlayed(game, playerId));
            }
            result = TrainerEffectResult.noop("STADIUM_ACTIVE");
        } else {
            TrainerEffectStrategy strategy = trainerEffectRegistry == null
                ? null
                : trainerEffectRegistry.find(trainerCard).orElse(null);
            if (strategy == null) {
                log.warn("No TrainerEffectStrategy registered for card '{}' (id={}). Discarding without effect.", trainerCard.name(), cardInstanceId);
                game.hand().getOrDefault(playerId, List.of()).remove(cardInstanceId);
                game.discardPile().computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(cardInstanceId);
                result = TrainerEffectResult.noop("NO_EFFECT_IMPLEMENTED");
            } else {
                BooleanSupplier coinFlip = buildCoinFlipSupplier(coinFlipResults);
                game.hand().getOrDefault(playerId, List.of()).remove(cardInstanceId);
                result = strategy.apply(new TrainerEffectContext(
                    game,
                    playerId,
                    trainerCard,
                    cardInstanceId,
                    slot,
                    targetId,
                    targetPlayerId,
                    selectedCardIds,
                    selectedEnergyCardIds,
                    selectedCondition,
                    acceptedOptionalEffect,
                    coinFlip
                ));
                game.discardPile().computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(cardInstanceId);
            }
        }

        if (supporter) {
            game.turnFlags().setSupporterPlayed(true);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("playerId", playerId);
        payload.put("cardId", cardInstanceId);
        payload.put("cardName", trainerCard.name());
        payload.put("targetSlot", slot);
        payload.put("targetId", targetId);
        payload.put("result", result.result());
        payload.put("effect", result.payload());
        payload.put("remainingHandSize", game.hand().getOrDefault(playerId, List.of()).size());
        payload.put("phase", game.turnPhase().name());
        payload.put("currentTurnPlayer", game.currentTurnPlayer());
        gameEngineFacade.publish(game, stadium ? GameEventType.STADIUM_PLAYED : GameEventType.TRAINER_PLAYED, payload);
        gameStateRepository.appendActionLog(
            game.id(),
            GameActionType.PLAY_TRAINER,
            playerId,
            result.result(),
            Instant.now(),
            baseActionPayload(game, GameActionType.PLAY_TRAINER, playerId, Map.of(
                "effectResult", result.result(),
                "effectPayload", result.payload()
            ))
        );
        return gameStateRepository.save(game);
    }

    public GameAggregate useAbility(UUID gameId, UUID playerId, String slotReference, int abilityIndex, String targetId) {
        GameAggregate game = get(gameId);
        String slot = game.normalizeSlot(slotReference);
        turnActionValidator.validateAbility(game, playerId, slot, abilityIndex);

        String cardId = game.cardAtSlot(playerId, slot);
        GameCard card = game.cardCatalog().get(cardId);
        String abilityName = card.abilities().get(abilityIndex);
        AbilityEffectStrategy strategy = abilityEffectRegistry == null
            ? null
            : abilityEffectRegistry.find(abilityName).orElse(null);
        TrainerEffectResult result = strategy == null
            ? null
            : strategy.apply(new AbilityEffectContext(game, playerId, card, slot, abilityIndex, targetId));
        if (result == null) {
            if ("Destiny Burst".equalsIgnoreCase(abilityName)) {
                throw new InvalidActionException(
                    "Destiny Burst solo se activa automaticamente cuando este Pokemon queda Fuera de Combate por el dano de un ataque"
                );
            }
            if ("Spiky Shield".equalsIgnoreCase(abilityName)) {
                throw new InvalidActionException(
                    "Spiky Shield se activa automaticamente cuando este Pokemon recibe dano de un ataque"
                );
            }
            throw new InvalidActionException("La habilidad " + abilityName + " no tiene efecto implementado");
        }
        String abilityKey = playerId + ":" + slot + ":" + abilityIndex;
        if (strategy == null || !strategy.isRepeatable()) {
            game.turnFlags().markAbilityUsed(abilityKey);
        }

        gameEngineFacade.publish(game, GameEventType.ABILITY_USED, Map.of(
            "playerId", playerId,
            "slotReference", slot,
            "abilityIndex", abilityIndex,
            "abilityName", abilityName,
            "targetId", targetId == null ? "" : targetId,
            "result", result.result(),
            "phase", game.turnPhase().name(),
            "currentTurnPlayer", game.currentTurnPlayer()
        ));
        appendAction(game, GameActionType.USE_ABILITY, playerId);
        return gameStateRepository.save(game);
    }

    private UUID opponentOf(GameAggregate game, UUID player) {
        if (player == null) {
            return game.playerTwo();
        }

        return player.equals(game.playerOne())
            ? game.playerTwo()
            : game.playerOne();
    }

    private static BooleanSupplier buildCoinFlipSupplier(List<Boolean> coinFlipResults) {
        if (coinFlipResults != null && !coinFlipResults.isEmpty()) {
            Iterator<Boolean> iterator = coinFlipResults.iterator();
            return () -> {
                try {
                    return iterator.next();
                } catch (NoSuchElementException e) {
                    return Math.random() < 0.5;
                }
            };
        }
        return () -> Math.random() < 0.5;
    }

    private void appendAction(GameAggregate game, GameActionType actionType, UUID player) {
        gameStateRepository.appendActionLog(
            game.id(),
            actionType,
            player,
            game.gameState().name() + "/" + game.turnPhase().name(),
            Instant.now(),
            baseActionPayload(game, actionType, player, Map.of())
        );
    }

    private Map<String, Object> baseActionPayload(
        GameAggregate game,
        GameActionType actionType,
        UUID player,
        Map<String, Object> details
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", game.id().toString());
        payload.put("action", actionType.name());
        payload.put("playerId", player == null ? null : player.toString());
        payload.put("gameState", game.gameState().name());
        payload.put("phase", game.turnPhase().name());
        payload.put("currentTurnPlayer", game.currentTurnPlayer() == null ? null : game.currentTurnPlayer().toString());
        payload.put("handSize", player == null ? null : game.hand().getOrDefault(player, List.of()).size());
        payload.put("deckRemaining", player == null ? null : game.deckCardsRemaining().getOrDefault(player, 0));
        if (details != null) {
            payload.putAll(details);
        }
        return payload;
    }

    private void drawOptionalMulliganCards(GameAggregate game, UUID player, int cards) {
        for (int i = 0; i < cards; i++) {
            drawOne(game, player);
        }
    }

    private void drawForTurn(GameAggregate game, UUID player) {
        if (game.firstTurn() && player.equals(game.firstPlayer())) {
            return;
        }
        drawOne(game, player);
    }

    private boolean drawOne(GameAggregate game, UUID player) {
        var deck = game.deck().get(player);
        if (deck == null || deck.isEmpty()) {
            return false;
        }
        game.hand().get(player).add(deck.remove(0));
        game.deckCardsRemaining().put(player, deck.size());
        return true;
    }

    private AttachedEnergy attachEnergy(GameAggregate game, UUID player, String energyCardId, String targetSlot) {
        List<String> hand = game.hand().getOrDefault(player, List.of());
        if (!hand.remove(energyCardId)) {
            throw new InvalidActionException("La energia indicada no esta en la mano del jugador");
        }
        GameCard energyCard = game.cardCatalog().get(energyCardId);
        String energyType = DeckService.inferEnergyType(energyCard.name(), energyCard.energyType());
        int energyAmount = 1;

        String lowerName = energyCard.name() != null ? energyCard.name().toLowerCase() : "";
        if (lowerName.contains("double colorless")) {
            energyAmount = 2;
        }

        AttachedEnergy attachedEnergy = new AttachedEnergy(energyCardId, energyType, energyAmount);
        game.attachEnergy(player, targetSlot, attachedEnergy);

        if (lowerName.contains("rainbow")) {
            game.addDamageCounters(player, targetSlot, 1);
        }

        game.turnFlags().setEnergyAttached(true);
        return attachedEnergy;
    }

    private void retreatActivePokemon(GameAggregate game, UUID player, String targetSlot, List<String> energyCardIds) {
        GameCard active = game.cardCatalog().get(game.activePokemon().get(player));
        int retreatCost = active == null ? 0 : active.retreatCost();
        if (retreatCost > 0 && game.hasActiveEffect(player, FairyGardenStadiumEffect.EFFECT_KEY)) {
            boolean hasFairyEnergy = game.attachedEnergiesFor(player, "ACTIVE").stream()
                .anyMatch(e -> "Fairy".equalsIgnoreCase(e.energyType()));
            if (hasFairyEnergy) {
                retreatCost = 0;
            }
        }
        if (energyCardIds.size() != retreatCost) {
            throw new InvalidActionException("RETREAT requiere seleccionar exactamente " + retreatCost + " energias para descartar");
        }

        List<AttachedEnergy> discarded = game.removeAttachedEnergies(player, "ACTIVE", energyCardIds);
        discarded.forEach(energy -> game.discardPile().computeIfAbsent(player, ignored -> new ArrayList<>()).add(energy.cardId()));

        int benchIndex = game.benchIndex(targetSlot);
        game.swapActiveWithBench(player, benchIndex);
        game.refreshActivePokemonDerivedState(player);
        game.statusByPlayer().remove(player);
    }

    private void applyDamage(GameAggregate game, UUID player, int damage) {
        if (player == null || damage <= 0) {
            return;
        }
        int counters = damage / 10;
        game.addDamageCounters(player, "ACTIVE", counters);
    }

    private void resolveBetweenTurnsForBothPlayers(GameAggregate game) {
        statusEffectManager.resolveBetweenTurns(game, game.playerOne());
        statusEffectManager.resolveBetweenTurns(game, game.playerTwo());
        game.clearExpiredActiveEffects(game.turnNumber());
        publishKnockoutResult(game, victoryService.applyKnockoutAndPrizes(game, game.playerOne(), game.playerTwo()));
        publishKnockoutResult(game, victoryService.applyKnockoutAndPrizes(game, game.playerTwo(), game.playerOne()));
        victoryService.applyBenchKnockoutsAndPrizes(game, game.playerOne(), game.playerTwo())
            .forEach(result -> publishKnockoutResult(game, result));
        victoryService.applyBenchKnockoutsAndPrizes(game, game.playerTwo(), game.playerOne())
            .forEach(result -> publishKnockoutResult(game, result));
    }

    private void completeBetweenTurnsAndStartNextTurn(GameAggregate game, boolean allowPendingReplacement) {
        if (game.gameState() == GameState.FINISHED || game.turnPhase() != TurnPhase.BETWEEN_TURNS) {
            return;
        }
        if (game.gameState() == GameState.WAITING_REPLACEMENT && !allowPendingReplacement) {
            return;
        }

        boolean wasSuddenDeathBeforeClose = game.suddenDeath();
        if (!allowPendingReplacement) {
            resolveBetweenTurnsForBothPlayers(game);
            closeGameAndPublishIfNeeded(game);
        }
        gameEngineFacade.publish(game, GameEventType.BETWEEN_TURNS_RESOLVED, Map.of(
            "playerOneHp", game.activeHp().getOrDefault(game.playerOne(), 0),
            "playerTwoHp", game.activeHp().getOrDefault(game.playerTwo(), 0)
        ));
        if (game.gameState() != GameState.FINISHED
            && game.gameState() != GameState.WAITING_REPLACEMENT
            && (wasSuddenDeathBeforeClose || !game.suddenDeath())) {
            gameEngineFacade.advanceTurnPhase(game);
            startNextTurn(game);
        }
    }

    private void startNextTurn(GameAggregate game) {
        game.setCurrentTurnPlayer(
            game.currentTurnPlayer() != null
                && game.currentTurnPlayer().equals(game.playerOne())
                ? game.playerTwo()
                : game.playerOne()
        );
	        game.turnFlags().reset();
	        game.setFirstTurn(false);
	        game.setTurnNumber(game.turnNumber() + 1);
	        game.markTurnStartedFor(game.currentTurnPlayer());
	        gameEngineFacade.publish(game, GameEventType.TURN_STARTED, Map.of(
            "currentTurnPlayer", game.currentTurnPlayer(),
            "phase", game.turnPhase().name()
        ));
    }

    private void publishKnockoutResult(GameAggregate game, VictoryService.KnockoutResult knockout) {
        if (knockout == null || !knockout.knockedOut()) {
            return;
        }
        Map<String, Object> koPayload = new HashMap<>();
        koPayload.put("attacker", knockout.attacker());
        koPayload.put("defender", knockout.defender());
        koPayload.put("knockedOutCard", knockout.knockedOutCard());
        koPayload.put("promotedCard", knockout.promotedCard());
        koPayload.put("defenderHasNoPokemon", knockout.defenderHasNoPokemon());
        gameEngineFacade.publish(game, GameEventType.KO, koPayload);

        Map<String, Object> prizePayload = new HashMap<>();
        prizePayload.put("player", knockout.attacker());
        prizePayload.put("prizesTaken", knockout.prizesTaken());
        prizePayload.put("prizeCardsRemaining", knockout.prizeCardsRemaining());
        gameEngineFacade.publish(game, GameEventType.PRIZE_TAKEN, prizePayload);
    }

    private void publishAttackResolved(GameAggregate game, AttackContext context) {
        UUID attacker = game.currentTurnPlayer();
        UUID defender = opponentOf(game, attacker);
        Map<String, Object> payload = new HashMap<>();
        payload.put("attacker", attacker);
        payload.put("defender", defender);
        payload.put("attackingPokemon", game.activePokemon().get(attacker));
        payload.put("defendingPokemon", game.activePokemon().get(defender));
        payload.put("damage", context.damage());
        payload.put("baseDamage", context.baseDamage());
        payload.put("requiredEnergy", context.requiredEnergy());
        payload.put("attachedEnergy", context.attachedEnergy());
        payload.put("requiredEnergyCost", context.requiredEnergyCost());
        payload.put("attachedEnergyTypes", context.attachedEnergies().stream().map(AttachedEnergy::energyType).toList());
        payload.put("cancelled", context.cancelled());
        payload.put("weaknessMultiplier", context.weaknessMultiplier());
        payload.put("resistanceReduction", context.resistanceReduction());
        payload.put("auditTrail", context.auditTrail());
        if (context.coinFlipResults() != null && !context.coinFlipResults().isEmpty()) {
            payload.put("coinFlipResults", context.coinFlipResults());
        }
        List<Map<String, Object>> revealedCards = revealedCards(game, context.revealedCardIds());
        if (!revealedCards.isEmpty()) {
            payload.put("revealedCards", revealedCards);
        }
        gameEngineFacade.publish(game, GameEventType.ATTACK_RESOLVED, payload);
    }

    private List<Map<String, Object>> revealedCards(GameAggregate game, List<String> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> cards = new ArrayList<>();
        for (String cardId : cardIds) {
            GameCard card = game.cardCatalog().get(cardId);
            if (card == null) {
                continue;
            }
            String rawCardId = rawCardId(card.id());
            Map<String, Object> revealed = new HashMap<>();
            revealed.put("cardId", rawCardId);
            revealed.put("setId", setId(rawCardId));
            revealed.put("name", card.name() == null ? rawCardId : card.name());
            revealed.put("instanceId", card.id());
            cards.add(revealed);
        }
        return cards;
    }

    private String rawCardId(String cardId) {
        if (cardId == null) {
            return "";
        }
        int separator = cardId.indexOf(':');
        return separator >= 0 ? cardId.substring(0, separator) : cardId;
    }

    private String setId(String cardId) {
        int separator = cardId == null ? -1 : cardId.indexOf('-');
        return separator > 0 ? cardId.substring(0, separator) : "";
    }

    private boolean triggerDestinyBurst(GameAggregate game, UUID attacker, UUID defender) {
        String defenderActiveId = game.activePokemon().get(defender);
        if (defenderActiveId == null) {
            log.debug("DestinyBurst: no active pokemon for defender {}", defender);
            return false;
        }

        GameCard defenderCard = game.cardCatalog().get(defenderActiveId);
        if (defenderCard == null) {
            log.debug("DestinyBurst: card {} not found in catalog", defenderActiveId);
            return false;
        }

        log.debug("DestinyBurst: defender card {} abilities: {}", defenderCard.name(), defenderCard.abilities());

        boolean hasDestinyBurst = defenderCard.abilities().stream()
            .anyMatch(a -> "Destiny Burst".equalsIgnoreCase(a));
        if (!hasDestinyBurst) {
            log.debug("DestinyBurst: ability not found on {}", defenderCard.name());
            return false;
        }

        int hp = game.activeHp().getOrDefault(defender, 1);
        log.debug("DestinyBurst: defender {} active HP = {}", defender, hp);
        if (hp > 0) {
            log.debug("DestinyBurst: defender still has HP > 0, not KO'd");
            return false;
        }

        log.debug("DestinyBurst: ALL CONDITIONS MET, flipping coin...");

        boolean heads = Math.random() < 0.5;
        log.debug("DestinyBurst: coin flip result = {}", heads ? "HEADS" : "TAILS");

        Map<String, Object> coinFlipPayload = new HashMap<>();
        coinFlipPayload.put("coinResult", heads ? "HEADS" : "TAILS");
        coinFlipPayload.put("abilityName", "Destiny Burst");
        coinFlipPayload.put("pokemonName", defenderCard.name());
        coinFlipPayload.put("defendingPlayer", defender);
        coinFlipPayload.put("attackingPlayer", attacker);
        gameEngineFacade.publish(game, GameEventType.COIN_FLIP, coinFlipPayload);

        if (heads) {
            int beforeHp = game.activeHp().getOrDefault(attacker, 0);
            game.addDamageCounters(attacker, "ACTIVE", 5);
            int afterHp = game.activeHp().getOrDefault(attacker, 0);
            log.debug("DestinyBurst: placed 5 damage counters on attacker {} (HP before={}, after={})",
                attacker, beforeHp, afterHp);
            return true;
        }

        log.debug("DestinyBurst: tails, no damage placed");
        return false;
    }

    private boolean triggerSpikyShield(GameAggregate game, UUID attacker, UUID defender, int damage) {
        if (damage <= 0) {
            log.debug("SpikyShield: no damage dealt, skipping");
            return false;
        }

        String defenderActiveId = game.activePokemon().get(defender);
        if (defenderActiveId == null) {
            log.debug("SpikyShield: no active pokemon for defender {}", defender);
            return false;
        }

        GameCard defenderCard = game.cardCatalog().get(defenderActiveId);
        if (defenderCard == null) {
            log.debug("SpikyShield: card {} not found in catalog", defenderActiveId);
            return false;
        }

        boolean hasSpikyShield = defenderCard.abilities().stream()
            .anyMatch(a -> "Spiky Shield".equalsIgnoreCase(a));
        if (!hasSpikyShield) {
            log.debug("SpikyShield: ability not found on {}", defenderCard.name());
            return false;
        }

        log.debug("SpikyShield: ALL CONDITIONS MET, placing 3 damage counters on attacker {}", attacker);

        game.addDamageCounters(attacker, "ACTIVE", 3);

        Map<String, Object> payload = new HashMap<>();
        payload.put("abilityName", "Spiky Shield");
        payload.put("pokemonName", defenderCard.name());
        payload.put("defendingPlayer", defender);
        payload.put("attackingPlayer", attacker);
        payload.put("damageCountersPlaced", 3);
        gameEngineFacade.publish(game, GameEventType.ABILITY_USED, payload);

        log.debug("SpikyShield: placed 3 damage counters on attacker {}", attacker);
        return true;
    }

    private void closeGameAndPublishIfNeeded(GameAggregate game) {
        GameState previousState = game.gameState();
        UUID previousWinner = game.winner();
        boolean wasSuddenDeath = game.suddenDeath();
        victoryService.closeGameIfNeeded(game);
        if (!wasSuddenDeath && game.suddenDeath() && game.gameState() == GameState.ACTIVE) {
            gameEngineFacade.publish(game, GameEventType.SUDDEN_DEATH_STARTED, Map.of(
                "firstPlayer", game.firstPlayer(),
                "currentTurnPlayer", game.currentTurnPlayer(),
                "prizeCardsPerPlayer", 1
            ));
        }
        if (previousState != GameState.FINISHED && game.gameState() == GameState.FINISHED) {
            gameEngineFacade.publish(game, GameEventType.GAME_FINISHED, Map.of(
                "winner", game.winner(),
                "reason", finishReason(game, previousWinner)
            ));
        }
    }

    private void transitionToCoinFlip(GameAggregate game) {
        game.setGameState(GameState.COIN_FLIP);
        game.setSetupPhase(null);
        game.setCurrentTurnPlayer(null);
        gameEngineFacade.publish(game, GameEventType.SETUP_COMPLETED, Map.of(
            "nextPhase", "COIN_FLIP"
        ));
    }

    private void startGameAfterCoinFlip(GameAggregate game) {
        game.setCurrentTurnPlayer(game.firstPlayer());
        game.markTurnStartedFor(game.firstPlayer());

        gameEngineFacade.startActive(game);
        gameEngineFacade.publish(game, GameEventType.GAME_STARTED, Map.of(
            "firstPlayer", game.firstPlayer(),
            "currentTurnPlayer", game.currentTurnPlayer(),
            "phase", game.turnPhase().name()
        ));
    }

    private void startGameAfterInteractiveSetup(GameAggregate game) {
        game.setSetupPhase(SetupPhase.SETUP_REVEAL);
        gameEngineFacade.publish(game, GameEventType.SETUP_COMPLETED, Map.of(
            "setupPhase", game.setupPhase().name()
        ));

        game.setCurrentTurnPlayer(game.firstPlayer());
        game.markTurnStartedFor(game.firstPlayer());

        gameEngineFacade.startActive(game);
        game.setSetupPhase(null);
        gameEngineFacade.publish(game, GameEventType.GAME_STARTED, Map.of(
            "firstPlayer", game.firstPlayer(),
            "currentTurnPlayer", game.currentTurnPlayer(),
            "phase", game.turnPhase().name()
        ));
    }

    private void refreshSetupSummary(GameAggregate game, UUID playerId) {
        PlayerSetupState state = game.setupByPlayer().computeIfAbsent(playerId, ignored -> new PlayerSetupState());
        state.setHasActive(game.activePokemon().get(playerId) != null);
        state.setBenchCount(game.benchOccupiedCount(playerId));
        state.setHandSize(game.hand().getOrDefault(playerId, List.of()).size());
        state.setPrizeCount(game.prizeCards().getOrDefault(playerId, List.of()).size());
    }

    private void requireSetupPlayer(GameAggregate game, UUID playerId) {
        require(game.gameState() == GameState.SETUP, "La partida debe estar en SETUP");
        require(playerBelongsToGame(game, playerId), "El jugador no pertenece a la partida");
        require(game.setupPhase() != null, "La partida no tiene fase de setup interactivo activa");
    }

    private String finishReason(GameAggregate game, UUID previousWinner) {
        if (game.winner() == null || game.winner().equals(previousWinner)) {
            return "UNKNOWN";
        }
        UUID loser = game.winner().equals(game.playerOne()) ? game.playerTwo() : game.playerOne();
        if (game.prizeCardsRemaining().getOrDefault(game.winner(), 1) <= 0) {
            return "PRIZES";
        }
        if (!game.hasPokemonInPlay(loser)) {
            return "KO_TOTAL";
        }
        if (game.deck().getOrDefault(loser, List.of()).isEmpty()) {
            return "DECK_OUT";
        }
        return "UNKNOWN";
    }

    private Map<String, Object> eventPayloadWithDeck(String playerKey, UUID playerId, UUID deckId) {
        return deckId == null
            ? Map.of(playerKey, playerId)
            : Map.of(playerKey, playerId, "deckId", deckId);
    }

    private boolean playerBelongsToGame(GameAggregate game, UUID playerId) {
        return playerId != null
            && (playerId.equals(game.playerOne()) || playerId.equals(game.playerTwo()));
    }

    private void registerRequestedDeckIds(GameAggregate game, UUID playerOneDeckId, UUID playerTwoDeckId) {
        if (playerOneDeckId != null) {
            game.deckIdsByPlayer().put(game.playerOne(), playerOneDeckId);
        }
        if (playerTwoDeckId != null) {
            game.deckIdsByPlayer().put(game.playerTwo(), playerTwoDeckId);
        }
    }

    private boolean hasBothDecks(UUID playerOneDeckId, UUID playerTwoDeckId) {
        return playerOneDeckId != null && playerTwoDeckId != null;
    }

    private String normalizeActionTarget(GameAggregate game, GameActionType actionType, String targetSlot) {
        if (actionType == GameActionType.RETREAT && (targetSlot == null || targetSlot.isBlank())) {
            return "BENCH_0";
        }
        return game.normalizeSlot(targetSlot);
    }

    private int require(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(
                "Para setup manual se requiere " + fieldName + ". Para setup real enviá deckId de ambos jugadores."
            );
        }
        return value;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidActionException(message);
        }
    }

    private List<GameCard> realDeckCardsFor(UUID playerId, UUID deckId) {
        if (deckService == null) {
            throw new IllegalStateException("DeckService is required for setup with real decks");
        }
        DeckResponse deck = deckService.getEnrichedForGame(playerId, deckId);
        if (!playerId.equals(deck.playerId())) {
            throw new IllegalArgumentException("El mazo " + deckId + " no pertenece al jugador " + playerId);
        }
        if (!deck.validation().valid()) {
            throw new IllegalArgumentException(String.join(" ", deck.validation().errors()));
        }

        List<GameCard> cards = new ArrayList<>();
        int copyIndex = 0;
        for (DeckCardInput input : deck.cards()) {
            for (int i = 0; i < input.quantity(); i++) {
                cards.add(toGameCard(playerId, input, copyIndex++));
            }
        }
        return cards;
    }

    private GameCard toGameCard(UUID playerId, DeckCardInput input, int copyIndex) {
        return new GameCard(
            input.cardId() + ":" + playerId + ":" + copyIndex,
            input.name(),
            input.type(),
            subtypesOf(input),
            input.hp() == null ? 0 : input.hp(),
            input.attackDamage() == null ? 0 : input.attackDamage(),
            input.attackRequiredEnergy() == null ? 0 : input.attackRequiredEnergy(),
            input.attackName(),
            input.attackText(),
            input.energyType(),
            input.attackCost(),
            input.retreatCost() == null ? 0 : input.retreatCost(),
            input.evolvesFrom(),
            input.abilities(),
            input.weaknessType(),
            input.weaknessAmount(),
            input.resistanceType(),
            input.resistanceAmount(),
            input.attacks()
        );
    }

    private Set<String> subtypesOf(DeckCardInput input) {
        Set<String> subtypes = new LinkedHashSet<>();
        if (input.subtype() != null && !input.subtype().isBlank()) {
            for (String subtype : input.subtype().split(",")) {
                String trimmed = subtype.trim();
                if (!trimmed.isEmpty()) {
                    subtypes.add(trimmed);
                }
            }
        }
        if (input.basicPokemon() || input.basicEnergy()) {
            subtypes.add("Basic");
        }
        if (input.aceSpec()) {
            subtypes.add("ACE SPEC");
        }
        return subtypes;
    }
}
