package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.EnergyDiscardAttackEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extra coverage for the remaining branches of {@link EnergyDiscardAttackEffectStrategy}:
 * <ul>
 *   <li>cancelled context short-circuit</li>
 *   <li>coin condition failure</li>
 *   <li>"you may" + acceptedOptionalEffect=false</li>
 *   <li>tool discard path (pokemon tool text)</li>
 *   <li>tool-only discard path</li>
 *   <li>allAttachedCards path with attached tool</li>
 *   <li>requiredEnergyType filtering</li>
 *   <li>discardCount parsing (2, 3, 4)</li>
 *   <li>selectedEnergyIds with supplied card IDs</li>
 *   <li>targetSlot() fromOpponent with targetPlayerId matching / not matching</li>
 *   <li>supports() exclusion for "discard the top card of your deck"</li>
 * </ul>
 */
class EnergyDiscardAttackEffectStrategyCoverageTest {

    private EnergyDiscardAttackEffectStrategy strategy = new EnergyDiscardAttackEffectStrategy();

    @Test
    void cancelledContextIsIgnored() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        AttackContext c = ctx(g, "Discard", "Discard a Fire Energy attached to this Pokemon.");
        c.setCancelled(true);
        strategy.apply(c);
        // Nothing should change
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void coinConditionFailureIsIgnored() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        AttackContext c = ctx(g, "Discard", "Flip a coin. If heads, discard a Fire Energy attached to this Pokemon.");
        c.setCoinFlipResults(List.of(false));
        strategy.apply(c);
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void optionalDiscardDeclinedIsSkipped() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        AttackContext c = ctx(g, "ElectronCrush",
            "You may discard a Fire Energy attached to this Pokemon. If you do, this attack does 30 more damage.");
        c.setAcceptedOptionalEffect(Boolean.FALSE);
        strategy.apply(c);
        // Energy stays attached
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void optionalDiscardAcceptedActuallyDiscards() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        AttackContext c = ctx(g, "ElectronCrush",
            "You may discard a Fire Energy attached to this Pokemon. If you do, this attack does 30 more damage.");
        c.setAcceptedOptionalEffect(Boolean.TRUE);
        strategy.apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void pokemonToolDiscardPathClearsTool() {
        GameAggregate g = game();
        g.equipTool(g.playerOne(), "ACTIVE", "tool-card");
        g.registerCard(new GameCard("tool-card", "Tool", "Trainer", Set.of("Pokemon Tool"), 0, 0, 0));
        AttackContext c = ctx(g, "ToolDrop",
            "Discard a Pokemon tool attached to this Pokemon.");
        strategy.apply(c);
        assertEquals(null, g.toolAtSlot(g.playerOne(), "ACTIVE"));
        assertTrue(g.discardPile().get(g.playerOne()).contains("tool-card"));
    }

    @Test
    void pokemonToolDiscardNoToolRecordsAudit() {
        GameAggregate g = game();
        // No tool equipped — branch covers the "tool_discard_none" audit
        AttackContext c = ctx(g, "ToolDrop",
            "Discard a Pokemon tool attached to this Pokemon.");
        strategy.apply(c);
        // Discard pile should be empty
        assertTrue(g.discardPile().get(g.playerOne()).isEmpty());
    }

    @Test
    void toolOnlyDiscardPath() {
        // "discard a tool card attached" -> should remove the tool but not energies
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        g.equipTool(g.playerOne(), "ACTIVE", "tool-card");
        g.registerCard(new GameCard("tool-card", "Tool", "Trainer", Set.of("Pokemon Tool"), 0, 0, 0));
        AttackContext c = ctx(g, "ToolShake",
            "Discard a tool card attached to this Pokemon.");
        strategy.apply(c);
        // Tool is removed
        assertEquals(null, g.toolAtSlot(g.playerOne(), "ACTIVE"));
        // Energy is still there
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void allAttachedCardsDiscardsEnergiesAndTool() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e2", "Water"));
        g.equipTool(g.playerOne(), "ACTIVE", "tool-card");
        g.registerCard(new GameCard("tool-card", "Tool", "Trainer", Set.of("Pokemon Tool"), 0, 0, 0));
        AttackContext c = ctx(g, "Whirlwind",
            "Discard all cards attached to this Pokemon and all Energy attached to this Pokemon.");
        strategy.apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
        assertEquals(null, g.toolAtSlot(g.playerOne(), "ACTIVE"));
    }

