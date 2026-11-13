package com.utn.pokemontcg.game.domain.engine;

import com.utn.pokemontcg.game.domain.exception.InvalidActionPhaseException;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import org.springframework.stereotype.Component;

@Component
public class RuleValidator {

    public void validate(GameAggregate game, GameActionType actionType) {
        if (game.gameState().name().equals("FINISHED")) {
            throw new InvalidActionPhaseException("La partida ya finalizó.");
        }

        if (actionType == GameActionType.ATTACK && game.turnPhase() != TurnPhase.ATTACK) {
            throw new InvalidActionPhaseException("Solo se puede atacar durante la fase ATTACK.");
        }
    }
}
