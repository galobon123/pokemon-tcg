package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MysticalFireAbilityEffectStrategy implements AbilityEffectStrategy {

    private static final int MAX_HAND_SIZE = 6;

    @Override
    public String abilityName() {
        return "Mystical Fire";
    }

    @Override
    public TrainerEffectResult apply(AbilityEffectContext context) {
        GameAggregate game = context.game();
        UUID player = context.player();

        List<String> hand = game.hand().getOrDefault(player, List.of());
        List<String> deck = game.deck().getOrDefault(player, List.of());
        int currentHandSize = hand.size();

        if (currentHandSize >= MAX_HAND_SIZE) {
            return TrainerEffectResult.of("MYSTICAL_FIRE_NO_DRAW", Map.of(
                "abilityName", "Mystical Fire",
                "handSize", currentHandSize,
                "cardsDrawn", 0
            ));
        }

        int cardsNeeded = MAX_HAND_SIZE - currentHandSize;
        int cardsDrawn = 0;

        for (int i = 0; i < cardsNeeded; i++) {
            if (deck.isEmpty()) {
                break;
            }
            hand.add(deck.remove(0));
            cardsDrawn++;
        }

        game.deckCardsRemaining().put(player, deck.size());

        Map<String, Object> payload = new HashMap<>();
        payload.put("abilityName", "Mystical Fire");
        payload.put("handSize", currentHandSize);
        payload.put("finalHandSize", hand.size());
        payload.put("cardsDrawn", cardsDrawn);
        payload.put("deckRemaining", deck.size());

        return TrainerEffectResult.of("MYSTICAL_FIRE_DRAW", payload);
    }
}
