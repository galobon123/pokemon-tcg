package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.GameState;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.strategy.CassiusTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.EvosodaTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.GreatBallTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.MaxReviveTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.PotionTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.RedCardTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.SuperPotionTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.TeamFlareGruntTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.EnergySearchTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.EscapeRopeTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.ItemEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.LysandreTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.ProfessorSycamoreTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.ProfessorsLetterTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.RollerSkatesTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.ShaunaTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.SupporterEffectStrategy;
import com.utn.pokemontcg.game.domain.strategy.SwitchTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.TiernoTrainerEffect;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectContext;
import com.utn.pokemontcg.game.domain.strategy.TrainerEffectRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainerEffectStrategyCoverageTest {

    @Test
    void searchEffectsMoveBasicEnergyFromDeckToHand() {
        GameAggregate game = gameWithDeck("energy-1", "basic-1", "energy-2");
        game.registerCard(energy("energy-1"));
        game.registerCard(basic("basic-1"));
        game.registerCard(energy("energy-2"));

        var searchResult = new EnergySearchTrainerEffect().apply(context(game, "Energy Search", null));
        var letterResult = new ProfessorsLetterTrainerEffect().apply(context(game, "Professor's Letter", null));

        assertEquals("BASIC_ENERGY_SEARCHED", searchResult.result());
        assertEquals(1, searchResult.payload().get("cardsFound"));
        List<Map<String, Object>> revealedCards = (List<Map<String, Object>>) searchResult.payload().get("revealedCards");
        assertEquals("energy-1", revealedCards.getFirst().get("cardId"));
        assertEquals("Fire Energy", revealedCards.getFirst().get("name"));
        assertEquals(1, letterResult.payload().get("cardsFound"));
        assertTrue(game.hand().get(game.playerOne()).containsAll(List.of("energy-1", "energy-2")));
        assertFalse(game.hand().get(game.playerOne()).contains("basic-1"));
        assertEquals(1, game.deckCardsRemaining().get(game.playerOne()));
    }

    @Test
    void supporterEffectsMoveCardsBetweenHandDeckAndDiscard() {
        GameAggregate sycamoreGame = gameWithDeck("d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8");
        sycamoreGame.hand().get(sycamoreGame.playerOne()).addAll(List.of("h1", "h2"));

        var sycamore = new ProfessorSycamoreTrainerEffect().apply(context(sycamoreGame, "Professor Sycamore", null));

        assertEquals(7, sycamore.payload().get("cardsDrawn"));
        assertEquals(List.of("h1", "h2"), sycamoreGame.discardPile().get(sycamoreGame.playerOne()));
        assertEquals(7, sycamoreGame.hand().get(sycamoreGame.playerOne()).size());

        GameAggregate shaunaGame = gameWithDeck("d1", "d2", "d3", "d4", "d5", "d6");
        shaunaGame.hand().get(shaunaGame.playerOne()).addAll(List.of("h1", "h2"));

        var shauna = new ShaunaTrainerEffect().apply(context(shaunaGame, "Shauna", null));

        assertEquals("HAND_SHUFFLED_AND_DRAWN", shauna.result());
        assertEquals(5, shauna.payload().get("cardsDrawn"));
        assertEquals(5, shaunaGame.hand().get(shaunaGame.playerOne()).size());
        assertEquals(3, shaunaGame.deckCardsRemaining().get(shaunaGame.playerOne()));
    }

    @Test
    void tiernoShufflesHandAndDrawsFive() {
        GameAggregate game = gameWithDeck("p1-d1", "p1-d2", "p1-d3", "p1-d4", "p1-d5");
        game.hand().get(game.playerOne()).add("p1-h1");

        var result = new TiernoTrainerEffect().apply(context(game, "Tierno", null));

        assertEquals("HAND_SHUFFLED_AND_DRAWN", result.result());
        assertEquals(5, result.payload().get("cardsDrawn"));
        assertEquals(5, game.hand().get(game.playerOne()).size());
    }

    @Test
    void switchEscapeRopeAndLysandreSwapActivePokemon() {
        GameAggregate game = gameWithDeck();
        game.activePokemon().put(game.playerOne(), "p1-active");
        game.activePokemon().put(game.playerTwo(), "p2-active");
        game.bench().get(game.playerOne()).addAll(List.of("p1-bench-0", "p1-bench-1"));
        game.bench().get(game.playerTwo()).addAll(List.of("p2-bench-0", "p2-bench-1"));

        var switchResult = new SwitchTrainerEffect().apply(context(game, "Switch", "BENCH_1"));

        assertEquals("ACTIVE_SWITCHED", switchResult.result());
        assertEquals("p1-bench-1", game.activePokemon().get(game.playerOne()));
        assertEquals("p1-active", game.bench().get(game.playerOne()).get(1));

        var escapeRopeResult = new EscapeRopeTrainerEffect().apply(context(game, "Escape Rope", "BENCH_1"));

        assertEquals("ESCAPE_ROPE_RESOLVED", escapeRopeResult.result());
        assertEquals(true, escapeRopeResult.payload().get("opponentSwapped"));
        assertEquals("p2-bench-0", game.activePokemon().get(game.playerTwo()));

        var lysandreResult = new LysandreTrainerEffect().apply(context(game, "Lysandre", "BENCH_0"));

        assertEquals("OPPONENT_ACTIVE_SWITCHED", lysandreResult.result());
        assertEquals("p2-active", game.activePokemon().get(game.playerTwo()));
    }

    @Test
    void registryAndNoopStrategiesResolveExpectedResults() {
        TrainerEffectRegistry registry = new TrainerEffectRegistry(List.of(new EnergySearchTrainerEffect()));

        assertTrue(registry.find(new GameCard("c1", " energy search ", "Trainer", Set.of("Item"), 0, 0, 0)).isPresent());
        assertTrue(registry.find(new GameCard("c2", null, "Trainer", Set.of("Item"), 0, 0, 0)).isEmpty());
        assertTrue(registry.find(null).isEmpty());
        assertEquals("ITEM", new ItemEffectStrategy().cardName());
        assertEquals("ITEM_HAS_NO_REGISTERED_EFFECT", new ItemEffectStrategy().apply(context(gameWithDeck(), "Item", null)).result());
        assertEquals("SUPPORTER", new SupporterEffectStrategy().cardName());
        assertEquals("SUPPORTER_HAS_NO_REGISTERED_EFFECT", new SupporterEffectStrategy().apply(context(gameWithDeck(), "Supporter", null)).result());
    }

    @Test
    void rollerSkatesAlwaysReportsCoinFlipPayload() {
        GameAggregate game = gameWithDeck("d1", "d2", "d3");

        var result = new RollerSkatesTrainerEffect().apply(context(game, "Roller Skates", null));

        assertEquals("COIN_FLIPPED", result.result());
        assertTrue(result.payload().containsKey("heads"));
        assertTrue(result.payload().containsKey("cardsDrawn"));
    }

    @Test
    void xy1TrainerEffectsResolveAutomatically() {
        GameAggregate game = gameWithDeck("basic-1", "energy-1");
        game.registerCard(basic("basic-1"));
        game.registerCard(energy("energy-1"));

        var greatBall = new GreatBallTrainerEffect().apply(context(game, "Great Ball", null));

        assertEquals("POKEMON_FOUND", greatBall.result());
        assertTrue(game.hand().get(game.playerOne()).contains("basic-1"));

        game.discardPile().get(game.playerOne()).add("basic-1");
        var maxRevive = new MaxReviveTrainerEffect().apply(context(game, "Max Revive", null));

        assertEquals("POKEMON_TO_TOP_OF_DECK", maxRevive.result());
        assertEquals("basic-1", game.deck().get(game.playerOne()).getFirst());

        game.addDamageCounters(game.playerOne(), "ACTIVE", 8);
        game.attachEnergy(game.playerOne(), "ACTIVE", new AttachedEnergy("energy-attached", "Fire"));
        var superPotion = new SuperPotionTrainerEffect().apply(context(game, "Super Potion", "ACTIVE"));

        assertEquals("DAMAGE_HEALED_AND_ENERGY_DISCARDED", superPotion.result());
        assertEquals(2, game.damageCountersAtSlot(game.playerOne(), "ACTIVE"));
    }

    @Test
    void redCardAndTeamFlareAffectOpponent() {
        GameAggregate game = gameWithDeck();
        game.deck().put(game.playerTwo(), new java.util.ArrayList<>(List.of("d1", "d2", "d3", "d4", "d5")));
        game.hand().get(game.playerTwo()).addAll(List.of("h1", "h2"));

        var redCard = new RedCardTrainerEffect().apply(context(game, "Red Card", null));

        assertEquals("OPPONENT_HAND_SHUFFLED_AND_DRAWN", redCard.result());
        assertEquals(4, game.hand().get(game.playerTwo()).size());

        game.attachEnergy(game.playerTwo(), "ACTIVE", new AttachedEnergy("opp-energy", "Water"));
        var flare = new TeamFlareGruntTrainerEffect().apply(context(game, "Team Flare Grunt", null));

        assertEquals("OPPONENT_ENERGY_DISCARDED", flare.result());
        assertEquals(0, game.attachedEnergyCount(game.playerTwo(), "ACTIVE"));
    }

    @Test
    void evosodaEvolvesPokemonFromDeck() {
        GameAggregate game = gameWithDeck("charmeleon-1");
        UUID p1 = game.playerOne();
        String slot = "ACTIVE";
        GameCard base = new GameCard("charmander-1", "Charmander", "Pokemon", Set.of("Basic"), 60, 10, 1, "Fire", List.of(), 0, null, List.of());
        GameCard evolution = new GameCard("charmeleon-1", "Charmeleon", "Pokemon", Set.of("Stage 1"), 80, 20, 1, "Fire", List.of(), 1, "Charmander", List.of());
        game.registerCard(base);
        game.registerCard(evolution);
        game.activePokemon().put(p1, "charmander-1");

        var result = new EvosodaTrainerEffect().apply(context(game, "Evosoda", slot));

        assertEquals("POKEMON_EVOLVED_FROM_DECK", result.result());
        assertEquals("charmeleon-1", game.cardAtSlot(p1, slot));
        assertEquals(0, game.deckCardsRemaining().get(p1));
    }

    @Test
    void evosodaReturnsNoEvolutionFoundWhenNotMatching() {
        GameAggregate game = gameWithDeck("pikachu-1");
        UUID p1 = game.playerOne();
        String slot = "ACTIVE";
        GameCard base = new GameCard("charmander-1", "Charmander", "Pokemon", Set.of("Basic"), 60, 10, 1, "Fire", List.of(), 0, null, List.of());
        GameCard nonMatch = new GameCard("pikachu-1", "Pikachu", "Pokemon", Set.of("Basic"), 50, 10, 1, "Lightning", List.of(), 0, "Pichu", List.of());
        game.registerCard(base);
        game.registerCard(nonMatch);
        game.activePokemon().put(p1, "charmander-1");

        var result = new EvosodaTrainerEffect().apply(context(game, "Evosoda", slot));

        assertEquals("NO_EVOLUTION_FOUND", result.result());
        assertEquals("charmander-1", game.cardAtSlot(p1, slot));
    }

    @Test
    void potionHealsUpToTwoDamageCounters() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        game.addDamageCounters(p1, "ACTIVE", 4);

        var result = new PotionTrainerEffect().apply(context(game, "Potion", "ACTIVE"));

        assertEquals("DAMAGE_HEALED", result.result());
        assertEquals(2, game.damageCountersAtSlot(p1, "ACTIVE"));
        assertEquals(2, result.payload().get("healedCounters"));
    }

    @Test
    void potionHealsOnlyUpToExistingDamage() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        game.addDamageCounters(p1, "ACTIVE", 1);

        var result = new PotionTrainerEffect().apply(context(game, "Potion", "ACTIVE"));

        assertEquals("DAMAGE_HEALED", result.result());
        assertEquals(0, game.damageCountersAtSlot(p1, "ACTIVE"));
        assertEquals(1, result.payload().get("healedCounters"));
    }

    @Test
    void potionDoesNotGoNegativeWithNoDamage() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();

        var result = new PotionTrainerEffect().apply(context(game, "Potion", "ACTIVE"));

        assertEquals("DAMAGE_HEALED", result.result());
        assertEquals(0, game.damageCountersAtSlot(p1, "ACTIVE"));
        assertEquals(0, result.payload().get("healedCounters"));
    }

    @Test
    void cassiusReturnsBenchPokemonAndAttachedCardsToDeck() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        GameCard basic = new GameCard("bench-mon", "BenchMon", "Pokemon", Set.of("Basic"), 60, 10, 1, "Fire", List.of(), 0, null, List.of());
        game.registerCard(basic);
        game.benchPokemon(p1, "bench-mon", "BENCH_0");

        var result = new CassiusTrainerEffect().apply(context(game, "Cassius", "BENCH_0"));

        assertEquals("POKEMON_RETURNED_TO_DECK", result.result());
        assertEquals("BENCH_0", result.payload().get("targetSlot"));
        assertTrue(game.deck().get(p1).contains("bench-mon"));
        assertEquals(1, game.deckCardsRemaining().get(p1));
    }

    @Test
    void cassiusOnActiveWithSingleBenchPromotesAutomatically() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        GameCard active = new GameCard("active-mon", "ActiveMon", "Pokemon", Set.of("Basic"), 60, 10, 1, "Fire", List.of(), 0, null, List.of());
        GameCard bench = new GameCard("the-bench", "BenchMon", "Pokemon", Set.of("Basic"), 60, 10, 1, "Water", List.of(), 0, null, List.of());
        game.registerCard(active);
        game.registerCard(bench);
        game.activePokemon().put(p1, "active-mon");
        game.benchPokemon(p1, "the-bench", "BENCH_0");

        var result = new CassiusTrainerEffect().apply(context(game, "Cassius", "ACTIVE"));

        assertEquals("POKEMON_RETURNED_TO_DECK", result.result());
        assertEquals("the-bench", game.activePokemon().get(p1));
        assertTrue(game.deck().get(p1).contains("active-mon"));
    }

    @Test
    void cassiusOnActiveWithMultipleBenchTriggersReplacement() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        GameCard active = new GameCard("active-mon", "ActiveMon", "Pokemon", Set.of("Basic"), 60, 10, 1, "Fire", List.of(), 0, null, List.of());
        GameCard bench0 = new GameCard("b0", "Bench0", "Pokemon", Set.of("Basic"), 60, 10, 1, "Water", List.of(), 0, null, List.of());
        GameCard bench1 = new GameCard("b1", "Bench1", "Pokemon", Set.of("Basic"), 60, 10, 1, "Grass", List.of(), 0, null, List.of());
        game.registerCard(active);
        game.registerCard(bench0);
        game.registerCard(bench1);
        game.activePokemon().put(p1, "active-mon");
        game.benchPokemon(p1, "b0", "BENCH_0");
        game.benchPokemon(p1, "b1", "BENCH_1");

        var result = new CassiusTrainerEffect().apply(context(game, "Cassius", "ACTIVE"));

        assertEquals("POKEMON_RETURNED_TO_DECK", result.result());
        assertEquals(GameState.WAITING_REPLACEMENT, game.gameState());
        assertEquals(p1, game.replacementPlayerId());
    }

    @Test
    void professorsLetterWithManualSelectionPicksSpecificCards() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        List<String> deck = game.deck().computeIfAbsent(p1, k -> new java.util.ArrayList<>());
        deck.add("fire-1:1");
        deck.add("water-1:1");
        GameCard fire = new GameCard("fire-1:1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0, null, List.of());
        GameCard water = new GameCard("water-1:1", "Water Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Water", List.of(), 0, null, List.of());
        game.registerCard(fire);
        game.registerCard(water);
        game.deckCardsRemaining().put(p1, 2);

        GameCard trainer = new GameCard("letter", "Professor's Letter", "Trainer", Set.of("Item"), 0, 0, 0);
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), null, null, null, List.of("fire-1"), List.of(), null, null, () -> true);
        var result = new ProfessorsLetterTrainerEffect().apply(ctx);

        assertEquals("BASIC_ENERGY_SEARCHED", result.result());
        assertEquals(1, result.payload().get("cardsFound"));
        assertTrue(game.hand().get(p1).contains("fire-1:1"));
        assertEquals(1, game.deckCardsRemaining().get(p1));
    }

    @Test
    void energySearchWithManualSelectionPicksOneCard() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        List<String> deck = game.deck().computeIfAbsent(p1, k -> new java.util.ArrayList<>());
        deck.add("lightning-1:1");
        deck.add("grass-1:1");
        GameCard lightning = new GameCard("lightning-1:1", "Lightning Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Lightning", List.of(), 0, null, List.of());
        GameCard grass = new GameCard("grass-1:1", "Grass Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Grass", List.of(), 0, null, List.of());
        game.registerCard(lightning);
        game.registerCard(grass);
        game.deckCardsRemaining().put(p1, 2);

        GameCard trainer = new GameCard("search", "Energy Search", "Trainer", Set.of("Item"), 0, 0, 0);
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), null, null, null, List.of("lightning-1"), List.of(), null, null, () -> true);
        var result = new EnergySearchTrainerEffect().apply(ctx);

        assertEquals("BASIC_ENERGY_SEARCHED", result.result());
        assertEquals(1, result.payload().get("cardsFound"));
        assertTrue(game.hand().get(p1).contains("lightning-1:1"));
    }

    @Test
    void professorSycamoreHandlesEmptyHandGracefully() {
        GameAggregate game = gameWithDeck("d1", "d2", "d3", "d4", "d5", "d6", "d7");
        game.hand().get(game.playerOne()).clear();

        var result = new ProfessorSycamoreTrainerEffect().apply(context(game, "Professor Sycamore", null));

        assertEquals(7, result.payload().get("cardsDrawn"));
        assertEquals(0, game.deckCardsRemaining().get(game.playerOne()));
    }

    @Test
    void cassiusReturnsNoPokemonInSlotForEmptySlot() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();

        var result = new CassiusTrainerEffect().apply(context(game, "Cassius", "BENCH_0"));

        assertEquals("NO_POKEMON_IN_SLOT", result.result());
    }

    @Test
    void greatBallWithManualSelectionFindsPokemon() {
        GameAggregate game = gameWithDeck("basic-1");
        UUID p1 = game.playerOne();
        GameCard pokemon = new GameCard("basic-1", "Basic Pokemon", "Pokemon", Set.of("Basic"), 60, 10, 1);
        game.registerCard(pokemon);

        GameCard trainer = new GameCard("gb", "Great Ball", "Trainer", Set.of("Item"), 0, 0, 0);
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), null, null, null, List.of("basic-1"), List.of(), null, null, () -> true);
        var result = new GreatBallTrainerEffect().apply(ctx);

        assertEquals("POKEMON_FOUND", result.result());
        assertEquals("basic-1", result.payload().get("cardId"));
        assertTrue(game.hand().get(p1).contains("basic-1"));
    }

    @Test
    void energySearchSkipsNonExistingCardId() {
        GameAggregate game = gameWithDeck();
        UUID p1 = game.playerOne();
        List<String> deck = game.deck().computeIfAbsent(p1, k -> new java.util.ArrayList<>());
        deck.add("fire-1:1");
        GameCard fire = new GameCard("fire-1:1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0, null, List.of());
        game.registerCard(fire);
        game.deckCardsRemaining().put(p1, 1);

        GameCard trainer = new GameCard("search", "Energy Search", "Trainer", Set.of("Item"), 0, 0, 0);
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), null, null, null, List.of("water-99"), List.of(), null, null, () -> true);
        var result = new EnergySearchTrainerEffect().apply(ctx);

        assertEquals("BASIC_ENERGY_SEARCHED", result.result());
        assertEquals(0, result.payload().get("cardsFound"));
    }

    @Test
    void evosodaWithManualSelectionFindsEvolutionInDeck() {
        GameAggregate game = gameWithDeck("other-1", "charmeleon-1");
        UUID p1 = game.playerOne();
        String slot = "ACTIVE";
        GameCard base = new GameCard("charmander-1", "Charmander", "Pokemon", Set.of("Basic"), 60, 10, 1, "Fire", List.of(), 0, null, List.of());
        GameCard other = new GameCard("other-1", "Other", "Pokemon", Set.of("Basic"), 50, 10, 1, "Water", List.of(), 0, null, List.of());
        GameCard evolution = new GameCard("charmeleon-1", "Charmeleon", "Pokemon", Set.of("Stage 1"), 80, 20, 1, "Fire", List.of(), 1, "Charmander", List.of());
        game.registerCard(base);
        game.registerCard(other);
        game.registerCard(evolution);
        game.activePokemon().put(p1, "charmander-1");

        GameCard trainer = new GameCard("evosoda", "Evosoda", "Trainer", Set.of("Item"), 0, 0, 0);
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), slot, null, null, List.of("charmeleon-1"), List.of(), null, null, () -> true);
        var result = new EvosodaTrainerEffect().apply(ctx);

        assertEquals("POKEMON_EVOLVED_FROM_DECK", result.result());
        assertEquals("charmeleon-1", game.cardAtSlot(p1, slot));
    }

    @Test
    void redCardHandlesEmptyOpponentHandGracefully() {
        GameAggregate game = gameWithDeck();
        game.hand().get(game.playerTwo()).clear();
        game.deck().put(game.playerTwo(), new java.util.ArrayList<>(List.of("d1", "d2", "d3", "d4")));

        var result = new RedCardTrainerEffect().apply(context(game, "Red Card", null));

        assertEquals("OPPONENT_HAND_SHUFFLED_AND_DRAWN", result.result());
        assertEquals(4, game.hand().get(game.playerTwo()).size());
    }

    @Test
    void teamFlareGruntDoesNothingWhenOpponentHasNoEnergy() {
        GameAggregate game = gameWithDeck();
        game.activePokemon().put(game.playerTwo(), "p2-active");

        var result = new TeamFlareGruntTrainerEffect().apply(context(game, "Team Flare Grunt", null));

        assertEquals("NO_ENERGY_ATTACHED", result.result());
    }

    @Test
    void greatBallManualSelectionRejectsNonPokemonCard() {
        GameAggregate game = gameWithDeck("energy-1", "basic-1");
        UUID p1 = game.playerOne();
        GameCard energy = new GameCard("energy-1", "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0, null, List.of());
        GameCard pokemon = new GameCard("basic-1", "Basic Pokemon", "Pokemon", Set.of("Basic"), 60, 10, 1);
        game.registerCard(energy);
        game.registerCard(pokemon);

        GameCard trainer = new GameCard("gb", "Great Ball", "Trainer", Set.of("Item"), 0, 0, 0);
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), null, null, null, List.of("energy-1"), List.of(), null, null, () -> true);
        var result = new GreatBallTrainerEffect().apply(ctx);

        assertEquals("NO_POKEMON_FOUND", result.result());
        assertFalse(game.hand().get(p1).contains("energy-1"));
    }

    @Test
    void maxReviveWhenDiscardIsEmptyReturnsNoneFound() {
        GameAggregate game = gameWithDeck("d1");

        var result = new MaxReviveTrainerEffect().apply(context(game, "Max Revive", null));

        assertEquals("NO_POKEMON_IN_DISCARD", result.result());
    }

    @Test
    void maxReviveManualSelectionPicksSpecificPokemonFromDiscard() {
        GameAggregate game = gameWithDeck("d1");
        UUID p1 = game.playerOne();
        GameCard pokemon = new GameCard("basic-discard", "Basic Pokemon", "Pokemon", Set.of("Basic"), 60, 10, 1);
        game.registerCard(pokemon);
        game.discardPile().get(p1).add("basic-discard");

        GameCard trainer = new GameCard("mr", "Max Revive", "Trainer", Set.of("Item"), 0, 0, 0);
        var ctx = new TrainerEffectContext(game, p1, trainer, trainer.id(), null, null, null, List.of("basic-discard"), List.of(), null, null, () -> true);
        var result = new MaxReviveTrainerEffect().apply(ctx);

        assertEquals("POKEMON_TO_TOP_OF_DECK", result.result());
        assertEquals("basic-discard", game.deck().get(p1).getFirst());
    }

    private TrainerEffectContext context(GameAggregate game, String cardName, String targetSlot) {
        GameCard trainer = new GameCard("trainer", cardName, "Trainer", Set.of("Item"), 0, 0, 0);
        return new TrainerEffectContext(game, game.playerOne(), trainer, trainer.id(), targetSlot, null);
    }

    private GameAggregate gameWithDeck(String... playerOneDeck) {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        GameAggregate game = new GameAggregate(p1);
        game.setPlayerTwo(p2);
        game.initializeZonesFor(p1, List.of(playerOneDeck));
        game.initializeZonesFor(p2, List.of());
        game.activePokemon().put(p1, "p1-active");
        game.activePokemon().put(p2, "p2-active");
        return game;
    }

    private GameCard energy(String id) {
        return new GameCard(id, "Fire Energy", "Energy", Set.of("Basic"), 0, 0, 0, "Fire", List.of(), 0);
    }

    private GameCard basic(String id) {
        return new GameCard(id, "Basic Pokemon", "Pokemon", Set.of("Basic"), 60, 10, 1);
    }
}
