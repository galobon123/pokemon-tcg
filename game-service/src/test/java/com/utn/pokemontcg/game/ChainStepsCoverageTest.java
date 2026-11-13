package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.chain.DamageCalculationStep;
import com.utn.pokemontcg.game.domain.chain.EnergyValidationStep;
import com.utn.pokemontcg.game.domain.chain.ModifierStep;
import com.utn.pokemontcg.game.domain.chain.PreAttackStep;
import com.utn.pokemontcg.game.domain.exception.InsufficientEnergyException;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.ShadowCircleStadiumEffect;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage push for the four chain classes with the lowest branch coverage in
 * the JaCoCo report. Each test targets a specific uncovered branch called out
 * by the audit: rainbow-energy substitution, raw-damage fallback, immunity
 * text, Shadow Circle, coin-flip variants, the +N bonus fallback, and the
 * lower-bound clamps in {@link ModifierStep}.
 */
class ChainStepsCoverageTest {

    // ----- EnergyValidationStep -----

    @Test
    void energyValidation_rainbowEnergySubstitutesForMissingTypedEnergy() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setAttackName("Hydro Pump");
        context.setRequiredEnergyCost(List.of("Water", "Colorless"));
        context.setAttachedEnergies(List.of(
            new AttachedEnergy("rainbow-1", "Rainbow"),
            new AttachedEnergy("rainbow-2", "Rainbow")
        ));

