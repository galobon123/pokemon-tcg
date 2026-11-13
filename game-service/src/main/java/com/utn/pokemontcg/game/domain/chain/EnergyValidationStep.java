package com.utn.pokemontcg.game.domain.chain;

import com.utn.pokemontcg.game.domain.exception.InsufficientEnergyException;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;

import java.util.HashMap;
import java.util.Map;

public class EnergyValidationStep implements AttackStep {
    @Override
    public void execute(AttackContext context) {
        context.audit("EnergyValidationStep:start");
        if (context.cancelled()) return;

        if (!context.requiredEnergyCost().isEmpty()) {
            validateRealEnergyCost(context);
            context.audit("EnergyValidationStep:ok");
            return;
        }

        if (context.attachedEnergy() < context.requiredEnergy()) {
            context.setCancelled(true);
            context.audit("EnergyValidationStep:cancelled_insufficient_energy");
            return;
        }
        context.audit("EnergyValidationStep:ok");
    }

    private void validateRealEnergyCost(AttackContext context) {
        Map<String, Integer> available = new HashMap<>();
        int rainbowCount = 0;

        for (AttachedEnergy energy : context.attachedEnergies()) {
            String type = normalizeType(energy.energyType());
            if ("Rainbow".equals(type)) {
                rainbowCount += energy.energyAmount();
            } else {
                available.merge(type, energy.energyAmount(), Integer::sum);
            }
        }

        int colorlessNeeded = 0;
        for (String required : context.requiredEnergyCost()) {
            String normalized = normalizeType(required);
            if ("Colorless".equals(normalized)) {
                colorlessNeeded++;
                continue;
            }
            int remaining = available.getOrDefault(normalized, 0) - 1;
            if (remaining < 0) {
                if (rainbowCount > 0) {
                    rainbowCount--;
                    continue;
                }
                throw missingEnergy(context, normalized, 1);
            }
            available.put(normalized, remaining);
        }

        int remainingAnyEnergy = available.values().stream().mapToInt(Integer::intValue).sum() + rainbowCount;
        if (remainingAnyEnergy < colorlessNeeded) {
            throw missingEnergy(context, "Colorless", colorlessNeeded - remainingAnyEnergy);
        }
    }

    private InsufficientEnergyException missingEnergy(AttackContext context, String type, int missing) {
        context.audit("EnergyValidationStep:cancelled_missing_" + type);
        String attackName = context.attackName() == null || context.attackName().isBlank()
            ? "el ataque"
            : context.attackName();
        return new InsufficientEnergyException(
            "No puedes atacar: te falta " + missing + " Energia de " + spanishType(type) + " para usar " + attackName
        );
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "Colorless";
        }
        return type.trim();
    }

    private String spanishType(String type) {
        return switch (normalizeType(type)) {
            case "Fire" -> "Fuego";
            case "Water" -> "Agua";
            case "Grass" -> "Planta";
            case "Lightning" -> "Electrica";
            case "Psychic" -> "Psiquica";
            case "Fighting" -> "Lucha";
            case "Darkness" -> "Oscuridad";
            case "Metal" -> "Metal";
            case "Fairy" -> "Hada";
            case "Dragon" -> "Dragon";
            case "Colorless" -> "Incolora";
            default -> type;
        };
    }
}
