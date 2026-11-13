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
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.NoOpAbilityEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.PotionTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectRegistry;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameMainPhaseActionsTest {

    @Test
    void benchPokemon_evolve_playTrainer_equipTool_and_useAbility() {
        GameApplicationService service = service();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        var created = service.create(p1);
        service.join(created.id(), p2);
        var setup = service.runInitialSetup(created.id(), 60, 12, 60, 12);
        UUID player = setup.currentTurnPlayer();
        setup.setFirstTurn(false);
        setup.turnsStartedByPlayer().put(player, 2);

        GameCard extraBasic = new GameCard("extra-basic", "Extra Basic", "Pokemon", Set.of("Basic"), 80, 10, 1);
        setup.registerCard(extraBasic);
        setup.hand().get(player).add(extraBasic.id());

        var benched = service.benchPokemon(created.id(), player, extraBasic.id());

        assertFalse(benched.hand().get(player).contains(extraBasic.id()));
        assertTrue(benched.bench().get(player).contains(extraBasic.id()));

        String activeId = benched.activePokemon().get(player);
        GameCard active = benched.cardCatalog().get(activeId);
        GameCard evolution = new GameCard("stage-one", "Stage One", "Pokemon", Set.of("Stage 1"), 120, 30, 2, null, List.of(), 1, active.name(), List.of("Focus"));
        benched.registerCard(evolution);
        benched.hand().get(player).add(evolution.id());
        benched.markTurnStartedFor(player);
        benched.addDamageCounters(player, "ACTIVE", 2);
        benched.attachEnergy(player, "ACTIVE", new AttachedEnergy("fire-energy", "Fire"));
        benched.equipTool(player, "ACTIVE", "tool-before-evolution");
        new StatusEffectManager().applyCondition(benched, player, StatusCondition.POISONED);

        var evolved = service.evolvePokemon(created.id(), player, activeId, evolution.id(), "ACTIVE");

        assertEquals(evolution.id(), evolved.activePokemon().get(player));
        assertEquals(2, evolved.damageCountersAtSlot(player, "ACTIVE"));
        assertEquals(1, evolved.attachedEnergyCount(player, "ACTIVE"));
        assertEquals("tool-before-evolution", evolved.toolAtSlot(player, "ACTIVE"));
        assertFalse(evolved.statusByPlayer().containsKey(player));
        evolved.clearTool(player, "ACTIVE");

        GameCard potion = new GameCard("potion", "Potion", "Trainer", Set.of("Item"), 0, 0, 0);
        evolved.registerCard(potion);
        evolved.hand().get(player).add(potion.id());
        evolved.activeDamageCounters().put(player, 3);

        var afterPotion = service.playTrainer(created.id(), player, potion.id(), "ACTIVE", null);

        assertEquals(1, afterPotion.activeDamageCounters().get(player));
        assertTrue(afterPotion.discardPile().get(player).contains(potion.id()));

        GameCard tool = new GameCard("tool", "Muscle Band", "Trainer", Set.of("Pokemon Tool"), 0, 0, 0);
        afterPotion.registerCard(tool);
        afterPotion.hand().get(player).add(tool.id());

        var afterTool = service.playTrainer(created.id(), player, tool.id(), "ACTIVE", null);

        assertEquals(tool.id(), afterTool.toolAtSlot(player, "ACTIVE"));

        InvalidActionException unsupportedAbility = assertThrows(
            InvalidActionException.class,
            () -> service.useAbility(created.id(), player, "ACTIVE", 0, null)
        );

        assertTrue(unsupportedAbility.getMessage().contains("La habilidad Focus no tiene efecto implementado"));
        assertFalse(afterTool.turnFlags().abilityUsed(player + ":ACTIVE:0"));
    }

    @Test
    void evolveBenchPokemon_preservesActiveSpecialConditions() {
        GameApplicationService service = service();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        var created = service.create(p1);
        service.join(created.id(), p2);
        var setup = service.runInitialSetup(created.id(), 60, 12, 60, 12);
        UUID player = setup.currentTurnPlayer();
        setup.setFirstTurn(false);
        setup.turnsStartedByPlayer().put(player, 2);

        GameCard basic = new GameCard("bench-basic", "Bench Basic", "Pokemon", Set.of("Basic"), 80, 10, 1);
        setup.registerCard(basic);
        setup.hand().get(player).add(basic.id());
        var benched = service.benchPokemon(created.id(), player, basic.id());
        int benchIndex = benched.bench().get(player).indexOf(basic.id());
        String benchSlot = "BENCH_" + benchIndex;

        GameCard evolution = new GameCard("bench-stage-one", "Bench Stage One", "Pokemon", Set.of("Stage 1"), 120, 30, 2, null, List.of(), 1, basic.name(), List.of());
        benched.registerCard(evolution);
        benched.hand().get(player).add(evolution.id());
        benched.markTurnStartedFor(player);
        benched.setTurnNumber(benched.turnNumber() + 1);
        new StatusEffectManager().applyCondition(benched, player, StatusCondition.POISONED);

        var evolved = service.evolvePokemon(created.id(), player, basic.id(), evolution.id(), benchSlot);

        assertEquals(evolution.id(), evolved.bench().get(player).get(benchIndex));
        assertTrue(evolved.statusByPlayer().get(player).contains(StatusCondition.POISONED));
    }

    @Test
    void playTrainerWithoutRegisteredEffectSucceedsWithNoop() {
        GameApplicationService service = service();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        var created = service.create(p1);
        service.join(created.id(), p2);
        var setup = service.runInitialSetup(created.id(), 60, 12, 60, 12);
        UUID player = setup.currentTurnPlayer();

        GameCard unsupportedItem = new GameCard("unknown-item", "Unknown Item", "Trainer", Set.of("Item"), 0, 0, 0);
        setup.registerCard(unsupportedItem);
        setup.hand().get(player).add(unsupportedItem.id());

        var result = service.playTrainer(created.id(), player, unsupportedItem.id(), "ACTIVE", null);

        assertFalse(result.hand().get(player).contains(unsupportedItem.id()));
        assertTrue(result.discardPile().getOrDefault(player, List.of()).contains(unsupportedItem.id()));
    }

    private GameApplicationService service() {
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
}
