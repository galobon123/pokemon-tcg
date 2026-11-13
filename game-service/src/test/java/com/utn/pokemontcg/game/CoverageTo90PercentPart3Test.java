package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.SetupEngineService;
import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.chain.EnergyValidationStep;
import com.utn.pokemontcg.game.domain.model.*;
import com.utn.pokemontcg.game.domain.strategy.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CoverageTo90PercentPart3Test {

    @Test
    void setupEngine_initSuddenDeath() {
        var service = new SetupEngineService();
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p2 = UUID.randomUUID();
        game.setPlayerTwo(p2);
        game.initializeZonesFor(game.playerOne(), List.of());
        game.initializeZonesFor(p2, List.of());
        List<String> deck1 = new ArrayList<>();
        List<String> deck2 = new ArrayList<>();
        deck1.add("xy1-basic-1");
        deck2.add("xy1-basic-2");
        game.registerCard(new GameCard("xy1-basic-1", "Basic1", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard("xy1-basic-2", "Basic2", "Pokemon", Set.of("Basic"), 60, 10, 1));
        for (int i = 0; i < 10; i++) {
            String id1 = "xy1-energy-p1-" + i;
            String id2 = "xy1-energy-p2-" + i;
            deck1.add(id1);
            deck2.add(id2);
            game.registerCard(new GameCard(id1, "Energy" + i, "Energy", Set.of("Basic"), 0, 0, 0));
            game.registerCard(new GameCard(id2, "Energy" + i, "Energy", Set.of("Basic"), 0, 0, 0));
        }
        game.rememberOriginalDeck(game.playerOne(), deck1);
        game.rememberOriginalDeck(p2, deck2);
        game.setupByPlayer().put(game.playerOne(), new PlayerSetupState());
        game.setupByPlayer().put(p2, new PlayerSetupState());
        assertDoesNotThrow(() -> service.initSuddenDeath(game));
        assertTrue(game.suddenDeath());
    }

    @Test
    void energyDiscard_darknessEnergyMatch() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("dark-1", "Darkness"));
        AttackContext context = ctx(g, "Dark Slash",
            "Discard a [D] Energy attached to this Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(context);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void energyDiscard_lightningEnergyMatch() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("light-1", "Lightning"));
        AttackContext context = ctx(g, "Thunder Claw",
            "Discard a [L] Energy attached to this Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(context);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void persistent_supportsVariousPatterns() {
        var strat = new PersistentAttackEffectStrategy();
        assertTrue(strat.supports(ctx(game(), "Lock", "Your opponent can't play any Supporter card during his or her next turn.")));
        assertTrue(strat.supports(ctx(game(), "Neutralize", "Your opponent's Active Pokemon has no Abilities during your opponent's next turn.")));
        assertTrue(strat.supports(ctx(game(), "Root", "The Defending Pokemon can't retreat during your opponent's next turn.")));
        assertTrue(strat.supports(ctx(game(), "Block", "Your opponent can't use that attack during his or her next turn.")));
    }

    @Test
    void attackUtility_supportsPatterns() {
        var strat = new AttackUtilityEffectStrategy();
        assertTrue(strat.supports(ctx(game(), "Look", "Look at the top 3 cards of your deck and put them back in any order.")));
        assertTrue(strat.supports(ctx(game(), "Draw", "Draw 4 cards.")));
        assertTrue(strat.supports(ctx(game(), "Red Card", "Your opponent shuffles his or her hand into his or her deck.")));
    }

    @Test
    void coinFlipDamage_supportsMoreDamagePattern() {
        var strat = new CoinFlipDamageAttackEffectStrategy();
        assertTrue(strat.supports(ctx(game(), "Flip", "Flip 3 coins. This attack does 40 damage times the number of heads.")));
    }

    @Test
    void energyValidation_failsWithMissingSpecificType() {
        AttackContext context = ctx(game(), "Strike", "Deals damage.");
        context.setRequiredEnergyCost(List.of("Fire", "Water", "Grass"));
        context.setAttachedEnergies(List.of(
            new AttachedEnergy("e1", "Fire"),
            new AttachedEnergy("e2", "Water")
        ));
        assertThrows(com.utn.pokemontcg.game.domain.exception.InsufficientEnergyException.class,
            () -> new EnergyValidationStep().execute(context));
    }

    @Test
    void energyValidation_passesWithColorlessMatch() {
        AttackContext context = ctx(game(), "Strike", "Deals damage.");
        context.setRequiredEnergyCost(List.of("Fire", "Colorless"));
        context.setAttachedEnergies(List.of(
            new AttachedEnergy("e1", "Fire"),
            new AttachedEnergy("e2", "Colorless")
        ));
        assertDoesNotThrow(() -> new EnergyValidationStep().execute(context));
    }

    private AttackContext ctx(GameAggregate game, String attackName, String attackText) {
        AttackContext context = new AttackContext(game);
        context.setAttackName(attackName);
        context.setAttackText(attackText);
        context.setAttachedEnergies(game.attachedEnergiesFor(game.currentTurnPlayer(), "ACTIVE"));
        context.setAttackerCard(game.cardCatalog().get(game.activePokemon().get(game.currentTurnPlayer())));
        return context;
    }

    private GameAggregate game() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(p1);
        game.setFirstPlayer(p1);
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());
        game.activePokemon().put(p1, "active-1");
        game.activePokemon().put(p2, "active-2");
        game.registerCard(new GameCard("active-1", "Attacker", "Pokemon", Set.of("Basic"), 100, 10, 1));
        game.registerCard(new GameCard("active-2", "Defender", "Pokemon", Set.of("Basic"), 100, 10, 1));
        return game;
    }
}
