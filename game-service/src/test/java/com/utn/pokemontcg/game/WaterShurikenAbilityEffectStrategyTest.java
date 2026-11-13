package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectContext;
import com.utn.pokemontcg.game.domain.strategy.WaterShurikenAbilityEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WaterShurikenAbilityEffectStrategyTest {

    @Test
    void rejectsNullTarget() {
        var strategy = new WaterShurikenAbilityEffectStrategy();
        var ctx = new AbilityEffectContext(game(), game().playerOne(), null, "Water Shuriken", 0, null);
        assertThrows(InvalidActionException.class, () -> strategy.apply(ctx));
    }

    @Test
    void rejectsInvalidTargetFormat() {
        var strategy = new WaterShurikenAbilityEffectStrategy();
        var ctx = new AbilityEffectContext(game(), game().playerOne(), null, "Water Shuriken", 0, "bad-format");
        assertThrows(InvalidActionException.class, () -> strategy.apply(ctx));
    }

    @Test
    void rejectsNonOpponentTarget() {
        GameAggregate game = game();
        var strategy = new WaterShurikenAbilityEffectStrategy();
        String target = "ACTIVE:" + game.playerOne().toString();
        var ctx = new AbilityEffectContext(game, game.playerOne(), null, "Water Shuriken", 0, target);
        assertThrows(InvalidActionException.class, () -> strategy.apply(ctx));
    }

    @Test
    void appliesDamageWhenCorrectEnergyInHand() {
        GameAggregate game = game();
        var strategy = new WaterShurikenAbilityEffectStrategy();
        GameCard water = new GameCard("water-1", "Water Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Water", List.of(), 0);
        game.registerCard(water);
        game.hand().get(game.playerOne()).add("water-1");
        String target = "ACTIVE:" + game.playerTwo().toString();
        var ctx = new AbilityEffectContext(game, game.playerOne(), null, "Water Shuriken", 0, target);
        var result = strategy.apply(ctx);
        assertEquals("WATER_SHURIKEN_ACTIVATED", result.result());
        assertEquals(3, game.damageCountersAtSlot(game.playerTwo(), "ACTIVE"));
        assertFalse(game.hand().get(game.playerOne()).contains("water-1"));
    }

    private GameAggregate game() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(p1);
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());
        game.activePokemon().put(p1, "active-1");
        game.activePokemon().put(p2, "active-2");
        game.registerCard(new GameCard("active-1", "Active", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard("active-2", "Defender", "Pokemon", Set.of("Basic"), 60, 10, 1));
        return game;
    }
}
