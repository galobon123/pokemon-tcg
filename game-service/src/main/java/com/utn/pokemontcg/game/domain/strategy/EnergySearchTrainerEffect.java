package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EnergySearchTrainerEffect extends AbstractTrainerEffectStrategy {
    @Override
    public String cardName() {
        return "Energy Search";
    }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        List<String> selected = context.selectedCardIds();
        GameAggregate game = context.game();
        UUID player = context.player();
        List<String> found = new ArrayList<>();
        if (!selected.isEmpty()) {
            List<String> deck = game.deck().computeIfAbsent(player, k -> new ArrayList<>());
            List<String> hand = game.hand().computeIfAbsent(player, k -> new ArrayList<>());
            for (String rawId : selected) {
                if (found.size() >= 1) break;
                String instanceId = deck.stream()
                    .filter(id -> id.startsWith(rawId + ":") || id.equals(rawId))
                    .findFirst().orElse(null);
                if (instanceId == null) continue;
                GameCard card = game.cardCatalog().get(instanceId);
                if (card != null && card.isBasicEnergy() && deck.remove(instanceId)) {
                    hand.add(instanceId);
                    found.add(rawId);
                }
            }
            Collections.shuffle(deck);
            game.deckCardsRemaining().put(player, deck.size());
        } else {
            found = searchBasicEnergy(game, player, 1);
        }
        return result("BASIC_ENERGY_SEARCHED", Map.of(
            "cardsFound", found.size(),
            "cardIds", found,
            "revealedCards", revealedCards(game, found)
        ));
    }
}
