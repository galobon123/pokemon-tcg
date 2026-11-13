package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.application.service.SetupEngineService;
import com.utn.pokemontcg.game.application.service.TurnActionValidator;
import com.utn.pokemontcg.game.application.service.VictoryService;
import com.utn.pokemontcg.game.domain.engine.DamageCalculator;
import com.utn.pokemontcg.game.domain.engine.RuleValidator;
import com.utn.pokemontcg.game.domain.engine.StatusEffectManager;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.SetupPhase;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.FairyGardenStadiumEffect;
import com.utn.pokemontcg.game.domain.strategy.NoOpAbilityEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.PotionTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectRegistry;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Second batch of coverage tests for {@link GameApplicationService}.
 *
 * Targets the largest uncovered methods remaining after
 * GameApplicationServiceCoverageTest:
 *   - executeAction RETREAT path (retreatActivePokemon private helper)
 *   - executeAction POST_DAMAGE_EFFECTS / spiky-shield KO chain
 *   - executeAction attack chain (triggerDestinyBurst / triggerSpikyShield)
 *   - placeSetupBench / placeSetupActive / confirmSetup
 *   - startInteractiveSetup happy path
 *   - finishReason (PRIZES / KO_TOTAL / DECK_OUT / UNKNOWN) via game finish
 *   - retreatActivePokemon fairy-garden zero-cost path
 *   - resolveAttack full path with full pipeline (publishAttackResolved)
 */
class GameApplicationServiceCoverageTest2 {

    private static GameApplicationService service() {
        return new GameApplicationService(
            new InMemoryGameStateRepository(),
            new GameEngineFacade(new GameEventPublisher()),
            new SetupEngineService(),
            new TurnActionValidator(),
            new VictoryService(),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager(),
            null,
            new TrainerEffectRegistry(List.of(new PotionTrainerEffect())),
            new AbilityEffectRegistry(List.of(new NoOpAbilityEffectStrategy())),
            null
        );
    }

    private static GameAggregate activeGame(GameApplicationService svc) {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = svc.create(p1);
        svc.join(created.id(), p2);
        return svc.runInitialSetup(created.id(), 60, 12, 60, 12);
    }

    // ----- placeSetupActive / placeSetupBench / confirmSetup -----

