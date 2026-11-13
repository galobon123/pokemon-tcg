package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch/line coverage push for {@link StanceChangeAbilityEffectStrategy}
 * (94.7% line / 71.4% branch). Targets the 4 missed branches:
 *  - handCardId == null
 *  - handCardId isBlank()
 *  - playerHand == null (when hand not initialised)
 *  - handCard == null (card not in catalog)
 */
class StanceChangeAbilityEffectStrategyCoverageTest {

    @Test
    void abilityNameIsStanceChange() {
        assertEquals("Stance Change", new StanceChangeAbilityEffectStrategy().abilityName());
    }

    @Test
    void applySwapsActiveWithAegislashFromHand() {
        GameAggregate game = newInitializedGame();
        UUID player = game.playerOne();
        GameCard shield = aegislash("aegislash-shield", "Aegislash");
        GameCard blade = aegislash("aegislash-blade", "Aegislash");
        game.registerCard(shield);
        game.registerCard(blade);
        game.activePokemon().put(player, shield.id());
        game.hand().get(player).add(blade.id());

        AbilityEffectContext ctx = new AbilityEffectContext(
            game, player, shield, "ACTIVE", 0, blade.id());
        TrainerEffectResult result = new StanceChangeAbilityEffectStrategy().apply(ctx);

        assertEquals("STANCE_CHANGE_APPLIED", result.result());
        assertEquals(blade.id(), game.activePokemon().get(player));
        assertTrue(game.hand().get(player).contains(shield.id()));
        assertEquals(blade.id(), result.payload().get("handCardId"));
    }

