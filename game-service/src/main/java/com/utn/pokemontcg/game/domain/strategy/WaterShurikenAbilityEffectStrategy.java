package com.utn.pokemontcg.game.domain.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;

@Component
public class WaterShurikenAbilityEffectStrategy implements AbilityEffectStrategy {

    private static final int DAMAGE_COUNTERS = 3;

    @Override
    public String abilityName() {
        return "Water Shuriken";
    }

    @Override
    public TrainerEffectResult apply(AbilityEffectContext context) {
        GameAggregate game = context.game();
        UUID player = context.player();
        String targetId = context.targetId();

        // 1. Validaciones iniciales del objetivo
        if (targetId == null || targetId.isBlank()) {
            throw new InvalidActionException("Water Shuriken requiere seleccionar un Pokemon del rival como objetivo");
        }

        String[] parts = targetId.split(":");
        if (parts.length != 2) {
            throw new InvalidActionException("Formato de objetivo invalido");
        }

        String targetSlot = game.normalizeSlot(parts[0]);
        UUID targetPlayer;

        try {
            targetPlayer = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new InvalidActionException("Identificador de jugador objetivo invalido");
        }

        // 2. Validar que el objetivo sea realmente el oponente
        UUID opponent = opponentOf(game, player);
        if (!targetPlayer.equals(opponent)) {
            throw new InvalidActionException("Water Shuriken solo puede seleccionar Pokemon del rival como objetivo");
        }

        if (!game.isOccupiedSlot(targetPlayer, targetSlot)) {
            throw new InvalidActionException("El slot objetivo no contiene un Pokemon");
        }

        // 3. Buscar la energía en la mano
        List<String> currentHand = game.hand().get(player);
        if (currentHand == null || currentHand.isEmpty()) {
            throw new InvalidActionException("Water Shuriken requiere descartar una Energia Water de la mano");
        }

        String waterEnergyId = findWaterEnergyInHand(game, currentHand);
        if (waterEnergyId == null) {
            throw new InvalidActionException("Water Shuriken requiere descartar una Energia Water de la mano");
        }

        // 🔥 SOLUCIÓN AL BUG: Evitamos UnsupportedOperationException si la mano es inmutable
        try {
            currentHand.remove(waterEnergyId);
        } catch (UnsupportedOperationException e) {
            // Si la lista original era inmutable, creamos una copia mutable y actualizamos el mapa
            List<String> mutableHand = new ArrayList<>(currentHand);
            mutableHand.remove(waterEnergyId);
            game.hand().put(player, mutableHand);
        }

        // 4. Mover la carta a la pila de descartes
        game.discardPile().computeIfAbsent(player, k -> new ArrayList<>()).add(waterEnergyId);

        // 5. Aplicar contadores de daño
        game.addDamageCounters(targetPlayer, targetSlot, DAMAGE_COUNTERS);

        // 6. Construir el resultado del efecto
        Map<String, Object> payload = new HashMap<>();
        payload.put("abilityName", "Water Shuriken");
        payload.put("discardedEnergy", waterEnergyId);
        payload.put("targetPlayer", targetPlayer.toString());
        payload.put("targetSlot", targetSlot);
        payload.put("damageCounters", DAMAGE_COUNTERS);

        return TrainerEffectResult.of("WATER_SHURIKEN_ACTIVATED", payload);
    }

    private String findWaterEnergyInHand(GameAggregate game, List<String> hand) {
        for (String cardId : hand) {
            GameCard card = game.cardCatalog().get(cardId);
            if (card != null && card.isEnergy() && "Water".equalsIgnoreCase(card.energyType())) {
                return cardId;
            }
        }
        return null;
    }

    private UUID opponentOf(GameAggregate game, UUID player) {
        if (player == null) {
            return game.playerTwo();
        }
        return player.equals(game.playerOne()) ? game.playerTwo() : game.playerOne();
    }
}