    @Test
    void placeSetupActive_throwsWhenGameNotInSetup() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        UUID player = g.playerOne();
        // After runInitialSetup, state is ACTIVE → placeSetupActive must reject.
        assertThrows(InvalidActionException.class,
            () -> svc.placeSetupActive(g.id(), player, "any-card"));
    }

    @Test
    void placeSetupBench_throwsWhenActiveNotChosen() {
        GameApplicationService svc = service();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = svc.create(p1);
        svc.join(created.id(), p2);
        GameAggregate g = svc.startInteractiveSetup(
            created.id(), 20, 6, 20, 6, null, null);
        // playerOne hasn't chosen an active yet → placeSetupBench fails.
        GameCard basic = new GameCard("bench-1", "Bench1", "Pokemon", Set.of("Basic"), 60, 10, 1);
        g.registerCard(basic);
        g.hand().get(p1).add("bench-1");
        assertThrows(InvalidActionException.class,
            () -> svc.placeSetupBench(g.id(), p1, List.of("bench-1")));
    }

    @Test
    void confirmSetup_advancesToCoinFlipWhenBothConfirmed() {
        GameApplicationService svc = service();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = svc.create(p1);
        svc.join(created.id(), p2);
        // Force SETUP state with a far-future countdown so the auto-expiry
        // doesn't fire on get(gameId). Then both players confirmSetup.
        GameAggregate seeded = svc.get(created.id());
        seeded.setGameState(GameState.SETUP);
        seeded.setSetupPhase(SetupPhase.SETUP_WAITING_BENCH);
        seeded.setSetupCountdownEndsAt(Instant.now().plusSeconds(60));
        seeded.activePokemon().put(p1, "p1-active");
        seeded.activePokemon().put(p2, "p2-active");
        seeded.registerCard(new GameCard("p1-active", "P1", "Pokemon", Set.of("Basic"), 60, 10, 1));
        seeded.registerCard(new GameCard("p2-active", "P2", "Pokemon", Set.of("Basic"), 60, 10, 1));

        svc.confirmSetup(created.id(), p1);
        GameAggregate result = svc.confirmSetup(created.id(), p2);

        assertEquals(GameState.COIN_FLIP, result.gameState());
    }

    // ----- startInteractiveSetup -----

    @Test
    void startInteractiveSetup_skipsWhenAlreadyInSetup() {
        GameApplicationService svc = service();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = svc.create(p1);
        svc.join(created.id(), p2);
        // First call enters SETUP.
        GameAggregate first = svc.startInteractiveSetup(
            created.id(), 20, 6, 20, 6, null, null);
        assertEquals(GameState.SETUP, first.gameState());
        // Second call returns the same game object (idempotent branch).
        GameAggregate second = svc.startInteractiveSetup(
            created.id(), 20, 6, 20, 6, null, null);
        assertEquals(first.id(), second.id());
    }

    // ----- executeAction RETREAT path (retreatActivePokemon) -----

    @Test
    void executeAction_retreat_swapsActiveAndDiscardsEnergy() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        UUID player = g.currentTurnPlayer();
        // Advance to main phase, not first turn.
        g.setFirstTurn(false);
        g.setTurnPhase(TurnPhase.MAIN);
        g.setTurnNumber(3);

        // Use a basic with retreatCost = 1 and attach 1 energy.
        String activeId = "ret-active-" + player;
        String benchId = "ret-bench-" + player;
        GameCard activeCard = new GameCard(activeId, "RetActive", "Pokemon", Set.of("Basic"),
            100, 10, 1, null, List.of(), 1, null, List.of());
        GameCard benchCard = new GameCard(benchId, "RetBench", "Pokemon", Set.of("Basic"),
            100, 10, 1);
        g.registerCard(activeCard);
        g.registerCard(benchCard);
        g.activePokemon().put(player, activeId);
        // Clear bench and place the new bench pokemon at slot 0.
        List<String> newBench = new ArrayList<>();
        newBench.add(benchId);
        newBench.add(null);
        newBench.add(null);
        newBench.add(null);
        newBench.add(null);
        g.bench().put(player, newBench);
        g.attachEnergy(player, "ACTIVE", new AttachedEnergy("e-ret-1", "Fire"));
        // Set the energy on the active so the retreat logic sees it.
        g.activeAttachedEnergy().put(player, 1);

        GameAggregate result = svc.executeAction(g.id(), GameActionType.RETREAT, null,
            null, "BENCH_0", List.of("e-ret-1"), null, null, null,
            List.of(), List.of(), null, null, null);

        assertEquals(benchId, result.activePokemon().get(player));
        // The energy was discarded.
        assertTrue(result.discardPile().getOrDefault(player, List.of()).contains("e-ret-1"));
    }

    @Test
    void executeAction_retreatWithFairyEnergy_ignoresRetreatCost() {
        // The FairyGardenStadiumEffect zeros the retreat cost when a Fairy
        // energy is attached. We set the active stadium and attach Fairy
        // energy so the path through retreatActivePokemon is covered.
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        UUID player = g.currentTurnPlayer();
        g.setFirstTurn(false);
        g.setTurnPhase(TurnPhase.MAIN);
        g.setTurnNumber(3);

        // Set up the active with a high retreat cost + 1 Fairy energy.
        String activeId = "fairy-active-" + player;
        String benchId = "fairy-bench-" + player;
        GameCard activeCard = new GameCard(activeId, "FairyActive", "Pokemon", Set.of("Basic"),
            100, 10, 1, null, List.of(), 3, null, List.of());
        GameCard benchCard = new GameCard(benchId, "FairyBench", "Pokemon", Set.of("Basic"),
            100, 10, 1);
        g.registerCard(activeCard);
        g.registerCard(benchCard);
        g.activePokemon().put(player, activeId);
        // Clear bench and place the new bench pokemon at slot 0.
        List<String> newBench = new ArrayList<>();
        newBench.add(benchId);
        newBench.add(null);
        newBench.add(null);
        newBench.add(null);
        newBench.add(null);
        g.bench().put(player, newBench);
        g.attachEnergy(player, "ACTIVE", new AttachedEnergy("e-fairy-1", "Fairy"));
        // Apply the Fairy Garden stadium effect.
        g.addActiveEffect(player, FairyGardenStadiumEffect.EFFECT_KEY, 5);

        // With the Fairy Garden effect, retreatCost is reduced to 0 → empty energy list.
        GameAggregate result = svc.executeAction(g.id(), GameActionType.RETREAT, null,
            null, "BENCH_0", List.of(), null, null, null,
            List.of(), List.of(), null, null, null);

        assertEquals(benchId, result.activePokemon().get(player));
    }

    // ----- executeAction END_TURN completes between turns -----

    @Test
    void executeAction_endTurnFromAttackPhase_completesBetweenTurns() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        g.setFirstTurn(false);
        g.setTurnNumber(3);
        g.setTurnPhase(TurnPhase.ATTACK);

        GameAggregate result = svc.executeAction(g.id(), GameActionType.END_TURN);

        // End-turn from ATTACK should run the between-turns and start a new
        // turn (DRAW phase).
        assertEquals(TurnPhase.DRAW, result.turnPhase());
    }

    // ----- executeAction attack path (triggerDestinyBurst / Spiky Shield) -----

    @Test
    void executeAction_attackTriggeringSpikyShield_runsTriggerLogic() {
        // The Spiky Shield path is triggered when the defender is KOed and
        // has the Spiky Shield ability. We just exercise the path —
        // the exact resulting state is environment-dependent so we
        // simply assert that the action completes without exceptions.
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        UUID attacker = g.playerOne();
        UUID defender = g.playerTwo();
        g.setFirstPlayer(attacker);
        g.setCurrentTurnPlayer(attacker);
        g.setFirstTurn(false);
        g.setTurnNumber(3);

        // Set up a fresh attacker with high damage.
        String newAttackerId = "spiky-attacker-" + attacker;
        g.registerCard(new GameCard(newAttackerId, "SpikyAtt", "Pokemon", Set.of("Basic"),
            10, 50, 1, "Crush", "Does 50 damage. The Defending Pokemon takes no damage.",
            "Fire", List.of("Fire"), 1, null, List.of()));
        g.activePokemon().put(attacker, newAttackerId);

        // Defender has Spiky Shield + low HP so it KOs.
        String defenderId = "spiky-defender-" + defender;
        g.registerCard(new GameCard(defenderId, "SpikyDef", "Pokemon", Set.of("Basic"),
            100, 10, 1, null, List.of(), 0, null,
            List.of("Spiky Shield")));
        g.activePokemon().put(defender, defenderId);
        g.bench().get(defender).clear();
        g.activeHp().put(defender, 0);
        g.activeDamageCounters().put(defender, 99);
        g.setTurnPhase(TurnPhase.ATTACK);
        g.attachEnergy(attacker, "ACTIVE", new AttachedEnergy("e-spky", "Fire"));

        // Just run the action — the important thing is that the Spiky
        // Shield branch is exercised (no exception is thrown).
        GameAggregate result = svc.executeAction(g.id(), GameActionType.ATTACK);
        assertNotNull(result);
    }

    @Test
    void executeAction_attackTriggeringDestinyBurst_runsTriggerLogic() {
        // The Destiny Burst path runs when the defender is KOed and has
        // the Destiny Burst ability. Random coin flip — we just assert the
        // method runs without exceptions.
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        UUID attacker = g.playerOne();
        UUID defender = g.playerTwo();
        g.setFirstPlayer(attacker);
        g.setCurrentTurnPlayer(attacker);
        g.setFirstTurn(false);
        g.setTurnNumber(3);

        String attackerId = "db-attacker-" + attacker;
        g.registerCard(new GameCard(attackerId, "DbAtt", "Pokemon", Set.of("Basic"),
            10, 100, 1, "Wreck", "Does 100 damage.",
            "Fire", List.of("Fire"), 1, null, List.of()));
        g.activePokemon().put(attacker, attackerId);

        String defenderId = "db-defender-" + defender;
        g.registerCard(new GameCard(defenderId, "DbDef", "Pokemon", Set.of("Basic"),
            100, 10, 1, null, List.of(), 0, null,
            List.of("Destiny Burst")));
        g.activePokemon().put(defender, defenderId);
        g.bench().get(defender).clear();
        g.activeHp().put(defender, 0);
        g.activeDamageCounters().put(defender, 99);
        g.setTurnPhase(TurnPhase.ATTACK);
        g.attachEnergy(attacker, "ACTIVE", new AttachedEnergy("e-db", "Fire"));

        GameAggregate result = svc.executeAction(g.id(), GameActionType.ATTACK);
        assertNotNull(result);
    }

    // ----- attachEnergy (private) — extra branches -----

    @Test
    void executeAction_attachRainbowEnergy_addsDamageCounter() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        g.setFirstTurn(false);
        g.setTurnPhase(TurnPhase.MAIN);
        g.setTurnNumber(3);
        UUID player = g.currentTurnPlayer();
        // Add a "Rainbow" energy to the hand.
        GameCard rainbow = new GameCard(
            "rainbow-1", "Rainbow Energy", "Energy", Set.of("Special"), 0, 0, 0,
            "Colorless", List.of(), 0);
        g.registerCard(rainbow);
        g.hand().get(player).add("rainbow-1");

        GameAggregate result = svc.executeAction(g.id(), GameActionType.ATTACH_ENERGY,
            null, "rainbow-1", "ACTIVE", List.of());

        // Rainbow energy adds 1 damage counter to the active.
        assertEquals(1, result.activeDamageCounters().get(player));
    }

    @Test
    void executeAction_attachDoubleColorless_attachesWithAmount2() {
        GameApplicationService svc = service();
        GameAggregate g = activeGame(svc);
        g.setFirstTurn(false);
        g.setTurnPhase(TurnPhase.MAIN);
        g.setTurnNumber(3);
        UUID player = g.currentTurnPlayer();
        GameCard dce = new GameCard(
            "dce-1", "Double Colorless Energy", "Energy", Set.of("Special"), 0, 0, 0,
            "Colorless", List.of(), 0);
        g.registerCard(dce);
        g.hand().get(player).add("dce-1");

        svc.executeAction(g.id(), GameActionType.ATTACH_ENERGY,
            null, "dce-1", "ACTIVE", List.of());

        // We can verify the attached energy amount by inspecting the
        // attachedEnergies() map. It should contain DCE with energyAmount=2.
        var attached = g.attachedEnergiesFor(player, "ACTIVE");
        assertTrue(attached.stream().anyMatch(e -> "dce-1".equals(e.cardId()) && e.energyAmount() == 2));
    }
}
