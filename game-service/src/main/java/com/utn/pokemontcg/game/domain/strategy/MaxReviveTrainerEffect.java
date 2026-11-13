package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameCard;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MaxReviveTrainerEffect extends AbstractTrainerEffectStrategy {
    @Override
    public String cardName() { return "Max Revive"; }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        List<String> discard = context.game().discardPile().getOrDefault(context.player(), new ArrayList<>());
        List<String> deck = context.game().deck().getOrDefault(context.player(), new ArrayList<>());
        List<String> candidates = context.selectedCardIds().isEmpty() ? new ArrayList<>(discard) : context.selectedCardIds();
        for (String id : new ArrayList<>(candidates)) {
            if (!discard.contains(id)) {
                continue;
            }
            GameCard card = context.game().cardCatalog().get(id);
            if (card != null && card.isPokemon()) {
                discard.remove(id);
                deck.add(0, id);
                context.game().deckCardsRemaining().put(context.player(), deck.size());
                return result("POKEMON_TO_TOP_OF_DECK", Map.of("cardId", id));
            }
        }
        return TrainerEffectResult.noop("NO_POKEMON_IN_DISCARD");
    }
}
