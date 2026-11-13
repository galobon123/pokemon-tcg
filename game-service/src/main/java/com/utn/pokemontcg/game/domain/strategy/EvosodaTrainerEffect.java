package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameCard;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class EvosodaTrainerEffect extends AbstractTrainerEffectStrategy {
    @Override
    public String cardName() { return "Evosoda"; }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        String slot = context.game().normalizeSlot(context.targetSlot());
        String baseId = context.game().cardAtSlot(context.player(), slot);
        GameCard base = context.game().cardCatalog().get(baseId);
        List<String> deck = context.game().deck().getOrDefault(context.player(), new ArrayList<>());
        List<String> candidates = context.selectedCardIds().isEmpty() ? new ArrayList<>(deck) : context.selectedCardIds();
        for (String id : new ArrayList<>(candidates)) {
            if (!deck.contains(id)) {
                continue;
            }
            GameCard card = context.game().cardCatalog().get(id);
            if (card != null && base != null && base.name().equalsIgnoreCase(card.evolvesFrom())) {
                deck.remove(id);
                context.game().evolveAtSlot(context.player(), slot, id);
                Collections.shuffle(deck);
                context.game().deckCardsRemaining().put(context.player(), deck.size());
                return result("POKEMON_EVOLVED_FROM_DECK", Map.of("targetSlot", slot, "cardId", id));
            }
        }
        Collections.shuffle(deck);
        return TrainerEffectResult.noop("NO_EVOLUTION_FOUND");
    }
}
