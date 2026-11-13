package com.utn.pokemontcg.game.domain.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class RedCardTrainerEffect extends AbstractTrainerEffectStrategy {
    @Override
    public String cardName() { return "Red Card"; }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        var opponent = opponentOf(context.game(), context.player());
        List<String> deck = context.game().deck().computeIfAbsent(opponent, k -> new ArrayList<>());
        List<String> hand = context.game().hand().computeIfAbsent(opponent, k -> new ArrayList<>());
        deck.addAll(hand);
        hand.clear();
        Collections.shuffle(deck);
        int drawn = draw(context.game(), opponent, 4).size();
        return result("OPPONENT_HAND_SHUFFLED_AND_DRAWN", Map.of("cardsDrawn", drawn));
    }
}
