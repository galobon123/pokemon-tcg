package com.utn.pokemontcg.game.application.service;

import com.utn.pokemontcg.game.domain.exception.BenchFullException;
import com.utn.pokemontcg.game.domain.exception.EvolutionNotAllowedException;
import com.utn.pokemontcg.game.domain.exception.InvalidActionPhaseException;
import com.utn.pokemontcg.game.domain.exception.SupporterAlreadyPlayedException;
import com.utn.pokemontcg.game.domain.chain.ModifierStep;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.FairyGardenStadiumEffect;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;

@Service
public class TurnActionValidator {

    private static final Logger log = LoggerFactory.getLogger(TurnActionValidator.class);

    private final AbilityEffectRegistry abilityEffectRegistry;

    public TurnActionValidator() {
        this.abilityEffectRegistry = null;
    }

    public TurnActionValidator(AbilityEffectRegistry abilityEffectRegistry) {
        this.abilityEffectRegistry = abilityEffectRegistry;
    }

    public void validate(GameAggregate game, GameActionType action) {
        validate(game, action, null, null, List.of(), null);
    }

    public void validate(
        GameAggregate game,
        GameActionType action,
        String energyCardId,
        String targetSlot,
        List<String> energyCardIds
    ) {
        validate(game, action, energyCardId, targetSlot, energyCardIds, null);
    }

