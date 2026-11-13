package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.application.service.SetupEngineService;
import com.utn.pokemontcg.game.application.service.TurnActionValidator;
import com.utn.pokemontcg.game.application.service.VictoryService;
import com.utn.pokemontcg.game.domain.engine.DamageCalculator;
import com.utn.pokemontcg.game.domain.engine.RuleValidator;
import com.utn.pokemontcg.game.domain.engine.StatusEffectManager;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.StanceChangeAbilityEffectStrategy;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StanceChangeAbilityTest {

    private GameApplicationService service;
    private InMemoryGameStateRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryGameStateRepository();
        SetupEngineService setupEngineService = new SetupEngineService();
        AbilityEffectRegistry registry = new AbilityEffectRegistry(List.of(new StanceChangeAbilityEffectStrategy()));
        service = new GameApplicationService(
            repository,
            new GameEngineFacade(new GameEventPublisher()),
            setupEngineService,
            new TurnActionValidator(registry),
            new VictoryService(setupEngineService),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager(),
            null,
            null,
            registry,
            null
        );
    }

    private GameAggregate gameWithSetup() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate created = service.create(p1);
        service.join(created.id(), p2);
        GameAggregate game = service.runInitialSetup(created.id(), 60, 12, 60, 12);
        game.setFirstTurn(false);
        return game;
    }

    private GameCard aegislash(String id, String name) {
        return new GameCard(
            id, name, "Pokemon", Set.of("Basic"),
            140, 10, 1, null, null, "Metal", List.of(), 3, null, List.of("Stance Change")
        );
    }

    private GameCard otherPokemon(String id, String name) {
        return new GameCard(
            id, name, "Pokemon", Set.of("Basic"),
            100, 10, 1, null, null, "Colorless", List.of(), 1, null, List.of()
        );
    }

    private void setupAegislashActiveWithState(GameAggregate game) {
        UUID player = game.currentTurnPlayer();
        GameCard shieldAegislash = aegislash("aegislash-shield", "Aegislash");
        GameCard bladeAegislash = aegislash("aegislash-blade", "Aegislash");
        GameCard toolCard = new GameCard("tool-1", "Muscle Band", "Trainer", Set.of("Pokemon Tool"), 0, 0, 0);
        game.registerCard(shieldAegislash);
        game.registerCard(bladeAegislash);
        game.registerCard(toolCard);

        game.activePokemon().put(player, shieldAegislash.id());
        game.activeHp().put(player, 120);
        game.activeDamageCounters().put(player, 2);
        game.damageCountersBySlot().computeIfAbsent(player, k -> new HashMap<>()).put("ACTIVE", 2);
        game.attachEnergy(player, "ACTIVE", new AttachedEnergy("fairy-1", "Fairy"));
        game.attachEnergy(player, "ACTIVE", new AttachedEnergy("metal-1", "Metal"));
        game.attachEnergy(player, "ACTIVE", new AttachedEnergy("colorless-1", "Colorless"));
        game.equipTool(player, "ACTIVE", toolCard.id());
        game.statusByPlayer().put(player, EnumSet.of(StatusCondition.BURNED));
        game.hand().get(player).add(bladeAegislash.id());

        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.MAIN);
    }

    @Test
    void successfulSwapPreservesState() {
        GameAggregate game = gameWithSetup();
        setupAegislashActiveWithState(game);
        UUID player = game.currentTurnPlayer();

        service.useAbility(game.id(), player, "ACTIVE", 0, "aegislash-blade");

        assertEquals("aegislash-blade", game.activePokemon().get(player),
            "El Active debe ser ahora el Aegislash de la mano");
        assertEquals(3, game.attachedEnergyCount(player, "ACTIVE"),
            "Debe preservar las energias adjuntas");
        assertEquals("tool-1", game.toolAtSlot(player, "ACTIVE"),
            "Debe preservar la herramienta equipada");
        assertEquals(2, game.damageCountersAtSlot(player, "ACTIVE"),
            "Debe preservar los contadores de dano");
        assertTrue(game.statusByPlayer().getOrDefault(player, EnumSet.noneOf(StatusCondition.class)).contains(StatusCondition.BURNED),
            "Debe preservar las condiciones de estado");
        assertTrue(game.hand().get(player).contains("aegislash-shield"),
            "El Aegislash anterior debe haber pasado a la mano");
    }

    @Test
    void noAegislashInHandRejected() {
        GameAggregate game = gameWithSetup();
        setupAegislashActiveWithState(game);
        UUID player = game.currentTurnPlayer();
        game.hand().get(player).clear();

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> service.useAbility(game.id(), player, "ACTIVE", 0, "missing-aegislash"));
        assertTrue(ex.getMessage().contains("mano"),
            "Debe rechazar cuando no hay Aegislash en la mano");
    }

    @Test
    void nonAegislashHandCardRejected() {
        GameAggregate game = gameWithSetup();
        setupAegislashActiveWithState(game);
        UUID player = game.currentTurnPlayer();
        game.hand().get(player).clear();
        GameCard notAegislash = otherPokemon("not-aegislash", "Not Aegislash");
        game.registerCard(notAegislash);
        game.hand().get(player).add(notAegislash.id());

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> service.useAbility(game.id(), player, "ACTIVE", 0, notAegislash.id()));
        assertTrue(ex.getMessage().contains("Aegislash"),
            "Debe rechazar una carta que no sea Aegislash");
    }

    @Test
    void oncePerTurnEnforced() {
        GameAggregate game = gameWithSetup();
        setupAegislashActiveWithState(game);
        UUID player = game.currentTurnPlayer();

        GameCard secondBlade = aegislash("aegislash-blade-2", "Aegislash");
        game.registerCard(secondBlade);
        game.hand().get(player).add(secondBlade.id());

        service.useAbility(game.id(), player, "ACTIVE", 0, "aegislash-blade");

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> service.useAbility(game.id(), player, "ACTIVE", 0, "aegislash-blade-2"));
        assertTrue(ex.getMessage().contains("ya fue usada"),
            "Stance Change debe ser una vez por turno");
    }

    @Test
    void benchedAegislashRejected() {
        GameAggregate game = gameWithSetup();
        UUID player = game.currentTurnPlayer();
        GameCard aegislash = aegislash("aegislash-bench", "Aegislash");
        GameCard activePokemon = otherPokemon("active-pokemon", "Active Pokemon");
        game.registerCard(aegislash);
        game.registerCard(activePokemon);
        game.activePokemon().put(player, activePokemon.id());
        game.bench().get(player).add(0, aegislash.id());
        game.hand().get(player).add("aegislash-hand");
        game.registerCard(aegislash("aegislash-hand", "Aegislash"));
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.MAIN);

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> service.useAbility(game.id(), player, "BENCH_0", 0, "aegislash-hand"));
        assertTrue(ex.getMessage().contains("Activo"),
            "Aegislash en banca no puede usar Stance Change");
    }
}
