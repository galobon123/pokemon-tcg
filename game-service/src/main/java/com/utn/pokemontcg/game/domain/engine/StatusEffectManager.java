package com.utn.pokemontcg.game.domain.engine;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.UUID;

@Component
public class StatusEffectManager {

    private final SecureRandom random = new SecureRandom();

    public void applyCondition(GameAggregate game, UUID target, StatusCondition condition) {
        if (protectedBySweetVeil(game, target)) {
            return;
        }
        EnumSet<StatusCondition> statuses = game.statusByPlayer().computeIfAbsent(target, ignored -> EnumSet.noneOf(StatusCondition.class));

        if (condition == StatusCondition.ASLEEP || condition == StatusCondition.CONFUSED || condition == StatusCondition.PARALYZED) {
            statuses.remove(StatusCondition.ASLEEP);
            statuses.remove(StatusCondition.CONFUSED);
            statuses.remove(StatusCondition.PARALYZED);
        }
        statuses.add(condition);
    }

    private boolean protectedBySweetVeil(GameAggregate game, UUID owner) {
        String activeId = game.activePokemon().get(owner);
        if (activeId == null) return false;
        GameCard active = game.cardCatalog().get(activeId);
        if (active == null || active.abilities() == null) return false;
        boolean hasSweetVeil = active.abilities().stream()
            .anyMatch(a -> "Sweet Veil".equalsIgnoreCase(a));
        if (!hasSweetVeil) return false;
        return game.attachedEnergiesFor(owner, "ACTIVE").stream()
            .anyMatch(e -> "Fairy".equalsIgnoreCase(e.energyType()));
    }

    public void resolveBetweenTurns(GameAggregate game, UUID target) {
        resolveBetweenTurns(game, target, random.nextBoolean(), random.nextBoolean());
    }

    public void resolveBetweenTurns(GameAggregate game, UUID target, boolean burnedTails, boolean asleepHeads) {
        EnumSet<StatusCondition> statuses = game.statusByPlayer().getOrDefault(target, EnumSet.noneOf(StatusCondition.class));

        if (statuses.contains(StatusCondition.POISONED)) {
            addDamageCounters(game, target, 1);
        }
        if (statuses.contains(StatusCondition.BURNED) && burnedTails) {
            addDamageCounters(game, target, 2);
        }
        if (statuses.contains(StatusCondition.ASLEEP) && asleepHeads) {
            statuses.remove(StatusCondition.ASLEEP);
        }
        // PARALYZED only recovers during the checkup at the end of the paralyzed Pokemon's
        // OWN turn, so it actually misses exactly one of its own turns. Clearing it on the
        // opponent's between-turns checkup (right after it was applied) would let the
        // paralyzed Pokemon act on its very next turn, defeating the condition.
        if (statuses.contains(StatusCondition.PARALYZED) && target.equals(game.currentTurnPlayer())) {
            statuses.remove(StatusCondition.PARALYZED);
        }
        if (statuses.isEmpty()) {
            game.statusByPlayer().remove(target);
        } else {
            game.statusByPlayer().put(target, statuses);
        }
    }

    public void clearSpecialConditions(GameAggregate game, UUID target) {
        game.statusByPlayer().remove(target);
    }

    public void clearSpecialConditions(GameAggregate game, UUID target, String targetSlot) {
        if ("ACTIVE".equals(game.normalizeSlot(targetSlot))) {
            clearSpecialConditions(game, target);
        }
    }

    private void addDamageCounters(GameAggregate game, UUID target, int counters) {
        game.addDamageCounters(target, "ACTIVE", counters);
    }
}
