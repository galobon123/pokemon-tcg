package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class DriveOffAbilityEffectStrategy implements AbilityEffectStrategy {

    @Override
    public String abilityName() {
        return "Drive Off";
    }

    @Override
    public TrainerEffectResult apply(AbilityEffectContext context) {
        GameAggregate game = context.game();
        UUID player = context.player();

        // Drive Off solo funciona si el Pokémon con la habilidad es el Active
        String activeCardId = game.activePokemon().get(player);
        if (activeCardId == null || !activeCardId.equals(context.pokemonCard().id())) {
            throw new InvalidActionException(
                "Drive Off solo puede usarse cuando este Pokémon es tu Pokémon Activo"
            );
        }

        // Parsear targetId: formato "slot:playerId"
        String targetId = context.targetId();
        if (targetId == null || targetId.isBlank()) {
            throw new InvalidActionException(
                "Drive Off requiere seleccionar un Pokémon de la banca del oponente"
            );
        }

        String[] parts = targetId.split(":");
        if (parts.length != 2) {
            throw new InvalidActionException(
                "Target inválido para Drive Off: " + targetId
            );
        }

        String targetSlot = parts[0];
        UUID targetPlayerId = UUID.fromString(parts[1]);

        // Validar que el target es el oponente
        UUID opponent = opponentOf(game, player);
        if (!opponent.equals(targetPlayerId)) {
            throw new InvalidActionException(
                "Drive Off solo puede cambiar el Pokémon Activo del oponente"
            );
        }

        // Normalizar slot y obtener índice del bench
        String normalizedSlot = game.normalizeSlot(targetSlot);
        int benchIndex = game.benchIndex(normalizedSlot);

        // Ejecutar el switch
        game.swapActiveWithBench(opponent, benchIndex);

        Map<String, Object> payload = new HashMap<>();
        payload.put("abilityName", "Drive Off");
        payload.put("targetSlot", normalizedSlot);
        payload.put("opponentId", opponent.toString());

        return TrainerEffectResult.of("DRIVE_OFF_ACTIVATED", payload);
    }

    private UUID opponentOf(GameAggregate game, UUID player) {
        return player != null && player.equals(game.playerOne())
            ? game.playerTwo()
            : game.playerOne();
    }
}
