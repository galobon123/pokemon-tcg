package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.chain.AttackResolutionPipeline;
import com.utn.pokemontcg.game.domain.chain.DamageCalculationStep;
import com.utn.pokemontcg.game.domain.chain.ModifierStep;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FurCoatAbilityTest {

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

    private int resolveDamage(GameAggregate game, int baseDamage) {
        AttackContext context = new AttackContext(game);
        context.setBaseDamage(baseDamage);
        context.setRequiredEnergy(0);
        context.setAttachedEnergy(0);
        new AttackResolutionPipeline(List.of(
            new ModifierStep(),
            new DamageCalculationStep()
        )).resolve(context);
        return context.damage();
    }

    @Test
    void defendingFurfrouTakesReducedDamage() {
        GameAggregate game = gameWithPlayers();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        GameCard furfrou = pokemon("furfrou", "Furfrou", List.of("Fur Coat"));
        GameCard attackerCard = pokemon("attacker", "Attacker", List.of());
        game.registerCard(furfrou);
        game.registerCard(attackerCard);
        game.activePokemon().put(attacker, attackerCard.id());
        game.activePokemon().put(defender, furfrou.id());

        assertEquals(40, resolveDamage(game, 60),
            "Fur Coat debe reducir el dano recibido por el Furfrou defensor en 20");
    }

    @Test
    void reductionFloorsAtZero() {
        GameAggregate game = gameWithPlayers();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        GameCard furfrou = pokemon("furfrou", "Furfrou", List.of("Fur Coat"));
        GameCard attackerCard = pokemon("attacker", "Attacker", List.of());
        game.registerCard(furfrou);
        game.registerCard(attackerCard);
        game.activePokemon().put(attacker, attackerCard.id());
        game.activePokemon().put(defender, furfrou.id());

        assertEquals(0, resolveDamage(game, 10),
            "El dano no puede ser negativo tras aplicar la reduccion de Fur Coat");
    }

    @Test
    void noReductionWhenFurfrouIsBenched() {
        GameAggregate game = gameWithPlayers();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        GameCard furfrou = pokemon("furfrou", "Furfrou", List.of("Fur Coat"));
        GameCard defenderCard = pokemon("defender", "Defender", List.of());
        game.registerCard(furfrou);
        game.registerCard(defenderCard);
        game.activePokemon().put(attacker, "attacker-card");
        game.activePokemon().put(defender, defenderCard.id());
        game.bench().get(defender).add(0, furfrou.id());

        assertEquals(60, resolveDamage(game, 60),
            "Fur Coat no debe reducir dano si Furfrou esta en la banca");
    }

    @Test
    void noSelfReductionWhenFurfrouAttacks() {
        GameAggregate game = gameWithPlayers();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        GameCard furfrou = pokemon("furfrou", "Furfrou", List.of("Fur Coat"));
        GameCard defenderCard = pokemon("defender", "Defender", List.of());
        game.registerCard(furfrou);
        game.registerCard(defenderCard);
        game.activePokemon().put(attacker, furfrou.id());
        game.activePokemon().put(defender, defenderCard.id());

        assertEquals(60, resolveDamage(game, 60),
            "Fur Coat no debe reducir el dano propio cuando Furfrou ataca");
    }
}
