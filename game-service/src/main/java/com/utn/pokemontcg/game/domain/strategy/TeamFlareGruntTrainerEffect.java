package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TeamFlareGruntTrainerEffect extends AbstractTrainerEffectStrategy {
    @Override
    public String cardName() { return "Team Flare Grunt"; }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        var opponent = opponentOf(context.game(), context.player());
        List<AttachedEnergy> energies = new ArrayList<>(context.game().attachedEnergiesFor(opponent, "ACTIVE"));
        if (energies.isEmpty()) return TrainerEffectResult.noop("NO_ENERGY_ATTACHED");
        AttachedEnergy energy = energies.stream()
            .filter(e -> context.selectedEnergyCardIds().contains(e.cardId()))
            .findFirst()
            .orElse(energies.get(0));
        context.game().removeAttachedEnergies(opponent, "ACTIVE", List.of(energy.cardId()));
        context.game().discardPile().computeIfAbsent(opponent, ignored -> new ArrayList<>()).add(energy.cardId());
        return result("OPPONENT_ENERGY_DISCARDED", Map.of("energyCardId", energy.cardId()));
    }
}
