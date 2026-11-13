package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.TurnActionValidator;
import com.utn.pokemontcg.game.domain.exception.BenchFullException;
import com.utn.pokemontcg.game.domain.exception.EvolutionNotAllowedException;
import com.utn.pokemontcg.game.domain.exception.InvalidActionPhaseException;
import com.utn.pokemontcg.game.domain.exception.SupporterAlreadyPlayedException;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameRuleExceptionTest {

    private final TurnActionValidator validator = new TurnActionValidator();

    @Test
    void benchFullReturnsTypedException() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.hand().put(player, new ArrayList<>(List.of("basic-6")));
        game.bench().put(player, new ArrayList<>(List.of("basic-1", "basic-2", "basic-3", "basic-4", "basic-5")));
        game.registerCard(new GameCard("basic-6", "Basic", "Pokemon", Set.of("Basic"), 70, 10, 1));

        BenchFullException ex = assertThrows(BenchFullException.class,
            () -> validator.validateBenchPokemon(game, player, "basic-6"));

        assertEquals("BENCH_FULL", ex.code());
    }

    @Test
    void supporterAlreadyPlayedReturnsTypedException() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.turnFlags().setSupporterPlayed(true);

        SupporterAlreadyPlayedException ex = assertThrows(SupporterAlreadyPlayedException.class,
            () -> validator.validate(game, com.utn.pokemontcg.game.domain.model.GameActionType.PLAY_SUPPORTER));

        assertEquals("SUPPORTER_ALREADY_PLAYED", ex.code());
    }

    @Test
    void actionOutsideMainReturnsTypedPhaseException() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.setTurnPhase(TurnPhase.ATTACK);

        InvalidActionPhaseException ex = assertThrows(InvalidActionPhaseException.class,
            () -> validator.validateBenchPokemon(game, player, "basic-1"));

        assertEquals("INVALID_ACTION_PHASE", ex.code());
    }

    @Test
    void invalidEvolutionReturnsTypedException() {
        UUID player = UUID.randomUUID();
        GameAggregate game = mainPhaseGame(player);
        game.turnsStartedByPlayer().put(player, 1);

        EvolutionNotAllowedException ex = assertThrows(EvolutionNotAllowedException.class,
            () -> validator.validateEvolution(game, player, "basic-1", "stage-1", "ACTIVE"));

        assertEquals("EVOLUTION_NOT_ALLOWED", ex.code());
    }

    private GameAggregate mainPhaseGame(UUID player) {
        GameAggregate game = new GameAggregate(player);
        game.setGameState(GameState.ACTIVE);
        game.setCurrentTurnPlayer(player);
        game.setTurnPhase(TurnPhase.MAIN);
        game.activePokemon().put(player, "basic-1");
        game.registerCard(new GameCard("basic-1", "Basic", "Pokemon", Set.of("Basic"), 70, 10, 1));
        return game;
    }
}
