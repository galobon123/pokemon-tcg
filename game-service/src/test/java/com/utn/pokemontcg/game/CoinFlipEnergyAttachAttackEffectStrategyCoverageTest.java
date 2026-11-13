package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.CoinFlipEnergyAttachAttackEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage for {@link CoinFlipEnergyAttachAttackEffectStrategy}.
 * Exercises apply(), flipCount(), targetSlot(), requiredEnergyType() and supports().
 */
class CoinFlipEnergyAttachAttackEffectStrategyCoverageTest {

    private final CoinFlipEnergyAttachAttackEffectStrategy strategy = new CoinFlipEnergyAttachAttackEffectStrategy();

    // ---------- apply() ----------

    @Test
    void cancelledContextReturnsEarly() {
        GameAggregate game = game();
        registerEnergy(game, "fire-1", "Fire Energy", "Fire");
        game.discardPile().get(game.playerOne()).add("fire-1");
        game.bench().get(game.playerOne()).add("b1");

        AttackContext c = context(game, "Energy Burst",
            "Flip a coin. For each heads, attach a Fire Energy from your discard pile to your Benched Pokemon.");
        c.setCancelled(true);
        c.setCoinFlip(() -> true);

        strategy.apply(c);

        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
        assertTrue(c.auditTrail().stream().noneMatch(a -> a.startsWith("AttackEffect:coin_flip_energy_attached")),
            "Cancelled context should not produce attached audit");
    }

    @Test
    void zeroHeadsAttachesNothing() {
        GameAggregate game = game();
        registerEnergy(game, "fire-1", "Fire Energy", "Fire");
        registerEnergy(game, "fire-2", "Fire Energy", "Fire");
        game.discardPile().get(game.playerOne()).add("fire-1");
        game.discardPile().get(game.playerOne()).add("fire-2");
        game.bench().get(game.playerOne()).add("b1");

        AttackContext c = context(game, "Energy Burst",
            "Flip a coin. For each heads, attach a Fire Energy from your discard pile to your Benched Pokemon.");
        c.setCoinFlip(() -> false);
        c.setTargetSlot("BENCH_0");

        strategy.apply(c);

        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
        assertEquals(List.of("fire-1", "fire-2"), game.discardPile().get(game.playerOne()));
        assertTrue(c.auditTrail().stream()
            .anyMatch(a -> a.contains("coin_flip_energy_attached=0,heads=0")));
    }

