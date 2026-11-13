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

class FinalCoverageBoostTest {

    @Test
    void utilitySupportsOpponentSwitchByPlayer() {
        assertTrue(new AttackUtilityEffectStrategy().supports(context(game(),
            "Escape", "Your opponent switches his or her Active Pokemon with 1 of his or her Benched Pokemon.")));
    }

    @Test
    void utilitySupportsCoinConditionalSelfDamage() {
        assertTrue(new AttackUtilityEffectStrategy().supports(context(game(),
            "Struggle", "Flip a coin. If tails, this Pokemon does 10 damage to itself.")));
    }

    @Test
    void utilitySupportsDiscardTopCards() {
        assertTrue(new AttackUtilityEffectStrategy().supports(context(game(),
            "Mountain", "Discard the top card of your opponent's deck.")));
    }

    @Test
    void utilitySupportsPutOnTopOfDeck() {
        assertTrue(new AttackUtilityEffectStrategy().supports(context(game(),
            "Recycle", "Put a card from your discard pile on top of your deck.")));
    }

    @Test
    void energyAttachSupportsFromDiscardPile() {
        assertTrue(new EnergyAttachAttackEffectStrategy().supports(context(game(),
            "Recycle", "Attach a Fire Energy card from your discard pile to this Pokemon.")));
    }

    @Test
    void energyAttachSupportsFromHand() {
        assertTrue(new EnergyAttachAttackEffectStrategy().supports(context(game(),
            "Manual", "Attach a Fire Energy card from your hand to this Pokemon.")));
    }

    @Test
    void persistentAttackSupportsDuringOpponentNextTurn() {
        assertTrue(new PersistentAttackEffectStrategy().supports(context(game(),
            "Barrier", "During your opponent's next turn, any damage done to this Pokemon by attacks is reduced by 20.")));
    }

    @Test
    void persistentAttackSupportsCannotUseNextTurn() {
        assertTrue(new PersistentAttackEffectStrategy().supports(context(game(),
            "Overload", "This Pokemon can't use Overload during your next turn.")));
    }

    @Test
    void persistentAttackSupportsPreventDamageMax60() {
        assertTrue(new PersistentAttackEffectStrategy().supports(context(game(),
            "Harden", "During your opponent's next turn, if this Pokemon would be damaged by an attack, prevent that attack's damage if it is 60 or less.")));
    }

    @Test
    void energyDiscardSupportsFromDefendingPokemon() {
        assertTrue(new EnergyDiscardAttackEffectStrategy().supports(context(game(),
            "Splash", "Discard a Water Energy attached to your opponent's Active Pokemon.")));
    }

    @Test
    void energyDiscardSupportsAllEnergy() {
        assertTrue(new EnergyDiscardAttackEffectStrategy().supports(context(game(),
            "Crush", "Discard all Energy attached to this Pokemon.")));
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
