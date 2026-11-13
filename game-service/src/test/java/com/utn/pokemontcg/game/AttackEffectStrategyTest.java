package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.chain.DamageCalculationStep;
import com.utn.pokemontcg.game.domain.chain.ModifierStep;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.StatusCondition;
import com.utn.pokemontcg.game.domain.strategy.BenchDamageAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.CoinFlipDamageAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.ConditionalDamageAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.AttackUtilityEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.CoinFlipEnergyAttachAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.CoinBonusDamageAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.EnergyAttachAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.EnergyDiscardAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.PersistentAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.SelfHealingAttackEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.SpecialConditionAttackEffectStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackEffectStrategyTest {

    @Test
    void appliesSpecialConditionFromAttackText() {
        GameAggregate game = game();
        AttackContext context = context(game, "Poison Powder", "The Defending Pokemon is now Poisoned.");

        new SpecialConditionAttackEffectStrategy().apply(context);

        assertTrue(game.statusByPlayer().get(game.playerTwo()).contains(StatusCondition.POISONED));
    }

    @Test
    void addsBonusDamageWhenCoinFlipIsHeads() {
        AttackContext context = context(game(), "Quick Toss", "Flip a coin. If heads, this attack does 20 more damage.");
        context.setDamage(30);
        context.setCoinFlip(() -> true);

        new CoinBonusDamageAttackEffectStrategy(() -> true).apply(context);

        assertEquals(20, context.preAttackBonus());
    }

    @Test
    void doesNotApplyCoinConditionalBonusWhenResultIsTails() {
        AttackContext context = context(game(), "Quick Toss", "Flip a coin. If heads, this attack does 20 more damage.");
        context.setDamage(30);
        context.setCoinFlipResults(List.of(false));

        new CoinBonusDamageAttackEffectStrategy(() -> true).apply(context);

        assertEquals(0, context.preAttackBonus());
    }

    @Test
    void doesNotApplyCoinConditionalSpecialConditionWhenResultIsTails() {
        GameAggregate game = game();
        AttackContext context = context(game, "Stun Spore", "Flip a coin. If heads, your opponent's Active Pokemon is now Paralyzed.");
        context.setCoinFlipResults(List.of(false));

        new SpecialConditionAttackEffectStrategy().apply(context);

        assertFalse(game.statusByPlayer().containsKey(game.playerTwo()));
    }

    @Test
    void healsTheAttackingActivePokemon() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerOne(), "ACTIVE", 4);
        AttackContext context = context(game, "Drain", "Heal 20 damage from this Pokemon.");

        new SelfHealingAttackEffectStrategy().apply(context);

        assertEquals(2, game.activeDamageCounters().get(game.playerOne()));
    }

    @Test
    void supportsRecoverAttackText() {
        GameAggregate game = game();
        AttackContext context = context(game, "Recover", "Discard a [W] Energy attached to this Pokémon. If you do, remove all damage counters from this Pokémon.");
        assertTrue(new SelfHealingAttackEffectStrategy().supports(context));
    }

    @Test
    void healsAllDamageFromRecoverText() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerOne(), "ACTIVE", 7);
        AttackContext context = context(game, "Recover", "Discard a [W] Energy attached to this Pokémon. If you do, remove all damage counters from this Pokémon.");

        new SelfHealingAttackEffectStrategy().apply(context);

        assertEquals(0, game.activeDamageCounters().get(game.playerOne()));
    }

    @Test
    void discardsEnergyFromAttacker() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("energy-1", "Fire"));
        AttackContext context = context(game, "Fire Blast", "Discard an Energy attached to this Pokemon.");

        new EnergyDiscardAttackEffectStrategy().apply(context);

        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
        assertTrue(game.discardPile().get(game.playerOne()).contains("energy-1"));
    }

    @Test
    void discardsAllCardsAttachedToAttacker() {
        GameAggregate game = game();
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("energy-1", "Fire"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("energy-2", "Fire"));
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("energy-3", "Fire"));
        game.equipTool(game.playerOne(), "ACTIVE", "tool-1");
        AttackContext context = context(game, "Overload", "Discard all cards attached to this Pokemon.");

        new EnergyDiscardAttackEffectStrategy().apply(context);

        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
        assertEquals(List.of("energy-1", "energy-2", "energy-3", "tool-1"), game.discardPile().get(game.playerOne()));
        assertEquals(null, game.toolAtSlot(game.playerOne(), "ACTIVE"));
    }

    @Test
    void switchesBothPlayersActivePokemonWhenAttackRequiresEachPlayerToSwitch() {
        GameAggregate game = game();
        game.bench().get(game.playerOne()).add("attacker-bench");
        game.bench().get(game.playerTwo()).add("defender-bench");
        AttackContext context = context(game, "Warp", "Each player switches his or her Active Pokemon with 1 of his or her Benched Pokemon.");

        new AttackUtilityEffectStrategy().apply(context);

        assertEquals("attacker-bench", game.activePokemon().get(game.playerOne()));
        assertEquals("defender-bench", game.activePokemon().get(game.playerTwo()));
        assertEquals("active-1", game.bench().get(game.playerOne()).get(0));
        assertEquals("active-2", game.bench().get(game.playerTwo()).get(0));
    }

    @Test
    void appliesDamageToOpponentBench() {
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).add("bench-basic");
        AttackContext context = context(game, "Snipe", "This attack does 20 damage to 1 of your opponent's Benched Pokemon.");

        new BenchDamageAttackEffectStrategy().apply(context);

        assertEquals(2, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
    }

    @Test
    void attachesEnergyFromDiscardToSelectedBenchedPokemon() {
        GameAggregate game = game();
        game.bench().get(game.playerOne()).add("bench-basic");
        game.registerCard(energy("grass-energy", "Grass Energy", "Grass"));
        game.discardPile().get(game.playerOne()).add("grass-energy");
        AttackContext context = context(game, "Energy Bloom", "Attach a Grass Energy card from your discard pile to 1 of your Benched Pokemon.");
        context.setTargetSlot("BENCH_0");

        new EnergyAttachAttackEffectStrategy().apply(context);

        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
        assertTrue(game.discardPile().get(game.playerOne()).isEmpty());
    }

    @Test
    void storesPersistentDamageReductionUntilOpponentNextTurnEnds() {
        GameAggregate game = game();
        game.setTurnNumber(4);
        AttackContext context = context(game, "Shell Shield", "During your opponent's next turn, any damage done to this Pokemon by attacks is reduced by 20.");

        new PersistentAttackEffectStrategy().apply(context);

        assertEquals(20, game.activeEffects().get(game.playerOne()).get(ModifierStep.DEFENDER_DAMAGE_REDUCTION));
        assertEquals(5, game.activeEffectExpiresAfterTurn().get(game.playerOne()).get(ModifierStep.DEFENDER_DAMAGE_REDUCTION));
    }

    @Test
    void clearsPersistentEffectsAfterTheirExpirationTurn() {
        GameAggregate game = game();
        game.addActiveEffectUntilTurn(game.playerOne(), ModifierStep.CANNOT_ATTACK, 1, 3);

        game.clearExpiredActiveEffects(3);

        assertTrue(game.activeEffects().get(game.playerOne()).isEmpty());
        assertTrue(game.activeEffectExpiresAfterTurn().get(game.playerOne()).isEmpty());
    }

    @Test
    void coinFlipDamageSetsDamageTimesHeads() {
        AttackContext context = context(game(), "Dual Blades", "Flip 2 coins. This attack does 30 damage times the number of heads.");
        context.setBaseDamage(30);
        context.setCoinFlip(sequence(true, false));

        new CoinFlipDamageAttackEffectStrategy().apply(context);

        assertEquals(30, context.baseDamage());
    }

    @Test
    void conditionalDamageUsesDamageCounters() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerTwo(), "ACTIVE", 3);
        AttackContext context = context(game, "Second Bite", "Does 10 more damage for each damage counter on your opponent's Active Pokemon.");
        context.setDamage(10);

        new ConditionalDamageAttackEffectStrategy().apply(context);

        assertEquals(30, context.preAttackBonus());
    }

    @Test
    void utilityAttackDrawsCards() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).addAll(List.of("d1", "d2"));
        AttackContext context = context(game, "Double Draw", "Draw 2 cards.");

        new AttackUtilityEffectStrategy().apply(context);

        assertEquals(2, game.hand().get(game.playerOne()).size());
    }

    @Test
    void energyGlideAttachesToActiveBeforeSwitchingWithSelectedBench() {
        GameAggregate game = game();
        game.bench().get(game.playerOne()).add("bench-basic");
        game.registerCard(energy("lightning-energy", "Lightning Energy", "Lightning"));
        game.deck().get(game.playerOne()).add("lightning-energy");
        AttackContext context = context(game, "Energy Glide", "Search your deck for a Lightning Energy card and attach it to this Pokemon. Shuffle your deck afterward. If you attached Energy in this way, switch this Pokemon with 1 of your Benched Pokemon.");
        context.setTargetSlot("BENCH_0");
        context.setSelectedCardIds(List.of("lightning-energy"));

        new EnergyAttachAttackEffectStrategy().apply(context);
        new AttackUtilityEffectStrategy().apply(context);

        assertEquals(List.of("lightning-energy"), context.revealedCardIds());
        assertEquals("bench-basic", game.activePokemon().get(game.playerOne()));
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
    }

    @Test
    void madMountainDiscardsOneOpponentDeckCardPerAttackerDamageCounterOnlyWhenBothCoinsAreHeads() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerOne(), "ACTIVE", 3);
        game.deck().get(game.playerTwo()).addAll(List.of("d1", "d2", "d3", "d4"));
        AttackContext context = context(game, "Mad Mountain", "Flip 2 coins. If both of them are heads, discard the top card of your opponent's deck for each damage counter on this Pokemon.");
        context.setCoinFlip(sequence(true, true));

        new AttackUtilityEffectStrategy().apply(context);

        assertEquals(List.of("d1", "d2", "d3"), game.discardPile().get(game.playerTwo()));
        assertEquals(List.of(true, true), context.coinFlipResults());
    }

    @Test
    void madMountainDoesNothingUnlessBothCoinsAreHeads() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerOne(), "ACTIVE", 3);
        game.deck().get(game.playerTwo()).addAll(List.of("d1", "d2", "d3"));
        AttackContext context = context(game, "Mad Mountain", "Flip 2 coins. If both of them are heads, discard the top card of your opponent's deck for each damage counter on this Pokemon.");
        context.setCoinFlip(sequence(true, false));

        new AttackUtilityEffectStrategy().apply(context);

        assertTrue(game.discardPile().get(game.playerTwo()).isEmpty());
        assertEquals(List.of(true, false), context.coinFlipResults());
    }

    @Test
    void utilitySupportsDrawText() {
        AttackUtilityEffectStrategy strat = new AttackUtilityEffectStrategy();
        assertTrue(strat.supports(context(game(), "Draw", "Draw a card.")));
        assertTrue(strat.supports(context(game(), "Search", "Search your deck for a Basic Pokemon and put it into your hand.")));
        assertFalse(strat.supports(context(game(), "Tackle", "Deal 30 damage.")));
    }

    @Test
    void utilityDrawsOneCardAndFourCards() {
        GameAggregate game = game();
        game.deck().get(game.playerOne()).add("d1");
        game.deck().get(game.playerTwo()).addAll(List.of("d1", "d2", "d3", "d4"));
        AttackContext ctx = context(game, "Quick Draw", "Draw a card.");
        new AttackUtilityEffectStrategy().apply(ctx);
        assertEquals(1, game.hand().get(game.playerOne()).size());

        AttackContext ctx4 = context(game, "Nuzzle", "Draw 4 cards.");
        new AttackUtilityEffectStrategy().apply(ctx4);
        assertEquals(4, game.hand().get(game.playerTwo()).size());
    }

    @Test
    void utilitySelfDamageDoes10And30Damage() {
        GameAggregate game = game();
        AttackContext ctx10 = context(game, "Struggle", "This Pokemon does 10 damage to itself.");
        new AttackUtilityEffectStrategy().apply(ctx10);
        assertEquals(1, game.damageCountersAtSlot(game.playerOne(), "ACTIVE"));

        AttackContext ctx30 = context(game, "Wild Charge", "This Pokemon does 30 damage to itself.");
        new AttackUtilityEffectStrategy().apply(ctx30);
        assertEquals(4, game.damageCountersAtSlot(game.playerOne(), "ACTIVE"));
    }

    @Test
    void utilitySwitchesWhenOnlyAttackerHasBench() {
        GameAggregate game = game();
        game.bench().get(game.playerOne()).add("bench-basic");
        AttackContext ctx = context(game, "Warp", "Switch this Pokemon with 1 of your Benched Pokemon.");
        new AttackUtilityEffectStrategy().apply(ctx);
        assertEquals("bench-basic", game.activePokemon().get(game.playerOne()));
    }

    @Test
    void utilityDiscardsTopOpponentCardsOneAndRandom() {
        GameAggregate game = game();
        game.deck().get(game.playerTwo()).addAll(List.of("d1", "d2", "d3"));
        AttackContext ctx = context(game, "Mountain", "Discard the top card of your opponent's deck.");
        new AttackUtilityEffectStrategy().apply(ctx);
        assertEquals(2, game.deck().get(game.playerTwo()).size());

        game.addDamageCounters(game.playerOne(), "ACTIVE", 1);
        AttackContext ctx2 = context(game, "Mad", "Discard the top card of your opponent's deck for each damage counter on this Pokemon.");
        new AttackUtilityEffectStrategy().apply(ctx2);
        assertEquals(1, game.deck().get(game.playerTwo()).size());
    }

    @Test
    void utilitySwitchesOpponentWhenRequired() {
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).add("defender-bench");
        AttackContext ctx = context(game, "Escape", "Switch 1 of your opponent's Benched Pokemon with his or her Active Pokemon.");
        new AttackUtilityEffectStrategy().apply(ctx);
        assertEquals(game.playerTwo(), game.pendingForcedSwitchPlayerId());
    }

    @Test
    void utilityMoveEnergyFromActiveToBench() {
        GameAggregate game = game();
        game.bench().get(game.playerOne()).add("bench-basic");
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("e-1", "Fire"));
        AttackContext ctx = context(game, "Energy Shift", "Move an energy from this Pokemon to another of your Pokemon.");
        ctx.setTargetSlot("BENCH_0");
        new AttackUtilityEffectStrategy().apply(ctx);
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
    }

    @Test
    void utilityOpponentHandCardShuffleDiscardText() {
        GameAggregate game = game();
        game.hand().get(game.playerTwo()).addAll(List.of("h1", "h2"));
        game.deck().get(game.playerTwo()).addAll(List.of("d1", "d2", "d3"));
        AttackContext ctx = context(game, "Red Hand", "Choose a card from your opponent's hand and put it on top of their deck.");
        ctx.setSelectedCardIds(List.of("h1"));
        new AttackUtilityEffectStrategy().apply(ctx);
        assertFalse(game.hand().get(game.playerTwo()).contains("h1"));
    }

    @Test
    void utilitySearchesDeckForSupporter() {
        GameAggregate game = game();
        game.registerCard(new GameCard("supp-1", "Shauna", "Trainer", java.util.Set.of("Supporter"), 0, 0, 0));
        game.deck().get(game.playerOne()).add("supp-1");
        AttackContext ctx = context(game, "Search", "Search your deck for a Supporter card and put it into your hand.");
        new AttackUtilityEffectStrategy().apply(ctx);
        assertEquals(1, game.hand().get(game.playerOne()).size());
        assertEquals(0, game.deck().get(game.playerOne()).size());
    }

    @Test
    void utilitySearchesDeckForPokemon() {
        GameAggregate game = game();
        game.registerCard(new GameCard("poke-1", "Charmander", "Pokemon", java.util.Set.of("Basic"), 50, 10, 1));
        game.deck().get(game.playerOne()).add("poke-1");
        AttackContext ctx = context(game, "Search", "Search your deck for a Pokemon and put it into your hand.");
        new AttackUtilityEffectStrategy().apply(ctx);
        assertEquals(1, game.hand().get(game.playerOne()).size());
    }

    @Test
    void utilityDiscardsTopCardAndAttachesIfEnergy() {
        GameAggregate game = game();
        GameCard energy = new GameCard("fire-1", "Fire Energy", "Energy", java.util.Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0);
        game.registerCard(energy);
        game.deck().get(game.playerOne()).add("fire-1");
        AttackContext ctx = context(game, "Energy Burn", "Discard the top card of your deck. If you discarded an Energy card, attach it to this Pokemon.");
        new AttackUtilityEffectStrategy().apply(ctx);
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
    }

    @Test
    void utilityDiscardsTopCardAndSkipsIfNotEnergy() {
        GameAggregate game = game();
        game.registerCard(new GameCard("poke-1", "Charmander", "Pokemon", java.util.Set.of("Basic"), 50, 10, 1));
        game.deck().get(game.playerOne()).add("poke-1");
        AttackContext ctx = context(game, "Toss", "Discard the top card of your deck. If you discarded an Energy card, attach it to this Pokemon.");
        new AttackUtilityEffectStrategy().apply(ctx);
        assertEquals(0, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
    }

    @Test
    void utilityPutsDiscardCardOnTopOfDeck() {
        GameAggregate game = game();
        game.discardPile().get(game.playerOne()).add("d1");
        AttackContext ctx = context(game, "Recycle", "Put a card from your discard pile on top of your deck.");
        new AttackUtilityEffectStrategy().apply(ctx);
        assertEquals("d1", game.deck().get(game.playerOne()).get(0));
    }

    @Test
    void utilityReturnsItemCardsFromDiscardToHand() {
        GameAggregate game = game();
        game.registerCard(new GameCard("item-1", "Potion", "Trainer", java.util.Set.of("Item"), 0, 0, 0));
        game.registerCard(new GameCard("item-2", "Switch", "Trainer", java.util.Set.of("Item"), 0, 0, 0));
        game.discardPile().get(game.playerOne()).addAll(List.of("item-1", "item-2"));
        AttackContext ctx = context(game, "Item Retrieval", "Put 2 item cards from your discard pile into your hand.");
        new AttackUtilityEffectStrategy().apply(ctx);
        assertEquals(2, game.hand().get(game.playerOne()).size());
    }

    @Test
    void utilityDamagesEachOpponentPokemon() {
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).add("bench-poke");
        AttackContext ctx = context(game, "Splash", "Put 2 damage counters each of your opponent's Pokemon.");
        new AttackUtilityEffectStrategy().apply(ctx);
        assertEquals(2, game.damageCountersAtSlot(game.playerTwo(), "ACTIVE"));
        assertEquals(2, game.damageCountersAtSlot(game.playerTwo(), "BENCH_0"));
    }

    @Test
    void utilityShufflesOpponentHandCardIntoDeckRandom() {
        GameAggregate game = game();
        game.hand().get(game.playerTwo()).add("h1");
        game.deck().get(game.playerTwo()).add("d1");
        AttackContext ctx = context(game, "Hand Snatch", "Take a random card from your opponent's hand and put it into your opponent's deck.");
        new AttackUtilityEffectStrategy().apply(ctx);
        assertTrue(game.hand().get(game.playerTwo()).isEmpty());
        assertEquals(2, game.deckCardsRemaining().get(game.playerTwo()));
    }

    @Test
    void selfNamedAttackBlockBlocksOnlySelectedAttackIndex() {
        GameAggregate game = game();
        AttackContext context = context(game, "X Blast", "This Pokemon can't use X Blast during your next turn.");
        context.setAttackIndex(1);

        new PersistentAttackEffectStrategy().apply(context);

        assertTrue(game.activeEffects().get(game.playerOne()).containsKey(ModifierStep.BLOCKED_ATTACK_PREFIX + 1));
        assertFalse(game.activeEffects().get(game.playerOne()).containsKey(ModifierStep.CANNOT_ATTACK));
    }

    @Test
    void allHeadsPersistentEffectRequiresEveryCoinToBeHeads() {
        GameAggregate game = game();
        AttackContext context = context(game, "Flash Needle", "Flip 3 coins. This attack does 40 damage times the number of heads. If all of them are heads, prevent all effects of attacks, including damage, done to this Pokemon during your opponent's next turn.");
        context.setCoinFlipResults(List.of(true, false, true));

        new PersistentAttackEffectStrategy().apply(context);

        assertFalse(game.activeEffects().getOrDefault(game.playerOne(), java.util.Map.of()).containsKey(ModifierStep.DEFENDER_DAMAGE_REDUCTION));
    }

    @Test
    void persistentBonusScopedToAttackNameDoesNotApplyToDifferentAttack() {
        GameAggregate game = game();
        AttackContext context = context(game, "Metal Wallop", "During your next turn, this Pokemon's Metal Wallop attack does 40 more damage.");
        context.setAttackIndex(0);
        game.setTurnNumber(1);

        new PersistentAttackEffectStrategy().apply(context);

        String scopedKey = ModifierStep.ATTACKER_DAMAGE_BONUS + ":Metal Wallop";
        assertTrue(game.activeEffects().get(game.playerOne()).containsKey(scopedKey));
        assertFalse(game.activeEffects().get(game.playerOne()).containsKey(ModifierStep.ATTACKER_DAMAGE_BONUS));
        assertEquals(40, game.activeEffects().get(game.playerOne()).get(scopedKey));
    }

    @Test
    void persistentBonusUnscopedWhenTextDoesNotMentionAttackName() {
        GameAggregate game = game();
        AttackContext context = context(game, "Generic Blast", "During your next turn, this Pokemon's attacks do 20 more damage.");
        context.setAttackIndex(0);
        game.setTurnNumber(1);
        game.setTurnNumber(1);

        new PersistentAttackEffectStrategy().apply(context);

        assertTrue(game.activeEffects().get(game.playerOne()).containsKey(ModifierStep.ATTACKER_DAMAGE_BONUS));
        assertEquals(20, game.activeEffects().get(game.playerOne()).get(ModifierStep.ATTACKER_DAMAGE_BONUS));
    }

    @Test
    void hardenPreventsAttackDamageWhenFinalDamageIsSixtyOrLess() {
        GameAggregate game = game();
        AttackContext harden = context(game, "Harden", "During your opponent's next turn, if this Pokemon would be damaged by an attack, prevent that attack's damage done to this Pokemon if that damage is 60 or less.");
        game.setTurnNumber(1);

        new PersistentAttackEffectStrategy().apply(harden);

        assertEquals(60, game.activeEffectValue(game.playerOne(), ModifierStep.DEFENDER_PREVENT_DAMAGE_MAX));

        game.setCurrentTurnPlayer(game.playerTwo());
        AttackContext incoming = context(game, "Strike", "");
        incoming.setBaseDamage(60);

        new DamageCalculationStep().execute(incoming);

        assertEquals(0, incoming.damage());
        assertFalse(game.hasActiveEffect(game.playerOne(), ModifierStep.DEFENDER_PREVENT_DAMAGE_MAX));
    }

    @Test
    void hardenDoesNotPreventAttackDamageAboveSixty() {
        GameAggregate game = game();
        AttackContext harden = context(game, "Harden", "During your opponent's next turn, if this Pokemon would be damaged by an attack, prevent that attack's damage done to this Pokemon if that damage is 60 or less.");
        game.setTurnNumber(1);

        new PersistentAttackEffectStrategy().apply(harden);

        game.setCurrentTurnPlayer(game.playerTwo());
        AttackContext incoming = context(game, "Strike", "");
        incoming.setBaseDamage(70);

        new DamageCalculationStep().execute(incoming);

        assertEquals(70, incoming.damage());
    }

    @Test
    void appliesConditionalDamageWhenTextHasYouMayDoMoreDamage() {
        GameAggregate game = game();
        AttackContext context = context(game, "Hypno Headbutt", "You may do 30 more damage. If you do, this Pokemon is now Asleep.");
        context.setDamage(50);
        context.setAcceptedOptionalEffect(true);

        new ConditionalDamageAttackEffectStrategy().apply(context);

        assertEquals(30, context.preAttackBonus());
    }

    @Test
    void appliesSleepToAttackerWhenTextSaysThisPokemonIsNowAsleep() {
        GameAggregate game = game();
        AttackContext context = context(game, "Hypno Headbutt", "You may do 30 more damage. If you do, this Pokemon is now Asleep.");

        new SpecialConditionAttackEffectStrategy().apply(context);

        assertTrue(game.statusByPlayer().get(game.playerOne()).contains(StatusCondition.ASLEEP));
        assertFalse(game.statusByPlayer().containsKey(game.playerTwo()));
    }

    @Test
    void discardsToolInsteadOfEnergyWhenTextMentionsPokemonTool() {
        GameAggregate game = game();
        game.cardCatalog().put("tool-1", new GameCard("tool-1", "Rocky Helmet", "Tool", java.util.Set.of("Pokemon Tool"), 0, 0, 0, "", List.of(), 0));
        game.toolsBySlot().computeIfAbsent(game.playerTwo(), ignored -> new java.util.HashMap<>()).put("ACTIVE", "tool-1");
        AttackContext context = context(game, "Joust", "Discard a Pokemon Tool card attached to your opponent's Active Pokemon.");

        assertTrue(new EnergyDiscardAttackEffectStrategy().supports(context));

        new EnergyDiscardAttackEffectStrategy().apply(context);

        assertNull(game.toolAtSlot(game.playerTwo(), "ACTIVE"));
        assertTrue(game.discardPile().get(game.playerTwo()).contains("tool-1"));
    }

    @Test
    void selfHealingHealsAttackerForDamageDealt() {
        GameAggregate game = game();
        game.addDamageCounters(game.playerOne(), "ACTIVE", 5);
        AttackContext ctx = context(game, "Absorb", "Heal 10 damage from this Pokemon.");
        new SelfHealingAttackEffectStrategy().apply(ctx);
        assertEquals(4, game.damageCountersAtSlot(game.playerOne(), "ACTIVE"));
    }

    @Test
    void selfHealingDoesNotHealBelowZero() {
        GameAggregate game = game();
        AttackContext ctx = context(game, "Absorb", "Heal 10 damage from this Pokemon.");
        new SelfHealingAttackEffectStrategy().apply(ctx);
        assertEquals(0, game.damageCountersAtSlot(game.playerOne(), "ACTIVE"));
    }

    @Test
    void coinFlipEnergyAttachSupportsSpecificText() {
        assertTrue(new CoinFlipEnergyAttachAttackEffectStrategy().supports(
            context(game(), "Energy Shower", "Flip a coin. For each heads, attach a Fire Energy card from your discard pile to this Pokemon.")));
        assertFalse(new CoinFlipEnergyAttachAttackEffectStrategy().supports(
            context(game(), "Tackle", "Deal 30 damage.")));
    }

    @Test
    void coinFlipEnergyAttachAttachesToBenchForHeads() {
        GameAggregate game = game();
        GameCard fire = new GameCard("fire-1", "Fire Energy", "Energy", java.util.Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0);
        game.registerCard(fire);
        game.discardPile().get(game.playerOne()).add("fire-1");
        game.bench().get(game.playerOne()).add("bench-basic");
        AttackContext ctx = context(game, "Energy Shower", "Flip 2 coins. For each heads, attach a Fire Energy card from your discard pile to this Pokemon.");
        ctx.setCoinFlip(() -> true);
        ctx.setTargetSlot("BENCH_0");
        new CoinFlipEnergyAttachAttackEffectStrategy().apply(ctx);
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "BENCH_0"));
    }

    @Test
    void energyAttachFromDiscardWorksForActive() {
        GameAggregate game = game();
        game.registerCard(new GameCard("fire-1", "Fire Energy", "Energy", java.util.Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0));
        game.discardPile().get(game.playerOne()).add("fire-1");
        AttackContext ctx = context(game, "Energy Absorb", "Attach a Fire Energy card from your discard pile to this Pokemon.");
        new EnergyAttachAttackEffectStrategy().apply(ctx);
        assertEquals(1, game.attachedEnergyCount(game.playerOne(), "ACTIVE"));
    }

    @Test
    void utilityForEachTailsDiscardsOpponentHand() {
        GameAggregate game = game();
        game.hand().get(game.playerTwo()).addAll(List.of("h1", "h2", "h3"));
        AttackContext ctx = context(game, "Nightmare", "Flip 4 coins. For each tails, discards a card from your opponent's hand.");
        ctx.setCoinFlip(sequence(false, true, false, true));
        new AttackUtilityEffectStrategy().apply(ctx);
        assertEquals(1, game.hand().get(game.playerTwo()).size());
    }

    @Test
    void utilityOpponentSwitchesTriggersPendingSwitch() {
        GameAggregate game = game();
        game.bench().get(game.playerTwo()).add("bench-poke");
        AttackContext ctx = context(game, "Escape", "Your opponent switches his or her Active Pokemon with 1 of his or her Benched Pokemon.");
        new AttackUtilityEffectStrategy().apply(ctx);
        assertEquals(game.playerTwo(), game.pendingForcedSwitchPlayerId());
    }

    @Test
    void utilityDiscardTopOwnDeckWithNoAttachIfNotEnergy() {
        GameAggregate game = game();
        game.registerCard(new GameCard("poke-1", "Pikachu", "Pokemon", java.util.Set.of("Basic"), 50, 10, 1));
        game.deck().get(game.playerOne()).add("poke-1");
        AttackContext ctx = context(game, "Mill", "Discard the top card of your deck.");
        new AttackUtilityEffectStrategy().apply(ctx);
        assertEquals(1, game.discardPile().get(game.playerOne()).size());
        assertEquals(0, game.deck().get(game.playerOne()).size());
    }

    private java.util.function.BooleanSupplier sequence(boolean... values) {
        java.util.ArrayDeque<Boolean> queue = new java.util.ArrayDeque<>();
        for (boolean value : values) queue.add(value);
        return queue::removeFirst;
    }

    private AttackContext context(GameAggregate game, String attackName, String attackText) {
        AttackContext context = new AttackContext(game);
        context.setAttackName(attackName);
        context.setAttackText(attackText);
        return context;
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

    private GameCard energy(String id, String name, String type) {
        return new GameCard(id, name, "Energy", java.util.Set.of("Basic"), 0, 0, 0, type, List.of(), 0);
    }
}