        assertDoesNotThrow(() -> new EnergyValidationStep().execute(context));
        assertTrue(context.auditTrail().contains("EnergyValidationStep:ok"),
            "Rainbow energy debe poder sustituir cualquier tipo de energia faltante");
    }

    @Test
    void energyValidation_throwsWhenColorlessCannotBeCoveredByRemainingAny() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setAttackName("Tackle");
        context.setRequiredEnergyCost(List.of("Colorless", "Colorless", "Colorless"));
        context.setAttachedEnergies(List.of(
            new AttachedEnergy("fire-1", "Fire")
        ));

        InsufficientEnergyException ex = assertThrows(InsufficientEnergyException.class,
            () -> new EnergyValidationStep().execute(context));
        assertTrue(ex.getMessage().contains("Incolora") || ex.getMessage().contains("Colorless"),
            "El mensaje de error debe nombrar el tipo Colorless / Incolora");
    }

    @Test
    void energyValidation_normalizesNullTypeAsColorless() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setAttackName("Splash");
        context.setRequiredEnergyCost(List.of("Colorless"));
        // null/blank energyType in the AttachedEnergy collapses to "Colorless"
        context.setAttachedEnergies(List.of(
            new AttachedEnergy("blank-energy", null)
        ));

        assertDoesNotThrow(() -> new EnergyValidationStep().execute(context));
    }

    @Test
    void energyValidation_errorMessageIncludesLocalizedType() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setAttackName("Llamarada");
        context.setRequiredEnergyCost(List.of("Fire", "Colorless"));
        context.setAttachedEnergies(List.of(
            new AttachedEnergy("water-1", "Water")
        ));

        InsufficientEnergyException ex = assertThrows(InsufficientEnergyException.class,
            () -> new EnergyValidationStep().execute(context));
        assertTrue(ex.getMessage().contains("Fuego"),
            "spanishType() debe traducir Fire a 'Fuego' en el mensaje de error");
    }

    @Test
    void energyValidation_usesAttackNameInErrorMessage() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setAttackName("Hyper Beam");
        context.setRequiredEnergyCost(List.of("Lightning"));
        context.setAttachedEnergies(List.of(
            new AttachedEnergy("fighting-1", "Fighting")
        ));

        InsufficientEnergyException ex = assertThrows(InsufficientEnergyException.class,
            () -> new EnergyValidationStep().execute(context));
        assertTrue(ex.getMessage().contains("Hyper Beam"),
            "El mensaje debe incluir el nombre del ataque cuando esta disponible");
    }

    @Test
    void energyValidation_fallbackAttackNameWhenBlank() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setAttackName("   ");
        context.setRequiredEnergyCost(List.of("Water"));
        context.setAttachedEnergies(List.of(
            new AttachedEnergy("fire-1", "Fire")
        ));

        InsufficientEnergyException ex = assertThrows(InsufficientEnergyException.class,
            () -> new EnergyValidationStep().execute(context));
        assertTrue(ex.getMessage().contains("el ataque"),
            "Si el nombre del ataque esta en blanco se usa el placeholder 'el ataque'");
    }

    // ----- DamageCalculationStep -----

    @Test
    void damageCalculation_usesRawCalculationWhenAttackerOrDefenderMissing() {
        GameAggregate game = new GameAggregate(UUID.randomUUID());
        AttackContext context = new AttackContext(game);
        context.setBaseDamage(50);
        context.setPreAttackBonus(10);
        context.setAttackerModifier(5);
        context.setDefenderModifier(3);
        context.setWeaknessMultiplier(2);
        context.setResistanceReduction(10);

        new DamageCalculationStep().execute(context);

        // raw = (50 + 10 + 5) * 2 - 10 + 3 = 123
        assertEquals(123, context.damage(),
            "Sin cartas atacante/defensor se usa el calculo crudo");
    }

    @Test
    void damageCalculation_weaknessImmunityTextForcesMultiplierToOne() {
        GameAggregate game = gameWithPlayers();
        GameCard attackerCard = new GameCard("attacker", "Attacker", "Pokemon",
            Set.of("Basic"), 100, 10, 1, "Sky Attack",
            "This attack isn't affected by Weakness.", "Colorless",
            List.of(), 0, null, List.of(), null, null, null, null, List.of());
        GameCard defenderCard = new GameCard("defender", "Defender", "Pokemon",
            Set.of("Basic"), 100, 10, 1, null, null, "Colorless", List.of(), 0, null,
            List.of(), "Colorless", 2, null, null, List.of());
        game.registerCard(attackerCard);
        game.registerCard(defenderCard);
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();
        game.activePokemon().put(attacker, attackerCard.id());
        game.activePokemon().put(defender, defenderCard.id());

        AttackContext context = new AttackContext(game);
        context.setAttackerCard(attackerCard);
        context.setDefenderCard(defenderCard);
        context.setAttackName(attackerCard.attackName());
        context.setAttackText(attackerCard.attackText());
        context.setBaseDamage(50);
        new ModifierStep().execute(context);

        new DamageCalculationStep().execute(context);

        assertEquals(1, context.weaknessMultiplier(),
            "El texto 'isn't affected by Weakness' debe neutralizar la debilidad");
    }

    @Test
    void damageCalculation_resistanceImmunityTextForcesReductionToZero() {
        GameAggregate game = gameWithPlayers();
        GameCard attackerCard = new GameCard("attacker", "Attacker", "Pokemon",
            Set.of("Basic"), 100, 10, 1, "Aurora Beam",
            "This attack isn't affected by Resistance.", "Colorless",
            List.of(), 0, null, List.of(), null, null, null, null, List.of());
        GameCard defenderCard = new GameCard("defender", "Defender", "Pokemon",
            Set.of("Basic"), 100, 10, 1, null, null, "Colorless", List.of(), 0, null,
            List.of(), null, null, "Colorless", 20, List.of());
        game.registerCard(attackerCard);
        game.registerCard(defenderCard);
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();
        game.activePokemon().put(attacker, attackerCard.id());
        game.activePokemon().put(defender, defenderCard.id());

        AttackContext context = new AttackContext(game);
        context.setAttackerCard(attackerCard);
        context.setDefenderCard(defenderCard);
        context.setAttackName(attackerCard.attackName());
        context.setAttackText(attackerCard.attackText());
        context.setBaseDamage(50);
        new ModifierStep().execute(context);

        new DamageCalculationStep().execute(context);

        assertEquals(0, context.resistanceReduction(),
            "El texto 'isn't affected by Resistance' debe neutralizar la reduccion");
    }

    @Test
    void damageCalculation_shadowCircleStadiumWithDarknessEnergySuppressesWeakness() {
        GameAggregate game = gameWithPlayers();
        GameCard attackerCard = new GameCard("attacker", "Attacker", "Pokemon",
            Set.of("Basic"), 100, 10, 1, "Bite", "Simple Bite.", "Darkness",
            List.of(), 0, null, List.of(), null, null, null, null, List.of());
        GameCard defenderCard = new GameCard("defender", "Defender", "Pokemon",
            Set.of("Basic"), 100, 10, 1, null, null, "Colorless", List.of(), 0, null,
            List.of(), "Darkness", 2, null, null, List.of());
        game.registerCard(attackerCard);
        game.registerCard(defenderCard);
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();
        game.activePokemon().put(attacker, attackerCard.id());
        game.activePokemon().put(defender, defenderCard.id());

        // Activate Shadow Circle on the defender
        game.addActiveEffect(defender, ShadowCircleStadiumEffect.EFFECT_KEY, 1);
        // Attach a Darkness energy to the defender's active pokemon
        game.attachEnergy(defender, "ACTIVE", new AttachedEnergy("dark-1", "Darkness"));

        AttackContext context = new AttackContext(game);
        context.setAttackerCard(attackerCard);
        context.setDefenderCard(defenderCard);
        context.setAttackName(attackerCard.attackName());
        context.setAttackText(attackerCard.attackText());
        context.setBaseDamage(50);

        new DamageCalculationStep().execute(context);

        assertEquals(1, context.weaknessMultiplier(),
            "Shadow Circle + Darkness energy suprime la debilidad");
        assertTrue(context.auditTrail().stream()
            .anyMatch(a -> a.contains("shadow_circle_suppressed_weakness")),
            "El audit trail debe registrar la supresion de Shadow Circle");
    }

    @Test
    void damageCalculation_setsDamageToZeroWhenContextCancelled() {
        GameAggregate game = gameWithPlayers();
        AttackContext context = new AttackContext(game);
        context.setCancelled(true);
        context.setBaseDamage(100);

        new DamageCalculationStep().execute(context);

        assertEquals(0, context.damage(),
            "Si el contexto esta cancelado el dano se fuerza a 0");
    }

    // ----- PreAttackStep -----

    @Test
    void preAttackStep_skipsWhenNoCoinFlipInText() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setAttackText("This attack does 30 damage to the Defending Pokemon.");
        context.setBaseDamage(30);

        new PreAttackStep().execute(context);

        assertTrue(context.auditTrail().stream()
            .anyMatch(a -> a.contains("no_pre_damage_coin_flip")),
            "Sin 'flip' el step debe registrarse como 'no_pre_damage_coin_flip'");
        assertEquals(0, context.preAttackBonus());
    }

    @Test
    void preAttackStep_usesExplicitCoinFlipCount() {
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setAttackText("Flip 3 coins. This attack does 30 more damage for each heads.");
        context.setBaseDamage(30);
        context.setCoinFlip(sequence(true, true, false));

        new PreAttackStep().execute(context);

        assertEquals(3, context.coinFlipResults().size(),
            "El numero de flips debe leerse del texto 'Flip N coins'");
        assertEquals(60, context.preAttackBonus(),
            "2 heads * 30 = 60 de bonus pre-attack");
    }

    @Test
    void preAttackStep_fallsBackToPlusPatternForDamagePerHeads() {
        // Text must (a) contain "more damage for each heads" to enter the
        // isPreDamageCoinFlip path but (b) NOT match the "N more damage for each
        // heads" regex, so the +N fallback in damagePerHeads kicks in.
        AttackContext context = new AttackContext(new GameAggregate(UUID.randomUUID()));
        context.setAttackText("Flip a coin. This attack does more damage for each heads. Damage bonus: +40.");
        context.setBaseDamage(30);
        context.setCoinFlip(() -> true);

        new PreAttackStep().execute(context);

        assertEquals(40, context.preAttackBonus(),
            "1 head * 40 (del patron '+N') = 40 de bonus pre-attack");
    }

    // ----- ModifierStep -----

    @Test
    void modifierStep_clampsWeaknessMultiplierBelowOne() {
        GameAggregate game = gameWithPlayers();
        AttackContext context = new AttackContext(game);
        context.setBaseDamage(50);
        context.setWeaknessMultiplier(0);
        new ModifierStep().execute(context);

        assertEquals(1, context.weaknessMultiplier(),
            "El multiplicador de debilidad nunca puede bajar de 1");
    }

    @Test
    void modifierStep_clampsResistanceReductionBelowZero() {
        GameAggregate game = gameWithPlayers();
        AttackContext context = new AttackContext(game);
        context.setBaseDamage(50);
        context.setResistanceReduction(-5);
        new ModifierStep().execute(context);

        assertEquals(0, context.resistanceReduction(),
            "La reduccion por resistencia nunca puede quedar negativa");
    }

    @Test
    void modifierStep_furCoatDoesNotApplyWhenDefenderActiveCardHasNoAbilities() {
        GameAggregate game = gameWithPlayers();
        UUID attacker = game.currentTurnPlayer();
        UUID defender = game.playerOne().equals(attacker) ? game.playerTwo() : game.playerOne();

        // defender card with no abilities list at all
        GameCard defenderCard = new GameCard("defender", "Defender", "Pokemon",
            Set.of("Basic"), 100, 10, 1, null, null, "Colorless", List.of(), 0, null,
            null, null, null, null, null, List.of());
        game.registerCard(defenderCard);
        game.activePokemon().put(defender, defenderCard.id());

        AttackContext context = new AttackContext(game);
        context.setBaseDamage(50);
        new ModifierStep().execute(context);

        assertEquals(0, context.defenderModifier(),
            "Fur Coat no debe aplicar si el Pokemon activo defensor no tiene lista de abilities");
        assertFalse(context.auditTrail().stream()
            .anyMatch(a -> a.contains("Fur Coat") || a.contains("FurCoat")));
    }

    @Test
    void modifierStep_furCoatDoesNotApplyWhenDefenderActiveCardIsNull() {
        GameAggregate game = gameWithPlayers();
        UUID defender = game.playerOne().equals(game.currentTurnPlayer())
            ? game.playerTwo() : game.playerOne();
        // No active card id at all
        game.activePokemon().remove(defender);

        AttackContext context = new AttackContext(game);
        context.setBaseDamage(50);
        new ModifierStep().execute(context);

        assertEquals(0, context.defenderModifier(),
            "Fur Coat no debe aplicar si el Pokemon activo defensor es null");
    }

    @Test
    void modifierStep_furCoatDoesNotApplyWhenDefenderCardNotInCatalog() {
        GameAggregate game = gameWithPlayers();
        UUID defender = game.playerOne().equals(game.currentTurnPlayer())
            ? game.playerTwo() : game.playerOne();
        // active card id points to a non-existent card
        game.activePokemon().put(defender, "missing-card-id");

        AttackContext context = new AttackContext(game);
        context.setBaseDamage(50);
        new ModifierStep().execute(context);

        assertEquals(0, context.defenderModifier(),
            "Fur Coat no debe aplicar si la carta activa no esta en el catalogo");
    }

    // ----- helpers -----

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

    private BooleanSupplier sequence(boolean... values) {
        ArrayDeque<Boolean> queue = new ArrayDeque<>();
        for (boolean value : values) {
            queue.add(value);
        }
        return queue::removeFirst;
    }
}
