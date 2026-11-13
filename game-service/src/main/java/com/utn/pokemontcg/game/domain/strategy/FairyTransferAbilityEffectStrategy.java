package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FairyTransferAbilityEffectStrategy implements AbilityEffectStrategy {

    @Override
    public String abilityName() {
        return "Fairy Transfer";
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public TrainerEffectResult apply(AbilityEffectContext context) {
        GameAggregate game = context.game();
        String activeSlot = game.normalizeSlot(context.slotReference());
        if (!"ACTIVE".equals(activeSlot)) {
            throw new InvalidActionException("Fairy Transfer solo puede usarse desde el Pokemon Activo");
        }

        String targetId = context.targetId();
        if (targetId == null || targetId.isBlank() || !targetId.contains("|")) {
            throw new InvalidActionException("Fairy Transfer requiere objetivo con formato 'srcSlot|destSlot|energyCardId'");
        }
        String[] parts = targetId.split("\\|", 3);
        if (parts.length != 3) {
            throw new InvalidActionException("Fairy Transfer requiere objetivo con formato 'srcSlot|destSlot|energyCardId'");
        }
        String srcSlot = game.normalizeSlot(parts[0]);
        String destSlot = game.normalizeSlot(parts[1]);
        String energyCardId = parts[2];

        if (!game.isOccupiedSlot(context.player(), srcSlot)) {
            throw new InvalidActionException("El Pokemon origen no pertenece al jugador o no esta en juego");
        }
        if (!game.isOccupiedSlot(context.player(), destSlot)) {
            throw new InvalidActionException("El Pokemon destino no pertenece al jugador o no esta en juego");
        }

        List<AttachedEnergy> srcEnergies = game.attachedEnergiesFor(context.player(), srcSlot);
        AttachedEnergy energy = srcEnergies.stream()
            .filter(e -> e.cardId().equals(energyCardId))
            .findFirst()
            .orElseThrow(() -> new InvalidActionException("La energia indicada no esta adjunta al Pokemon origen"));

        if (!"Fairy".equalsIgnoreCase(energy.energyType())) {
            throw new InvalidActionException("Fairy Transfer solo puede mover energias Fairy");
        }

        game.removeAttachedEnergies(context.player(), srcSlot, List.of(energyCardId));
        game.attachEnergy(context.player(), destSlot, energy);

        Map<String, Object> payload = new HashMap<>();
        payload.put("abilityName", "Fairy Transfer");
        payload.put("srcSlot", srcSlot);
        payload.put("destSlot", destSlot);
        payload.put("energyCardId", energyCardId);

        return TrainerEffectResult.of("FAIRY_TRANSFER_APPLIED", payload);
    }
}
