package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.chain.ModifierStep;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.PersistentAttackEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Branch coverage for {@link PersistentAttackEffectStrategy}.
 *
 * The strategy applies at most one effect per text branch, so each test uses a single
 * trigger phrase to keep the assertions clean. The active-effect value is read from
 * {@code game.activeEffectValue(player, key)} which is independent of the expiration
 * counter, so we do not need to advance the turn.
 */
class PersistentAttackEffectStrategyCoverageTest {

    private final PersistentAttackEffectStrategy s = new PersistentAttackEffectStrategy();

    // -----------------------------------------------------------------------
    // (a) "can't retreat during your opponent" → defender CANNOT_RETREAT
    // -----------------------------------------------------------------------
    @Test
    void applyDefenderCannotRetreatDuringOpponentsTurn() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Anchor Shot",
            "The Defending Pokemon can't retreat during your opponent's next turn.");
        s.apply(ctx);
        assertEquals(1, game.activeEffectValue(game.playerTwo(), ModifierStep.CANNOT_RETREAT));
        assertFalse(game.hasActiveEffect(game.playerOne(), ModifierStep.CANNOT_RETREAT));
    }

    // -----------------------------------------------------------------------
    // (b) "can't retreat during your next turn" → self CANNOT_RETREAT
    // -----------------------------------------------------------------------
    @Test
    void applySelfCannotRetreatDuringNextTurn() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Crippling Slam",
            "The Defending Pokemon is now Paralyzed. This Pokemon can't retreat during your next turn.");
        s.apply(ctx);
        assertEquals(1, game.activeEffectValue(game.playerOne(), ModifierStep.CANNOT_RETREAT));
        assertFalse(game.hasActiveEffect(game.playerTwo(), ModifierStep.CANNOT_RETREAT));
    }

    // -----------------------------------------------------------------------
    // (c) "can't retreat" without "your opponent" → self CANNOT_RETREAT
    // -----------------------------------------------------------------------
    @Test
    void applySelfCannotRetreatWithoutOpponentMention() {
        // "can't retreat" alone (no "your opponent") hits the `&& !text.contains("your opponent")` path.
        GameAggregate game = game();
        AttackContext ctx = context(game, "Root Lock",
            "The Defending Pokemon can't retreat.");
        s.apply(ctx);
        assertEquals(1, game.activeEffectValue(game.playerOne(), ModifierStep.CANNOT_RETREAT));
    }

    // -----------------------------------------------------------------------
    // (d) "can't play any supporter" → SUPPORTER_LOCK on defender
    // -----------------------------------------------------------------------
    @Test
    void applySupporterLockOnDefender() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Bureaucracy",
            "Your opponent can't play any Supporter cards from their hand during their next turn.");
        s.apply(ctx);
        assertEquals(1, game.activeEffectValue(game.playerTwo(), ModifierStep.SUPPORTER_LOCK));
    }

    // -----------------------------------------------------------------------
    // (e) "has no abilities" → ABILITY_LOCK on defender
    // -----------------------------------------------------------------------
    @Test
    void applyAbilityLockOnDefender() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Silence Roar",
            "The Defending Pokemon has no abilities, except for Pokemon-EX, until your next turn.");
        s.apply(ctx);
        assertEquals(1, game.activeEffectValue(game.playerTwo(), ModifierStep.ABILITY_LOCK));
    }

    // -----------------------------------------------------------------------
    // (f) isOpponentAttackBlockText + selectedOpponentAttackIndex >= 0 → BLOCKED_ATTACK
    // -----------------------------------------------------------------------
    @Test
    void applyBlockedOpponentAttackWithSelectedIndex() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Seal Off",
            "Choose 1 of your opponent's attacks. That attack can't be used during your opponent's next turn.");
        ctx.setSelectedOpponentAttackIndex(0);
        s.apply(ctx);
        assertEquals(1, game.activeEffectValue(game.playerTwo(),
            ModifierStep.BLOCKED_ATTACK_PREFIX + 0));
    }

    // -----------------------------------------------------------------------
    // (g) isOpponentAttackBlockText + selectedOpponentAttackIndex < 0 → no effect applied
    // -----------------------------------------------------------------------
    @Test
    void applySkipsOpponentAttackBlockWhenIndexNotSelected() {
        // The text satisfies isOpponentAttackBlockText, but selectedOpponentAttackIndex is -1
        // (default), so the `&& context.selectedOpponentAttackIndex() >= 0` guard short-circuits.
        GameAggregate game = game();
        AttackContext ctx = context(game, "Seal Off",
            "Choose 1 of your opponent's attacks. That attack can't be used during your opponent's next turn.");
        s.apply(ctx);
        assertFalse(game.hasActiveEffect(game.playerTwo(),
            ModifierStep.BLOCKED_ATTACK_PREFIX + 0));
        assertFalse(game.hasActiveEffect(game.playerTwo(),
            ModifierStep.BLOCKED_ATTACK_PREFIX + 1));
    }

    // -----------------------------------------------------------------------
    // (h) "can't attack during your opponent" → defender CANNOT_ATTACK
    // -----------------------------------------------------------------------
    @Test
    void applyDefenderCannotAttackDuringOpponent() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Stun Lock",
            "The Defending Pokemon can't attack during your opponent's next turn.");
        s.apply(ctx);
        assertEquals(1, game.activeEffectValue(game.playerTwo(), ModifierStep.CANNOT_ATTACK));
    }

    // -----------------------------------------------------------------------
    // (i) "during your next turn" + "more damage" with scoped bonus
    //      isScopedBonus returns true via "this attack" → ATTACKER_DAMAGE_BONUS:attackName
    // -----------------------------------------------------------------------
    @Test
    void applyScopedAttackerDamageBonusViaThisAttack() {
        GameAggregate game = game();
        // The text contains "this attack" → isScopedBonus short-circuits to true,
        // so the effect key is scoped to the attack name.
        AttackContext ctx = context(game, "Tail Gain",
            "If this Pokemon has any damage counters on it, this attack does 30 more damage during your next turn.");
        s.apply(ctx);
        assertEquals(30, game.activeEffectValue(game.playerOne(),
            ModifierStep.ATTACKER_DAMAGE_BONUS + ":Tail Gain"));
        // The non-scoped key must remain absent.
        assertEquals(0, game.activeEffectValue(game.playerOne(), ModifierStep.ATTACKER_DAMAGE_BONUS));
    }

    // -----------------------------------------------------------------------
    // (j) "prevent" + "damage" + "or less" → DEFENDER_PREVENT_DAMAGE_MAX threshold
    // -----------------------------------------------------------------------
    @Test
    void applyThresholdPreventionByDamageAmount() {
        GameAggregate game = game();
        // The text is crafted to NOT contain "prevent all damage" so only the
        // threshold branch fires.
        AttackContext ctx = context(game, "Safeguard",
            "Prevent damage from attacks that do 30 or less damage to this Pokemon.");
        s.apply(ctx);
        assertEquals(30, game.activeEffectValue(game.playerOne(), ModifierStep.DEFENDER_PREVENT_DAMAGE_MAX));
    }

    // -----------------------------------------------------------------------
    // (k) "prevent all damage" / "prevent all effects of attacks" → DEFENDER_DAMAGE_REDUCTION=999
    // -----------------------------------------------------------------------
    @Test
    void applyPreventAllDamageSetsMaxReduction() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Sanctuary",
            "Prevent all damage done to this Pokemon by attacks during your opponent's next turn.");
        s.apply(ctx);
        assertEquals(999, game.activeEffectValue(game.playerOne(), ModifierStep.DEFENDER_DAMAGE_REDUCTION));
    }

    @Test
    void applyPreventAllEffectsOfAttacksSetsMaxReduction() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Void Shield",
            "Prevent all effects of attacks, including damage, done to this Pokemon during your opponent's next turn.");
        s.apply(ctx);
        assertEquals(999, game.activeEffectValue(game.playerOne(), ModifierStep.DEFENDER_DAMAGE_REDUCTION));
    }

    // -----------------------------------------------------------------------
    // (l) "during your opponent's next turn" + "reduced by" → DEFENDER_DAMAGE_REDUCTION
    // -----------------------------------------------------------------------
    @Test
    void applyReducedByAmountInOpponentsNextTurn() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Eclipse Veil",
            "During your opponent's next turn, any damage done to this Pokemon by attacks is reduced by 20.");
        s.apply(ctx);
        assertEquals(20, game.activeEffectValue(game.playerOne(), ModifierStep.DEFENDER_DAMAGE_REDUCTION));
    }

    // -----------------------------------------------------------------------
    // (m) isSelfAttackBlockText + attackIndex >= 0 → BLOCKED_ATTACK on self
    // -----------------------------------------------------------------------
    @Test
    void applySelfBlockedAttackByIndex() {
        GameAggregate game = game();
        // "this pokemon can't use" + "during your next turn" satisfies isSelfAttackBlockText.
        AttackContext ctx = context(game, "Lost Wind",
            "This Pokemon can't use its first attack during your next turn.");
        ctx.setAttackIndex(0);
        s.apply(ctx);
        assertEquals(1, game.activeEffectValue(game.playerOne(),
            ModifierStep.BLOCKED_ATTACK_PREFIX + 0));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private AttackContext context(GameAggregate game, String attackName, String attackText) {
        AttackContext ctx = new AttackContext(game);
        ctx.setAttackName(attackName);
        ctx.setAttackText(attackText);
        return ctx;
    }

    private GameAggregate game() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(p1);
        game.setFirstPlayer(p1);
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());
        game.activePokemon().put(p1, "active-1");
        game.activePokemon().put(p2, "active-2");
        game.registerCard(new GameCard("active-1", "Attacker", "Pokemon", Set.of("Basic"), 100, 10, 1));
        game.registerCard(new GameCard("active-2", "Defender", "Pokemon", Set.of("Basic"), 100, 10, 1));
        game.damageCountersBySlot().get(p1).put("ACTIVE", 0);
        game.damageCountersBySlot().get(p2).put("ACTIVE", 0);
        return game;
    }
}
