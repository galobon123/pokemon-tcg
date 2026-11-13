package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class SpecialConditionAttackEffectStrategy extends AbstractAttackEffectStrategy {
    @Override
    public String effectName() {
        return "SPECIAL_CONDITION_BY_ATTACK_NAME";
    }

    @Override
    public boolean supports(AttackContext context) {
        return !conditionsFor(effectText(context)).isEmpty();
    }

    @Override
    public void apply(AttackContext context) {
        List<StatusCondition> conditions = conditionsFor(effectText(context));
        if (effectText(context).contains("choose either") && context.selectedCondition() != null && !context.selectedCondition().isBlank()) {
            try {
                conditions = List.of(StatusCondition.valueOf(context.selectedCondition().trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Fall back to text-inferred conditions when the client sends an invalid value.
            }
        }
        if (conditions.isEmpty() || context.cancelled()) {
            return;
        }
        if (!coinConditionAllowsEffect(context)) {
            return;
        }
        UUID attacker = context.game().currentTurnPlayer();
        UUID defender = attacker != null && attacker.equals(context.game().playerOne())
            ? context.game().playerTwo()
            : context.game().playerOne();
        String text = effectText(context);
        if (isSelfTargetedCondition(text, conditions)) {
            applyTo(context, attacker, conditions);
            context.audit("AttackEffect:conditions=self," + conditions);
            return;
        }
        applyTo(context, defender, conditions);
        if (text.contains("both active")) {
            applyTo(context, attacker, conditions);
        }
        context.audit("AttackEffect:conditions=" + conditions);
    }

    private boolean isSelfTargetedCondition(String text, List<StatusCondition> conditions) {
        if (!text.contains("this pokemon") && !text.contains("this pokémon")) {
            return false;
        }
        boolean hasSelfSleep = text.contains("now asleep") || text.contains("is now asleep");
        boolean hasSelfConfuse = text.contains("now confused") || text.contains("is now confused");
        boolean hasSelfParalyze = text.contains("now paralyzed") || text.contains("is now paralyzed");
        if (!hasSelfSleep && !hasSelfConfuse && !hasSelfParalyze) {
            return false;
        }
        boolean mentionsOpponentCondition = text.contains("opponent") && (text.contains("asleep") || text.contains("confus") || text.contains("paraly"));
        return !mentionsOpponentCondition;
    }

    private void applyTo(AttackContext context, UUID player, List<StatusCondition> conditions) {
        if (protectedBySweetVeil(context.game(), player)) {
            context.audit("AttackEffect:sweet_veil_blocked," + conditions);
            return;
        }
        EnumSet<StatusCondition> statuses = context.game().statusByPlayer()
            .computeIfAbsent(player, ignored -> EnumSet.noneOf(StatusCondition.class));
        for (StatusCondition condition : conditions) {
            if (condition == StatusCondition.ASLEEP || condition == StatusCondition.CONFUSED || condition == StatusCondition.PARALYZED) {
                statuses.remove(StatusCondition.ASLEEP);
                statuses.remove(StatusCondition.CONFUSED);
                statuses.remove(StatusCondition.PARALYZED);
            }
            statuses.add(condition);
        }
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

    private List<StatusCondition> conditionsFor(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<StatusCondition> conditions = new ArrayList<>();
        if (normalized.contains("poison")) conditions.add(StatusCondition.POISONED);
        if (normalized.contains("burn")) conditions.add(StatusCondition.BURNED);
        if (normalized.contains("sleep") || normalized.contains("asleep")) conditions.add(StatusCondition.ASLEEP);
        if (normalized.contains("confus")) conditions.add(StatusCondition.CONFUSED);
        if (normalized.contains("paraly")) conditions.add(StatusCondition.PARALYZED);
        return conditions;
    }
}
