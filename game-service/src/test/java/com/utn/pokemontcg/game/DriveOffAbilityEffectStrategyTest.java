package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectContext;
import com.utn.pokemontcg.game.domain.strategy.DriveOffAbilityEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage for {@link DriveOffAbilityEffectStrategy}.
 * Exercises validation paths and the happy-path active/bench swap.
 */
class DriveOffAbilityEffectStrategyTest {

    private final DriveOffAbilityEffectStrategy strategy = new DriveOffAbilityEffectStrategy();

    @Test
    void abilityCardMustBeActivePokemon() {
        GameAggregate game = game();
        GameCard driveOffUser = pokemon("drive-off-user", "Drive Off User", List.of("Drive Off"));
        game.registerCard(driveOffUser);
        // Active is a DIFFERENT card than the ability card
        game.activePokemon().put(game.playerOne(), "some-other-pokemon");

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(ctx(game, game.playerOne(), driveOffUser, "BENCH_0:" + game.playerTwo())));
        assertTrue(ex.getMessage().contains("solo puede usarse cuando este Pokémon es tu Pokémon Activo"));
    }

    @Test
    void nullActiveIsRejected() {
        GameAggregate game = game();
        GameCard driveOffUser = pokemon("drive-off-user", "Drive Off User", List.of("Drive Off"));
        game.registerCard(driveOffUser);
        // No active for the player
        game.activePokemon().remove(game.playerOne());

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(ctx(game, game.playerOne(), driveOffUser, "BENCH_0:" + game.playerTwo())));
        assertTrue(ex.getMessage().contains("solo puede usarse cuando este Pokémon es tu Pokémon Activo"));
    }

    @Test
    void nullTargetIdIsRejected() {
        GameAggregate game = game();
        GameCard driveOffUser = setUserAsActiveWithDriveOff(game);

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(ctx(game, game.playerOne(), driveOffUser, null)));
        assertTrue(ex.getMessage().contains("requiere seleccionar un Pokémon de la banca del oponente"));
    }

    @Test
    void blankTargetIdIsRejected() {
        GameAggregate game = game();
        GameCard driveOffUser = setUserAsActiveWithDriveOff(game);

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(ctx(game, game.playerOne(), driveOffUser, "   ")));
        assertTrue(ex.getMessage().contains("requiere seleccionar un Pokémon de la banca del oponente"));
    }

    @Test
    void targetIdWithoutColonIsInvalidFormat() {
        GameAggregate game = game();
        GameCard driveOffUser = setUserAsActiveWithDriveOff(game);

        // "BENCH_0" splits into 1 part, not 2
        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(ctx(game, game.playerOne(), driveOffUser, "BENCH_0")));
        assertTrue(ex.getMessage().contains("Target inválido para Drive Off"));
    }

    @Test
    void targetIdWithOwnPlayerIsRejected() {
        GameAggregate game = game();
        GameCard driveOffUser = setUserAsActiveWithDriveOff(game);

        // targetPlayerId == self, not opponent
        String targetId = "BENCH_0:" + game.playerOne();
        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> strategy.apply(ctx(game, game.playerOne(), driveOffUser, targetId)));
        assertTrue(ex.getMessage().contains("solo puede cambiar el Pokémon Activo del oponente"));
    }

    @Test
    void happyPathSwapsOpponentActiveWithBench() {
        GameAggregate game = game();
        GameCard driveOffUser = setUserAsActiveWithDriveOff(game);

        // Opponent has an active and a bench pokemon at BENCH_0
        GameCard opponentActive = pokemon("opponent-active", "Opponent Active", List.of());
        GameCard opponentBench = pokemon("opponent-bench", "Opponent Bench", List.of());
        game.registerCard(opponentActive);
        game.registerCard(opponentBench);
        game.activePokemon().put(game.playerTwo(), opponentActive.id());
        game.bench().get(game.playerTwo()).add(0, opponentBench.id());

        String targetId = "BENCH_0:" + game.playerTwo();
        var result = strategy.apply(ctx(game, game.playerOne(), driveOffUser, targetId));

        assertEquals("DRIVE_OFF_ACTIVATED", result.result());
        // The bench pokemon is now the active
        assertEquals(opponentBench.id(), game.activePokemon().get(game.playerTwo()));
        // The previous active is now at BENCH_0
        assertEquals(opponentActive.id(), game.bench().get(game.playerTwo()).get(0));
        assertNotNull(result.payload());
        assertEquals("BENCH_0", result.payload().get("targetSlot"));
        assertEquals(game.playerTwo().toString(), result.payload().get("opponentId"));
    }

    @Test
    void happyPathPreservesAbilityWhenSwapIsDone() {
        // The strategy should NOT care about the opponent's abilities - the swap
        // is mechanical. Just verify the swap works for a second opponent slot too.
        GameAggregate game = game();
        GameCard driveOffUser = setUserAsActiveWithDriveOff(game);

        GameCard opponentActive = pokemon("opp-a", "OppA", List.of());
        GameCard opponentBench1 = pokemon("opp-b1", "OppB1", List.of());
        GameCard opponentBench2 = pokemon("opp-b2", "OppB2", List.of());
        game.registerCard(opponentActive);
        game.registerCard(opponentBench1);
        game.registerCard(opponentBench2);
        game.activePokemon().put(game.playerTwo(), opponentActive.id());
        game.bench().get(game.playerTwo()).add(0, opponentBench1.id());
        game.bench().get(game.playerTwo()).add(1, opponentBench2.id());

        String targetId = "BENCH_1:" + game.playerTwo();
        var result = strategy.apply(ctx(game, game.playerOne(), driveOffUser, targetId));

        assertEquals("DRIVE_OFF_ACTIVATED", result.result());
        // BENCH_1's pokemon is now active
        assertEquals(opponentBench2.id(), game.activePokemon().get(game.playerTwo()));
        // BENCH_0 stays the same
        assertEquals(opponentBench1.id(), game.bench().get(game.playerTwo()).get(0));
        // BENCH_1 now has the previous active
        assertEquals(opponentActive.id(), game.bench().get(game.playerTwo()).get(1));
    }

    // ---------- helpers ----------

    private AbilityEffectContext ctx(GameAggregate game, UUID player, GameCard pokemonCard, String targetId) {
        return new AbilityEffectContext(game, player, pokemonCard, "ACTIVE", 0, targetId);
    }

    private GameAggregate game() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(p1);
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());
        game.registerCard(new GameCard("active-1", "Active", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard("active-2", "Defender", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.activePokemon().put(p1, "active-1");
        game.activePokemon().put(p2, "active-2");
        return game;
    }

    private GameCard pokemon(String id, String name, List<String> abilities) {
        return new GameCard(
            id, name, "Pokemon", Set.of("Basic"),
            100, 10, 1, null, null, "Colorless", List.of(), 1, null, abilities
        );
    }

    /**
     * Register a "Drive Off" ability card and make it the active pokemon
     * for the current turn player. Returns the card so it can be passed
     * to the strategy as the pokemonCard.
     */
    private GameCard setUserAsActiveWithDriveOff(GameAggregate game) {
        GameCard driveOffUser = pokemon("drive-off-user", "Drive Off User", List.of("Drive Off"));
        game.registerCard(driveOffUser);
        game.activePokemon().put(game.currentTurnPlayer(), driveOffUser.id());
        return driveOffUser;
    }
}