    public void validate(
        GameAggregate game,
        GameActionType action,
        String energyCardId,
        String targetSlot,
        List<String> energyCardIds,
        Integer attackIndexForValidation
    ) {
        TurnPhase phase = game.turnPhase();
        require(game.gameState() == GameState.ACTIVE, "La partida debe estar ACTIVE para ejecutar acciones");
        require(game.currentTurnPlayer() != null, "No hay jugador activo para el turno");

        switch (action) {
            case DRAW -> require(phase == TurnPhase.DRAW, "DRAW solo en fase DRAW");
            case ATTACH_ENERGY -> {
                require(phase == TurnPhase.MAIN, "ATTACH_ENERGY solo en MAIN");
                require(!isFirstPlayerFirstTurn(game), "El jugador que inicia no puede unir Energia en su primer turno");
                require(!game.turnFlags().energyAttached(), "Solo se puede unir 1 energía por turno");
                require(energyCardId != null && !energyCardId.isBlank(), "energyCardId es obligatorio para unir energia");
                require(game.hand().getOrDefault(game.currentTurnPlayer(), List.of()).contains(energyCardId),
                    "La energia indicada no esta en la mano del jugador");
                GameCard energyCard = game.cardCatalog().get(energyCardId);
                require(energyCard != null && (energyCard.isBasicEnergy() || energyCard.isEnergy()),
                    "La carta indicada no es una energia");
                require(game.isOccupiedSlot(game.currentTurnPlayer(), game.normalizeSlot(targetSlot)),
                    "targetSlot debe apuntar a un Pokemon propio en juego");
            }
            case PLAY_SUPPORTER -> {
                require(phase == TurnPhase.MAIN, "PLAY_SUPPORTER solo en MAIN");
                require(!game.hasActiveEffect(game.currentTurnPlayer(), ModifierStep.SUPPORTER_LOCK), "No puedes jugar Supporter en este turno");
                if (game.turnFlags().supporterPlayed()) {
                    throw new SupporterAlreadyPlayedException("Solo se puede jugar 1 partidario por turno");
                }
            }
            case RETREAT -> {
                require(phase == TurnPhase.MAIN, "RETREAT solo en MAIN");
                require(!game.hasActiveEffect(game.currentTurnPlayer(), ModifierStep.CANNOT_RETREAT), "Este Pokemon no puede retirarse en este turno");
                require(!game.turnFlags().retreated(), "Solo se puede retirar 1 vez por turno");
                require(!activeHas(game, StatusCondition.ASLEEP), "Un Pokemon Dormido no puede retirarse");
                require(!activeHas(game, StatusCondition.PARALYZED), "Un Pokemon Paralizado no puede retirarse");
                String retreatTarget = targetSlot == null || targetSlot.isBlank() ? "BENCH_0" : game.normalizeSlot(targetSlot);
                require(retreatTarget.startsWith("BENCH_"), "RETREAT requiere targetSlot BENCH_0..BENCH_4");
                require(game.isOccupiedSlot(game.currentTurnPlayer(), retreatTarget), "No hay Pokemon en " + retreatTarget);
                GameCard active = game.cardCatalog().get(game.activePokemon().get(game.currentTurnPlayer()));
                int retreatCost = active == null ? 0 : active.retreatCost();
                if (retreatCost > 0 && game.hasActiveEffect(game.currentTurnPlayer(), FairyGardenStadiumEffect.EFFECT_KEY)) {
                    boolean hasFairyEnergy = game.attachedEnergiesFor(game.currentTurnPlayer(), "ACTIVE").stream()
                        .anyMatch(e -> "Fairy".equalsIgnoreCase(e.energyType()));
                    if (hasFairyEnergy) {
                        retreatCost = 0;
                    }
                }
                int attached = game.attachedEnergyCount(game.currentTurnPlayer(), "ACTIVE");
                require(attached >= retreatCost,
                    "No puedes retirar: necesitas descartar " + retreatCost + " energias pero solo tienes " + attached + " adjuntas");
                List<String> selected = energyCardIds == null ? List.of() : energyCardIds;
                require(selected.size() == retreatCost,
                    "RETREAT requiere seleccionar exactamente " + retreatCost + " energias para descartar");
            }
            case ATTACK -> {
                require(phase == TurnPhase.ATTACK, "ATTACK solo en fase ATTACK");
                require(!game.firstTurn(), "No se puede atacar en el primer turno global");
                require(game.activePokemon().get(game.currentTurnPlayer()) != null, "El jugador activo no tiene Pokemon Activo");
                require(!game.hasActiveEffect(game.currentTurnPlayer(), ModifierStep.CANNOT_ATTACK), "Este Pokemon no puede atacar en este turno");
                require(!activeHas(game, StatusCondition.ASLEEP), "Un Pokemon Dormido no puede atacar");
                require(!activeHas(game, StatusCondition.PARALYZED), "Un Pokemon Paralizado no puede atacar");
                if (attackIndexForValidation != null && attackIndexForValidation >= 0) {
                    require(!game.hasActiveEffect(game.currentTurnPlayer(), ModifierStep.BLOCKED_ATTACK_PREFIX + attackIndexForValidation),
                        "Ese ataque esta bloqueado en este turno");
                }
            }
            case END_TURN -> require(
                phase == TurnPhase.MAIN || phase == TurnPhase.ATTACK,
                "END_TURN solo en MAIN o ATTACK"
            );
            case TAKE_PRIZE -> require(phase == TurnPhase.BETWEEN_TURNS, "TAKE_PRIZE solo en BETWEEN_TURNS");
            case APPLY_SPECIAL_CONDITION -> require(phase == TurnPhase.ATTACK || phase == TurnPhase.BETWEEN_TURNS,
                "APPLY_SPECIAL_CONDITION solo en ATTACK o BETWEEN_TURNS");
            case RESOLVE_BETWEEN_TURNS -> require(phase == TurnPhase.BETWEEN_TURNS,
                "RESOLVE_BETWEEN_TURNS solo en BETWEEN_TURNS");
            case SETUP_PLACE_ACTIVE, SETUP_PLACE_BENCH, SETUP_CONFIRM,
                 BENCH_POKEMON, EVOLVE_POKEMON, PLAY_TRAINER, EQUIP_TOOL, USE_ABILITY -> throw new InvalidActionException(
                action + " usa un endpoint dedicado"
            );
        }
    }

