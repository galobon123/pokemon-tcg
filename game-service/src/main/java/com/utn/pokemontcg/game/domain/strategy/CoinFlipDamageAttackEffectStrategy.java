package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.chain.ModifierStep;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CoinFlipDamageAttackEffectStrategy extends AbstractAttackEffectStrategy {
    @Override
    public String effectName() {
        return "COIN_FLIP_DAMAGE";
    }

    @Override
    public boolean supports(AttackContext context) {
        String text = effectText(context);
        return text.contains("flip") && (containsAny(text, "damage times", "times the number of heads", "until you get tails")
            || (text.contains("for each heads") && !text.contains("more damage")));
    }

    @Override
    public void apply(AttackContext context) {
        if (context.cancelled()) return;
        String text = effectText(context);
        int flips = flipCount(context, text);
        List<Boolean> results = new ArrayList<>();
        if (text.contains("until you get tails")) {
            boolean heads;
            do {
                heads = context.coinFlip().getAsBoolean();
                results.add(heads);
            } while (heads && results.size() < 20);
        } else {
            for (int i = 0; i < flips; i++) {
                results.add(context.coinFlip().getAsBoolean());
            }
        }
        if (context.coinFlipResults().isEmpty()) {
            context.setCoinFlipResults(results);
        } else {
            List<Boolean> merged = new ArrayList<>(context.coinFlipResults());
            merged.addAll(results);
            context.setCoinFlipResults(merged);
        }
        int heads = (int) results.stream().filter(Boolean::booleanValue).count();
        int amount = amount(text, context.baseDamage());
        if (text.contains("more damage")) {
            context.setPreAttackBonus(context.preAttackBonus() + heads * amount);
        } else {
            context.setBaseDamage(heads * amount);
        }
        if (heads == results.size() && text.contains("prevent all effects of attacks")) {
            context.game().addActiveEffectUntilTurn(attacker(context), ModifierStep.DEFENDER_DAMAGE_REDUCTION, 999, context.game().turnNumber() + 1);
        }
        context.audit("AttackEffect:coin_flip_damage_heads=" + heads + ",amount=" + amount);
    }

    private int flipCount(AttackContext context, String text) {
        Matcher explicit = Pattern.compile("flips?\\s+(\\d+)\\s+coins").matcher(text);
        if (explicit.find()) return Integer.parseInt(explicit.group(1));
        if (text.contains("for each damage counter on this")) return context.game().damageCountersAtSlot(attacker(context), "ACTIVE");
        if (text.contains("for each fighting energy attached")) {
            return (int) context.game().attachedEnergiesFor(attacker(context), "ACTIVE").stream()
                .filter(e -> "Fighting".equalsIgnoreCase(e.energyType()))
                .count();
        }
        return 1;
    }

    private int amount(String text, int fallback) {
        Matcher matcher = Pattern.compile("(\\d+)\\s+damage").matcher(text);
        if (matcher.find()) return Integer.parseInt(matcher.group(1));
        matcher = Pattern.compile("(\\d+)\\s+more\\s+damage").matcher(text);
        if (matcher.find()) return Integer.parseInt(matcher.group(1));
        return fallback;
    }
}
