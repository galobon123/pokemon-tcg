package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.*;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.domain.model.*;
import com.utn.pokemontcg.game.domain.state.FinishedState;
import com.utn.pokemontcg.game.domain.state.WaitingState;
import com.utn.pokemontcg.game.domain.strategy.*;
import com.utn.pokemontcg.game.model.User;
import com.utn.pokemontcg.game.security.AuthenticatedPlayerResolver;
import com.utn.pokemontcg.game.application.service.SetupEngineService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CoverageTo90PercentTest {

    @Test
    void waitingStateSetsGameState() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        new WaitingState().enter(game);
        assertEquals(GameState.WAITING, game.gameState());
    }

    @Test
    void finishedStateSetsGameState() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        new FinishedState().enter(game);
        assertEquals(GameState.FINISHED, game.gameState());
    }

    @Test
    void confusionCheck_skipsWhenNotConfused() {
        AttackContext context = new AttackContext(game());
        context.setConfused(false);
        new ConfusionCheckStep().execute(context);
        assertFalse(context.cancelled());
    }

    @Test
    void confusionCheck_skipsWhenCancelled() {
        AttackContext context = new AttackContext(game());
        context.setCancelled(true);
        new ConfusionCheckStep().execute(context);
        assertTrue(context.cancelled());
    }

    @Test
    void attackEffectRegistry_appliesPreDamageModifiers() {
        var registry = new AttackEffectRegistry(List.of(
            new CoinBonusDamageAttackEffectStrategy(() -> true),
            new DefaultAttackEffectStrategy()
        ));
        AttackContext context = ctx(game(), "Quick Toss",
            "Flip a coin. If heads, this attack does 20 more damage.");
        context.setCoinFlip(() -> true);
        registry.applyPreDamageModifiers(context);
        assertTrue(context.preAttackBonus() >= 20);
    }

    @Test
    void attackEffectRegistry_appliesPostDamageEffects() {
        var strategy = new SelfHealingAttackEffectStrategy();
        var registry = new AttackEffectRegistry(List.of(strategy, new DefaultAttackEffectStrategy()));
        GameAggregate g = game();
        g.addDamageCounters(g.playerOne(), "ACTIVE", 3);
        AttackContext context = ctx(g, "Drain", "Heal 20 damage from this Pokemon.");
        registry.apply(context);
        assertEquals(1, g.damageCountersAtSlot(g.playerOne(), "ACTIVE"));
    }

    @Test
    void attackEffectRegistry_fallsbackToDefaultWhenNoMatch() {
        var defaultStrategy = new DefaultAttackEffectStrategy();
        var registry = new AttackEffectRegistry(List.of(defaultStrategy));
        AttackContext context = ctx(game(), "Unknown", "Does something weird.");
        registry.applyPostDamageEffects(context);
    }

    @Test
    void resolver_resolvesPlayerIdFromUser() {
        var resolver = new AuthenticatedPlayerResolver();
        User user = new User();
        UUID pid = UUID.randomUUID();
        user.setPlayerId(pid);
        assertEquals(pid, resolver.resolve(user));
    }

    @Test
    void resolver_resolveOrFallback_returnsFallbackWhenNull() {
        var resolver = new AuthenticatedPlayerResolver();
        UUID fallback = UUID.randomUUID();
        assertEquals(fallback, resolver.resolveOrFallback(null, fallback));
    }

    @Test
    void resolver_requireMatching_throwsOnMismatch() {
        var resolver = new AuthenticatedPlayerResolver();
        User user = new User();
        user.setPlayerId(UUID.randomUUID());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> resolver.requireMatching(user, UUID.randomUUID()));
    }

    @Test
    void resolver_requireMatching_passesWhenNullUser() {
        var resolver = new AuthenticatedPlayerResolver();
        UUID pid = UUID.randomUUID();
        assertEquals(pid, resolver.requireMatching(null, pid));
    }

    @Test
    void resolver_assertOwner_doesNothingWhenUserIsNull() {
        var resolver = new AuthenticatedPlayerResolver();
        resolver.assertOwner(null, UUID.randomUUID());
    }

    @Test
    void resolver_assertOwner_throwsOnMismatch() {
        var resolver = new AuthenticatedPlayerResolver();
        User user = new User();
        user.setPlayerId(UUID.randomUUID());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> resolver.assertOwner(user, UUID.randomUUID()));
    }

    @Test
    void coinBonus_withHeadsConditionTailsResult() {
        AttackContext context = ctx(game(), "Bonus",
            "Flip a coin. If heads, this attack does 20 more damage.");
        context.setCoinFlip(() -> false);
        context.setCoinFlipResults(List.of());
        new CoinBonusDamageAttackEffectStrategy(() -> false).apply(context);
        assertEquals(0, context.preAttackBonus());
    }

    @Test
    void coinBonus_coinConditionMet() {
        AttackContext context = ctx(game(), "Bonus",
            "Flip a coin. If heads, this attack does 20 more damage.");
        context.setCoinFlip(() -> true);
        context.setCoinFlipResults(List.of());
        new CoinBonusDamageAttackEffectStrategy(() -> true).apply(context);
        assertEquals(20, context.preAttackBonus());
    }

    @Test
    void energyDiscard_fromOpponent() {
        GameAggregate g = game();
        g.attachEnergy(g.playerTwo(), "ACTIVE", new AttachedEnergy("e1", "Water"));
        AttackContext context = ctx(g, "Splash",
            "Discard a Water Energy attached to your opponent's Active Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(context);
        assertEquals(0, g.attachedEnergyCount(g.playerTwo(), "ACTIVE"));
    }

    @Test
    void energyDiscard_allEnergy() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e2", "Fire"));
        AttackContext context = ctx(g, "Crush",
            "Discard all Energy attached to this Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(context);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void energyDiscard_optionalDeclined() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        AttackContext context = ctx(g, "Optional",
            "You may discard an Energy attached to this Pokemon.");
        context.setAcceptedOptionalEffect(false);
        new EnergyDiscardAttackEffectStrategy().apply(context);
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void energyDiscard_fromThisPokemon() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Fire"));
        AttackContext context = ctx(g, "Wildfire",
            "Discard an Energy attached to this Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(context);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void energyDiscard_toolCard() {
        GameAggregate g = game();
        AttackContext context = ctx(g, "Joust",
            "Discard a Pokemon Tool card attached to your opponent's Active Pokemon.");
        new EnergyDiscardAttackEffectStrategy().apply(context);
    }

    @Test
    void energyAttach_fromDiscardPile() {
        GameAggregate g = game();
        g.registerCard(new GameCard("fire-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        g.discardPile().get(g.playerOne()).add("fire-1");
        AttackContext context = ctx(g, "Recycle",
            "Attach a Fire Energy card from your discard pile to this Pokemon.");
        new EnergyAttachAttackEffectStrategy().apply(context);
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void energyAttach_attach2EnergyDistribute() {
        GameAggregate g = game();
        g.registerCard(new GameCard("fire-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        g.registerCard(new GameCard("fire-2", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        g.deck().get(g.playerOne()).addAll(List.of("fire-1", "fire-2"));
        g.bench().get(g.playerOne()).addAll(List.of("bench-1", "bench-2"));
        AttackContext context = ctx(g, "Geomancy",
            "Attach 2 Basic Energy cards from your deck to your Benched Pokemon in any way you like.");
        context.setSelectedCardIds(List.of("BENCH_0", "BENCH_1", "fire-1", "fire-2"));
        new EnergyAttachAttackEffectStrategy().apply(context);
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "BENCH_0"));
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "BENCH_1"));
    }

    @Test
    void gameEngineFacade_resolveAttackWithNullIndex() {
        var publisher = new GameEventPublisher();
        var facade = new GameEngineFacade(publisher);
        GameAggregate g = facadeGame();
        facade.resolveAttack(g, null, List.of(), null, null, List.of(), List.of(), null, null, null);
        assertEquals(TurnPhase.BETWEEN_TURNS, g.turnPhase());
    }

    @Test
    void gameEngineFacade_resolveAttackWithNegativeIndex() {
        var publisher = new GameEventPublisher();
        var facade = new GameEngineFacade(publisher);
        GameAggregate g = facadeGame();
        facade.resolveAttack(g, -1, List.of(true), "ACTIVE", g.playerTwo(), List.of(), List.of(), null, true, 0);
        assertEquals(TurnPhase.BETWEEN_TURNS, g.turnPhase());
    }

    @Test
    void gameEngineFacade_resolveAttackWithCoinResultMerging() {
        var publisher = new GameEventPublisher();
        var facade = new GameEngineFacade(publisher);
        GameAggregate g = facadeGame();
        facade.resolveAttack(g, 0, List.of(true), null, null, List.of(), List.of(), null, null, null);
        assertEquals(TurnPhase.BETWEEN_TURNS, g.turnPhase());
    }

    @Test
    void energyValidation_passesWithExactCost() {
        AttackContext context = ctx(energyGame(), "Strike", "Deals damage.");
        context.setRequiredEnergyCost(List.of("Fire", "Colorless"));
        context.setAttachedEnergies(List.of(
            new AttachedEnergy("e1", "Fire"),
            new AttachedEnergy("e2", "Colorless")
        ));
        assertDoesNotThrow(() -> new EnergyValidationStep().execute(context));
    }

    @Test
    void energyValidation_failsWhenMissingColorless() {
        AttackContext context = ctx(game(), "Strike", "Deals damage.");
        context.setRequiredEnergyCost(List.of("Colorless", "Colorless"));
        context.setAttachedEnergies(List.of(new AttachedEnergy("e1", "Fire")));
        assertThrows(com.utn.pokemontcg.game.domain.exception.InsufficientEnergyException.class,
            () -> new EnergyValidationStep().execute(context));
    }

    @Test
    void energyValidation_passesWithRainbowForMissingType() {
        AttackContext context = ctx(game(), "Strike", "Deals damage.");
        context.setRequiredEnergyCost(List.of("Water", "Fire"));
        context.setAttachedEnergies(List.of(
            new AttachedEnergy("rain-1", "Rainbow"),
            new AttachedEnergy("fire-1", "Fire")
        ));
        assertDoesNotThrow(() -> new EnergyValidationStep().execute(context));
    }

    @Test
    void setupEngine_validateMinDeckSize() {
        var service = new SetupEngineService();
        assertThrows(IllegalArgumentException.class, () ->
            service.preparePlayer(3, 0, 0));
    }

    @Test
    void setupEngine_validateMinBasics() {
        var service = new SetupEngineService();
        assertThrows(IllegalArgumentException.class, () ->
            service.preparePlayer(60, 0, 0));
    }

    @Test
    void gameAggregate_swapWithBenchNegativeIndex() {
        GameAggregate g = game();
        g.bench().get(g.playerOne()).add("bench-1");
        assertThrows(IllegalArgumentException.class, () ->
            g.swapActiveWithBench(g.playerOne(), -1));
    }

    @Test
    void gameAggregate_swapWithBenchEmptySlot() {
        GameAggregate g = game();
        assertThrows(IllegalArgumentException.class, () ->
            g.swapActiveWithBench(g.playerOne(), 0));
    }

    @Test
    void gameAggregate_addDamageCountersNonPositive() {
        GameAggregate g = game();
        g.addDamageCounters(g.playerOne(), "ACTIVE", 0);
        assertEquals(0, g.damageCountersAtSlot(g.playerOne(), "ACTIVE"));
    }

    private GameAggregate facadeGame() {
        GameAggregate g = new GameAggregate(UUID.randomUUID());
        g.setPlayerTwo(UUID.randomUUID());
        g.setTurnPhase(TurnPhase.ATTACK);
        g.setGameState(GameState.ACTIVE);
        g.setCurrentTurnPlayer(g.playerOne());
        g.setFirstPlayer(g.playerOne());
        g.initializeZonesFor(g.playerOne(), List.of("c1"));
        g.initializeZonesFor(g.playerTwo(), List.of("c2"));
        g.registerCard(new GameCard("c1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1,
            "Thunder Shock", "Flip a coin. If heads, the Defending Pokemon is now Paralyzed.",
            "Lightning", List.of("Lightning"), 1, null, List.of()));
        g.registerCard(new GameCard("c2", "Charmander", "Pokemon", Set.of("Basic"), 50, 10, 1));
        g.activePokemon().put(g.playerOne(), "c1");
        g.activePokemon().put(g.playerTwo(), "c2");
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Lightning"));
        return g;
    }

    private GameAggregate energyGame() {
        GameAggregate g = game();
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("fire-1", "Fire"));
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("water-1", "Water"));
        g.attachEnergy(g.playerOne(), "ACTIVE", new AttachedEnergy("grass-1", "Grass"));
        return g;
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
        game.damageCountersBySlot().get(p1).put("ACTIVE", 0);
        game.damageCountersBySlot().get(p2).put("ACTIVE", 0);
        return game;
    }
}