    public void validateBenchPokemon(GameAggregate game, java.util.UUID player, String cardInstanceId) {
        requireMainAction(game, player);
        require(!isFirstPlayerFirstTurn(game), "El jugador que inicia no puede colocar Pokemon en la banca en su primer turno");
        require(cardInstanceId != null && !cardInstanceId.isBlank(), "cardInstanceId es obligatorio");
        require(game.hand().getOrDefault(player, List.of()).contains(cardInstanceId), "La carta no esta en la mano del jugador");
        GameCard card = game.cardCatalog().get(cardInstanceId);
        require(card != null && card.isBasicPokemon(), "Solo se puede bajar un Pokemon Basico a la banca");
        if (game.benchOccupiedCount(player) >= 5) {
            throw new BenchFullException("La banca ya esta llena");
        }
    }

    public void validateEvolution(
        GameAggregate game,
        java.util.UUID player,
        String fromCardInstanceId,
        String toCardInstanceId,
        String targetSlot
    ) {
        requireMainAction(game, player);
        require(!isFirstPlayerFirstTurn(game), "El jugador que inicia no puede evolucionar en su primer turno");
        String slot = game.normalizeSlot(targetSlot);
        requireEvolution(game.turnsStartedByPlayer().getOrDefault(player, 0) > 1,
            "No se puede evolucionar en el primer turno del jugador");
        requireEvolution(!game.slotEnteredThisTurn(player, slot), "No se puede evolucionar un Pokemon que entro en juego este turno");
        requireEvolution(!game.turnFlags().slotEvolved(slot), "Este slot ya evoluciono en este turno");
        require(game.hand().getOrDefault(player, List.of()).contains(toCardInstanceId), "La evolucion no esta en la mano del jugador");
        requireEvolution(fromCardInstanceId != null && fromCardInstanceId.equals(game.cardAtSlot(player, slot)),
            "fromCardInstanceId no coincide con el Pokemon del slot objetivo");
        GameCard fromCard = game.cardCatalog().get(fromCardInstanceId);
        GameCard toCard = game.cardCatalog().get(toCardInstanceId);
        requireEvolution(fromCard != null && toCard != null, "No se encontraron las cartas de evolucion");
        requireEvolution(toCard.evolvesFrom() != null && toCard.evolvesFrom().equalsIgnoreCase(fromCard.name()),
            "La carta " + toCard.name() + " no evoluciona de " + fromCard.name());
    }

    public void validateTrainer(GameAggregate game, java.util.UUID player, String cardInstanceId, String targetSlot) {
        requireMainAction(game, player);
        require(game.hand().getOrDefault(player, List.of()).contains(cardInstanceId), "La carta de Entrenador no esta en la mano");

        // Defensive lookup: cardInstanceId may be a composite (cardId:player:copy). Try direct lookup, then fallback to base card id.
        GameCard card = game.cardCatalog().get(cardInstanceId);
        // If direct lookup failed, try these fallbacks to handle different id formats from frontend
        if (card == null && cardInstanceId != null) {
            // 1) If composite was sent but catalog has base id: take part before first ':'
            if (cardInstanceId.contains(":")) {
                String base = cardInstanceId.split(":", 2)[0];
                card = game.cardCatalog().get(base);
                if (card != null) {
                    log.debug("validateTrainer: normalized cardInstanceId {} -> base {}", cardInstanceId, base);
                }
            }
            // 2) If base id was sent but catalog uses composite keys like '<cardId>:<player>:<copy>', find matching entry
            if (card == null && !cardInstanceId.contains(":")) {
                for (var entry : game.cardCatalog().entrySet()) {
                    if (entry.getKey() != null && entry.getKey().startsWith(cardInstanceId + ":")) {
                        card = entry.getValue();
                        log.debug("validateTrainer: resolved base {} -> catalog key {}", cardInstanceId, entry.getKey());
                        break;
                    }
                }
            }
        }

        require(card != null && card.isTrainer(), "La carta indicada no es Entrenador");
        boolean isStadium = card.hasSubtype("Stadium") || card.hasRuleContaining("Stadium");
        boolean isSupporter = card.hasSubtype("Supporter") || card.hasRuleContaining("Supporter");
        boolean isTool = card.hasSubtype("Pokemon Tool") || card.hasSubtype("Tool") || card.hasRuleContaining("Pokemon Tool");
        boolean isItem = card.hasSubtype("Item") || card.hasSubtype("Object") || card.hasSubtype("Objeto");
        if (!isStadium && !isSupporter && !isTool && !isItem) {
            isItem = true;
        }
        require(isStadium || isSupporter || isTool || isItem, "Subtipo de Entrenador no soportado");
        if (isStadium) {
            require(!game.turnFlags().stadiumPlayed(), "Solo se puede jugar 1 Estadio por turno");
        }
        if (isSupporter) {
            require(!game.hasActiveEffect(player, ModifierStep.SUPPORTER_LOCK), "No puedes jugar Supporter en este turno");
            require(!game.turnFlags().supporterPlayed(), "Solo se puede jugar 1 Partidario por turno");
        }
        if (isTool) {
            String slot = game.normalizeSlot(targetSlot);
            require(game.isOccupiedSlot(player, slot), "targetSlot debe apuntar a un Pokemon propio en juego");
            require(game.toolAtSlot(player, slot) == null, "El Pokemon objetivo ya tiene una Herramienta equipada");
        }
        if (isItem) {
            require(!opponentActiveHasForestCurse(game, player), "No puedes jugar Objetos mientras el Pokemon Activo rival tenga la habilidad Forest's Curse");
        }
    }