    @Test
    void twoHeadsAttachesUpToTwoEnergy() {
        GameAggregate game = game();
        registerEnergy(game, "fire-1", "Fire Energy", "Fire");
        registerEnergy(game, "fire-2", "Fire Energy", "Fire");
        registerEnergy(game, "fire-3", "Fire Energy", "Fire");
        game.discardPile().get(game.playerOne()).add("fire-1");
        game.discardPile().get(game.playerOne()).add("fire-2");
        game.discardPile().get(game.playerOne()).add("fire-3");
        game.bench().get(game.playerOne()).add("b1");

        AttackContext c = context(game, "Energy Burst",
            "Flip 2 coins. For each heads, attach a Fire Energy from your discard pile to your Benched Pokemon.");
        c.setCoinFlip(() -> true);
        c.setTargetSlot("BENCH_0");

        strategy.apply(c);

        assertEquals(2, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
        assertEquals(1, game.discardPile().get(game.playerOne()).size());
        assertTrue(c.auditTrail().stream()
            .anyMatch(a -> a.contains("coin_flip_energy_attached=2,heads=2")));
    }

    @Test
    void targetSlotReturnsNullWhenBenchEmpty() {
        GameAggregate game = game();
        registerEnergy(game, "fire-1", "Fire Energy", "Fire");
        game.discardPile().get(game.playerOne()).add("fire-1");
        // No bench

        AttackContext c = context(game, "Energy Burst",
            "Flip a coin. For each heads, attach a Fire Energy from your discard pile to your Benched Pokemon.");
        c.setCoinFlip(() -> true);
        // targetSlot null -> normalize -> "ACTIVE" -> not BENCH_ -> fall through to bench loop
        // empty bench -> null target

        strategy.apply(c);

        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
        assertTrue(c.auditTrail().stream()
            .anyMatch(a -> a.equals("AttackEffect:coin_flip_energy_attach_no_target")));
    }

    @Test
    void requiredEnergyTypeFiltersMismatch() {
        GameAggregate game = game();
        registerEnergy(game, "water-1", "Water Energy", "Water");
        registerEnergy(game, "water-2", "Water Energy", "Water");
        game.discardPile().get(game.playerOne()).add("water-1");
        game.discardPile().get(game.playerOne()).add("water-2");
        game.bench().get(game.playerOne()).add("b1");

        // Text requires "fire energy" -> only Fire passes
        AttackContext c = context(game, "Energy Burst",
            "Flip a coin. For each heads, attach a Fire Energy from your discard pile to your Benched Pokemon.");
        c.setCoinFlip(() -> true);
        c.setTargetSlot("BENCH_0");

        strategy.apply(c);

        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
        assertEquals(2, game.discardPile().get(game.playerOne()).size(),
            "Water energies should remain in discard when Fire is required");
    }

    @Test
    void nonEnergyCardInDiscardIsSkipped() {
        GameAggregate game = game();
        // A Pokemon card
        GameCard pokemonCard = new GameCard("poke-1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1);
        game.registerCard(pokemonCard);
        registerEnergy(game, "fire-1", "Fire Energy", "Fire");
        game.discardPile().get(game.playerOne()).add("poke-1");
        game.discardPile().get(game.playerOne()).add("fire-1");
        game.bench().get(game.playerOne()).add("b1");

        AttackContext c = context(game, "Energy Burst",
            "Flip a coin. For each heads, attach a Fire Energy from your discard pile to your Benched Pokemon.");
        c.setCoinFlip(() -> true);
        c.setTargetSlot("BENCH_0");

        strategy.apply(c);

        // Only energy should be attached
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
        assertTrue(game.discardPile().get(game.playerOne()).contains("poke-1"),
            "Pokemon card must stay in discard");
    }

    @Test
    void unknownCardInDiscardIsSkipped() {
        GameAggregate game = game();
        // Card id is not in cardCatalog
        game.discardPile().get(game.playerOne()).add("unknown-card");
        game.bench().get(game.playerOne()).add("b1");

        AttackContext c = context(game, "Energy Burst",
            "Flip a coin. For each heads, attach a Fire Energy from your discard pile to your Benched Pokemon.");
        c.setCoinFlip(() -> true);
        c.setTargetSlot("BENCH_0");

        strategy.apply(c);

        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
    }

    @Test
    void supportsReturnsTrueForMatchingText() {
        AttackContext c = context(game(),
            "Energy Burst",
            "Flip a coin. For each heads, attach a Fire Energy from your discard pile to your Benched Pokemon.");
        assertTrue(strategy.supports(c));
    }

    @Test
    void supportsReturnsFalseWhenMissingDiscardReference() {
        // Text has flip + for each heads + attach + energy, but no "from your discard"
        AttackContext c = context(game(),
            "Energy Burst",
            "Flip a coin. For each heads, attach a Fire Energy from your deck to this Pokemon.");
        assertFalse(strategy.supports(c));
    }

    @Test
    void supportsReturnsFalseWhenMissingCoinPhrase() {
        AttackContext c = context(game(),
            "Energy Burst",
            "Attach a Fire Energy from your discard pile to your Benched Pokemon.");
        assertFalse(strategy.supports(c));
    }

    @Test
    void supportsReturnsFalseForTackle() {
        AttackContext c = context(game(), "Tackle", "Deal 30 damage.");
        assertFalse(strategy.supports(c));
    }

    // ---------- flipCount() ----------

    @Test
    void flipCountWithExplicitCount() {
        GameAggregate game = game();
        AttackContext c = context(game,
            "Triple Flip",
            "Flip 3 coins. For each heads, attach a Fire Energy from your discard pile to this Pokemon.");
        c.setCoinFlip(() -> true);
        strategy.apply(c);
        // flipCount parsed "3" from "flip 3 coins" -> 3 results
        assertEquals(3, c.coinFlipResults().size());
    }

    @Test
    void flipCountDefaultsToOne() {
        GameAggregate game = game();
        AttackContext c = context(game,
            "Single Flip",
            "Flip a coin. For each heads, attach a Fire Energy from your discard pile to this Pokemon.");
        c.setCoinFlip(() -> true);
        c.setTargetSlot("ACTIVE");
        strategy.apply(c);
        assertEquals(1, c.coinFlipResults().size());
    }

    // ---------- targetSlot() ----------

    @Test
    void targetSlotRespectsRequestedOccupiedSlot() {
        GameAggregate game = game();
        game.bench().get(game.playerOne()).add("b1");
        game.bench().get(game.playerOne()).add("b2");

        AttackContext c = context(game, "Energy Burst",
            "Flip a coin. For each heads, attach a Fire Energy from your discard pile to your Benched Pokemon.");
        c.setTargetSlot("BENCH_1");

        // No coin flips needed for this branch test
        c.setCoinFlip(() -> true);
        registerEnergy(game, "fire-1", "Fire Energy", "Fire");
        game.discardPile().get(game.playerOne()).add("fire-1");

        strategy.apply(c);

        // Requested BENCH_1 was occupied -> attached to BENCH_1
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "BENCH_1"));
        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
    }