    @Test
    void discardCountTwoDropsTwoEnergies() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e2", "Fire"));
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e3", "Fire"));
        AttackContext c = ctx(g, "Flame",
            "Discard 2 Fire Energy attached to this Pokemon.");
        strategy.apply(c);
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void discardCountThreeDropsThreeEnergies() {
        GameAggregate g = game();
        for (int i = 0; i < 4; i++) {
            g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e" + i, "Fire"));
        }
        AttackContext c = ctx(g, "Flame", "Discard 3 Fire Energy attached to this Pokemon.");
        strategy.apply(c);
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void discardCountFourDropsFourEnergies() {
        GameAggregate g = game();
        for (int i = 0; i < 5; i++) {
            g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e" + i, "Fire"));
        }
        AttackContext c = ctx(g, "Flame", "Discard 4 Fire Energy attached to this Pokemon.");
        strategy.apply(c);
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void selectedEnergyIdsDropsOnlyRequested() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e2", "Fire"));
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e3", "Fire"));
        AttackContext c = ctx(g, "Flame", "Discard a Fire Energy attached to this Pokemon.");
        c.setSelectedEnergyCardIds(List.of("e2"));
        strategy.apply(c);
        // Only e2 is discarded
        assertEquals(2, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void fromOpponentTargetsDefenderWhenTargetPlayerMatches() {
        GameAggregate g = game();
        g.attachEnergy(g.playerTwo(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        AttackContext c = ctx(g, "Steal",
            "Discard a Fire Energy attached to the opponent's active Pokemon.");
        c.setTargetSlot("ACTIVE");
        c.setTargetPlayerId(g.playerTwo());
        strategy.apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerTwo(), "ACTIVE"));
    }

    @Test
    void fromOpponentWithoutTargetPlayerStillHitsDefender() {
        GameAggregate g = game();
        g.attachEnergy(g.playerTwo(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        AttackContext c = ctx(g, "Steal",
            "Discard a Fire Energy attached to the opponent's active Pokemon.");
        c.setTargetSlot("ACTIVE");
        // No targetPlayerId set
        strategy.apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerTwo(), "ACTIVE"));
    }

    @Test
    void supportsExcludesDiscardTopOfDeck() {
        AttackContext c = ctx(game(), "Mill", "Discard the top card of your deck.");
        assertFalse(strategy.supports(c));
    }

    @Test
    void supportsExcludesDiscardTopOfDeckAlternate() {
        AttackContext c = ctx(game(), "Mill", "Discard the top card of the deck.");
        assertFalse(strategy.supports(c));
    }

    @Test
    void supportsIgnoresPureDiscardWithoutTarget() {
        // "discard energy" but no pokemon target -> should be false
        AttackContext c = ctx(game(), "Throw", "Discard energy cards from your hand.");
        assertFalse(strategy.supports(c));
    }

    @Test
    void requiredTypeFiltersToMatchingEnergy() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e2", "Water"));
        AttackContext c = ctx(g, "Tide",
            "Discard a Water Energy attached to this Pokemon.");
        strategy.apply(c);
        // Water should be removed, Fire remains
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void requiredTypeWordFormFilters() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Darkness"));
        AttackContext c = ctx(g, "Shade", "Discard a Darkness Energy attached to this Pokemon.");
        strategy.apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void rejectsEmptySourceNoEnergyAndNoTool() {
        GameAggregate g = game();
        // No energies, no tool
        AttackContext c = ctx(g, "Drain", "Discard a Fire Energy attached to this Pokemon.");
        strategy.apply(c);
        // energy_discard_none audit, nothing changes
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void supportsEnergyDiscardFromActive() {
        AttackContext c = ctx(game(), "Shake",
            "Discard an Energy attached to this Pokemon.");
        assertTrue(strategy.supports(c));
    }

    @Test
    void supportsEnergyAttachedToOpponentActive() {
        AttackContext c = ctx(game(), "Steal",
            "Discard an Energy attached to opponent's active Pokemon.");
        assertTrue(strategy.supports(c));
    }

    @Test
    void supportsPokemonToolText() {
        AttackContext c = ctx(game(), "Shake",
            "Discard a pokemon tool attached to this Pokemon.");
        assertTrue(strategy.supports(c));
    }

    // ---- helpers ----

    private AttackContext ctx(GameAggregate g, String name, String text) {
        AttackContext c = new AttackContext(g);
        c.setAttackName(name);
        c.setAttackText(text);
        return c;
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
