package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class StanceChangeAbilityEffectStrategy implements AbilityEffectStrategy {

    @Override
    public String abilityName() {
        return "Stance Change";
    }

    @Override
    public TrainerEffectResult apply(AbilityEffectContext context) {
        GameAggregate game = context.game();
        String activeSlot = game.normalizeSlot(context.slotReference());
        if (!"ACTIVE".equals(activeSlot)) {
            throw new InvalidActionException("Stance Change solo puede usarse desde el Pokemon Activo");
        }

        String handCardId = context.targetId();
        if (handCardId == null || handCardId.isBlank()) {
            throw new InvalidActionException("Stance Change requiere seleccionar un Aegislash de la mano");
        }

        List<String> playerHand = game.hand().get(context.player());
        if (playerHand == null || !playerHand.contains(handCardId)) {
            throw new InvalidActionException("La carta seleccionada no esta en la mano");
        }

        GameCard handCard = game.cardCatalog().get(handCardId);
        if (handCard == null || !"Aegislash".equalsIgnoreCase(handCard.name())) {
            throw new InvalidActionException("Stance Change requiere un Aegislash de la mano");
        }

        game.swapActiveWithHand(context.player(), handCardId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("abilityName", "Stance Change");
        payload.put("handCardId", handCardId);

        return TrainerEffectResult.of("STANCE_CHANGE_APPLIED", payload);
    }
}
