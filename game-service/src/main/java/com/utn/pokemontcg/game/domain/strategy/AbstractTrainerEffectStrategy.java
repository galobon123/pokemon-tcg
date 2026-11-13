package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractTrainerEffectStrategy implements TrainerEffectStrategy {

    protected UUID opponentOf(GameAggregate game, UUID player) {
        return player != null && player.equals(game.playerOne()) ? game.playerTwo() : game.playerOne();
    }

    protected List<String> draw(GameAggregate game, UUID player, int cards) {
        List<String> drawn = new ArrayList<>();
        List<String> deck = game.deck().computeIfAbsent(player, k -> new ArrayList<>());
        List<String> hand = game.hand().computeIfAbsent(player, k -> new ArrayList<>());
        for (int i = 0; i < cards && !deck.isEmpty(); i++) {
            String card = deck.remove(0);
            hand.add(card);
            drawn.add(card);
        }
        game.deckCardsRemaining().put(player, deck.size());
        return drawn;
    }

    protected List<String> searchBasicEnergy(GameAggregate game, UUID player, int maxCards) {
        List<String> deck = game.deck().computeIfAbsent(player, k -> new ArrayList<>());
        List<String> hand = game.hand().computeIfAbsent(player, k -> new ArrayList<>());
        List<String> found = new ArrayList<>();
        for (String instanceId : new ArrayList<>(deck)) {
            if (found.size() >= maxCards) {
                break;
            }
            GameCard card = game.cardCatalog().get(instanceId);
            if (card != null && card.isBasicEnergy()) {
                deck.remove(instanceId);
                hand.add(instanceId);
                String rawId = instanceId.contains(":")
                    ? instanceId.substring(0, instanceId.indexOf(':'))
                    : instanceId;
                found.add(rawId);
            }
        }
        Collections.shuffle(deck);
        game.deckCardsRemaining().put(player, deck.size());
        return found;
    }

    protected void shuffleHandIntoDeck(GameAggregate game, UUID player) {
        List<String> deck = game.deck().computeIfAbsent(player, k -> new ArrayList<>());
        List<String> hand = game.hand().computeIfAbsent(player, k -> new ArrayList<>());
        deck.addAll(hand);
        hand.clear();
        Collections.shuffle(deck);
        game.deckCardsRemaining().put(player, deck.size());
    }

    protected void discardHand(GameAggregate game, UUID player) {
        List<String> discard = game.discardPile().computeIfAbsent(player, ignored -> new ArrayList<>());
        List<String> hand = game.hand().computeIfAbsent(player, k -> new ArrayList<>());
        discard.addAll(hand);
        hand.clear();
    }

    protected TrainerEffectResult result(String result, Map<String, Object> payload) {
        return TrainerEffectResult.of(result, payload);
    }

    protected List<Map<String, Object>> revealedCards(GameAggregate game, List<String> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> cards = new ArrayList<>();
        for (String cardId : cardIds) {
            GameCard card = findCard(game, cardId);
            if (card == null) {
                continue;
            }
            cards.add(revealedCard(card));
        }
        return cards;
    }

    private GameCard findCard(GameAggregate game, String cardId) {
        GameCard exact = game.cardCatalog().get(cardId);
        if (exact != null) {
            return exact;
        }
        String rawId = rawCardId(cardId);
        return game.cardCatalog().entrySet().stream()
            .filter(entry -> rawCardId(entry.getKey()).equals(rawId) || rawCardId(entry.getValue().id()).equals(rawId))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private Map<String, Object> revealedCard(GameCard card) {
        String instanceId = card.id();
        String cardId = rawCardId(instanceId);
        Map<String, Object> revealed = new LinkedHashMap<>();
        revealed.put("cardId", cardId);
        revealed.put("setId", setId(cardId));
        revealed.put("name", card.name() == null ? cardId : card.name());
        revealed.put("instanceId", instanceId);
        return revealed;
    }

    private String rawCardId(String cardId) {
        if (cardId == null) {
            return "";
        }
        int separator = cardId.indexOf(':');
        return separator >= 0 ? cardId.substring(0, separator) : cardId;
    }

    private String setId(String cardId) {
        int separator = cardId == null ? -1 : cardId.indexOf('-');
        return separator > 0 ? cardId.substring(0, separator) : "";
    }
}
