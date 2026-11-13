package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.SetupEngineService;
import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.chain.EnergyValidationStep;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.domain.model.*;
import com.utn.pokemontcg.game.domain.strategy.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FinalBatchFor90Test {

    // === SetupEngineService ===
    @Test
    void setupEngine_preparePlayerWithMulligans() {
        var svc = new SetupEngineService();
        var state = svc.preparePlayer(60, 3, 1);
        assertTrue(state.hasActive());
        assertEquals(7 + 1, state.handSize());
    }

    @Test
    void setupEngine_preparePlayerOpeningHandWithCards() {
        var svc = new SetupEngineService();
        GameAggregate g = new GameAggregate(UUID.randomUUID());
        g.setPlayerTwo(UUID.randomUUID());
        List<GameCard> cards = new java.util.ArrayList<>(List.of(
            new GameCard("b1", "Basic1", "Pokemon", Set.of("Basic"), 60, 10, 1),
            new GameCard("b2", "Basic2", "Pokemon", Set.of("Basic"), 60, 10, 1)
        ));
        for (int i = 0; i < 10; i++)
            cards.add(new GameCard("e" + i, "Energy", "Energy", Set.of("Basic"), 0, 0, 0));
        var state = svc.preparePlayerOpeningHand(g.playerOne(), cards, 0, g);
        assertFalse(state.hasActive());
    }

    @Test
    void setupEngine_preparePlayerBoardWithCardListMulligan() {
        var svc = new SetupEngineService();
        GameAggregate g = new GameAggregate(UUID.randomUUID());
        g.setPlayerTwo(UUID.randomUUID());
        List<GameCard> cards = new java.util.ArrayList<>(List.of(
            new GameCard("b1", "Basic1", "Pokemon", Set.of("Basic"), 60, 10, 1)
        ));
        for (int i = 0; i < 10; i++)
            cards.add(new GameCard("e" + i, "Energy", "Energy", Set.of("Basic"), 0, 0, 0));
        var state = svc.preparePlayerBoard(g.playerOne(), cards, 0, g);
        assertTrue(state.hasActive());
    }

    // === GameEngineFacade ===
    @Test
    void facade_advanceFromDrawPhase() {
        var pub = new GameEventPublisher();
        var f = new GameEngineFacade(pub);
        GameAggregate g = facadeGame();
        g.setTurnPhase(TurnPhase.DRAW);
        f.advanceTurnPhase(g);
        assertEquals(TurnPhase.MAIN, g.turnPhase());
    }

    @Test
    void facade_advanceFromBetweenTurns() {
        var pub = new GameEventPublisher();
        var f = new GameEngineFacade(pub);
        GameAggregate g = facadeGame();
        g.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        f.advanceTurnPhase(g);
        assertEquals(TurnPhase.DRAW, g.turnPhase());
    }

    // === EnergyDiscardAttackEffectStrategy remaining ===
    @Test
    void energyDiscard_psychicMatch() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Psychic"));
        AttackContext c = ctx(g, "Psy", "Discard a [P] Energy attached to this Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void energyDiscard_grassMatch() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Grass"));
        AttackContext c = ctx(g, "Leaf", "Discard a [G] Energy attached to this Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void energyDiscard_fightingMatch() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fighting"));
        AttackContext c = ctx(g, "Punch", "Discard a [F] Energy attached to this Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void energyDiscard_fairyMatch() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fairy"));
        AttackContext c = ctx(g, "Charm", "Discard a [Y] Energy attached to this Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void energyDiscard_dragonMatch() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Dragon"));
        AttackContext c = ctx(g, "Roar", "Discard a [N] Energy attached to this Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    // === AttackUtilityEffectStrategy ===
    @Test
    void utility_supportsMoreDrawPatterns() {
        var s = new AttackUtilityEffectStrategy();
        assertTrue(s.supports(ctx(game(), "Draw", "Draw a card.")));
        assertTrue(s.supports(ctx(game(), "Draw2", "Draw 2 cards.")));
    }

    // === EnergyValidationStep ===
    @Test
    void energyValidation_withEmptyCost() {
        AttackContext c = ctx(game(), "Strike", "Deals damage.");
        c.setRequiredEnergyCost(List.of());
        c.setAttachedEnergies(List.of());
        assertDoesNotThrow(() -> new EnergyValidationStep().execute(c));
    }

    // === BenchDamageAttackEffectStrategy ===
    @Test
    void benchDamage_supportsPattern() {
        var s = new BenchDamageAttackEffectStrategy();
        assertTrue(s.supports(ctx(game(), "Snipe", "This attack does 10 damage to 1 of your opponent's Benched Pokemon.")));
    }

    private AttackContext ctx(GameAggregate g, String name, String text) {
        AttackContext c = new AttackContext(g);
        c.setAttackName(name);
        c.setAttackText(text);
        return c;
    }

    private GameAggregate facadeGame() {
        GameAggregate g = new GameAggregate(UUID.randomUUID());
        g.setPlayerTwo(UUID.randomUUID());
        g.setGameState(GameState.ACTIVE);
        g.setCurrentTurnPlayer(g.playerOne());
        g.setFirstPlayer(g.playerOne());
        g.initializeZonesFor(g.playerOne(), List.of("c1"));
        g.initializeZonesFor(g.playerTwo(), List.of("c2"));
        g.activePokemon().put(g.playerOne(), "c1");
        g.activePokemon().put(g.playerTwo(), "c2");
        g.registerCard(new GameCard("c1", "P1", "Pokemon", Set.of("Basic"), 60, 10, 1));
        g.registerCard(new GameCard("c2", "P2", "Pokemon", Set.of("Basic"), 60, 10, 1));
        return g;
    }

    private GameAggregate game() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        GameAggregate g = new GameAggregate(p1);
        g.setPlayerTwo(p2);
        g.setCurrentTurnPlayer(p1);
        g.setFirstPlayer(p1);
        g.initializeZonesFor(p1, List.of());
        g.initializeZonesFor(p2, List.of());
        g.activePokemon().put(p1, "a1");
        g.activePokemon().put(p2, "a2");
        g.registerCard(new GameCard("a1", "Att", "Pokemon", Set.of("Basic"), 100, 10, 1));
        g.registerCard(new GameCard("a2", "Def", "Pokemon", Set.of("Basic"), 100, 10, 1));
        return g;
    }
}
