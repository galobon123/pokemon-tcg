package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.deck.DeckService;
import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameCard;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CoinFlipEnergyAttachAttackEffectStrategy extends AbstractAttackEffectStrategy {
    @Override
    public String effectName() {
        return "COIN_FLIP_ENERGY_ATTACH";
    }

    @Override
    public boolean supports(AttackContext context) {
        String text = effectText(context);
        return text.contains("flip")
            && text.contains("for each heads")
            && text.contains("attach")
            && text.contains("energy")
            && containsAny(text, "from your discard", "from the discard", "from discard");
    }

    @Override
    public void apply(AttackContext context) {
        if (context.cancelled()) return;

        String text = effectText(context);
        int flips = flipCount(text);
        List<Boolean> results = new ArrayList<>();
        for (int i = 0; i < flips; i++) {
            results.add(context.coinFlip().getAsBoolean());
        }
        context.setCoinFlipResults(results);
        int heads = (int) results.stream().filter(Boolean::booleanValue).count();

        UUID player = attacker(context);
        String targetSlot = targetSlot(context, player);
        if (targetSlot == null) {
            context.audit("AttackEffect:coin_flip_energy_attach_no_target");
            return;
        }

        List<String> discard = context.game().discardPile().computeIfAbsent(player, ignored -> new ArrayList<>());
        String requiredType = requiredEnergyType(text);
        int attached = 0;

        for (String cardId : new ArrayList<>(discard)) {
            if (attached >= heads) break;
            GameCard card = context.game().cardCatalog().get(cardId);
            if (card == null || !card.isEnergy()) continue;
            String energyType = DeckService.inferEnergyType(card.name(), card.energyType());
            if (requiredType != null && !requiredType.equalsIgnoreCase(energyType)) continue;
            discard.remove(cardId);
            context.game().attachEnergy(player, targetSlot, new AttachedEnergy(cardId, energyType));
            attached++;
        }

        context.audit("AttackEffect:coin_flip_energy_attached=" + attached + ",heads=" + heads);
    }

    private int flipCount(String text) {
        Matcher explicit = Pattern.compile("flip\\s+(\\d+)\\s+coins").matcher(text);
        if (explicit.find()) return Integer.parseInt(explicit.group(1));
        return 1;
    }

    private String targetSlot(AttackContext context, UUID player) {
        String requested = context.game().normalizeSlot(context.targetSlot());
        if (requested.startsWith("BENCH_") && context.game().isOccupiedSlot(player, requested)) {
            return requested;
        }
        List<String> bench = context.game().bench().getOrDefault(player, List.of());
        for (int i = 0; i < bench.size(); i++) {
            if (bench.get(i) != null) return "BENCH_" + i;
        }
        return null;
    }

    private String requiredEnergyType(String text) {
        for (String type : List.of("Fire", "Water", "Grass", "Lightning", "Psychic", "Fighting", "Darkness", "Metal", "Fairy", "Dragon", "Colorless")) {
            if (text.contains(type.toLowerCase() + " energy")) {
                return type;
            }
        }
        return null;
    }
}
