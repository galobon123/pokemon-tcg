package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.chain.AttackContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractAttackEffectStrategy implements AttackEffectStrategy {

    private static final Pattern SLOT_REFERENCE = Pattern.compile("ACTIVE|BENCH_\\d+");

    /**
     * Slot references (ACTIVE / BENCH_n) the player chose for multi-target effects. These are
     * carried inside {@code selectedCardIds} alongside any card ids, so effects that need 2+
     * targets (e.g. Hydro Bombard, Geomancy) can read them without a dedicated request field.
     * Order is preserved and duplicates removed.
     */
    protected List<String> selectedSlots(AttackContext context) {
        List<String> slots = new ArrayList<>();
        for (String id : context.selectedCardIds()) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String candidate = id.trim().toUpperCase(Locale.ROOT);
            if (SLOT_REFERENCE.matcher(candidate).matches() && !slots.contains(candidate)) {
                slots.add(candidate);
            }
        }
        return slots;
    }

    /**
     * The entries of {@code selectedCardIds} that are NOT slot references (i.e. actual card ids,
     * such as the Energy cards chosen for Geomancy). Order is preserved.
     */
    protected List<String> selectedNonSlotCardIds(AttackContext context) {
        List<String> ids = new ArrayList<>();
        for (String id : context.selectedCardIds()) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String candidate = id.trim().toUpperCase(Locale.ROOT);
            if (!SLOT_REFERENCE.matcher(candidate).matches()) {
                ids.add(id);
            }
        }
        return ids;
    }

    protected UUID attacker(AttackContext context) {
        return context.game().currentTurnPlayer();
    }

    protected UUID defender(AttackContext context) {
        UUID attacker = attacker(context);
        return attacker != null && attacker.equals(context.game().playerOne())
            ? context.game().playerTwo()
            : context.game().playerOne();
    }

    protected String effectText(AttackContext context) {
        String name = context.attackName() == null ? "" : context.attackName();
        String text = context.attackText() == null ? "" : context.attackText();
        return java.text.Normalizer.normalize(name + " " + text, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('’', '\'')
            .toLowerCase(Locale.ROOT);
    }

    protected boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasCoinCondition(String text) {
        return text.contains("if heads")
            || text.contains("if tails")
            || text.contains("if both")
            || text.contains("if all of them are heads");
    }

    protected boolean coinConditionAllowsEffect(AttackContext context) {
        String text = effectText(context);
        boolean requiresAllHeads = text.contains("if all of them are heads") || text.contains("if both") && text.contains("heads");
        boolean requiresHeads = text.contains("if heads") && !requiresAllHeads;
        boolean requiresTails = text.contains("if tails") && !text.contains("does nothing");
        if (!requiresHeads && !requiresTails && !requiresAllHeads) {
            return true;
        }
        List<Boolean> results = context.coinFlipResults();
        if (results.isEmpty()) {
            java.util.ArrayList<Boolean> generated = new java.util.ArrayList<>();
            int flips = coinFlipCount(text);
            for (int i = 0; i < flips; i++) {
                generated.add(context.coinFlip().getAsBoolean());
            }
            results = generated;
            context.setCoinFlipResults(results);
        }
        boolean hasHeads = results.stream().anyMatch(Boolean::booleanValue);
        boolean hasTails = results.stream().anyMatch(result -> !result);
        boolean allHeads = !results.isEmpty() && results.stream().allMatch(Boolean::booleanValue);
        boolean allowed = (requiresHeads && hasHeads) || (requiresTails && hasTails) || (requiresAllHeads && allHeads);
        context.audit("AttackEffect:coin_condition=" + results + ",allowed=" + allowed);
        return allowed;
    }

    private int coinFlipCount(String text) {
        Matcher explicit = Pattern.compile("flips?\\s+(\\d+)\\s+coins").matcher(text);
        if (explicit.find()) return Integer.parseInt(explicit.group(1));
        return 1;
    }

    protected int firstDamageAmount(String value, int fallback) {
        Matcher matcher = Pattern.compile("(\\d+)\\s+damage").matcher(value);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return fallback;
    }

    protected int firstExplicitBonus(String value, int fallback) {
        Matcher matcher = Pattern.compile("\\+\\s*(\\d+)").matcher(value);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        Matcher moreDamage = Pattern.compile("(\\d+)\\s+more\\s+damage").matcher(value);
        if (moreDamage.find()) {
            return Integer.parseInt(moreDamage.group(1));
        }
        return fallback;
    }
}
