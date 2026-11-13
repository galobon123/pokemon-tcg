package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class UpsideDownEvolutionAbilityEffectStrategy implements AbilityEffectStrategy {

    @Override
    public String abilityName() {
        return "Upside-Down Evolution";
    }

    @Override
    public TrainerEffectResult apply(AbilityEffectContext context) {
        GameAggregate game = context.game();
        UUID player = context.player();
        String slot = context.slotReference();
        GameCard inkayCard = context.pokemonCard();

        if (!"ACTIVE".equals(slot)) {
            throw new InvalidActionException("Inkay debe ser tu Pokemon Activo para usar esta habilidad");
        }

        List<String> deck = game.deck().getOrDefault(player, List.of());
        String evolutionId = findEvolutionInDeck(game, deck, inkayCard.name());

        if (evolutionId != null) {
            deck.remove(evolutionId);
            game.evolveAtSlot(player, slot, evolutionId);
            Collections.shuffle(deck);
            game.deckCardsRemaining().put(player, deck.size());

            GameCard evolvedCard = game.cardCatalog().get(evolutionId);
            Map<String, Object> payload = new HashMap<>();
            payload.put("evolvedTo", evolvedCard != null ? evolvedCard.name() : evolutionId);
            payload.put("targetSlot", slot);
            payload.put("abilityName", "Upside-Down Evolution");
            return TrainerEffectResult.of("UPSIDE_DOWN_EVOLUTION", payload);
        }

        Collections.shuffle(deck);
        game.deckCardsRemaining().put(player, deck.size());

        return TrainerEffectResult.of("UPSIDE_DOWN_EVOLUTION_NO_CARD", Map.of(
            "abilityName", "Upside-Down Evolution",
            "reason", "No se encontro evolucion valida en el mazo"
        ));
    }

    private String findEvolutionInDeck(GameAggregate game, List<String> deck, String inkayName) {
        for (String id : deck) {
            GameCard card = game.cardCatalog().get(id);
            if (card != null && card.evolvesFrom() != null
                && card.evolvesFrom().equalsIgnoreCase(inkayName)) {
                return id;
            }
        }
        return null;
    }
}