    public void validateAbility(GameAggregate game, java.util.UUID player, String slotReference, int abilityIndex) {
        requireMainAction(game, player);
        String slot = game.normalizeSlot(slotReference);
        String cardId = game.cardAtSlot(player, slot);
        require(cardId != null, "slotReference debe apuntar a un Pokemon propio en juego");
        require(!game.hasActiveEffect(player, ModifierStep.ABILITY_LOCK), "Este Pokemon no puede usar habilidades en este turno");
        GameCard card = game.cardCatalog().get(cardId);
        require(card != null && abilityIndex >= 0 && abilityIndex < card.abilities().size(), "La habilidad indicada no existe");
        String abilityKey = player + ":" + slot + ":" + abilityIndex;
        String abilityName = card.abilities().get(abilityIndex);
        AbilityEffectStrategy strategy = abilityEffectRegistry == null ? null : abilityEffectRegistry.find(abilityName).orElse(null);
        if (strategy == null || !strategy.isRepeatable()) {
            require(!game.turnFlags().abilityUsed(abilityKey), "Esta habilidad ya fue usada este turno");
        }
    }

    private void requireMainAction(GameAggregate game, java.util.UUID player) {
        require(game.gameState() == GameState.ACTIVE, "La partida debe estar ACTIVE para ejecutar acciones");
        if (game.turnPhase() != TurnPhase.MAIN) {
            throw new InvalidActionPhaseException("La accion solo se puede ejecutar en MAIN");
        }
        require(player != null && player.equals(game.currentTurnPlayer()), "Solo el jugador activo puede ejecutar esta accion");
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new InvalidActionException(message);
    }

    private void requireEvolution(boolean condition, String message) {
        if (!condition) throw new EvolutionNotAllowedException(message);
    }

    private boolean activeHas(GameAggregate game, StatusCondition condition) {
        EnumSet<StatusCondition> statuses = game.statusByPlayer()
            .getOrDefault(game.currentTurnPlayer(), EnumSet.noneOf(StatusCondition.class));
        return statuses.contains(condition);
    }

    private boolean isFirstPlayerFirstTurn(GameAggregate game) {
        return game.firstTurn()
            && game.firstPlayer() != null
            && game.currentTurnPlayer() != null
            && game.currentTurnPlayer().equals(game.firstPlayer());
    }

    private boolean opponentActiveHasForestCurse(GameAggregate game, java.util.UUID player) {
        java.util.UUID opponent = player.equals(game.playerOne())
            ? game.playerTwo()
            : game.playerOne();
        String opponentActiveId = game.activePokemon().get(opponent);
        if (opponentActiveId == null) {
            return false;
        }
        GameCard opponentActive = game.cardCatalog().get(opponentActiveId);
        if (opponentActive == null || opponentActive.abilities() == null) {
            return false;
        }
        return opponentActive.abilities().stream()
            .anyMatch(a -> "Forest's Curse".equalsIgnoreCase(a));
    }
}
