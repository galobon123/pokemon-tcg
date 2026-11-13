package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.application.service.SetupEngineService;
import com.utn.pokemontcg.game.application.service.TurnActionValidator;
import com.utn.pokemontcg.game.application.service.VictoryService;
import com.utn.pokemontcg.game.domain.engine.DamageCalculator;
import com.utn.pokemontcg.game.domain.engine.RuleValidator;
import com.utn.pokemontcg.game.domain.engine.StatusEffectManager;
import com.utn.pokemontcg.game.domain.event.GameEvent;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.domain.model.GameActionType;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.application.service.InvalidActionException;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectContext;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectRegistry;
import com.utn.pokemontcg.game.domain.strategy.SpikyShieldAbilityEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectResult;
import com.utn.pokemontcg.game.infrastructure.repository.InMemoryGameStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpikyShieldAbilityTest {

    private GameEventPublisher eventPublisher;
    private List<GameEvent> capturedEvents;
    private GameApplicationService service;
    private InMemoryGameStateRepository repository;

    @BeforeEach
    void setUp() {
        eventPublisher = new GameEventPublisher();
        capturedEvents = new ArrayList<>();
        eventPublisher.subscribe(capturedEvents::add);

        repository = new InMemoryGameStateRepository();
        SetupEngineService setupEngineService = new SetupEngineService();
        service = new GameApplicationService(
            repository,
            new GameEngineFacade(eventPublisher),
            setupEngineService,
            new TurnActionValidator(),
            new VictoryService(setupEngineService),
            new RuleValidator(),
            new DamageCalculator(),
            new StatusEffectManager(),
            null,
            null,
            new AbilityEffectRegistry(List.of(new SpikyShieldAbilityEffectStrategy())),
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

    private void replaceDefenderActive(GameAggregate game, String cardId, List<String> abilities, int hp) {
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        GameCard card = new GameCard(
            cardId, cardId, "Pokemon", Set.of("Basic"),
            hp, 10, 1, null, List.of(), 1,
            null, abilities
        );
        game.registerCard(card);
        game.activePokemon().put(defender, cardId);
        game.activeHp().put(defender, hp);
        game.activeDamageCounters().put(defender, 0);
        game.damageCountersBySlot().computeIfAbsent(defender, k -> new HashMap<>()).put("ACTIVE", 0);
        game.activePokemonEx().put(defender, false);
    }

    @Test
    void spikyShieldTriggersWhenChesnaughtIsActiveAndReceivesDamage() {
        GameAggregate game = gameWithSetup();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        replaceDefenderActive(game, "chesnaught-test", List.of("Spiky Shield"), 100);
        game.setActiveAttackBaseDamage(30);
        game.activeAttachedEnergy().put(attacker, 1);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setTurnNumber(2);

        service.executeAction(game.id(), GameActionType.ATTACK);

        int attackerCounters = game.damageCountersAtSlot(attacker, "ACTIVE");
        assertEquals(3, attackerCounters,
            "Spiky Shield debe colocar exactamente 3 contadores de dano sobre el atacante");

        boolean abilityEventPublished = capturedEvents.stream()
            .anyMatch(e -> "ABILITY_USED".equals(e.type())
                && "Spiky Shield".equals(e.payload().get("abilityName")));
        assertTrue(abilityEventPublished, "Debe publicarse un evento ABILITY_USED para Spiky Shield");

        assertTrue(game.activeHp().getOrDefault(defender, 1) > 0,
            "Chesnaught no deberia estar Fuera de Combate (100 HP, 30 de dano)");
    }

    @Test
    void spikyShieldTriggersEvenWhenChesnaughtIsKoByAttack() {
        GameAggregate game = gameWithSetup();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        replaceDefenderActive(game, "chesnaught-test", List.of("Spiky Shield"), 60);
        game.setActiveAttackBaseDamage(100);
        game.activeAttachedEnergy().put(attacker, 1);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setTurnNumber(2);
        game.bench().get(defender).clear();

        service.executeAction(game.id(), GameActionType.ATTACK);

        assertTrue(game.activeHp().getOrDefault(defender, 1) <= 0,
            "Chesnaught debe quedar Fuera de Combate por el dano del ataque");

        int attackerCounters = game.damageCountersAtSlot(attacker, "ACTIVE");
        assertEquals(3, attackerCounters,
            "Spiky Shield debe activarse incluso si Chesnaught queda Fuera de Combate, colocando 3 contadores sobre el atacante");

        boolean abilityEventPublished = capturedEvents.stream()
            .anyMatch(e -> "ABILITY_USED".equals(e.type())
                && "Spiky Shield".equals(e.payload().get("abilityName")));
        assertTrue(abilityEventPublished,
            "Debe publicarse un evento ABILITY_USED para Spiky Shield aun si Chesnaught queda KO");
    }

    @Test
    void spikyShieldDoesNotTriggerWhenChesnaughtIsOnBench() {
        GameAggregate game = gameWithSetup();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        GameCard chesnaught = new GameCard(
            "chesnaught-bench", "Chesnaught", "Pokemon", Set.of("Basic"),
            100, 10, 1, null, List.of(), 1,
            null, List.of("Spiky Shield")
        );
        game.registerCard(chesnaught);
        game.bench().get(defender).add(0, "chesnaught-bench");

        game.setActiveAttackBaseDamage(30);
        game.activeAttachedEnergy().put(attacker, 1);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setTurnNumber(2);

        capturedEvents.clear();
        service.executeAction(game.id(), GameActionType.ATTACK);

        boolean abilityEventPublished = capturedEvents.stream()
            .anyMatch(e -> "ABILITY_USED".equals(e.type())
                && "Spiky Shield".equals(e.payload().get("abilityName")));
        assertFalse(abilityEventPublished,
            "Spiky Shield NO debe activarse si Chesnaught esta en Banca");
    }

    @Test
    void spikyShieldDoesNotTriggerWhenChesnaughtHasNoAbility() {
        GameAggregate game = gameWithSetup();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        replaceDefenderActive(game, "no-ability-test", List.of(), 100);
        game.setActiveAttackBaseDamage(30);
        game.activeAttachedEnergy().put(attacker, 1);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setTurnNumber(2);

        capturedEvents.clear();
        service.executeAction(game.id(), GameActionType.ATTACK);

        assertTrue(game.activeHp().getOrDefault(defender, 1) > 0);

        boolean abilityEventPublished = capturedEvents.stream()
            .anyMatch(e -> "ABILITY_USED".equals(e.type())
                && "Spiky Shield".equals(e.payload().get("abilityName")));
        assertFalse(abilityEventPublished,
            "Spiky Shield NO debe activarse si el Pokemon no tiene la habilidad");
    }

    @Test
    void spikyShieldDoesNotTriggerWhenNoDamageDealt() {
        GameAggregate game = gameWithSetup();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        replaceDefenderActive(game, "chesnaught-test", List.of("Spiky Shield"), 100);
        game.setActiveAttackBaseDamage(0);
        game.activeAttachedEnergy().put(attacker, 1);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setTurnNumber(2);

        capturedEvents.clear();
        service.executeAction(game.id(), GameActionType.ATTACK);

        int attackerCounters = game.damageCountersAtSlot(attacker, "ACTIVE");
        assertEquals(0, attackerCounters,
            "Spiky Shield NO debe activarse si no se hizo dano");

        boolean abilityEventPublished = capturedEvents.stream()
            .anyMatch(e -> "ABILITY_USED".equals(e.type())
                && "Spiky Shield".equals(e.payload().get("abilityName")));
        assertFalse(abilityEventPublished,
            "Spiky Shield NO debe activarse si no se hizo dano");
    }

    @Test
    void spikyShieldDoesNotTriggerOnSelfDamage() {
        GameAggregate game = gameWithSetup();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        replaceDefenderActive(game, "chesnaught-test", List.of("Spiky Shield"), 100);
        game.setActiveAttackBaseDamage(0);
        game.activeAttachedEnergy().put(attacker, 1);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setTurnNumber(2);

        capturedEvents.clear();
        service.executeAction(game.id(), GameActionType.ATTACK);

        boolean abilityEventPublished = capturedEvents.stream()
            .anyMatch(e -> "ABILITY_USED".equals(e.type())
                && "Spiky Shield".equals(e.payload().get("abilityName")));
        assertFalse(abilityEventPublished,
            "Spiky Shield NO debe activarse si no se hizo dano al defensor");
    }

    @Test
    void spikyShieldPlacesExactlyThreeDamageCounters() {
        SpikyShieldAbilityEffectStrategy strategy = new SpikyShieldAbilityEffectStrategy();

        UUID defender = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        GameAggregate game = new GameAggregate(attacker);
        game.setPlayerTwo(defender);
        game.activePokemon().put(defender, "chesnaught-test");
        game.activePokemon().put(attacker, "attacker-test");
        game.activeHp().put(defender, 100);
        game.activeHp().put(attacker, 130);
        game.activeDamageCounters().put(attacker, 0);
        game.damageCountersBySlot().computeIfAbsent(attacker, k -> new HashMap<>()).put("ACTIVE", 0);
        game.damageCountersBySlot().computeIfAbsent(defender, k -> new HashMap<>()).put("ACTIVE", 0);

        TrainerEffectResult result = strategy.executeSpikyShield(game, defender, attacker);

        assertEquals("SPIKY_SHIELD_ACTIVATED", result.result());
        assertEquals(3, game.damageCountersAtSlot(attacker, "ACTIVE"),
            "executeSpikyShield debe colocar exactamente 3 contadores de dano");
    }

    @Test
    void spikyShieldKoAttackerWhenLowHp() {
        GameAggregate game = gameWithSetup();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        GameCard chesnaught = new GameCard(
            "chesnaught-test", "Chesnaught", "Pokemon", Set.of("Basic"),
            60, 10, 1, null, List.of(), 1,
            null, List.of("Spiky Shield")
        );
        GameCard attackerCard = new GameCard(
            "attacker-card", "Attacker", "Pokemon", Set.of("Basic"),
            30, 100, 1
        );
        game.registerCard(chesnaught);
        game.registerCard(attackerCard);

        game.activePokemon().put(attacker, "attacker-card");
        game.activePokemon().put(defender, "chesnaught-test");
        game.activeHp().put(attacker, 30);
        game.activeHp().put(defender, 60);
        game.activeDamageCounters().put(attacker, 0);
        game.activeDamageCounters().put(defender, 0);
        game.damageCountersBySlot().computeIfAbsent(attacker, k -> new HashMap<>()).put("ACTIVE", 0);
        game.damageCountersBySlot().computeIfAbsent(defender, k -> new HashMap<>()).put("ACTIVE", 0);
        game.activePokemonEx().put(attacker, false);
        game.activePokemonEx().put(defender, false);
        game.activeAttachedEnergy().put(attacker, 1);
        game.setActiveAttackBaseDamage(100);
        game.setActiveAttackRequiredEnergy(1);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setTurnNumber(2);

        game.bench().get(defender).clear();
        game.bench().get(attacker).clear();

        capturedEvents.clear();
        service.executeAction(game.id(), GameActionType.ATTACK);

        boolean spikyShieldEventPublished = capturedEvents.stream()
            .anyMatch(e -> "ABILITY_USED".equals(e.type())
                && "Spiky Shield".equals(e.payload().get("abilityName")));
        assertTrue(spikyShieldEventPublished,
            "Debe publicarse un evento ABILITY_USED para Spiky Shield");

        boolean attackerKOPublished = capturedEvents.stream()
            .anyMatch(e -> "KO".equals(e.type())
                && attacker.equals(e.payload().get("defender")));
        assertTrue(attackerKOPublished,
            "Debe publicarse KO para el atacante con 30 HP por los 3 contadores de dano de Spiky Shield");
    }

    @Test
    void manualActivationThrowsError() {
        GameAggregate game = gameWithSetup();
        UUID attacker = game.playerOne();
        UUID defender = game.playerTwo();
        game.setCurrentTurnPlayer(defender);
        game.setTurnPhase(TurnPhase.MAIN);

        GameCard chesnaught = new GameCard(
            "chesnaught-test", "Chesnaught", "Pokemon", Set.of("Basic"),
            100, 10, 1, null, List.of(), 1,
            null, List.of("Spiky Shield")
        );
        game.registerCard(chesnaught);
        game.activePokemon().put(defender, "chesnaught-test");
        game.activeHp().put(defender, 100);
        game.activeDamageCounters().put(defender, 0);
        game.damageCountersBySlot().computeIfAbsent(defender, k -> new HashMap<>()).put("ACTIVE", 0);

        capturedEvents.clear();
        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> service.useAbility(game.id(), defender, "ACTIVE", 0, attacker.toString()));
        assertTrue(ex.getMessage().contains("Spiky Shield se activa automaticamente"),
            "El mensaje debe indicar que la habilidad es automatica");

        assertEquals(0, game.damageCountersAtSlot(attacker, "ACTIVE"),
            "Uso manual de Spiky Shield no debe colocar contadores de dano");
    }
}
