package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class CassiusTrainerEffect extends AbstractTrainerEffectStrategy {
    @Override
    public String cardName() { return "Cassius"; }

    @Override
    public TrainerEffectResult apply(TrainerEffectContext context) {
        String slot = context.game().normalizeSlot(context.targetSlot());
        // Cannot shuffle away the Active Pokemon if there is no Benched Pokemon to promote;
        // that would leave the player with no Active Pokemon, which is an illegal state.
        if ("ACTIVE".equals(slot) && context.game().benchOccupiedCount(context.player()) == 0) {
            throw new InvalidActionException("No puedes devolver tu Pokemon Activo al mazo si no tienes Pokemon en la Banca");
        }
        String pokemon = context.game().removeCardAtSlot(context.player(), slot);
        if (pokemon == null) {
            return TrainerEffectResult.noop("NO_POKEMON_IN_SLOT");
        }
        List<String> deck = context.game().deck().computeIfAbsent(context.player(), ignored -> new ArrayList<>());
        deck.add(pokemon);
        List<AttachedEnergy> energies = context.game().discardAllAttachedEnergies(context.player(), slot);
        for (AttachedEnergy energy : energies) {
            deck.add(energy.cardId());
        }
        String tool = context.game().toolAtSlot(context.player(), slot);
        if (tool != null) {
            context.game().clearTool(context.player(), slot);
            deck.add(tool);
        }
        Collections.shuffle(deck);
        context.game().deckCardsRemaining().put(context.player(), deck.size());

        if ("ACTIVE".equals(slot)) {
            context.game().statusByPlayer().remove(context.player());
            context.game().activeDamageCounters().put(context.player(), 0);
            context.game().activeHp().put(context.player(), 0);
            context.game().activePokemonEx().put(context.player(), false);

            int benchCount = context.game().benchOccupiedCount(context.player());
            if (benchCount == 1) {
                for (int i = 0; i < 5; i++) {
                    if (context.game().cardAtSlot(context.player(), "BENCH_" + i) != null) {
                        context.game().promoteBenchToActive(context.player(), i);
                        break;
                    }
                }
            } else if (benchCount > 1) {
                context.game().setGameState(GameState.WAITING_REPLACEMENT);
                context.game().setReplacementPlayerId(context.player());
            }
        }

        return result("POKEMON_RETURNED_TO_DECK", Map.of("targetSlot", slot, "cardId", pokemon));
    }
}