    @Test
    void applyThrowsWhenSlotIsNotActive() {
        GameAggregate game = newInitializedGame();
        UUID player = game.playerOne();
        GameCard shield = aegislash("aegislash-shield", "Aegislash");
        GameCard blade = aegislash("aegislash-blade", "Aegislash");
        game.registerCard(shield);
        game.registerCard(blade);
        game.activePokemon().put(player, shield.id());
        game.bench().get(player).add(0, shield.id());
        game.hand().get(player).add(blade.id());

        AbilityEffectContext ctx = new AbilityEffectContext(
            game, player, shield, "BENCH_0", 0, blade.id());

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> new StanceChangeAbilityEffectStrategy().apply(ctx));
        assertTrue(ex.getMessage().toLowerCase().contains("activo"),
            "Stance Change solo puede usarse desde el Active");
    }

    @Test
    void applyThrowsWhenHandCardIdIsNull() {
        GameAggregate game = newInitializedGame();
        UUID player = game.playerOne();
        GameCard shield = aegislash("aegislash-shield", "Aegislash");
        game.registerCard(shield);
        game.activePokemon().put(player, shield.id());

        AbilityEffectContext ctx = new AbilityEffectContext(
            game, player, shield, "ACTIVE", 0, null);

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> new StanceChangeAbilityEffectStrategy().apply(ctx));
        assertTrue(ex.getMessage().toLowerCase().contains("aegislash"),
            "handCardId == null debe rechazarse con mensaje claro");
    }

    @Test
    void applyThrowsWhenHandCardIdIsBlank() {
        GameAggregate game = newInitializedGame();
        UUID player = game.playerOne();
        GameCard shield = aegislash("aegislash-shield", "Aegislash");
        game.registerCard(shield);
        game.activePokemon().put(player, shield.id());

        AbilityEffectContext ctx = new AbilityEffectContext(
            game, player, shield, "ACTIVE", 0, "   ");

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> new StanceChangeAbilityEffectStrategy().apply(ctx));
        assertTrue(ex.getMessage().toLowerCase().contains("aegislash"),
            "handCardId en blanco debe rechazarse");
    }

    @Test
    void applyThrowsWhenHandCardIdIsEmptyString() {
        GameAggregate game = newInitializedGame();
        UUID player = game.playerOne();
        GameCard shield = aegislash("aegislash-shield", "Aegislash");
        game.registerCard(shield);
        game.activePokemon().put(player, shield.id());

        AbilityEffectContext ctx = new AbilityEffectContext(
            game, player, shield, "ACTIVE", 0, "");

        assertThrows(InvalidActionException.class,
            () -> new StanceChangeAbilityEffectStrategy().apply(ctx));
    }

    @Test
    void applyThrowsWhenPlayerHandListIsNull() {
        GameAggregate game = newInitializedGame();
        UUID player = game.playerOne();
        GameCard shield = aegislash("aegislash-shield", "Aegislash");
        GameCard blade = aegislash("aegislash-blade", "Aegislash");
        game.registerCard(shield);
        game.registerCard(blade);
        game.activePokemon().put(player, shield.id());
        // blow away the player's hand list (simulate uninitialised)
        game.hand().put(player, null);

        AbilityEffectContext ctx = new AbilityEffectContext(
            game, player, shield, "ACTIVE", 0, blade.id());

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> new StanceChangeAbilityEffectStrategy().apply(ctx));
        assertTrue(ex.getMessage().toLowerCase().contains("mano"),
            "Hand == null debe rechazarse con mensaje sobre la mano");
    }

    @Test
    void applyThrowsWhenHandCardNotInHand() {
        GameAggregate game = newInitializedGame();
        UUID player = game.playerOne();
        GameCard shield = aegislash("aegislash-shield", "Aegislash");
        GameCard blade = aegislash("aegislash-blade", "Aegislash");
        game.registerCard(shield);
        game.registerCard(blade);
        game.activePokemon().put(player, shield.id());
        // blade registered but NOT in hand list

        AbilityEffectContext ctx = new AbilityEffectContext(
            game, player, shield, "ACTIVE", 0, blade.id());

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> new StanceChangeAbilityEffectStrategy().apply(ctx));
        assertTrue(ex.getMessage().toLowerCase().contains("mano"),
            "Carta no presente en la mano debe rechazarse");
    }

    @Test
    void applyThrowsWhenHandCardNotInCatalog() {
        GameAggregate game = newInitializedGame();
        UUID player = game.playerOne();
        GameCard shield = aegislash("aegislash-shield", "Aegislash");
        game.registerCard(shield);
        game.activePokemon().put(player, shield.id());
        // card id is in hand but NOT registered in catalog
        game.hand().get(player).add("ghost-aegislash");

        AbilityEffectContext ctx = new AbilityEffectContext(
            game, player, shield, "ACTIVE", 0, "ghost-aegislash");

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> new StanceChangeAbilityEffectStrategy().apply(ctx));
        assertTrue(ex.getMessage().toLowerCase().contains("aegislash"),
            "Carta en mano pero sin GameCard en catalog debe rechazarse");
    }

    @Test
    void applyThrowsWhenHandCardIsNotAegislash() {
        GameAggregate game = newInitializedGame();
        UUID player = game.playerOne();
        GameCard shield = aegislash("aegislash-shield", "Aegislash");
        GameCard other = basic("not-aegislash", "Pikachu");
        game.registerCard(shield);
        game.registerCard(other);
        game.activePokemon().put(player, shield.id());
        game.hand().get(player).add(other.id());

        AbilityEffectContext ctx = new AbilityEffectContext(
            game, player, shield, "ACTIVE", 0, other.id());

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> new StanceChangeAbilityEffectStrategy().apply(ctx));
        assertTrue(ex.getMessage().toLowerCase().contains("aegislash"),
            "Carta que no es Aegislash debe rechazarse");
    }

    // ====================================================================
    // helpers
    // ====================================================================

    private GameAggregate newInitializedGame() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(p1);
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());
        return game;
    }

    private GameCard aegislash(String id, String name) {
        return new GameCard(id, name, "Pokemon", Set.of("Basic"),
            140, 10, 1, null, null, "Metal", List.of(), 1, null, List.of("Stance Change"));
    }

    private GameCard basic(String id, String name) {
        return new GameCard(id, name, "Pokemon", Set.of("Basic"),
            60, 10, 1);
    }
}
