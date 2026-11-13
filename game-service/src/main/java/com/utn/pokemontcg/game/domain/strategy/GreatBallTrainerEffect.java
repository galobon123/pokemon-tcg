package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameCard;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class GreatBallTrainerEffect extends AbstractTrainerEffectStrategy {
    @Override
    public String cardName() { return "Great Ball"; }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        List<String> deck = context.game().deck().getOrDefault(context.player(), new ArrayList<>());
        List<String> hand = context.game().hand().getOrDefault(context.player(), new ArrayList<>());
        int limit = Math.min(7, deck.size());
        List<String> topCards = new ArrayList<>(deck.subList(0, limit));
        List<String> candidates = context.selectedCardIds().isEmpty() ? topCards : context.selectedCardIds();
        for (String id : candidates) {
            if (!topCards.contains(id)) {
                continue;
            }
            GameCard card = context.game().cardCatalog().get(id);
            if (card != null && card.isPokemon()) {
                deck.remove(id);
                hand.add(id);
                Collections.shuffle(deck);
                context.game().deckCardsRemaining().put(context.player(), deck.size());
                return result("POKEMON_FOUND", Map.of("cardId", id));
            }
        }
        Collections.shuffle(deck);
        return TrainerEffectResult.noop("NO_POKEMON_FOUND");
    }
}
