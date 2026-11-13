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
import com.utn.pokemontcg.game.domain.strategy.DestinyBurstAbilityEffectStrategy;
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

class DestinyBurstAbilityTest {

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
            new AbilityEffectRegistry(List.of(new DestinyBurstAbilityEffectStrategy())),
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
    void destinyBurstTriggersWhenVoltorbIsActiveAndKoByAttack() {
        GameAggregate game = gameWithSetup();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        replaceDefenderActive(game, "voltorb-test", List.of("Destiny Burst"), 60);
        game.setActiveAttackBaseDamage(100);
        game.activeAttachedEnergy().put(attacker, 1);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setTurnNumber(2);
        game.bench().get(defender).clear();

        service.executeAction(game.id(), GameActionType.ATTACK);

        assertTrue(game.activeHp().getOrDefault(defender, 1) <= 0);

        boolean coinFlipEventPublished = capturedEvents.stream()
            .anyMatch(e -> "COIN_FLIP".equals(e.type())
                && "Destiny Burst".equals(e.payload().get("abilityName")));
        assertTrue(coinFlipEventPublished, "Debe publicarse un evento COIN_FLIP para Destiny Burst");

        int attackerCounters = game.damageCountersAtSlot(attacker, "ACTIVE");
        if (attackerCounters > 0) {
            assertEquals(5, attackerCounters,
                "Si salio cara, deben colocarse exactamente 5 contadores de dano sobre el atacante");
        }
    }

    @Test
    void destinyBurstDoesNotTriggerWhenVoltorbIsOnBench() {
        GameAggregate game = gameWithSetup();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        GameCard voltorb = new GameCard(
            "voltorb-bench", "Voltorb", "Pokemon", Set.of("Basic"),
            60, 10, 1, null, List.of(), 1,
            null, List.of("Destiny Burst")
        );
        game.registerCard(voltorb);
        game.bench().get(defender).add(0, "voltorb-bench");

        game.setActiveAttackBaseDamage(100);
        game.activeAttachedEnergy().put(attacker, 1);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setTurnNumber(2);

        capturedEvents.clear();
        service.executeAction(game.id(), GameActionType.ATTACK);

        boolean destinyBurstPublished = capturedEvents.stream()
            .anyMatch(e -> "COIN_FLIP".equals(e.type())
                && "Destiny Burst".equals(e.payload().get("abilityName")));
        assertFalse(destinyBurstPublished,
            "Destiny Burst NO debe activarse si Voltorb esta en Banca");
    }

    @Test
    void destinyBurstDoesNotTriggerWhenVoltorbHasNoAbility() {
        GameAggregate game = gameWithSetup();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        replaceDefenderActive(game, "no-ability-test", List.of(), 60);
        game.setActiveAttackBaseDamage(100);
        game.activeAttachedEnergy().put(attacker, 1);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setTurnNumber(2);
        game.bench().get(defender).clear();

        capturedEvents.clear();
        service.executeAction(game.id(), GameActionType.ATTACK);

        assertTrue(game.activeHp().getOrDefault(defender, 1) <= 0);

        boolean destinyBurstPublished = capturedEvents.stream()
            .anyMatch(e -> "COIN_FLIP".equals(e.type())
                && "Destiny Burst".equals(e.payload().get("abilityName")));
        assertFalse(destinyBurstPublished,
            "Destiny Burst NO debe activarse si el Pokemon no tiene la habilidad");
    }

    @Test
    void destinyBurstDoesNotTriggerWhenNotKo() {
        GameAggregate game = gameWithSetup();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        replaceDefenderActive(game, "voltorb-test", List.of("Destiny Burst"), 200);
        game.setActiveAttackBaseDamage(10);
        game.activeAttachedEnergy().put(attacker, 1);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setTurnNumber(2);

        capturedEvents.clear();
        service.executeAction(game.id(), GameActionType.ATTACK);

        assertTrue(game.activeHp().getOrDefault(defender, 1) > 0,
            "Voltorb no deberia estar Fuera de Combate (solo 10 de dano, 200 HP)");

        boolean destinyBurstPublished = capturedEvents.stream()
            .anyMatch(e -> "COIN_FLIP".equals(e.type())
                && "Destiny Burst".equals(e.payload().get("abilityName")));
        assertFalse(destinyBurstPublished,
            "Destiny Burst NO debe activarse si Voltorb no quedo Fuera de Combate");
    }

