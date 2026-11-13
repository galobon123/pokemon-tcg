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
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.DriveOffAbilityEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.FairyTransferAbilityEffectStrategy;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FairyTransferAbilityTest {

    private GameApplicationService service;
    private InMemoryGameStateRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryGameStateRepository();
        SetupEngineService setupEngineService = new SetupEngineService();
        AbilityEffectRegistry registry = new AbilityEffectRegistry(List.of(
            new FairyTransferAbilityEffectStrategy(),
            new DriveOffAbilityEffectStrategy()
        ));
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

    private GameCard pokemon(String id, String name, List<String> abilities) {
        return new GameCard(
            id, name, "Pokemon", Set.of("Basic"),
            100, 10, 1, null, null, "Colorless", List.of(), 1, null, abilities
        );
    }

    private void setupAromatisseActiveWithFairyEnergy(GameAggregate game) {
        UUID player = game.currentTurnPlayer();
        GameCard aromatisse = pokemon("aromatisse", "Aromatisse", List.of("Fairy Transfer"));
        GameCard benchPokemon = pokemon("bench-pokemon", "Bench Pokemon", List.of());
        game.registerCard(aromatisse);
        game.registerCard(benchPokemon);
        game.activePokemon().put(player, aromatisse.id());
        game.activeHp().put(player, 100);
        game.activeDamageCounters().put(player, 0);
        game.damageCountersBySlot().computeIfAbsent(player, k -> new HashMap<>()).put("ACTIVE", 0);
        game.bench().get(player).add(0, benchPokemon.id());
        game.attachEnergy(player, "ACTIVE", new AttachedEnergy("fairy-energy", "Fairy"));
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.MAIN);
    }

    @Test
    void successfulTransferBetweenOwnersPokemon() {
        GameAggregate game = gameWithSetup();
        setupAromatisseActiveWithFairyEnergy(game);
        UUID player = game.currentTurnPlayer();

        service.useAbility(game.id(), player, "ACTIVE", 0, "ACTIVE|BENCH_0|fairy-energy");

        assertEquals(0, game.attachedEnergyCount(player, "ACTIVE"),
            "La energia debe haberse removido del Pokemon Activo");
        assertEquals(1, game.attachedEnergyCount(player, "BENCH_0"),
            "La energia debe haberse adjuntado al Pokemon de la banca");
    }

    @Test
    void crossPlayerTransferRejected() {
        GameAggregate game = gameWithSetup();
        setupAromatisseActiveWithFairyEnergy(game);
        UUID player = game.currentTurnPlayer();
        UUID opponent = game.playerOne().equals(player) ? game.playerTwo() : game.playerOne();

        GameCard opponentActive = pokemon("opponent-active", "Opponent Active", List.of());
        game.registerCard(opponentActive);
        game.activePokemon().put(opponent, opponentActive.id());
        game.attachEnergy(opponent, "ACTIVE", new AttachedEnergy("opponent-fairy", "Fairy"));

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> service.useAbility(game.id(), player, "ACTIVE", 0, "ACTIVE|BENCH_0|opponent-fairy"));
        assertTrue(ex.getMessage().contains("origen") || ex.getMessage().contains("adjunta"),
            "Debe rechazar el intento de usar energia del oponente");
    }

    @Test
    void nonFairyEnergyRejected() {
        GameAggregate game = gameWithSetup();
        setupAromatisseActiveWithFairyEnergy(game);
        UUID player = game.currentTurnPlayer();

        game.attachEnergy(player, "ACTIVE", new AttachedEnergy("fire-energy", "Fire"));

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> service.useAbility(game.id(), player, "ACTIVE", 0, "ACTIVE|BENCH_0|fire-energy"));
        assertTrue(ex.getMessage().contains("Fairy"),
            "Debe rechazar energias que no sean Fairy");
    }

    @Test
    void benchedAromatisseCannotUse() {
        GameAggregate game = gameWithSetup();
        UUID player = game.currentTurnPlayer();
        GameCard aromatisse = pokemon("aromatisse", "Aromatisse", List.of("Fairy Transfer"));
        GameCard activePokemon = pokemon("active-pokemon", "Active Pokemon", List.of());
        game.registerCard(aromatisse);
        game.registerCard(activePokemon);
        game.activePokemon().put(player, activePokemon.id());
        game.bench().get(player).add(0, aromatisse.id());
        game.attachEnergy(player, "BENCH_0", new AttachedEnergy("fairy-energy", "Fairy"));
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.MAIN);

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> service.useAbility(game.id(), player, "BENCH_0", 0, "BENCH_0|ACTIVE|fairy-energy"));
        assertTrue(ex.getMessage().contains("Activo"),
            "Aromatisse en banca no puede usar Fairy Transfer");
    }

    @Test
    void repeatableWithinSameTurn() {
        GameAggregate game = gameWithSetup();
        setupAromatisseActiveWithFairyEnergy(game);
        UUID player = game.currentTurnPlayer();

        game.attachEnergy(player, "ACTIVE", new AttachedEnergy("fairy-energy-2", "Fairy"));

        service.useAbility(game.id(), player, "ACTIVE", 0, "ACTIVE|BENCH_0|fairy-energy");
        service.useAbility(game.id(), player, "ACTIVE", 0, "ACTIVE|BENCH_0|fairy-energy-2");

        assertEquals(2, game.attachedEnergyCount(player, "BENCH_0"),
            "Fairy Transfer debe poder usarse repetidamente en el mismo turno");
    }

    @Test
    void nonRepeatableAbilityStillBlockedOnSecondUse() {
        GameAggregate game = gameWithSetup();
        UUID player = game.currentTurnPlayer();
        UUID opponent = game.playerOne().equals(player) ? game.playerTwo() : game.playerOne();

        GameCard driveOffUser = pokemon("drive-off-user", "Drive Off User", List.of("Drive Off"));
        GameCard opponentBench = pokemon("opponent-bench", "Opponent Bench", List.of());
        game.registerCard(driveOffUser);
        game.registerCard(opponentBench);
        game.activePokemon().put(player, driveOffUser.id());
        game.activeHp().put(player, 100);
        game.activeDamageCounters().put(player, 0);
        game.damageCountersBySlot().computeIfAbsent(player, k -> new HashMap<>()).put("ACTIVE", 0);
        game.bench().get(opponent).add(0, opponentBench.id());
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.MAIN);

        String targetId = "BENCH_0:" + opponent;
        service.useAbility(game.id(), player, "ACTIVE", 0, targetId);

        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> service.useAbility(game.id(), player, "ACTIVE", 0, targetId));
        assertTrue(ex.getMessage().contains("ya fue usada"),
            "Drive Off no repetible debe bloquearse en segundo uso");
    }
}
