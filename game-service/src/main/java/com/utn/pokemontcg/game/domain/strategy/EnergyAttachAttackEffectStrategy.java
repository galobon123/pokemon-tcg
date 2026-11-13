package com.utn.pokemontcg.game.domain.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.utn.pokemontcg.game.application.service.deck.DeckService;
import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;
import com.utn.pokemontcg.game.domain.model.GameCard;

@Component
public class EnergyAttachAttackEffectStrategy extends AbstractAttackEffectStrategy {
    @Override
    public String effectName() {
        return "ENERGY_ATTACH";
    }

    @Override
    public boolean supports(AttackContext context) {
        String text = effectText(context);
        return text.contains("attach")
            && text.contains("energy")
            && containsAny(text, "from your deck", "search your deck", "from your discard", "from your hand", "from the discard", "from discard");
    }

    @Override
    public void apply(AttackContext context) {
        if (context.cancelled()) {
            return;
        }
        if (!coinConditionAllowsEffect(context)) {
            return;
        }

        UUID player = attacker(context);
        String text = effectText(context);

        String source = source(context);
        List<String> sourceZone = zone(context, player, source);
        if (sourceZone == null || sourceZone.isEmpty()) {
            context.audit("AttackEffect:energy_attach_no_source=" + source);
            return;
        }

        int count = attachCount(text);
        String requiredType = requiredEnergyType(text);
        boolean basicOnly = text.contains("basic energy");

        // When the attack attaches several Energy to DIFFERENT Pokémon ("attach them to your
        // Benched Pokémon in any way you like", e.g. Geomancy), each chosen own-slot receives one
        // Energy. Otherwise every Energy goes to a single resolved slot (e.g. Gather Energy).
        List<String> chosenSlots = new ArrayList<>();
        for (String slot : selectedSlots(context)) {
            if (context.game().isOccupiedSlot(player, slot) && !chosenSlots.contains(slot)) {
                chosenSlots.add(slot);
            }
        }
        boolean distribute = count > 1 && chosenSlots.size() > 1
            && (text.contains("in any way") || text.contains("benched"));

        String singleSlot = distribute ? null : targetSlot(context, player);
        if (!distribute && singleSlot == null) {
            context.audit("AttackEffect:energy_attach_no_target");
            return;
        }

        List<String> selectedEnergy = context.selectedEnergyCardIds().isEmpty()
            ? selectedNonSlotCardIds(context)
            : context.selectedEnergyCardIds();
        List<String> candidates = selectedEnergy.isEmpty() ? new ArrayList<>(sourceZone) : selectedEnergy;

        int attached = 0;
        int slotIndex = 0;
        for (String cardId : new ArrayList<>(candidates)) {
            if (attached >= count) {
                break;
            }
            GameCard card = context.game().cardCatalog().get(cardId);
            if (card == null || !card.isEnergy() || (basicOnly && !card.isBasicEnergy())) {
                continue;
            }
            String energyType = DeckService.inferEnergyType(card.name(), card.energyType());
            if (requiredType != null && !requiredType.equalsIgnoreCase(energyType)) {
                continue;
            }
            if (!sourceZone.contains(cardId)) {
                continue;
            }
            String slot;
            if (distribute) {
                if (slotIndex >= chosenSlots.size()) {
                    break;
                }
                slot = chosenSlots.get(slotIndex++);
            } else {
                slot = singleSlot;
            }
            sourceZone.remove(cardId);
            context.game().attachEnergy(player, slot, new AttachedEnergy(cardId, energyType));
            if ("deck".equals(source)) {
                context.revealCard(cardId);
            }
            attached++;
        }

        if ("deck".equals(source)) {
            context.game().deckCardsRemaining().put(player, sourceZone.size());
        }
        context.audit("AttackEffect:energy_attached=" + attached + ",source=" + source
            + ",target=" + (distribute ? chosenSlots : singleSlot));
    }

    private String targetSlot(AttackContext context, UUID player) {
        String text = effectText(context);
        String requested = context.game().normalizeSlot(context.targetSlot());
        boolean attachToThis = containsAny(text, "attach it to this", "attach a", "attach an") && text.contains("to this pokemon");
        boolean benchOnly = !attachToThis
            && containsAny(text, "to 1 of your benched", "to one of your benched", "to your benched", "to each of 2 of your benched");
        if (attachToThis) {
            return context.game().isOccupiedSlot(player, "ACTIVE") ? "ACTIVE" : null;
        }
        if (!benchOnly && context.game().isOccupiedSlot(player, requested)) {
            return requested;
        }
        if (!benchOnly) {
            return context.game().isOccupiedSlot(player, "ACTIVE") ? "ACTIVE" : null;
        }
        if (requested.startsWith("BENCH_") && context.game().isOccupiedSlot(player, requested)) {
            return requested;
        }
        List<String> bench = context.game().bench().getOrDefault(player, List.of());
        for (int i = 0; i < bench.size(); i++) {
            if (bench.get(i) != null) {
                return "BENCH_" + i;
            }
        }
        return null;
    }

    private String source(AttackContext context) {
        String text = effectText(context);
        if (text.contains("discard")) {
            return "discard";
        }
        if (text.contains("hand")) {
            return "hand";
        }
        return "deck";
    }

    private List<String> zone(AttackContext context, UUID player, String source) {
        return switch (source) {
            case "discard" -> context.game().discardPile().computeIfAbsent(player, ignored -> new ArrayList<>());
            case "hand" -> context.game().hand().computeIfAbsent(player, ignored -> new ArrayList<>());
            case "deck" -> context.game().deck().computeIfAbsent(player, ignored -> new ArrayList<>());
            default -> null;
        };
    }

    private int attachCount(String text) {
        if (text.contains(" 2 ") || text.contains(" two ")) {
            return 2;
        }
        return 1;
    }

    private String requiredEnergyType(String text) {
        for (String type : List.of("Fire", "Water", "Grass", "Lightning", "Psychic", "Fighting", "Darkness", "Metal", "Fairy", "Dragon", "Colorless")) {
            if (text.contains(type.toLowerCase() + " energy")) {
                return type;
            }
        }
        return null;
    }
}
