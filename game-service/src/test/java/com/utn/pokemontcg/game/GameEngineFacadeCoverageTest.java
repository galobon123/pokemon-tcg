package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.attack.AttackPatternAnalyzer;
import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.event.GameEventPublisher;
import com.utn.pokemontcg.game.domain.facade.GameEngineFacade;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.model.TurnPhase;
import com.utn.pokemontcg.game.domain.strategy.AttackEffectRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage tests for {@link GameEngineFacade}.
 *
 * The existing test files cover the constructors and main resolveAttack
 * flow, but the following branches stay red:
 *   - buildDefaultAttackContext legacy path (no attacks list)
 *   - parseDamage() null/blank/non-numeric inputs
 *   - firstNonBlank() with a blank primary
 *   - resolveAttack() with selectedOpponentAttackIndex parameter
 *   - constructor variations
 *   - statusByPlayer with CONFUSED on attacker
 *   - weaknessMultiplier / resistanceReduction fallbacks when cards are null
 */
class GameEngineFacadeCoverageTest {

    private static GameEventPublisher newPublisher() {
        return new GameEventPublisher();
    }

    @Test
    void oneArgConstructor_buildsDefaultAnalyzer() {
        GameEngineFacade facade = new GameEngineFacade(newPublisher());
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        // Should work without NullPointerException from internal analyzer
        facade.startSetup(game);
        assertEquals(GameState.SETUP, game.gameState());
    }

    @Test
    void twoArgConstructor_keepsProvidedRegistry() {
        AttackEffectRegistry registry = new AttackEffectRegistry(java.util.List.of());
        GameEngineFacade facade = new GameEngineFacade(newPublisher(), registry);
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        facade.startSetup(game);
        assertEquals(GameState.SETUP, game.gameState());
    }

    @Test
    void threeArgConstructor_usesProvidedAnalyzer() {
        AttackPatternAnalyzer analyzer = new AttackPatternAnalyzer();
        GameEngineFacade facade = new GameEngineFacade(newPublisher(), null, analyzer);
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        facade.startSetup(game);
        assertEquals(GameState.SETUP, game.gameState());
    }

