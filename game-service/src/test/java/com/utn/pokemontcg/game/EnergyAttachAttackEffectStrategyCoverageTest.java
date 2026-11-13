package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.strategy.EnergyAttachAttackEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extra coverage for {@link EnergyAttachAttackEffectStrategy}:
 * <ul>
 *   <li>cancelled context short-circuit</li>
 *   <li>coin condition failure</li>
 *   <li>source zone empty</li>
 *   <li>specific energy type filtering</li>
 *   <li>basic-only filter</li>
 *   <li>distribute path (in any way / benched)</li>
 *   <li>targetSlot null (no bench pokemon)</li>
 *   <li>card not energy / not in source zone</li>
 *   <li>"attach it to this" path</li>
 *   <li>benchOnly path / requested slot fallback to first bench</li>
 *   <li>source() — discard / hand / deck paths</li>
 *   <li>zone() — hand / deck / discard zones</li>
 * </ul>
 */
class EnergyAttachAttackEffectStrategyCoverageTest {

    private final EnergyAttachAttackEffectStrategy strategy = new EnergyAttachAttackEffectStrategy();

    @Test
    void cancelledContextIsIgnored() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.discardPile().get(g.playerOne()).add("fire-1");
        AttackContext c = ctx(g, "Energy", "Attach a Fire Energy card from your discard pile to this Pokemon.");
        c.setCancelled(true);
        strategy.apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void coinConditionFailureIsIgnored() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.discardPile().get(g.playerOne()).add("fire-1");
        AttackContext c = ctx(g, "Energy", "Flip a coin. If heads, attach a Fire Energy card from your discard pile to this Pokemon.");
        c.setCoinFlipResults(List.of(false));
        strategy.apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void emptySourceZoneIsAudited() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        // No fire-1 in discardPile
        AttackContext c = ctx(g, "Energy", "Attach a Fire Energy card from your discard pile to this Pokemon.");
        strategy.apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void requiredEnergyTypeFiltersMismatch() {
        GameAggregate g = game();
        g.registerCard(energy("water-1", "Water Energy", "Water"));
        g.discardPile().get(g.playerOne()).add("water-1");
        AttackContext c = ctx(g, "Energy", "Attach a Fire Energy card from your discard pile to this Pokemon.");
        strategy.apply(c);
        // Water doesn't match Fire required type -> nothing attached
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void basicOnlyFilterRejectsSpecialEnergy() {
        GameAggregate g = game();
        GameCard special = new GameCard("sp-1", "Special Energy", "Energy", Set.of("Special"), 0, 0, 0, "Fire", List.of(), 0);
        g.registerCard(special);
        g.discardPile().get(g.playerOne()).add("sp-1");
        AttackContext c = ctx(g, "Energy", "Attach a basic energy from your discard pile to this Pokemon.");
        strategy.apply(c);
        // Special energy is filtered out by basicOnly
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void cardNotInCatalogIsSkipped() {
        GameAggregate g = game();
        g.discardPile().get(g.playerOne()).add("missing-card");
        AttackContext c = ctx(g, "Energy", "Attach a Fire Energy card from your discard pile to this Pokemon.");
        strategy.apply(c);
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void cardNotEnergyIsSkipped() {
        GameAggregate g = game();
        g.registerCard(new GameCard("poke-1", "Pikachu", "Pokemon", Set.of("Basic"), 60, 10, 1));
        g.discardPile().get(g.playerOne()).add("poke-1");
        AttackContext c = ctx(g, "Energy", "Attach a Fire Energy card from your discard pile to this Pokemon.");
        strategy.apply(c);
        // Not an energy card -> nothing attached
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void distributePathSplitsAcrossBenched() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.registerCard(energy("fire-2", "Fire Energy", "Fire"));
        g.deck().get(g.playerOne()).add("fire-1");
        g.deck().get(g.playerOne()).add("fire-2");
        g.bench().get(g.playerOne()).add("b1");
        g.bench().get(g.playerOne()).add("b2");
        AttackContext c = ctx(g, "Geomancy",
            "Search your deck for 2 Fire Energy and attach them to your Benched Pokemon in any way you like.");
        c.setSelectedCardIds(List.of("BENCH_0", "BENCH_1", "fire-1", "fire-2"));
        strategy.apply(c);
        // Each bench gets one fire energy
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "BENCH_0"));
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "BENCH_1"));
        // Both energy cards removed from deck
        assertEquals(0, g.deck().get(g.playerOne()).size());
    }

    @Test
    void distributePathWithBenchedKeyword() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.deck().get(g.playerOne()).add("fire-1");
        g.bench().get(g.playerOne()).add("b1");
        AttackContext c = ctx(g, "Geo",
            "Attach a Fire Energy from your deck to one of your benched Pokemon.");
        c.setSelectedCardIds(List.of("BENCH_0", "fire-1"));
        strategy.apply(c);
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "BENCH_0"));
    }

    @Test
    void attachItToThisTargetsActive() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.deck().get(g.playerOne()).add("fire-1");
        AttackContext c = ctx(g, "Recharge",
            "Attach a Fire Energy card from your deck to this Pokemon.");
        c.setSelectedEnergyCardIds(List.of("fire-1"));
        strategy.apply(c);
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void attachItToThisWithoutActiveIsNoOp() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.deck().get(g.playerOne()).add("fire-1");
        g.activePokemon().remove(g.playerOne());
        AttackContext c = ctx(g, "Recharge",
            "Attach a Fire Energy card from your deck to this Pokemon.");
        c.setSelectedEnergyCardIds(List.of("fire-1"));
        strategy.apply(c);
        // No active -> the targetSlot path returns null -> audit "energy_attach_no_target"
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void benchOnlyFallsBackToFirstBenchWhenRequestedEmpty() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.deck().get(g.playerOne()).add("fire-1");
        g.bench().get(g.playerOne()).add("b1");
        AttackContext c = ctx(g, "Bench",
            "Search your deck for a Fire Energy and attach it to 1 of your benched Pokemon.");
        c.setSelectedEnergyCardIds(List.of("fire-1"));
        // targetSlot intentionally null -> falls back to BENCH_0
        strategy.apply(c);
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "BENCH_0"));
    }

    @Test
    void benchOnlyRequestedSlotOccupied() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.deck().get(g.playerOne()).add("fire-1");
        g.bench().get(g.playerOne()).add("b1");
        g.bench().get(g.playerOne()).add("b2");
        AttackContext c = ctx(g, "Bench",
            "Attach a Fire Energy to 1 of your benched Pokemon.");
        c.setTargetSlot("BENCH_1");
        c.setSelectedEnergyCardIds(List.of("fire-1"));
        strategy.apply(c);
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "BENCH_1"));
    }

    @Test
    void sourceRecognizesDiscardPile() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.discardPile().get(g.playerOne()).add("fire-1");
        AttackContext c = ctx(g, "Reclaim",
            "Attach a Fire Energy card from your discard pile to this Pokemon.");
        strategy.apply(c);
        // Card moved from discard to active
        assertEquals(0, g.discardPile().get(g.playerOne()).size());
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void sourceRecognizesHand() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.hand().get(g.playerOne()).add("fire-1");
        AttackContext c = ctx(g, "FreeAttach",
            "Attach a Fire Energy card from your hand to this Pokemon.");
        strategy.apply(c);
        assertEquals(0, g.hand().get(g.playerOne()).size());
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void sourceDefaultsToDeck() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.deck().get(g.playerOne()).add("fire-1");
        AttackContext c = ctx(g, "Search",
            "Search your deck for a Fire Energy and attach it to this Pokemon.");
        strategy.apply(c);
        assertEquals(0, g.deck().get(g.playerOne()).size());
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void supportsAllRequiredPatterns() {
        AttackContext c1 = ctx(game(), "X", "Attach a Fire Energy from your deck to this Pokemon.");
        assertTrue(strategy.supports(c1));
        AttackContext c2 = ctx(game(), "X", "Attach a Fire Energy from your hand to this Pokemon.");
        assertTrue(strategy.supports(c2));
        AttackContext c3 = ctx(game(), "X", "Attach a Fire Energy from your discard pile to this Pokemon.");
        assertTrue(strategy.supports(c3));
        AttackContext c4 = ctx(game(), "X", "Search your deck for a Fire Energy and attach it to this Pokemon.");
        assertTrue(strategy.supports(c4));
    }

    @Test
    void doesNotSupportPlainDamage() {
        AttackContext c = ctx(game(), "Tackle", "Deal 30 damage.");
        assertEquals(false, strategy.supports(c));
    }

    @Test
    void deckSourceRevealsAttachedCards() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.deck().get(g.playerOne()).add("fire-1");
        AttackContext c = ctx(g, "Search",
            "Search your deck for a Fire Energy and attach it to this Pokemon.");
        strategy.apply(c);
        // Card should be revealed
        assertTrue(c.revealedCardIds().contains("fire-1"));
    }

    @Test
    void deckSourceUpdatesDeckCount() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.deck().get(g.playerOne()).add("fire-1");
        g.deck().get(g.playerOne()).add("fire-2");
        g.registerCard(energy("fire-2", "Fire Energy", "Fire"));
        AttackContext c = ctx(g, "Search",
            "Search your deck for 2 Fire Energy and attach them to this Pokemon.");
        c.setSelectedEnergyCardIds(List.of("fire-1", "fire-2"));
        strategy.apply(c);
        assertEquals(0, g.deck().get(g.playerOne()).size());
        assertEquals(0, g.deckCardsRemaining().get(g.playerOne()));
    }

    @Test
    void handSourceSkipsCardsNotInHand() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.registerCard(energy("fire-2", "Fire Energy", "Fire"));
        g.hand().get(g.playerOne()).add("fire-1");
        // fire-2 is not in hand
        AttackContext c = ctx(gameHelperSamePlayer(g), "FromHand",
            "Attach a Fire Energy from your hand to this Pokemon.");
        // selectedEnergyCardIds: includes both; only fire-1 is in hand
        c.setSelectedEnergyCardIds(List.of("fire-1", "fire-2"));
        strategy.apply(c);
        // Only fire-1 attached
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void noTargetWhenBenchIsEmptyAndBenchOnly() {
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.deck().get(g.playerOne()).add("fire-1");
        // No bench at all
        AttackContext c = ctx(g, "Bench",
            "Search your deck for a Fire Energy and attach it to 1 of your benched Pokemon.");
        c.setSelectedEnergyCardIds(List.of("fire-1"));
        strategy.apply(c);
        // No bench, no target -> energy_attach_no_target audit
        assertEquals(0, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void basicOnlyFilterAllowsBasicEnergy() {
        GameAggregate g = game();
        GameCard basic = new GameCard("fire-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0);
        g.registerCard(basic);
        g.deck().get(g.playerOne()).add("fire-1");
        AttackContext c = ctx(g, "BasicOnly",
            "Attach a basic energy from your deck to this Pokemon.");
        c.setSelectedEnergyCardIds(List.of("fire-1"));
        strategy.apply(c);
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "ACTIVE"));
    }

    @Test
    void nonSlotCardIdsInSelectedCardIdsAreUsedAsEnergyCandidates() {
        // The energy card is referenced via selectedCardIds (not selectedEnergyCardIds)
        // — this is the "in any way" case where targetSlots live in selectedCardIds too.
        GameAggregate g = game();
        g.registerCard(energy("fire-1", "Fire Energy", "Fire"));
        g.deck().get(g.playerOne()).add("fire-1");
        g.bench().get(g.playerOne()).add("b1");
        AttackContext c = ctx(g, "Geo",
            "Search your deck for a Fire Energy and attach it to one of your benched Pokemon.");
        c.setSelectedCardIds(List.of("BENCH_0", "fire-1"));
        strategy.apply(c);
        assertEquals(1, g.attachedEnergyCount(g.playerOne(), "BENCH_0"));
    }

    // ---- helpers ----

    private AttackContext ctx(GameAggregate g, String name, String text) {
        AttackContext c = new AttackContext(g);
        c.setAttackName(name);
        c.setAttackText(text);
        return c;
    }

    private GameAggregate game() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        GameAggregate g = new GameAggregate(p1);
        g.setPlayerTwo(p2);
        g.setCurrentTurnPlayer(p1);
        g.setFirstPlayer(p1);
        g.initializeZonesFor(p1, List.of());
        g.initializeZonesFor(p2, List.of());
        g.activePokemon().put(p1, "a1");
        g.activePokemon().put(p2, "a2");
        g.registerCard(new GameCard("a1", "Att", "Pokemon", Set.of("Basic"), 100, 10, 1));
        g.registerCard(new GameCard("a2", "Def", "Pokemon", Set.of("Basic"), 100, 10, 1));
        return g;
    }

    private GameAggregate gameHelperSamePlayer(GameAggregate g) {
        // Reuse the supplied game (helper for one test).
        return g;
    }

    private GameCard energy(String id, String name, String type) {
        return new GameCard(id, name, "Energy", Set.of("Basic"), 0, 0, 0, type, List.of(), 0);
    }
}
