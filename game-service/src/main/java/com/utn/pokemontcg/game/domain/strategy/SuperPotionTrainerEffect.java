package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SuperPotionTrainerEffect extends AbstractTrainerEffectStrategy {
    @Override
    public String cardName() { return "Super Potion"; }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        String slot = context.game().normalizeSlot(context.targetSlot());
        List<AttachedEnergy> energies = new ArrayList<>(context.game().attachedEnergiesFor(context.player(), slot));
        if (energies.isEmpty()) {
            return TrainerEffectResult.noop("NO_ENERGY_TO_DISCARD");
        }
        int previous = context.game().damageCountersAtSlot(context.player(), slot);
        int healed = Math.min(6, previous);
        context.game().healDamageCounters(context.player(), slot, healed);
        AttachedEnergy energy = energies.stream()
            .filter(e -> context.selectedEnergyCardIds().contains(e.cardId()))
            .findFirst()
            .orElse(energies.get(0));
        context.game().removeAttachedEnergies(context.player(), slot, List.of(energy.cardId()));
        context.game().discardPile().computeIfAbsent(context.player(), ignored -> new ArrayList<>()).add(energy.cardId());
        return result("DAMAGE_HEALED_AND_ENERGY_DISCARDED", Map.of("targetSlot", slot, "healedCounters", healed));
    }
}