    @Test
    void manualActivationThrowsError() {
        GameAggregate game = gameWithSetup();
        UUID attacker = game.playerOne();
        UUID defender = game.playerTwo();
        game.setCurrentTurnPlayer(defender);
        game.setTurnPhase(TurnPhase.MAIN);

        GameCard voltorb = new GameCard(
            "voltorb-test", "Voltorb", "Pokemon", Set.of("Basic"),
            60, 10, 1, null, List.of(), 1,
            null, List.of("Destiny Burst")
        );
        game.registerCard(voltorb);
        game.activePokemon().put(defender, "voltorb-test");
        game.activeHp().put(defender, 60);
        game.activeDamageCounters().put(defender, 0);
        game.damageCountersBySlot().computeIfAbsent(defender, k -> new HashMap<>()).put("ACTIVE", 0);

        capturedEvents.clear();
        InvalidActionException ex = assertThrows(InvalidActionException.class,
            () -> service.useAbility(game.id(), defender, "ACTIVE", 0, attacker.toString()));
        assertTrue(ex.getMessage().contains("Destiny Burst solo se activa automaticamente"),
            "El mensaje debe indicar que la habilidad es automatica");

        assertEquals(0, game.damageCountersAtSlot(attacker, "ACTIVE"),
            "Uso manual de Destiny Burst no debe colocar contadores de dano");
    }

    @Test
    void destinyBurstPlacesExactlyFiveDamageCountersOnHeads() {
        DestinyBurstAbilityEffectStrategy strategy = new DestinyBurstAbilityEffectStrategy();

        UUID defender = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        GameAggregate game = new GameAggregate(attacker);
        game.setPlayerTwo(defender);
        game.activePokemon().put(defender, "voltorb-test");
        game.activePokemon().put(attacker, "attacker-test");
        game.activeHp().put(defender, 0);
        game.activeHp().put(attacker, 130);
        game.activeDamageCounters().put(attacker, 0);
        game.damageCountersBySlot().computeIfAbsent(attacker, k -> new HashMap<>()).put("ACTIVE", 0);
        game.damageCountersBySlot().computeIfAbsent(defender, k -> new HashMap<>()).put("ACTIVE", 0);

        AbilityEffectContext ctx = new AbilityEffectContext(
            game, defender, null, "ACTIVE", 0, attacker.toString()
        );

        int headsCount = 0;
        int tailsCount = 0;
        for (int i = 0; i < 200; i++) {
            game.activeDamageCounters().put(attacker, 0);
            game.damageCountersBySlot().get(attacker).put("ACTIVE", 0);

            TrainerEffectResult result = strategy.apply(ctx);

            int counters = game.damageCountersAtSlot(attacker, "ACTIVE");
            if ("DESTINY_BURST_ACTIVATED".equals(result.result())) {
                headsCount++;
                assertEquals(5, counters, "Al salir cara deben colocarse exactamente 5 contadores");
            } else {
                tailsCount++;
                assertEquals(0, counters, "Al salir cruz no deben colocarse contadores");
            }
        }

        assertTrue(headsCount > 60, "Debe haber resultados cara (" + headsCount + ")");
        assertTrue(tailsCount > 60, "Debe haber resultados cruz (" + tailsCount + ")");
    }

    @Test
    void destinyBurstKoAttackerWhenLowHp() {
        GameAggregate game = gameWithSetup();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        GameCard voltorb = new GameCard(
            "voltorb-test", "Voltorb", "Pokemon", Set.of("Basic"),
            60, 10, 1, null, List.of(), 1,
            null, List.of("Destiny Burst")
        );
        GameCard attackerCard = new GameCard(
            "attacker-card", "Attacker", "Pokemon", Set.of("Basic"),
            50, 100, 1
        );
        game.registerCard(voltorb);
        game.registerCard(attackerCard);

        game.activePokemon().put(attacker, "attacker-card");
        game.activePokemon().put(defender, "voltorb-test");
        game.activeHp().put(attacker, 50);
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

        int attackerCounters = game.damageCountersAtSlot(attacker, "ACTIVE");

        if (attackerCounters >= 5) {
            boolean attackerKo = game.activeHp().getOrDefault(attacker, 1) <= 0;
            assertTrue(attackerKo, "El atacante con 50 HP debe quedar Fuera de Combate por los 5 contadores de dano");

            boolean attackerKOPublished = capturedEvents.stream()
                .anyMatch(e -> "KO".equals(e.type())
                    && attacker.equals(e.payload().get("defender")));
            assertTrue(attackerKOPublished, "Debe publicarse KO para el atacante si quedo Fuera de Combate");
        }
    }
}
