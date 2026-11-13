package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.TurnFlags;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TurnFlagsAndCardTest {

    @Test
    void turnFlags_initialState() {
        TurnFlags f = new TurnFlags();
        assertFalse(f.energyAttached());
        assertFalse(f.retreated());
        assertFalse(f.supporterPlayed());
        assertFalse(f.stadiumPlayed());
        assertFalse(f.attacked());
    }

    @Test
    void turnFlags_markAbility() {
        TurnFlags f = new TurnFlags();
        f.markAbilityUsed("ability-1");
        assertTrue(f.abilityUsed("ability-1"));
        assertFalse(f.abilityUsed("ability-2"));
    }

    @Test
    void turnFlags_markEvolved() {
        TurnFlags f = new TurnFlags();
        f.markSlotEvolved("ACTIVE");
        assertTrue(f.slotEvolved("ACTIVE"));
        assertFalse(f.slotEvolved("BENCH_0"));
    }

    @Test
    void turnFlags_reset() {
        TurnFlags f = new TurnFlags();
        f.setEnergyAttached(true);
        f.setRetreated(true);
        f.setSupporterPlayed(true);
        f.setStadiumPlayed(true);
        f.setAttacked(true);
        f.markAbilityUsed("a");
        f.markSlotEvolved("ACTIVE");
        f.reset();
        assertFalse(f.energyAttached());
        assertFalse(f.retreated());
        assertFalse(f.supporterPlayed());
        assertFalse(f.stadiumPlayed());
        assertFalse(f.attacked());
        assertFalse(f.abilityUsed("a"));
        assertFalse(f.slotEvolved("ACTIVE"));
    }

    @Test
    void turnFlags_settersWork() {
        TurnFlags f = new TurnFlags();
        f.setEnergyAttached(true);
        assertTrue(f.energyAttached());
        f.setRetreated(true);
        assertTrue(f.retreated());
        f.setSupporterPlayed(true);
        assertTrue(f.supporterPlayed());
        f.setStadiumPlayed(true);
        assertTrue(f.stadiumPlayed());
        f.setAttacked(true);
        assertTrue(f.attacked());
    }

    @Test
    void gameCard_notBasicEnergy() {
        GameCard c = new GameCard("c1", "Charizard", "Pokemon", Set.of("Stage 2"), 150, 60, 3);
        assertFalse(c.isBasicEnergy());
    }

    @Test
    void gameCard_isBasicEnergy() {
        GameCard c = new GameCard("c1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0);
        assertTrue(c.isBasicEnergy());
    }

    @Test
    void gameCard_noSubtype() {
        GameCard c = new GameCard("c1", "Card", "Pokemon", null, 60, 10, 1);
        assertFalse(c.isBasicPokemon());
    }

    @Test
    void gameCard_hasRuleContainingBySubtype() {
        GameCard c = new GameCard("c1", "Card", "Pokemon", Set.of("Basic", "EX"), 170, 30, 2);
        assertTrue(c.hasRuleContaining("Basic"));
    }

    @Test
    void gameCard_hasRuleContainingBlank() {
        GameCard c = new GameCard("c1", "Card", "Pokemon", Set.of("Basic"), 60, 10, 1);
        assertFalse(c.hasRuleContaining(""));
    }
}
