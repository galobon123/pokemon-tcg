package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AttackEffectStrategyFinalEdgeCasesTest {

    @Test
    void utilityEffectShuffleYourDeck() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("c1", "c2", "c3"));
        AttackContext context = context(game, "Shuffle", "Shuffle your deck.");
        new AttackUtilityEffectStrategy().apply(context);
        assertEquals(3, game.deck().get(game.playerOne()).size());
    }

    @Test
    void energyAttachFromDiscardToBenchWithType() {
        GameAggregate game = game();
        game.registerCard(new GameCard("fire-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.discardPile().get(game.playerOne()).add("fire-1");
        game.bench().get(game.playerOne()).add("bench-1");
        AttackContext context = context(game, "Recycle", "Attach a Fire Energy card from your discard pile to 1 of your Benched Pokemon.");
        context.setTargetSlot("BENCH_0");
        context.setSelectedCardIds(List.of("fire-1"));
        new EnergyAttachAttackEffectStrategy().apply(context);
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
    }

    @Test
    void energyAttachFromDiscardToActive() {
        GameAggregate game = game();
        game.registerCard(new GameCard("fire-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.discardPile().get(game.playerOne()).add("fire-1");
        AttackContext context = context(game, "Recycle", "Attach a Fire Energy card from your discard pile to this Pokemon.");
        new EnergyAttachAttackEffectStrategy().apply(context);
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
    }

    @Test
    void conditionalDamage_discardEnergyForMoreDamage() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        AttackContext context = context(game, "Electron Crush",
            "You may discard an Energy attached to this Pokemon. If you do, this attack does 30 more damage.");
        context.setAcceptedOptionalEffect(true);
        new ConditionalDamageAttackEffectStrategy().apply(context);
        assertEquals(30, context.preAttackBonus());
    }

    @Test
    void utilityShufflesAndPutsOnTop() {
        GameAggregate game = game();
        game.hand().get(game.playerTwo()).add("h1");
        AttackContext context = context(game, "Red Card", "Shuffle your opponent's hand into their deck.");
        context.setTargetPlayerId(game.playerTwo());
        new AttackUtilityEffectStrategy().apply(context);
        assertTrue(game.hand().get(game.playerTwo()).isEmpty());
    }

    private AttackContext context(GameAggregate game, String attackName, String attackText) {
        AttackContext context = new AttackContext(game);
        context.setAttackName(attackName);
        context.setAttackText(attackText);
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
