package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.engine.StatusEffectManager;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.strategy.AbilityEffectContext;
import com.utn.pokemontcg.game.domain.strategy.SpecialConditionAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.SweetVeilAbilityEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectResult;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SweetVeilAbilityTest {

    private final StatusEffectManager statusEffectManager = new StatusEffectManager();

    private GameAggregate gameWithPlayers() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(p1);
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());
        return game;
    }

    private GameCard pokemon(String id, String name, List<String> abilities) {
        return new GameCard(
            id, name, "Pokemon", Set.of("Basic"),
            100, 10, 1, null, null, "Colorless", List.of(), 1, null, abilities
        );
    }

    @Test
    void conditionBlockedOnFairyEnergyActive() {
        GameAggregate game = gameWithPlayers();
        UUID owner = game.currentTurnPlayer();

        GameCard slurpuff = pokemon("slurpuff", "Slurpuff", List.of("Sweet Veil"));
        GameCard fairyPokemon = pokemon("fairy-pokemon", "Fairy Mon", List.of());
        game.registerCard(slurpuff);
        game.registerCard(fairyPokemon);
        game.activePokemon().put(owner, slurpuff.id());
        game.attachEnergy(owner, "ACTIVE", new AttachedEnergy("fairy-energy", "Fairy"));

        statusEffectManager.applyCondition(game, owner, StatusCondition.POISONED);

        assertTrue(game.statusByPlayer().getOrDefault(owner, EnumSet.noneOf(StatusCondition.class)).isEmpty(),
            "Sweet Veil debe bloquear condiciones en un Pokemon Activo con energia Fairy");
    }

    @Test
    void conditionBlockedOnFairyEnergyTargetViaAttackEffect() {
        GameAggregate game = gameWithPlayers();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        GameCard slurpuff = pokemon("slurpuff", "Slurpuff", List.of("Sweet Veil"));
        GameCard fairyPokemon = pokemon("fairy-pokemon", "Fairy Mon", List.of());
        game.registerCard(slurpuff);
        game.registerCard(fairyPokemon);
        game.activePokemon().put(defender, slurpuff.id());
        game.attachEnergy(defender, "ACTIVE", new AttachedEnergy("fairy-energy", "Fairy"));

        AttackContext context = new AttackContext(game);
        context.setAttackText("The Defending Pokemon is now Poisoned.");
        new SpecialConditionAttackEffectStrategy().apply(context);

        assertFalse(game.statusByPlayer().getOrDefault(defender, EnumSet.noneOf(StatusCondition.class)).contains(StatusCondition.POISONED),
            "Sweet Veil debe bloquear condiciones aplicadas por ataques");
    }

    @Test
    void noProtectionWithoutFairyEnergy() {
        GameAggregate game = gameWithPlayers();
        UUID owner = game.currentTurnPlayer();

        GameCard slurpuff = pokemon("slurpuff", "Slurpuff", List.of("Sweet Veil"));
        GameCard plainPokemon = pokemon("plain-pokemon", "Plain Mon", List.of());
        game.registerCard(slurpuff);
        game.registerCard(plainPokemon);
        game.activePokemon().put(owner, slurpuff.id());

        statusEffectManager.applyCondition(game, owner, StatusCondition.POISONED);

        assertTrue(game.statusByPlayer().getOrDefault(owner, EnumSet.noneOf(StatusCondition.class)).contains(StatusCondition.POISONED),
            "Sweet Veil no debe proteger sin energia Fairy adjunta");
    }

    @Test
    void inactiveWhenSlurpuffIsBenched() {
        GameAggregate game = gameWithPlayers();
        UUID owner = game.currentTurnPlayer();

        GameCard slurpuff = pokemon("slurpuff", "Slurpuff", List.of("Sweet Veil"));
        GameCard otherActive = pokemon("other-active", "Other Active", List.of());
        game.registerCard(slurpuff);
        game.registerCard(otherActive);
        game.activePokemon().put(owner, otherActive.id());
        game.bench().get(owner).add(0, slurpuff.id());
        game.attachEnergy(owner, "ACTIVE", new AttachedEnergy("fairy-energy", "Fairy"));

        statusEffectManager.applyCondition(game, owner, StatusCondition.POISONED);

        assertTrue(game.statusByPlayer().getOrDefault(owner, EnumSet.noneOf(StatusCondition.class)).contains(StatusCondition.POISONED),
            "Sweet Veil no debe activarse si Slurpuff esta en la banca");
    }

    @Test
    void manualActivationThrowsError() {
        SweetVeilAbilityEffectStrategy strategy = new SweetVeilAbilityEffectStrategy();
        GameAggregate game = gameWithPlayers();
        UUID player = game.currentTurnPlayer();

        GameCard slurpuff = pokemon("slurpuff", "Slurpuff", List.of("Sweet Veil"));
        game.registerCard(slurpuff);
        game.activePokemon().put(player, slurpuff.id());

        AbilityEffectContext ctx = new AbilityEffectContext(game, player, slurpuff, "ACTIVE", 0, null);
        Exception ex = assertThrows(Exception.class, () -> strategy.apply(ctx));
        assertTrue(ex.getMessage().contains("habilidad continua"),
            "El uso manual debe indicar que Sweet Veil es una habilidad continua");
    }
}
