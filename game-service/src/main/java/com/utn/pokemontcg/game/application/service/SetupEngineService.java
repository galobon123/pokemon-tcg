package com.utn.pokemontcg.game.application.service;

import com.utn.pokemontcg.game.domain.model.GameAggregate;
import com.utn.pokemontcg.game.domain.model.GameCard;
import com.utn.pokemontcg.game.domain.model.PlayerSetupState;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SetupEngineService {

    private final SecureRandom random = new SecureRandom();

    public PlayerSetupState preparePlayer(int deckSize, int basicCount, int opponentMulligans) {
        if (deckSize < 7) {
            throw new IllegalArgumentException("El mazo debe tener al menos 7 cartas para iniciar setup");
        }
        if (basicCount <= 0) {
            throw new IllegalArgumentException("El mazo debe tener al menos 1 Pokemon Basico");
        }

        PlayerSetupState state = new PlayerSetupState();

        int mulligans = 0;
        while (!drawContainsBasic(deckSize, basicCount, 7)) {
            mulligans++;
        }

        state.setMulligans(mulligans);
        state.setHasActive(true);
        state.setBenchCount(random.nextInt(6));
        state.setPrizeCount(6);
        state.setHandSize(7 + opponentMulligans);
        return state;
    }

    public PlayerSetupState preparePlayerBoard(UUID player, int deckSize, int basicCount, int opponentMulligans,
                                                GameAggregate game) {
        PlayerSetupState state = preparePlayer(deckSize, basicCount, opponentMulligans);
        int mulligans = 0;
        List<String> deck;
        List<String> hand;
        List<String> revealedHand = null;

        do {
            deck = buildDeck(player, deckSize, basicCount, game);
            Collections.shuffle(deck, random);
            hand = draw(deck, 7);
            if (containsBasic(hand)) {
                break;
            }
            revealedHand = List.copyOf(hand);
            mulligans++;
        } while (true);

        game.rememberOriginalDeck(player, deckWithOpeningHand(deck, hand));
        game.initializeZonesFor(player, deck);
        game.hand().put(player, hand);

        String active = removeFirstBasic(hand);
        game.activePokemon().put(player, active);
        game.markSlotEnteredOnSetup(player, "ACTIVE");
        game.activeHp().put(player, game.activeMaxHp(player));
        game.activeDamageCounters().put(player, 0);
        game.damageCountersBySlot().computeIfAbsent(player, ignored -> new java.util.HashMap<>()).put("ACTIVE", 0);
        game.activeAttachedEnergy().put(player, 0);
        game.activePokemonEx().put(player, game.cardCatalog().get(active).isPokemonEx());

        List<String> bench = game.bench().get(player);
        while (game.benchOccupiedCount(player) < 5) {
            String basic = removeFirstBasic(hand);
            if (basic == null) {
                break;
            }
            game.benchPokemon(player, basic, null);
        }

        game.prizeCards().put(player, draw(deck, 6));
        game.deck().put(player, new ArrayList<>(deck));
        game.prizeCardsRemaining().put(player, game.prizeCards().get(player).size());
        game.deckCardsRemaining().put(player, deck.size());

        state.setMulligans(mulligans);
        state.setHasActive(true);
        state.setBenchCount(game.benchOccupiedCount(player));
        state.setPrizeCount(game.prizeCards().get(player).size());
        state.setHandSize(hand.size() + opponentMulligans);
        if (revealedHand != null) {
            state.setRevealedHandCardIds(revealedHand);
        }
        return state;
    }

    public PlayerSetupState preparePlayerOpeningHand(UUID player, int deckSize, int basicCount, int opponentMulligans,
                                                      GameAggregate game) {
        preparePlayer(deckSize, basicCount, opponentMulligans);
        int mulligans = 0;
        List<String> deck;
        List<String> hand;
        List<String> revealedHand = null;

        do {
            deck = buildDeck(player, deckSize, basicCount, game);
            Collections.shuffle(deck, random);
            hand = draw(deck, 7);
            if (containsBasic(hand)) {
                break;
            }
            revealedHand = List.copyOf(hand);
            mulligans++;
        } while (true);

        PlayerSetupState state = setupOpeningHand(player, opponentMulligans, game, mulligans, deck, hand);
        if (revealedHand != null) {
            state.setRevealedHandCardIds(revealedHand);
        }
        return state;
    }

    public PlayerSetupState preparePlayerBoard(UUID player, List<GameCard> deckCards, int opponentMulligans,
                                               GameAggregate game) {
        if (deckCards.size() < 7) {
            throw new IllegalArgumentException("El mazo debe tener al menos 7 cartas para iniciar setup");
        }
        if (deckCards.stream().noneMatch(GameCard::isBasicPokemon)) {
            throw new IllegalArgumentException("El mazo debe tener al menos 1 Pokemon Basico");
        }

        PlayerSetupState state = new PlayerSetupState();
        int mulligans = 0;
        List<String> deck;
        List<String> hand;
        List<String> revealedHand = null;

        do {
            deck = buildDeck(deckCards, game);
            Collections.shuffle(deck, random);
            hand = draw(deck, 7);
            if (containsBasic(hand, game)) {
                break;
            }
            revealedHand = List.copyOf(hand);
            mulligans++;
        } while (true);

        setupBoardFromOpeningHand(player, opponentMulligans, game, state, mulligans, deck, hand, 6);
        if (revealedHand != null) {
            state.setRevealedHandCardIds(revealedHand);
        }
        return state;
    }

    public PlayerSetupState preparePlayerOpeningHand(UUID player, List<GameCard> deckCards, int opponentMulligans,
                                                      GameAggregate game) {
        if (deckCards.size() < 7) {
            throw new IllegalArgumentException("El mazo debe tener al menos 7 cartas para iniciar setup");
        }
        if (deckCards.stream().noneMatch(GameCard::isBasicPokemon)) {
            throw new IllegalArgumentException("El mazo debe tener al menos 1 Pokemon Basico");
        }

        int mulligans = 0;
        List<String> deck;
        List<String> hand;
        List<String> revealedHand = null;

        do {
            deck = buildDeck(deckCards, game);
            Collections.shuffle(deck, random);
            hand = draw(deck, 7);
            if (containsBasic(hand, game)) {
                break;
            }
            revealedHand = List.copyOf(hand);
            mulligans++;
        } while (true);

        PlayerSetupState state = setupOpeningHand(player, opponentMulligans, game, mulligans, deck, hand);
        if (revealedHand != null) {
            state.setRevealedHandCardIds(revealedHand);
        }
        return state;
    }

    public void initSuddenDeath(GameAggregate game) {
        if (game.playerOne() == null || game.playerTwo() == null) {
            throw new IllegalStateException("Muerte subita requiere dos jugadores");
        }

        PlayerSetupState p1 = setupSuddenDeathPlayer(game, game.playerOne(), 0);
        PlayerSetupState p2 = setupSuddenDeathPlayer(game, game.playerTwo(), p1.mulligans());
        drawOptionalMulliganCards(game, game.playerOne(), p2.mulligans());
        drawOptionalMulliganCards(game, game.playerTwo(), p1.mulligans());
        p1.setHandSize(game.hand().getOrDefault(game.playerOne(), List.of()).size());
        p2.setHandSize(game.hand().getOrDefault(game.playerTwo(), List.of()).size());

        game.setupByPlayer().clear();
        game.setupByPlayer().put(game.playerOne(), p1);
        game.setupByPlayer().put(game.playerTwo(), p2);
        game.setupConfirmedByPlayer().clear();
        game.clearStadium();
        game.statusByPlayer().remove(game.playerOne());
        game.statusByPlayer().remove(game.playerTwo());
        game.turnFlags().reset();
        game.setWinner(null);
        game.setSuddenDeath(true);
        game.setGameState(com.utn.pokemontcg.game.domain.model.GameState.ACTIVE);
        game.setSetupPhase(null);
        game.setTurnNumber(1);
        game.setFirstTurn(true);
        game.setFirstPlayer(random.nextBoolean() ? game.playerOne() : game.playerTwo());
        game.setCurrentTurnPlayer(game.firstPlayer());
        game.markTurnStartedFor(game.firstPlayer());
        game.setTurnPhase(com.utn.pokemontcg.game.domain.model.TurnPhase.MAIN);
    }

    private boolean drawContainsBasic(int deckSize, int basicCount, int drawCount) {
        int successes = 0;
        for (int i = 0; i < drawCount; i++) {
            int remainingDeck = deckSize - i;
            int remainingBasics = basicCount - successes;
            if (remainingBasics <= 0) {
                continue;
            }
            double chance = (double) remainingBasics / remainingDeck;
            if (random.nextDouble() < chance) {
                successes++;
            }
        }
        return successes > 0;
    }

    private List<String> buildDeck(UUID player, int deckSize, int basicCount, GameAggregate game) {
        List<String> cards = new ArrayList<>(deckSize);
        for (int i = 0; i < basicCount; i++) {
            String id = "xy1-basic-" + player + "-" + i;
            cards.add(id);
            game.registerCard(new GameCard(
                id,
                "Starter Basic " + i,
                "Pokemon",
                Set.of("Basic"),
                120,
                30,
                1
            ));
        }
        for (int i = basicCount; i < deckSize; i++) {
            String id = "xy1-energy-" + player + "-" + i;
            cards.add(id);
            game.registerCard(new GameCard(
                id,
                "Basic Energy " + i,
                "Energy",
                Set.of("Basic"),
                0,
                0,
                0
            ));
        }
        return cards;
    }

    private List<String> buildDeck(List<GameCard> deckCards, GameAggregate game) {
        List<String> cards = new ArrayList<>(deckCards.size());
        for (GameCard card : deckCards) {
            cards.add(card.id());
            game.registerCard(card);
        }
        return cards;
    }

    private List<String> draw(List<String> deck, int count) {
        List<String> drawn = new ArrayList<>();
        for (int i = 0; i < count && !deck.isEmpty(); i++) {
            drawn.add(deck.remove(0));
        }
        return drawn;
    }

    private boolean containsBasic(List<String> cards) {
        return cards.stream().anyMatch(card -> card.startsWith("xy1-basic-"));
    }

    private boolean containsBasic(List<String> cards, GameAggregate game) {
        return cards.stream().anyMatch(card -> game.cardCatalog().get(card).isBasicPokemon());
    }

    private String removeFirstBasic(List<String> cards) {
        for (int i = 0; i < cards.size(); i++) {
            String card = cards.get(i);
            if (card.startsWith("xy1-basic-")) {
                cards.remove(i);
                return card;
            }
        }
        return null;
    }

    private String removeFirstBasic(List<String> cards, GameAggregate game) {
        for (int i = 0; i < cards.size(); i++) {
            String card = cards.get(i);
            if (game.cardCatalog().get(card).isBasicPokemon()) {
                cards.remove(i);
                return card;
            }
        }
        return null;
    }

    private void setupBoardFromOpeningHand(UUID player, int opponentMulligans, GameAggregate game,
                                           PlayerSetupState state, int mulligans, List<String> deck,
                                           List<String> hand, int prizeCount) {
        game.rememberOriginalDeck(player, deckWithOpeningHand(deck, hand));
        game.initializeZonesFor(player, deck);
        game.hand().put(player, hand);

        String active = removeFirstBasic(hand, game);
        game.activePokemon().put(player, active);
        game.markSlotEnteredOnSetup(player, "ACTIVE");
        game.activeHp().put(player, game.activeMaxHp(player));
        game.activeDamageCounters().put(player, 0);
        game.damageCountersBySlot().computeIfAbsent(player, ignored -> new java.util.HashMap<>()).put("ACTIVE", 0);
        game.activeAttachedEnergy().put(player, 0);
        game.activePokemonEx().put(player, game.cardCatalog().get(active).isPokemonEx());

        List<String> bench = game.bench().get(player);
        while (game.benchOccupiedCount(player) < 5) {
            String basic = removeFirstBasic(hand, game);
            if (basic == null) {
                break;
            }
            game.benchPokemon(player, basic, null);
        }

        game.prizeCards().put(player, draw(deck, prizeCount));
        game.deck().put(player, new ArrayList<>(deck));
        game.prizeCardsRemaining().put(player, game.prizeCards().get(player).size());
        game.deckCardsRemaining().put(player, deck.size());

        state.setMulligans(mulligans);
        state.setHasActive(true);
        state.setBenchCount(game.benchOccupiedCount(player));
        state.setPrizeCount(game.prizeCards().get(player).size());
        state.setHandSize(hand.size() + opponentMulligans);
    }

    private PlayerSetupState setupOpeningHand(UUID player, int opponentMulligans, GameAggregate game,
                                              int mulligans, List<String> deck, List<String> hand) {
        game.rememberOriginalDeck(player, deckWithOpeningHand(deck, hand));
        game.initializeZonesFor(player, deck);
        game.hand().put(player, hand);
        game.prizeCards().put(player, draw(deck, 6));
        game.deck().put(player, new ArrayList<>(deck));
        game.prizeCardsRemaining().put(player, game.prizeCards().get(player).size());
        game.deckCardsRemaining().put(player, deck.size());

        PlayerSetupState state = new PlayerSetupState();
        state.setMulligans(mulligans);
        state.setHasActive(false);
        state.setBenchCount(0);
        state.setPrizeCount(game.prizeCards().get(player).size());
        state.setHandSize(hand.size() + opponentMulligans);
        return state;
    }

    private PlayerSetupState setupSuddenDeathPlayer(GameAggregate game, UUID player, int opponentMulligans) {
        List<String> originalDeck = originalDeckFor(game, player);
        if (originalDeck.size() < 7) {
            throw new IllegalStateException("No hay mazo original suficiente para iniciar muerte subita");
        }
        if (originalDeck.stream().noneMatch(cardId -> {
            GameCard card = game.cardCatalog().get(cardId);
            return card != null && card.isBasicPokemon();
        })) {
            throw new IllegalStateException("El mazo original debe tener al menos 1 Pokemon Basico para muerte subita");
        }

        PlayerSetupState state = new PlayerSetupState();
        int mulligans = 0;
        List<String> deck;
        List<String> hand;
        do {
            deck = new ArrayList<>(originalDeck);
            Collections.shuffle(deck, random);
            hand = draw(deck, 7);
            if (containsBasic(hand, game)) {
                break;
            }
            mulligans++;
        } while (true);

        setupBoardFromOpeningHand(player, opponentMulligans, game, state, mulligans, deck, hand, 1);
        return state;
    }

    private List<String> originalDeckFor(GameAggregate game, UUID player) {
        List<String> original = game.originalDecks().get(player);
        if (original != null && !original.isEmpty()) {
            return new ArrayList<>(original);
        }

        List<String> reconstructed = new ArrayList<>();
        reconstructed.addAll(game.deck().getOrDefault(player, List.of()));
        reconstructed.addAll(game.hand().getOrDefault(player, List.of()));
        reconstructed.addAll(game.prizeCards().getOrDefault(player, List.of()));
        reconstructed.addAll(game.discardPile().getOrDefault(player, List.of()));
        String active = game.activePokemon().get(player);
        if (active != null) {
            reconstructed.add(active);
        }
        reconstructed.addAll(game.bench().getOrDefault(player, List.of()).stream().filter(java.util.Objects::nonNull).toList());
        reconstructed.addAll(game.attachedEnergies().getOrDefault(player, java.util.Map.of()).values().stream()
            .flatMap(List::stream)
            .map(com.utn.pokemontcg.game.domain.model.AttachedEnergy::cardId)
            .toList());
        reconstructed.addAll(game.toolsBySlot().getOrDefault(player, java.util.Map.of()).values());
        return reconstructed;
    }

    private List<String> deckWithOpeningHand(List<String> deck, List<String> hand) {
        List<String> original = new ArrayList<>(hand.size() + deck.size());
        original.addAll(hand);
        original.addAll(deck);
        return original;
    }

    private void drawOptionalMulliganCards(GameAggregate game, UUID player, int cards) {
        for (int i = 0; i < cards; i++) {
            List<String> deck = game.deck().getOrDefault(player, List.of());
            if (deck.isEmpty()) {
                return;
            }
            game.hand().computeIfAbsent(player, ignored -> new ArrayList<>()).add(deck.remove(0));
            game.deckCardsRemaining().put(player, deck.size());
        }
    }
}