    @Test
    void targetSlotFallsBackToFirstOccupiedWhenRequestedEmpty() {
        GameAggregate game = game();
        game.bench().get(game.playerOne()).add("b0");
        // BENCH_1 is null/empty (only one bench pokemon)

        AttackContext c = context(game, "Energy Burst",
            "Flip a coin. For each heads, attach a Fire Energy from your discard pile to your Benched Pokemon.");
        c.setTargetSlot("BENCH_1");
        c.setCoinFlip(() -> true);
        registerEnergy(game, "fire-1", "Fire Energy", "Fire");
        game.discardPile().get(game.playerOne()).add("fire-1");

        strategy.apply(c);

        // BENCH_1 is not occupied -> fall back to first occupied (BENCH_0)
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
    }

    // ---------- requiredEnergyType() ----------

    @Test
    void requiredEnergyTypeDetectsFire() {
        GameAggregate game = game();
        registerEnergy(game, "water-1", "Water Energy", "Water");
        game.discardPile().get(game.playerOne()).add("water-1");
        game.bench().get(game.playerOne()).add("b1");

        // Text mentions "fire energy" -> required type Fire
        AttackContext c = context(game, "Energy Burst",
            "Flip a coin. For each heads, attach a Fire Energy from your discard pile to your Benched Pokemon.");
        c.setCoinFlip(() -> true);
        c.setTargetSlot("BENCH_0");

        strategy.apply(c);

        // Water is rejected -> nothing attached
        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
    }

    @Test
    void requiredEnergyTypeDetectsWater() {
        GameAggregate game = game();
        registerEnergy(game, "fire-1", "Fire Energy", "Fire");
        game.discardPile().get(game.playerOne()).add("fire-1");
        game.bench().get(game.playerOne()).add("b1");

        // Text mentions "water energy" -> required type Water
        AttackContext c = context(game, "Energy Burst",
            "Flip a coin. For each heads, attach a Water Energy from your discard pile to your Benched Pokemon.");
        c.setCoinFlip(() -> true);
        c.setTargetSlot("BENCH_0");

        strategy.apply(c);

        // Fire is rejected -> nothing attached
        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
    }

    @Test
    void requiredEnergyTypeReturnsNullWhenNoTypeMentioned() {
        GameAggregate game = game();
        registerEnergy(game, "fire-1", "Fire Energy", "Fire");
        game.discardPile().get(game.playerOne()).add("fire-1");
        game.bench().get(game.playerOne()).add("b1");

        // Text has no type mention -> any energy type works
        AttackContext c = context(game, "Energy Burst",
            "Flip a coin. For each heads, attach an Energy from your discard pile to your Benched Pokemon.");
        c.setCoinFlip(() -> true);
        c.setTargetSlot("BENCH_0");

        strategy.apply(c);

        // Fire should be attached (no required type filter)
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
    }

    @Test
    void auditContainsAttachedCountAndHeads() {
        GameAggregate game = game();
        registerEnergy(game, "fire-1", "Fire Energy", "Fire");
        game.discardPile().get(game.playerOne()).add("fire-1");
        game.bench().get(game.playerOne()).add("b1");

        AttackContext c = context(game, "Energy Burst",
            "Flip a coin. For each heads, attach a Fire Energy from your discard pile to your Benched Pokemon.");
        c.setCoinFlip(() -> true);
        c.setTargetSlot("BENCH_0");

        strategy.apply(c);

        assertTrue(c.auditTrail().stream()
            .anyMatch(a -> a.startsWith("AttackEffect:coin_flip_energy_attached=")
                && a.contains("heads=1")));
        assertNotNull(c.coinFlipResults());
    }

    // ---------- helpers ----------

    private AttackContext context(GameAggregate game, String attackName, String attackText) {
        AttackContext c = new AttackContext(game);
        c.setAttackName(attackName);
        c.setAttackText(attackText);
        return c;
    }

    private GameAggregate game() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.setCurrentTurnPlayer(p1);
        game.initializeZonesFor(p1, List.of());
        game.initializeZonesFor(p2, List.of());
        game.activePokemon().put(p1, "active-1");
        game.activePokemon().put(p2, "active-2");
        game.damageCountersBySlot().get(p1).put("ACTIVE", 0);
        game.damageCountersBySlot().get(p2).put("ACTIVE", 0);
        return game;
    }

    private void registerEnergy(GameAggregate game, String id, String name, String type) {
        game.registerCard(new GameCard(id, name, "Energy", Set.of("Basic"), 0, 0, 0, type, List.of(), 0));
    }
}