    @Test
    void startActive_advancesToDrawPhase() {
        GameEngineFacade facade = new GameEngineFacade(newPublisher());
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        game.setPlayerTwo(UUID.randomUUID());
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        game.setGameState(GameState.SETUP);
        game.initializeZonesFor(game.playerOne(), List.of("a1"));
        game.initializeZonesFor(game.playerTwo(), List.of("b1"));
        game.registerCard(new GameCard("a1", "Active", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard("b1", "Other", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.activePokemon().put(game.playerOne(), "a1");
        game.activePokemon().put(game.playerTwo(), "b1");

        facade.startActive(game);

        // startActive enters ActiveState, then DrawPhaseState.advance() flips
        // the turn phase to MAIN.
        assertEquals(TurnPhase.MAIN, game.turnPhase());
    }

    @Test
    void parseDamage_viaReflection_handlesNullBlankAndNonNumeric() throws Exception {
        GameEngineFacade facade = new GameEngineFacade(newPublisher());
        Method m = GameEngineFacade.class.getDeclaredMethod("parseDamage", String.class);
        m.setAccessible(true);
        assertEquals(0, m.invoke(facade, new Object[]{null}));
        assertEquals(0, m.invoke(facade, ""));
        assertEquals(0, m.invoke(facade, "   "));
        // Non-numeric after the regex strip returns empty → NumberFormatException
        // → catch → 0.
        assertEquals(0, m.invoke(facade, "abc"));
        // "30x" → "30" → Integer.parseInt succeeds → 30.
        assertEquals(30, m.invoke(facade, "30x"));
    }

    @Test
    void firstNonBlank_viaReflection_usesFallbackWhenPrimaryIsBlank() throws Exception {
        GameEngineFacade facade = new GameEngineFacade(newPublisher());
        Method m = GameEngineFacade.class.getDeclaredMethod("firstNonBlank", String.class, String.class);
        m.setAccessible(true);
        assertEquals("FB", m.invoke(facade, "", "FB"));
        assertEquals("FB", m.invoke(facade, "   ", "FB"));
        assertEquals("primary", m.invoke(facade, "primary", "FB"));
    }

    @Test
    void buildDefaultAttackContext_viaReflection_legacyPathWithNoAttacks() throws Exception {
        GameEngineFacade facade = new GameEngineFacade(newPublisher());
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p2 = UUID.randomUUID();
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        // Register active and defender cards with NO attacks list.
        game.registerCard(new GameCard("attacker", "Pikachu", "Pokemon", Set.of("Basic"),
            60, 25, 1));
        game.registerCard(new GameCard("defender", "Squirtle", "Pokemon", Set.of("Basic"),
            50, 10, 1));
        game.activePokemon().put(game.playerOne(), "attacker");
        game.activePokemon().put(p2, "defender");

        Method m = GameEngineFacade.class.getDeclaredMethod("buildDefaultAttackContext", GameAggregate.class);
        m.setAccessible(true);
        AttackContext ctx = (AttackContext) m.invoke(facade, game);

        assertNotNull(ctx);
        // Legacy path pulls attackDamage and attackRequiredEnergy from card
        // when attacks list is empty.
        assertEquals(25, ctx.baseDamage());
        assertEquals(1, ctx.requiredEnergy());
        assertEquals("Pikachu", ctx.attackName());
        assertEquals("attacker", ctx.attackerCard().id());
        assertEquals("defender", ctx.defenderCard().id());
    }

    @Test
    void buildDefaultAttackContext_legacyUsesAggregateValuesWhenCardMissing() throws Exception {
        GameEngineFacade facade = new GameEngineFacade(newPublisher());
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p2 = UUID.randomUUID();
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        // No registered cards → cardCatalog.get returns null.
        game.activePokemon().put(game.playerOne(), "missing-1");
        game.activePokemon().put(p2, "missing-2");
        // Set aggregate-level values (non-default) so the legacy branch
        // picks them up.
        game.setActiveAttackRequiredEnergy(2);
        game.setActiveAttackBaseDamage(50);

        Method m = GameEngineFacade.class.getDeclaredMethod("buildDefaultAttackContext", GameAggregate.class);
        m.setAccessible(true);
        AttackContext ctx = (AttackContext) m.invoke(facade, game);

        assertNotNull(ctx);
        // Card is null → branches fall back to aggregate values.
        assertEquals(50, ctx.baseDamage());
        assertEquals(2, ctx.requiredEnergy());
    }

    @Test
    void buildDefaultAttackContext_confusedAttackerFlagIsPropagated() throws Exception {
        GameEngineFacade facade = new GameEngineFacade(newPublisher());
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p2 = UUID.randomUUID();
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        game.registerCard(new GameCard("a1", "A", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.registerCard(new GameCard("b1", "B", "Pokemon", Set.of("Basic"), 60, 10, 1));
        game.activePokemon().put(game.playerOne(), "a1");
        game.activePokemon().put(p2, "b1");
        game.statusByPlayer().put(game.playerOne(), EnumSet.of(StatusCondition.CONFUSED));

        Method m = GameEngineFacade.class.getDeclaredMethod("buildDefaultAttackContext", GameAggregate.class);
        m.setAccessible(true);
        AttackContext ctx = (AttackContext) m.invoke(facade, game);
        assertTrue(ctx.confused());
    }

    @Test
    void buildDefaultAttackContext_weaknessResistanceFallbackWhenCardsNull() throws Exception {
        GameEngineFacade facade = new GameEngineFacade(newPublisher());
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p2 = UUID.randomUUID();
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        // Cards intentionally missing.
        game.setDefendingPokemonWeakToAttack(true);
        game.setDefendingPokemonResistsAttack(true);
        game.activePokemon().put(game.playerOne(), "missing-a");
        game.activePokemon().put(p2, "missing-b");

        Method m = GameEngineFacade.class.getDeclaredMethod("buildDefaultAttackContext", GameAggregate.class);
        m.setAccessible(true);
        AttackContext ctx = (AttackContext) m.invoke(facade, game);

        assertEquals(2, ctx.weaknessMultiplier());
        assertEquals(20, ctx.resistanceReduction());
    }

    @Test
    void resolveAttack_withNullAttackIndexFallsBackToFirstAttack() {
        GameEngineFacade facade = new GameEngineFacade(newPublisher());
        GameAggregate game = activeGameWithAttacks();
        AttackContext ctx = facade.resolveAttack(game);
        assertEquals(0, ctx.attackIndex());
    }

    @Test
    void resolveAttack_withSelectedOpponentAttackIndexSetsIt() {
        GameEngineFacade facade = new GameEngineFacade(newPublisher());
        GameAggregate game = activeGameWithAttacks();
        AttackContext ctx = facade.resolveAttack(
            game, Integer.valueOf(0), List.of(), null, null,
            List.of(), List.of(), null, null, Integer.valueOf(0));
        assertEquals(0, ctx.selectedOpponentAttackIndex());
    }

    @Test
    void resolveAttack_withCoinFlipResultsExhaustedFallsBackToRandom() {
        GameEngineFacade facade = new GameEngineFacade(newPublisher());
        GameAggregate game = activeGameWithAttacks();
        // 1 result, then 5 more flips (iter exhausts → random fallback)
        AttackContext ctx = facade.resolveAttack(game, Integer.valueOf(0), List.of(true));
        // We can't assert on a specific boolean since the fallback is random,
        // but the pipeline must finish without exception.
        assertNotNull(ctx);
    }

    @Test
    void resolveAttack_advancePhaseMovesToBetweenTurns() {
        GameEngineFacade facade = new GameEngineFacade(newPublisher());
        GameAggregate game = activeGameWithAttacks();
        AttackContext ctx = facade.resolveAttack(game);
        assertEquals(TurnPhase.BETWEEN_TURNS, game.turnPhase());
    }

    @Test
    void publish_emitsEventToSubscribers() {
        GameEventPublisher publisher = newPublisher();
        List<Object> events = new ArrayList<>();
        publisher.subscribe(events::add);
        GameEngineFacade facade = new GameEngineFacade(publisher);
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        facade.publish(game, com.utn.pokemontcg.game.domain.event.GameEventType.GAME_CREATED, Map.of("k", "v"));
        assertEquals(1, events.size());
    }

    private static GameAggregate activeGameWithAttacks() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        UUID p2 = UUID.randomUUID();
        game.setPlayerTwo(p2);
        game.setGameState(GameState.ACTIVE);
        game.setTurnPhase(TurnPhase.ATTACK);
        game.setCurrentTurnPlayer(game.playerOne());
        game.setFirstPlayer(game.playerOne());
        game.initializeZonesFor(game.playerOne(), List.of("a1"));
        game.initializeZonesFor(p2, List.of("b1"));
        GameCard attacker = new GameCard("a1", "Pikachu", "Pokemon", Set.of("Basic"),
            60, 30, 1,
            "Thunder Shock", "Flip a coin. If heads, the Defending Pokemon is now Paralyzed.",
            "Lightning", List.of("Lightning"), 1, null, List.of());
        GameCard defender = new GameCard("b1", "Charmander", "Pokemon", Set.of("Basic"),
            50, 10, 1);
        game.registerCard(attacker);
        game.registerCard(defender);
        game.activePokemon().put(game.playerOne(), "a1");
        game.activePokemon().put(p2, "b1");
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e1", "Lightning"));
        return game;
    }
}
