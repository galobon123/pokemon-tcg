package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.engine.RuleValidator;
import com.utn.pokemontcg.game.domain.exception.InvalidActionPhaseException;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleValidatorTest {

    /*
     * JaCoCo gap analysis before this pass:
     * - RuleValidator had 100% line and branch coverage already.
     * - The broader turn-rule cases from the prompt live in TurnActionValidator
     *   and GameApplicationService, not in this class.
     */
    private final RuleValidator validator = new RuleValidator();

    @Test
    void rejectsActionsWhenGameIsFinished() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setGameState(GameState.FINISHED);

        assertThrows(InvalidActionPhaseException.class, () -> validator.validate(game, GameActionType.DRAW));
    }

    @Test
    void rejectsAttackOutsideAttackPhase() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.MAIN);

        assertThrows(InvalidActionPhaseException.class, () -> validator.validate(game, GameActionType.ATTACK));
    }

    @Test
    void allowsAttackInAttackPhaseForActiveGame() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.ATTACK);

        assertDoesNotThrow(() -> validator.validate(game, GameActionType.ATTACK));
    }
}
