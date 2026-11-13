package com.utn.pokemontcg.game.application.service;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class VictoryService {

    private final SetupEngineService setupEngineService;

    public VictoryService() {
        this(null);
    }

    @Autowired
    public VictoryService(SetupEngineService setupEngineService) {
        this.setupEngineService = setupEngineService;
    }

    public KnockoutResult applyKnockoutAndPrizes(GameAggregate game, UUID attacker, UUID defender) {
        if (attacker == null || defender == null) return KnockoutResult.none(attacker, defender);

        if (game.gameState() == GameState.WAITING_REPLACEMENT) return KnockoutResult.none(attacker, defender);
        if (game.activeHp().getOrDefault(defender, 1) > 0) return KnockoutResult.none(attacker, defender);
        String knockedOutCard = game.activePokemon().get(defender);
        if (knockedOutCard == null) return KnockoutResult.none(attacker, defender);

        int prizeLoss = game.activePokemonEx().getOrDefault(defender, false) ? 2 : 1;

        discardKnockedOutPokemon(game, defender, "ACTIVE");
        int prizesTaken = takePrizeCards(game, attacker, prizeLoss);

        String promoted = promoteFromBenchIfPossible(game, defender);
        return new KnockoutResult(
            true,
            attacker,
            defender,
            knockedOutCard,
            promoted,
            prizesTaken,
            game.prizeCardsRemaining().getOrDefault(attacker, 0),
            !game.hasPokemonInPlay(defender)
        );
    }

    public List<KnockoutResult> applyBenchKnockoutsAndPrizes(GameAggregate game, UUID attacker, UUID defender) {
        if (attacker == null || defender == null) return List.of();
        List<KnockoutResult> results = new ArrayList<>();
        List<String> bench = game.bench().getOrDefault(defender, List.of());
        for (int benchIndex = 0; benchIndex < 5; benchIndex++) {
            String slot = "BENCH_" + benchIndex;
            String cardId = benchIndex < bench.size() ? bench.get(benchIndex) : null;
            if (cardId == null) continue;
            var card = game.cardCatalog().get(cardId);
            int hp = card == null ? 0 : card.hp();
            int damageCounters = game.damageCountersAtSlot(defender, slot);
            if (hp <= 0 || damageCounters * 10 < hp) continue;

            int prizeLoss = card != null && card.isPokemonEx() ? 2 : 1;
            discardKnockedOutPokemon(game, defender, slot);
            int prizesTaken = takePrizeCards(game, attacker, prizeLoss);
            results.add(new KnockoutResult(
                true,
                attacker,
                defender,
                cardId,
                null,
                prizesTaken,
                game.prizeCardsRemaining().getOrDefault(attacker, 0),
                !game.hasPokemonInPlay(defender)
            ));
        }
        return results;
    }

    public UUID checkWinner(GameAggregate game) {
        UUID p1 = game.playerOne();
        UUID p2 = game.playerTwo();

        if (p1 == null || p2 == null) return null;

        boolean p1PrizeWin = game.prizeCardsRemaining().getOrDefault(p1, 6) <= 0;
        boolean p2PrizeWin = game.prizeCardsRemaining().getOrDefault(p2, 6) <= 0;
        boolean p1NoPokemon = !game.hasPokemonInPlay(p1);
        boolean p2NoPokemon = !game.hasPokemonInPlay(p2);
        boolean p1DeckOut = game.deck().getOrDefault(p1, List.of()).isEmpty();
        boolean p2DeckOut = game.deck().getOrDefault(p2, List.of()).isEmpty();

        // Sudden death if both players meet win conditions simultaneously
        if ((p1PrizeWin && p2PrizeWin)
            || (p1NoPokemon && p2NoPokemon)
            || (p1DeckOut && p2DeckOut)) {
            startSuddenDeath(game);
            return null;
        }

        if (p1PrizeWin || p2NoPokemon || p2DeckOut) return p1;
        if (p2PrizeWin || p1NoPokemon || p1DeckOut) return p2;

        return null;
    }

    public void closeGameIfNeeded(GameAggregate game) {
        UUID winner = checkWinner(game);
        if (winner != null) {
            game.setWinner(winner);
            game.setGameState(GameState.FINISHED);
        }
    }

    private void startSuddenDeath(GameAggregate game) {
        if (setupEngineService != null) {
            setupEngineService.initSuddenDeath(game);
            return;
        }
        game.setGameState(GameState.SETUP);
        game.setSuddenDeath(true);
        game.prizeCardsRemaining().put(game.playerOne(), 1);
        game.prizeCardsRemaining().put(game.playerTwo(), 1);
        game.setWinner(null);
    }

    private void discardKnockedOutActive(GameAggregate game, UUID player) {
        discardKnockedOutPokemon(game, player, "ACTIVE");
    }

    private void discardKnockedOutPokemon(GameAggregate game, UUID player, String targetSlot) {
        String slot = game.normalizeSlot(targetSlot);
        String knockedOut = game.cardAtSlot(player, slot);
        if (knockedOut != null) {
            game.discardPile().computeIfAbsent(player, ignored -> new ArrayList<>()).add(knockedOut);
        }
        game.discardAllAttachedEnergies(player, slot)
            .forEach(energy -> game.discardPile().computeIfAbsent(player, ignored -> new ArrayList<>()).add(energy.cardId()));
        String tool = game.toolAtSlot(player, slot);
        if (tool != null) {
            game.discardPile().computeIfAbsent(player, ignored -> new ArrayList<>()).add(tool);
            game.clearTool(player, slot);
        }
        game.damageCountersBySlot().computeIfAbsent(player, ignored -> new java.util.HashMap<>()).put(slot, 0);
        game.removeCardAtSlot(player, slot);
        if ("ACTIVE".equals(slot)) {
            game.refreshActiveAttachedEnergy(player);
            game.activeDamageCounters().put(player, 0);
            game.activeHp().put(player, 0);
            game.activePokemonEx().put(player, false);
            game.statusByPlayer().remove(player);
        }
    }

    private int takePrizeCards(GameAggregate game, UUID player, int amount) {
        List<String> prizes = game.prizeCards().getOrDefault(player, List.of());
        List<String> hand = game.hand().computeIfAbsent(player, ignored -> new java.util.ArrayList<>());
        int taken = 0;
        while (taken < amount && !prizes.isEmpty()) {
            hand.add(prizes.remove(0));
            taken++;
        }
        game.prizeCardsRemaining().put(player, prizes.size());
        return taken;
    }

    private String promoteFromBenchIfPossible(GameAggregate game, UUID player) {
        List<String> bench = game.bench().getOrDefault(player, List.of());
        if (game.benchOccupiedCount(player) == 0) {
            return null;
        }
        if (game.benchOccupiedCount(player) > 1) {
            game.setGameState(GameState.WAITING_REPLACEMENT);
            game.setReplacementPlayerId(player);
            return null;
        }
        int firstOccupied = -1;
        for (int i = 0; i < bench.size(); i++) {
            if (bench.get(i) != null) {
                firstOccupied = i;
                break;
            }
        }
        if (firstOccupied < 0) return null;
        String promoted = game.promoteBenchToActive(player, firstOccupied);
        game.refreshActivePokemonDerivedState(player);
        var card = game.cardCatalog().get(promoted);
        game.activePokemonEx().put(player, card != null && card.isPokemonEx());
        game.setReplacementPlayerId(null);
        return promoted;
    }

    public record KnockoutResult(
        boolean knockedOut,
        UUID attacker,
        UUID defender,
        String knockedOutCard,
        String promotedCard,
        int prizesTaken,
        int prizeCardsRemaining,
        boolean defenderHasNoPokemon
    ) {
        static KnockoutResult none(UUID attacker, UUID defender) {
            return new KnockoutResult(false, attacker, defender, null, null, 0, 0, false);
        }
    }
}
