package com.utn.pokemontcg.game.domain.facade;

import com.utn.pokemontcg.game.domain.attack.AttackPatternAnalyzer;
import com.utn.pokemontcg.game.domain.chain.*;
import com.utn.pokemontcg.game.domain.event.GameEvent;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.event.GameEventType;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.strategy.AttackEffectRegistry;
import com.utn.pokemontcg.game.domain.state.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GameEngineFacade {

    private final GameEventPublisher eventPublisher;
    private final AttackEffectRegistry attackEffectRegistry;
    private final AttackPatternAnalyzer attackPatternAnalyzer;

    public GameEngineFacade(GameEventPublisher eventPublisher) {
        this(eventPublisher, null, null);
    }

    public GameEngineFacade(GameEventPublisher eventPublisher, AttackEffectRegistry attackEffectRegistry) {
        this(eventPublisher, attackEffectRegistry, null);
    }

    public GameEngineFacade(
        GameEventPublisher eventPublisher,
        AttackEffectRegistry attackEffectRegistry,
        AttackPatternAnalyzer attackPatternAnalyzer
    ) {
        this.eventPublisher = eventPublisher;
        this.attackEffectRegistry = attackEffectRegistry;
        this.attackPatternAnalyzer = attackPatternAnalyzer != null ? attackPatternAnalyzer : new AttackPatternAnalyzer();
    }

    public void startSetup(GameAggregate game) {
        new SetupState().enter(game);
        eventPublisher.publish(GameEvent.of(game.id(), GameEventType.GAME_SETUP, Map.of("state", game.gameState().name())));
    }

    public void startActive(GameAggregate game) {
        new ActiveState().enter(game);
        new DrawPhaseState().advance(game);
        eventPublisher.publish(GameEvent.of(game.id(), GameEventType.TURN_STARTED, Map.of(
            "phase", game.turnPhase().name(),
            "currentTurnPlayer", game.currentTurnPlayer()
        )));
    }

    public void advanceTurnPhase(GameAggregate game) {
        TurnPhaseState state = switch (game.turnPhase()) {
            case DRAW -> new DrawPhaseState();
            case MAIN -> new MainPhaseState();
            case ATTACK -> new AttackPhaseState();
            case BETWEEN_TURNS -> new BetweenTurnsPhaseState();
        };
        state.advance(game);
        eventPublisher.publish(GameEvent.of(game.id(), GameEventType.TURN_PHASE_CHANGED, Map.of("phase", game.turnPhase().name())));
    }

    public AttackContext resolveAttack(GameAggregate game) {
        return resolveAttack(game, null, List.of(), null, null, List.of(), List.of(), null, null, null);
    }

    public AttackContext resolveAttack(GameAggregate game, Integer attackIndex, List<Boolean> coinFlipResults) {
        return resolveAttack(game, attackIndex, coinFlipResults, null, null, List.of(), List.of(), null, null, null);
    }

    public AttackContext resolveAttack(GameAggregate game, Integer attackIndex, List<Boolean> coinFlipResults, String targetSlot) {
        return resolveAttack(game, attackIndex, coinFlipResults, targetSlot, null, List.of(), List.of(), null, null, null);
    }

    public AttackContext resolveAttack(
        GameAggregate game,
        Integer attackIndex,
        List<Boolean> coinFlipResults,
        String targetSlot,
        UUID targetPlayerId,
        List<String> selectedCardIds,
        List<String> selectedEnergyCardIds,
        String selectedCondition,
        Boolean acceptedOptionalEffect,
        Integer selectedOpponentAttackIndex
    ) {
        AttackResolutionPipeline pipeline = new AttackResolutionPipeline(List.of(
            new EnergyValidationStep(),
            new ConfusionCheckStep(),
            new SelectionStep(attackPatternAnalyzer),
            new PreAttackStep(),
            new ModifierStep(),
            new PreDamageEffectsStep(attackEffectRegistry),
            new DamageCalculationStep(),
            new PostDamageEffectsStep(attackEffectRegistry)
        ));
        AttackContext context = buildDefaultAttackContext(game, attackIndex);
        context.setTargetSlot(targetSlot);
        context.setTargetPlayerId(targetPlayerId);
        context.setSelectedCardIds(selectedCardIds);
        context.setSelectedEnergyCardIds(selectedEnergyCardIds);
        context.setSelectedCondition(selectedCondition);
        context.setAcceptedOptionalEffect(acceptedOptionalEffect);
        if (selectedOpponentAttackIndex != null && selectedOpponentAttackIndex >= 0) {
            context.setSelectedOpponentAttackIndex(selectedOpponentAttackIndex);
        }
        if (coinFlipResults != null && !coinFlipResults.isEmpty()) {
            final java.util.Iterator<Boolean> iter = coinFlipResults.iterator();
            context.setCoinFlip(() -> {
                try {
                    return iter.next();
                } catch (java.util.NoSuchElementException e) {
                    return Math.random() < 0.5;
                }
            });
        }

        pipeline.resolve(context);
        game.setTurnPhase(com.utn.pokemontcg.game.domain.model.TurnPhase.BETWEEN_TURNS);
        eventPublisher.publish(GameEvent.of(game.id(), GameEventType.TURN_PHASE_CHANGED, Map.of("phase", game.turnPhase().name())));
        return context;
    }

    public void publish(GameAggregate game, GameEventType type, Map<String, Object> payload) {
        eventPublisher.publish(GameEvent.of(game.id(), type, payload));
    }

    private AttackContext buildDefaultAttackContext(GameAggregate game) {
        return buildDefaultAttackContext(game, null);
    }

    private AttackContext buildDefaultAttackContext(GameAggregate game, Integer attackIndex) {
        AttackContext context = new AttackContext(game);
        var attacker = game.currentTurnPlayer();
        UUID defender = attacker != null && attacker.equals(game.playerOne()) ? game.playerTwo() : game.playerOne();
        GameCard activeCard = game.cardCatalog().get(game.activePokemon().get(attacker));
        GameCard defenderCard = game.cardCatalog().get(game.activePokemon().get(defender));

        List<?> attacks = activeCard != null ? activeCard.attacks() : List.of();
        int selectedIndex = (attackIndex != null && attackIndex >= 0 && attackIndex < attacks.size())
            ? attackIndex
            : 0;
        context.setAttackIndex(selectedIndex);

        if (!attacks.isEmpty() && selectedIndex < attacks.size()) {
            var selectedAttack = activeCard.attacks().get(selectedIndex);
            int numericDamage = parseDamage(selectedAttack.damage());
            context.setAttackName(
                selectedAttack.name() != null && !selectedAttack.name().isBlank()
                    ? selectedAttack.name()
                    : activeCard.name()
            );
            context.setAttackText(selectedAttack.text());
            context.setRequiredEnergy(selectedAttack.convertedEnergyCost());
            context.setRequiredEnergyCost(selectedAttack.cost() != null ? selectedAttack.cost() : List.of());
            context.setBaseDamage(numericDamage);
        } else {
            int requiredEnergy = activeCard != null ? activeCard.attackRequiredEnergy() : game.activeAttackRequiredEnergy();
            int baseDamage = activeCard != null ? activeCard.attackDamage() : game.activeAttackBaseDamage();
            if (game.activeAttackRequiredEnergy() != 1) {
                requiredEnergy = game.activeAttackRequiredEnergy();
            }
            if (game.activeAttackBaseDamage() != 30) {
                baseDamage = game.activeAttackBaseDamage();
            }
            context.setRequiredEnergy(requiredEnergy);
            context.setRequiredEnergyCost(activeCard == null ? List.of() : activeCard.attackCost());
            context.setAttackName(activeCard == null ? null : firstNonBlank(activeCard.attackName(), activeCard.name()));
            context.setAttackText(activeCard == null ? null : activeCard.attackText());
            context.setBaseDamage(baseDamage);
        }

        context.setAttackerCard(activeCard);
        context.setDefenderCard(defenderCard);
        int realAttachedEnergy = game.attachedEnergyCount(attacker, "ACTIVE");
        int legacyAttachedEnergy = game.activeAttachedEnergy().getOrDefault(attacker, 0);
        context.setAttachedEnergies(game.attachedEnergiesFor(attacker, "ACTIVE"));
        context.setAttachedEnergy(Math.max(realAttachedEnergy, legacyAttachedEnergy));
        context.setConfused(game.statusByPlayer()
            .getOrDefault(attacker, java.util.EnumSet.noneOf(com.utn.pokemontcg.game.domain.model.StatusCondition.class))
            .contains(com.utn.pokemontcg.game.domain.model.StatusCondition.CONFUSED));
        if (activeCard == null || defenderCard == null) {
            context.setWeaknessMultiplier(game.defendingPokemonWeakToAttack() ? 2 : 1);
            context.setResistanceReduction(game.defendingPokemonResistsAttack() ? 20 : 0);
        }
        return context;
    }

    private int parseDamage(String damage) {
        if (damage == null || damage.isBlank()) return 0;
        try {
            return Integer.parseInt(damage.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }
}
