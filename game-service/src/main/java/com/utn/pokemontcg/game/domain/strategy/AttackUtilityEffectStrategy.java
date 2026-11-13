package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.application.service.deck.DeckService;
import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameCard;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class AttackUtilityEffectStrategy extends AbstractAttackEffectStrategy {
    @Override
    public String effectName() {
        return "ATTACK_UTILITY";
    }

    @Override
    public boolean supports(AttackContext context) {
        String text = effectText(context);
        return containsAny(text, "draw", "search your deck", "discard the top card", "look at the top", "shuffles", "shuffle", "switch", "move an energy", "does 10 damage to itself", "does 20 damage to itself", "does 30 damage to itself", "put a card from your discard", "put 2 item cards", "put 2 damage counters", "remaining hp", "discards a card from", "random card from your opponent", "opponent's hand", "opponent hand", "hand of your opponent", "mano de tu oponente", "mano rival", "carta al azar")
            || isTopDeckOrderText(text);
    }

    @Override
    public void apply(AttackContext context) {
        if (context.cancelled()) return;
        if (!coinConditionAllowsEffect(context)) return;
        String text = effectText(context);
        boolean acceptedOptional = context.acceptedOptionalEffect() != Boolean.FALSE;
        if (text.contains("draw 4 cards")) auditCount(context, "draw_cards", draw(context, defender(context), 4), "defender");
        else if (text.contains("draw 2 cards")) auditCount(context, "draw_cards", draw(context, attacker(context), 2), "attacker");
        else if (text.contains("draw a card")) auditCount(context, "draw_cards", draw(context, attacker(context), 1), "attacker");

        if (text.contains("search your deck") && text.contains("put") && text.contains("into your hand")) {
            int count = text.contains("up to 2") ? 2 : text.contains("3 different") ? 3 : 1;
            auditCount(context, "deck_to_hand", moveFromDeckToHand(context, count, text), "attacker");
        }
        if (text.contains("discard the top card of your opponent")) {
            int count = text.contains("for each damage counter on this")
                ? context.game().damageCountersAtSlot(attacker(context), "ACTIVE")
                : 1;
            int discarded = discardTopCount(context, defender(context), count);
            if (discarded > 0) context.audit("AttackEffect:discard_top_card=" + discarded + ",target=defender");
        }
        if (text.contains("discard the top card of your deck")) {
            String discarded = discardTop(context, attacker(context));
            if (discarded != null) context.audit("AttackEffect:discard_top_card=1,target=attacker");
            GameCard card = context.game().cardCatalog().get(discarded);
            if (card != null && card.isEnergy() && text.contains("attach it")) {
                context.game().discardPile().getOrDefault(attacker(context), new ArrayList<>()).remove(discarded);
                context.game().attachEnergy(attacker(context), "ACTIVE", new AttachedEnergy(discarded, DeckService.inferEnergyType(card.name(), card.energyType())));
                context.audit("AttackEffect:energy_attached=1,source=deck_top,target=ACTIVE");
            }
        }
        if (isTopDeckOrderText(text)) {
            auditCount(context, "deck_top_reordered", reorderTopDeckCards(context, topDeckLookCount(text)), "attacker");
        }
        if (text.contains("does 10 damage to itself")) context.game().addDamageCounters(attacker(context), "ACTIVE", 1);
        if (text.contains("does 20 damage to itself")) context.game().addDamageCounters(attacker(context), "ACTIVE", 2);
        if (text.contains("does 30 damage to itself")) context.game().addDamageCounters(attacker(context), "ACTIVE", 3);
        if (containsAny(text, "each player switches", "each player switch", "both players switch", "you and your opponent switch")) {
            switchActiveWithSelectedBench(context, defender(context), "defender");
            switchActiveWithSelectedBench(context, attacker(context), "attacker");
        }
        if (text.contains("switch this") && context.game().benchOccupiedCount(attacker(context)) > 0) {
            int benchIndex = selectedBenchIndex(context, attacker(context));
            context.game().swapActiveWithBench(attacker(context), benchIndex);
            context.audit("AttackEffect:switch_active_bench=BENCH_" + benchIndex + ",target=attacker");
        }
        if ((text.contains("opponent switches") || text.contains("switch 1 of your opponent") || text.contains("your opponent switches")) && context.game().benchOccupiedCount(defender(context)) > 0) {
            context.game().setPendingForcedSwitchPlayerId(defender(context));
            context.audit("AttackEffect:pending_forced_switch,target=defender");
        }
        if (text.contains("move an energy from this") && context.game().benchOccupiedCount(attacker(context)) > 0) {
            String to = "BENCH_" + selectedBenchIndex(context, attacker(context));
            auditCount(context, "energy_moved", moveEnergy(context, attacker(context), "ACTIVE", to), "attacker,from=ACTIVE,to=" + to);
        }
        if (text.contains("put a card from your discard pile on top of your deck")) auditCount(context, "discard_to_deck", topDiscardToDeck(context, attacker(context), null), "attacker");
        if (text.contains("put 2 item cards from your discard pile into your hand")) auditCount(context, "discard_to_hand", discardToHand(context, 2, "Item"), "attacker");
        if (text.contains("put 2 damage counters each of your opponent")) damageEachOpponentPokemon(context, 2);
        if (text.contains("remaining hp of each") && text.contains("is 10")) setBothActiveRemainingHpTo10(context);
        if (isSelectedOpponentHandDiscardText(text)) auditCount(context, "opponent_hand_discarded", discardSelectedOpponentHandCard(context), "defender");
        if (isSelectedOpponentHandShuffleText(text)) auditCount(context, "opponent_hand_to_deck", shuffleSelectedOpponentHandCardIntoDeck(context), "defender");
        if (text.contains("random card from your opponent")) shuffleOpponentHandCardIntoDeck(context);
        if (text.contains("for each tails") && text.contains("discards a card from")) discardOpponentHandForTails(context, 4);
        if (acceptedOptional && text.contains("move an energy attached to your opponent") && context.game().benchOccupiedCount(defender(context)) > 0) {
            String to = "BENCH_" + selectedBenchIndex(context, defender(context));
            auditCount(context, "energy_moved", moveEnergy(context, defender(context), "ACTIVE", to), "defender,from=ACTIVE,to=" + to);
        }
        context.audit("AttackEffect:utility_applied");
    }

    private int draw(AttackContext context, java.util.UUID player, int count) {
        List<String> deck = context.game().deck().getOrDefault(player, new ArrayList<>());
        List<String> hand = context.game().hand().getOrDefault(player, new ArrayList<>());
        int moved = 0;
        for (int i = 0; i < count && !deck.isEmpty(); i++) {
            hand.add(deck.remove(0));
            moved++;
        }
        context.game().deckCardsRemaining().put(player, deck.size());
        return moved;
    }

    private int moveFromDeckToHand(AttackContext context, int count, String text) {
        List<String> deck = context.game().deck().getOrDefault(attacker(context), new ArrayList<>());
        List<String> hand = context.game().hand().getOrDefault(attacker(context), new ArrayList<>());
        int moved = 0;
        List<String> requested = context.selectedCardIds();
        List<String> candidates = requested.isEmpty() ? new ArrayList<>(deck) : requested;
        for (String id : new ArrayList<>(candidates)) {
            if (moved >= count) break;
            if (!deck.contains(id)) continue;
            GameCard card = context.game().cardCatalog().get(id);
            if (card == null) continue;
            boolean match = text.contains("supporter") ? card.hasSubtype("Supporter")
                : text.contains("energy") ? card.isBasicEnergy() || card.isEnergy()
                : text.contains("pokemon") || text.contains("pokémon") ? card.isPokemon()
                : true;
            if (match) {
                deck.remove(id);
                hand.add(id);
                context.revealCard(id);
                moved++;
            }
        }
        Collections.shuffle(deck);
        context.game().deckCardsRemaining().put(attacker(context), deck.size());
        return moved;
    }

    private String discardTop(AttackContext context, java.util.UUID player) {
        List<String> deck = context.game().deck().getOrDefault(player, new ArrayList<>());
        if (deck.isEmpty()) return null;
        String id = deck.remove(0);
        context.game().discardPile().computeIfAbsent(player, ignored -> new ArrayList<>()).add(id);
        context.game().deckCardsRemaining().put(player, deck.size());
        return id;
    }

    private int discardTopCount(AttackContext context, java.util.UUID player, int count) {
        List<String> deck = context.game().deck().getOrDefault(player, new ArrayList<>());
        List<String> discard = context.game().discardPile().computeIfAbsent(player, ignored -> new ArrayList<>());
        int moved = 0;
        for (int i = 0; i < count && !deck.isEmpty(); i++) {
            discard.add(deck.remove(0));
            moved++;
        }
        context.game().deckCardsRemaining().put(player, deck.size());
        return moved;
    }

    private int reorderTopDeckCards(AttackContext context, int count) {
        List<String> deck = context.game().deck().getOrDefault(attacker(context), new ArrayList<>());
        int limit = Math.min(count, deck.size());
        if (limit <= 1) return 0;
        List<String> currentTop = new ArrayList<>(deck.subList(0, limit));
        List<String> requested = context.selectedCardIds();
        if (requested.size() < limit) return 0;
        List<String> requestedTop = new ArrayList<>(requested.subList(0, limit));
        if (!currentTop.containsAll(requestedTop) || !requestedTop.containsAll(currentTop)) return 0;
        for (int i = 0; i < limit; i++) {
            deck.set(i, requestedTop.get(i));
        }
        return limit;
    }

    private int topDeckLookCount(String text) {
        String normalized = normalizedText(text);
        if ((normalized.contains("top 5") || normalized.contains("top five")) || normalized.contains("5 cartas")) return 5;
        if ((normalized.contains("top 4") || normalized.contains("top four")) || normalized.contains("4 cartas")) return 4;
        if ((normalized.contains("top 3") || normalized.contains("top three")) || normalized.contains("3 cartas")) return 3;
        if ((normalized.contains("top 2") || normalized.contains("top two")) || normalized.contains("2 cartas")) return 2;
        if (normalized.contains("1 carta")) return 1;
        return 1;
    }

    private boolean isTopDeckOrderText(String text) {
        String normalized = normalizedText(text);
        boolean english = normalized.contains("look at the top")
            && normalized.contains("cards of your deck")
            && normalized.contains("put them back");
        boolean spanish = containsAny(normalized, "mira las", "mira los", "mira la")
            && normalized.contains("cartas")
            && normalized.contains("mazo")
            && containsAny(normalized, "arriba", "superiores")
            && normalized.contains("orden");
        return english || spanish;
    }

    private String normalizedText(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private int firstBench(AttackContext context, java.util.UUID player) {
        List<String> bench = context.game().bench().getOrDefault(player, List.of());
        for (int i = 0; i < bench.size(); i++) if (bench.get(i) != null) return i;
        return 0;
    }

    private int selectedBenchIndex(AttackContext context, java.util.UUID player) {
        String slot = context.game().normalizeSlot(context.targetSlot());
        int index = context.game().benchIndex(slot);
        if (index >= 0 && context.game().isOccupiedSlot(player, slot)) return index;
        return firstBench(context, player);
    }

    private void switchActiveWithSelectedBench(AttackContext context, java.util.UUID player, String target) {
        if (context.game().benchOccupiedCount(player) <= 0) return;
        int benchIndex = selectedBenchIndex(context, player);
        context.game().swapActiveWithBench(player, benchIndex);
        context.audit("AttackEffect:switch_active_bench=BENCH_" + benchIndex + ",target=" + target);
    }

    private int moveEnergy(AttackContext context, java.util.UUID player, String from, String to) {
        List<AttachedEnergy> attached = new ArrayList<>(context.game().attachedEnergiesFor(player, from));
        if (attached.isEmpty()) return 0;
        AttachedEnergy energy = attached.stream()
            .filter(e -> context.selectedEnergyCardIds().contains(e.cardId()))
            .findFirst()
            .orElse(attached.get(0));
        context.game().removeAttachedEnergies(player, from, List.of(energy.cardId()));
        context.game().attachEnergy(player, to, energy);
        return 1;
    }

    private int topDiscardToDeck(AttackContext context, java.util.UUID player, String subtype) {
        List<String> discard = context.game().discardPile().getOrDefault(player, new ArrayList<>());
        if (discard.isEmpty()) return 0;
        String id = discard.remove(0);
        context.game().deck().getOrDefault(player, new ArrayList<>()).add(0, id);
        return 1;
    }

    private int discardToHand(AttackContext context, int count, String subtype) {
        List<String> discard = context.game().discardPile().getOrDefault(attacker(context), new ArrayList<>());
        List<String> hand = context.game().hand().getOrDefault(attacker(context), new ArrayList<>());
        int moved = 0;
        for (String id : new ArrayList<>(discard)) {
            if (moved >= count) break;
            GameCard card = context.game().cardCatalog().get(id);
            if (card != null && card.hasSubtype(subtype)) {
                discard.remove(id);
                hand.add(id);
                moved++;
            }
        }
        return moved;
    }

    private void auditCount(AttackContext context, String action, int count, String details) {
        if (count > 0) context.audit("AttackEffect:" + action + "=" + count + ",target=" + details);
    }

    private void damageEachOpponentPokemon(AttackContext context, int counters) {
        context.game().addDamageCounters(defender(context), "ACTIVE", counters);
        List<String> bench = context.game().bench().getOrDefault(defender(context), List.of());
        for (int i = 0; i < bench.size(); i++) if (bench.get(i) != null) context.game().addDamageCounters(defender(context), "BENCH_" + i, counters);
    }

    private void setBothActiveRemainingHpTo10(AttackContext context) {
        setRemainingHpTo10(context, attacker(context));
        setRemainingHpTo10(context, defender(context));
    }

    private void setRemainingHpTo10(AttackContext context, java.util.UUID player) {
        int currentCounters = context.game().damageCountersAtSlot(player, "ACTIVE");
        int remainingHp = Math.max(0, context.game().activeMaxHp(player) - currentCounters * 10);
        int countersToAdd = Math.max(0, (remainingHp - 10) / 10);
        context.game().addDamageCounters(player, "ACTIVE", countersToAdd);
    }

    private void shuffleOpponentHandCardIntoDeck(AttackContext context) {
        var opponent = defender(context);
        List<String> hand = context.game().hand().getOrDefault(opponent, new ArrayList<>());
        if (hand.isEmpty()) return;
        String id = selectedHandCard(context, hand);
        hand.remove(id);
        List<String> deck = context.game().deck().getOrDefault(opponent, new ArrayList<>());
        deck.add(id);
        Collections.shuffle(deck);
        context.game().deckCardsRemaining().put(opponent, deck.size());
    }

    private boolean isSelectedOpponentHandDiscardText(String text) {
        if (text.contains("random") || text.contains("for each tails")) return false;
        return containsAny(text, "opponent's hand", "opponent hand", "hand of your opponent", "mano de tu oponente", "mano rival")
            && containsAny(text, "discard a card", "discard 1 card", "discard one card", "discards a card",
                "descarta una carta", "descarta 1 carta", "elige una carta", "elige 1 carta");
    }

    private boolean isSelectedOpponentHandShuffleText(String text) {
        if (text.contains("random")) return false;
        return containsAny(text, "opponent's hand", "opponent hand", "hand of your opponent", "mano de tu oponente", "mano rival")
            && containsAny(text, "shuffle", "shuffles", "put", "pon", "devuelve", "baraja")
            && containsAny(text, "deck", "into your opponent's deck", "into his or her deck", "mazo", "en su mazo");
    }

    private int discardSelectedOpponentHandCard(AttackContext context) {
        var opponent = defender(context);
        List<String> hand = context.game().hand().getOrDefault(opponent, new ArrayList<>());
        if (hand.isEmpty()) return 0;
        String id = selectedHandCard(context, hand);
        hand.remove(id);
        context.game().discardPile().computeIfAbsent(opponent, ignored -> new ArrayList<>()).add(id);
        return 1;
    }

    private int shuffleSelectedOpponentHandCardIntoDeck(AttackContext context) {
        var opponent = defender(context);
        List<String> hand = context.game().hand().getOrDefault(opponent, new ArrayList<>());
        if (hand.isEmpty()) return 0;
        String id = selectedHandCard(context, hand);
        hand.remove(id);
        List<String> deck = context.game().deck().getOrDefault(opponent, new ArrayList<>());
        deck.add(id);
        Collections.shuffle(deck);
        context.game().deckCardsRemaining().put(opponent, deck.size());
        return 1;
    }

    private String selectedHandCard(AttackContext context, List<String> hand) {
        return context.selectedCardIds().stream()
            .filter(hand::contains)
            .findFirst()
            .orElse(hand.get(0));
    }

    private void discardOpponentHandForTails(AttackContext context, int flips) {
        List<Boolean> results = new ArrayList<>();
        for (int i = 0; i < flips; i++) results.add(context.coinFlip().getAsBoolean());
        context.setCoinFlipResults(results);
        int tails = (int) results.stream().filter(result -> !result).count();
        var opponent = defender(context);
        List<String> hand = context.game().hand().getOrDefault(opponent, new ArrayList<>());
        List<String> discard = context.game().discardPile().computeIfAbsent(opponent, ignored -> new ArrayList<>());
        for (int i = 0; i < tails && !hand.isEmpty(); i++) discard.add(hand.remove(0));
    }
}
