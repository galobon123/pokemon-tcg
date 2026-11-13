package com.utn.pokemontcg.game.domain.chain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PreAttackStep implements AttackStep {
    @Override
    public void execute(AttackContext context) {
        context.audit("PreAttackStep:start");
        if (context.cancelled()) return;
        String text = attackText(context);
        if (!isPreDamageCoinFlip(text)) {
            context.audit("PreAttackStep:no_pre_damage_coin_flip");
            return;
        }

        int flips = coinFlipCount(text);
        List<Boolean> results = new ArrayList<>();
        for (int i = 0; i < flips; i++) {
            results.add(context.coinFlip().getAsBoolean());
        }
        context.setCoinFlipResults(results);
        int heads = (int) results.stream().filter(Boolean::booleanValue).count();
        context.audit("PreAttackStep:coin_flips=" + results);

        if (heads == 0 && cancelsOnTails(text)) {
            context.setCancelled(true);
            context.setDamage(0);
            context.audit("PreAttackStep:attack_cancelled");
            return;
        }

        int bonus = heads * damagePerHeads(text);
        context.setPreAttackBonus(context.preAttackBonus() + bonus);
        context.audit("PreAttackStep:bonus_applied=" + context.preAttackBonus());
    }

    private String attackText(AttackContext context) {
        return ((context.attackName() == null ? "" : context.attackName()) + " "
            + (context.attackText() == null ? "" : context.attackText()))
            .toLowerCase(Locale.ROOT);
    }

    private boolean isPreDamageCoinFlip(String text) {
        return text.contains("flip") && (text.contains("does nothing") || text.contains("more damage for each heads"))
            && !text.contains("damage times") && !text.contains("times the number of heads")
            && !text.contains("until you get tails");
    }

    private boolean cancelsOnTails(String text) {
        return text.contains("if tails") && text.contains("does nothing");
    }

    private int coinFlipCount(String text) {
        Matcher explicit = Pattern.compile("flips?\\s+(\\d+)\\s+coins").matcher(text);
        if (explicit.find()) {
            return Integer.parseInt(explicit.group(1));
        }
        return 1;
    }

    private int damagePerHeads(String text) {
        Matcher matcher = Pattern.compile("(\\d+)\\s+more\\s+damage\\s+for\\s+each\\s+heads").matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        matcher = Pattern.compile("\\+\\s*(\\d+)").matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }
}
