package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.chain.ModifierStep;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PersistentAttackEffectStrategy extends AbstractAttackEffectStrategy {
    @Override
    public String effectName() {
        return "PERSISTENT_ATTACK_EFFECT";
    }

    @Override
    public boolean supports(AttackContext context) {
        String text = effectText(context);
        return text.contains("during your opponent's next turn")
            || text.contains("during your next turn")
            || text.contains("can't retreat")
            || text.contains("can't use")
            || text.contains("cannot use")
            || text.contains("can't play any supporter")
            || text.contains("has no abilities")
            || text.contains("can't attack during your next turn")
            || text.contains("cannot attack during your next turn")
            || text.contains("can't attack during your opponent")
            || text.contains("prevent all damage")
            || text.contains("prevent all effects of attacks")
            || isDamageThresholdPreventionText(text)
            || isOpponentAttackBlockText(text);
    }

    private boolean isDamageThresholdPreventionText(String text) {
        return text.contains("prevent")
            && text.contains("damage")
            && text.contains("or less");
    }

    private boolean isOpponentAttackBlockText(String text) {
        boolean mentionsOpponent = text.contains("opponent") || text.contains("rival") || text.contains("oponente");
        if (!mentionsOpponent) return false;
        boolean mentionsAttack = text.contains("attack") || text.contains("ataque");
        if (!mentionsAttack) return false;
        boolean mentionsBlock = text.contains("can't be used") || text.contains("cannot be used")
            || text.contains("can't use that attack") || text.contains("cannot use that attack")
            || text.contains("that pokémon can't use") || text.contains("that pokemon can't use")
            || text.contains("that pokémon cannot use") || text.contains("that pokemon cannot use")
            || text.contains("no puede usar ese ataque") || text.contains("no puede ser usado");
        return mentionsBlock;
    }

    @Override
    public void apply(AttackContext context) {
        if (context.cancelled()) {
            return;
        }
        if (!coinConditionAllowsEffect(context)) {
            return;
        }
        String text = effectText(context);
        int currentTurn = context.game().turnNumber();

        if (text.contains("during your opponent's next turn") && text.contains("reduced by")) {
            int amount = firstNumberBeforeOrAfter(text, "reduced by", 20);
            context.game().addActiveEffectUntilTurn(attacker(context), ModifierStep.DEFENDER_DAMAGE_REDUCTION, amount, currentTurn + 1);
            context.audit("AttackEffect:persistent_reduction=" + amount + ",expiresAfterTurn=" + (currentTurn + 1));
        }

        if (text.contains("prevent all damage") || text.contains("prevent all effects of attacks")) {
            context.game().addActiveEffectUntilTurn(attacker(context), ModifierStep.DEFENDER_DAMAGE_REDUCTION, 999, currentTurn + 1);
            context.audit("AttackEffect:persistent_prevent_damage,expiresAfterTurn=" + (currentTurn + 1));
        }

        if (isDamageThresholdPreventionText(text)) {
            int amount = firstNumberBeforeOrAfter(text, "or less", 60);
            context.game().addActiveEffectUntilTurn(attacker(context), ModifierStep.DEFENDER_PREVENT_DAMAGE_MAX, amount, currentTurn + 1);
            context.audit("AttackEffect:persistent_prevent_damage_max=" + amount + ",expiresAfterTurn=" + (currentTurn + 1));
        }

        if (text.contains("during your next turn") && containsAny(text, "more damage", "+")) {
            int amount = firstExplicitBonus(text, 10);
            String key = isScopedBonus(text, context)
                ? ModifierStep.ATTACKER_DAMAGE_BONUS + ":" + context.attackName()
                : ModifierStep.ATTACKER_DAMAGE_BONUS;
            context.game().addActiveEffectUntilTurn(attacker(context), key, amount, currentTurn + 2);
            context.audit("AttackEffect:persistent_bonus=" + amount + ",key=" + key + ",expiresAfterTurn=" + (currentTurn + 2));
        }

        if (text.contains("can't attack during your next turn") || text.contains("cannot attack during your next turn")) {
            context.game().addActiveEffectUntilTurn(attacker(context), ModifierStep.CANNOT_ATTACK, 1, currentTurn + 2);
            context.audit("AttackEffect:persistent_cannot_attack,expiresAfterTurn=" + (currentTurn + 2));
        }

        if (isSelfAttackBlockText(text) && context.attackIndex() >= 0) {
            String key = ModifierStep.BLOCKED_ATTACK_PREFIX + context.attackIndex();
            context.game().addActiveEffectUntilTurn(attacker(context), key, 1, currentTurn + 2);
            context.audit("AttackEffect:self_blocked_attack=" + context.attackIndex() + ",expiresAfterTurn=" + (currentTurn + 2));
        }

        if (text.contains("can't attack during your opponent")) {
            context.game().addActiveEffectUntilTurn(defender(context), ModifierStep.CANNOT_ATTACK, 1, currentTurn + 1);
            context.audit("AttackEffect:persistent_defender_cannot_attack,expiresAfterTurn=" + (currentTurn + 1));
        }

        if (text.contains("can't retreat during your opponent")) {
            context.game().addActiveEffectUntilTurn(defender(context), ModifierStep.CANNOT_RETREAT, 1, currentTurn + 1);
            context.audit("AttackEffect:persistent_defender_cannot_retreat,expiresAfterTurn=" + (currentTurn + 1));
        }

        if (text.contains("can't retreat during your next turn") || (text.contains("can't retreat") && !text.contains("your opponent"))) {
            context.game().addActiveEffectUntilTurn(attacker(context), ModifierStep.CANNOT_RETREAT, 1, currentTurn + 2);
            context.audit("AttackEffect:persistent_self_cannot_retreat,expiresAfterTurn=" + (currentTurn + 2));
        }

        if (text.contains("can't play any supporter")) {
            context.game().addActiveEffectUntilTurn(defender(context), ModifierStep.SUPPORTER_LOCK, 1, currentTurn + 1);
            context.audit("AttackEffect:persistent_supporter_lock,expiresAfterTurn=" + (currentTurn + 1));
        }

        if (text.contains("has no abilities")) {
            context.game().addActiveEffectUntilTurn(defender(context), ModifierStep.ABILITY_LOCK, 1, currentTurn + 2);
            context.audit("AttackEffect:persistent_ability_lock,expiresAfterTurn=" + (currentTurn + 2));
        }

        if (isOpponentAttackBlockText(text) && context.selectedOpponentAttackIndex() >= 0) {
            String key = ModifierStep.BLOCKED_ATTACK_PREFIX + context.selectedOpponentAttackIndex();
            context.game().addActiveEffectUntilTurn(defender(context), key, 1, currentTurn + 1);
            context.audit("AttackEffect:blocked_attack=" + context.selectedOpponentAttackIndex() + ",expiresAfterTurn=" + (currentTurn + 1));
        }
    }

    private int firstNumberBeforeOrAfter(String text, String fragment, int fallback) {
        int index = text.indexOf(fragment);
        if (index < 0) {
            return fallback;
        }
        Matcher after = Pattern.compile("(\\d+)").matcher(text.substring(index));
        if (after.find()) {
            return Integer.parseInt(after.group(1));
        }
        Matcher before = Pattern.compile("(\\d+)").matcher(text.substring(0, index));
        int last = fallback;
        while (before.find()) {
            last = Integer.parseInt(before.group(1));
        }
        return last;
    }

    private boolean isSelfAttackBlockText(String text) {
        return containsAny(text, "this pokemon can't use", "this pokémon can't use", "this pokemon cannot use", "this pokémon cannot use")
            && text.contains("during your next turn");
    }

    private boolean isScopedBonus(String text, AttackContext context) {
        if (text.contains("this attack")) return true;
        String attackName = context.attackName();
        String attackTextOnly = context.attackText();
        if (attackName == null || attackName.isBlank() || attackTextOnly == null || attackTextOnly.isBlank()) return false;
        String normalizedName = java.text.Normalizer.normalize(attackName, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('’', '\'')
            .toLowerCase(java.util.Locale.ROOT);
        String normalizedTextOnly = java.text.Normalizer.normalize(attackTextOnly, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('’', '\'')
            .toLowerCase(java.util.Locale.ROOT);
        return normalizedTextOnly.contains(normalizedName);
    }
}
