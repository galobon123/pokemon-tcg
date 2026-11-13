package com.utn.pokemontcg.game.domain.strategy;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.utn.pokemontcg.game.domain.chain.AttackContext;
import com.utn.pokemontcg.game.domain.model.AttachedEnergy;

@Component
public class EnergyDiscardAttackEffectStrategy extends AbstractAttackEffectStrategy {
    @Override
    public String effectName() {
        return "ENERGY_DISCARD";
    }

    @Override
    public boolean supports(AttackContext context) {
        String text = effectText(context);
        if (text.contains("discard the top card of your deck") || text.contains("discard the top card of the deck")) {
            return false;
        }
        return text.contains("discard")
            && (text.contains("energy") || text.contains("cards attached") || text.contains("attached cards") || text.contains("pokemon tool") || text.contains("pokémon tool") || text.contains("tool"))
            && containsAny(text, "this pokemon", "this pokémon", "attached to this",
                "opponent's active", "opponent's pokémon", "opponent's pokemon",
                "defending pokémon", "defending pokemon", "that pokémon", "that pokémon");
    }

    @Override
    public void apply(AttackContext context) {
        if (context.cancelled()) {
            return;
        }
        if (!coinConditionAllowsEffect(context)) {
            return;
        }
        String text = effectText(context);
        // Optional discard ("You may discard an Energy…", e.g. Electron Crush): if the player
        // declined, skip the discard so it stays consistent with the conditional damage bonus.
        if (text.contains("you may") && text.contains("discard") && context.acceptedOptionalEffect() == Boolean.FALSE) {
            context.audit("AttackEffect:optional_discard_declined");
            return;
        }
        boolean fromOpponent = containsAny(text, "opponent's active", "opponent's pokémon", "opponent's pokemon", "defending pokémon", "defending pokemon", "that pokémon", "that pokemon");
        var player = fromOpponent ? defender(context) : attacker(context);
        String targetSlot = targetSlot(context, player, fromOpponent);

        if (containsAny(text, "pokemon tool", "pokémon tool") && !discardsAllAttachedCards(text)) {
            String tool = context.game().toolAtSlot(player, targetSlot);
            if (tool != null) {
                context.game().clearTool(player, targetSlot);
                context.game().discardPile().computeIfAbsent(player, ignored -> new ArrayList<>()).add(tool);
                context.audit("AttackEffect:tool_discarded=1,target=" + (fromOpponent ? "defender" : "attacker"));
            } else {
                context.audit("AttackEffect:tool_discard_none");
            }
            return;
        }

        List<AttachedEnergy> attached = new ArrayList<>(context.game().attachedEnergiesFor(player, targetSlot));
        boolean allAttachedCards = discardsAllAttachedCards(text);
        // When the attack restricts the discard to a specific Energy type (e.g. Heartfelt Song:
        // "Discard a [D] Energy") only that type is a valid target.
        String requiredType = requiredEnergyType(text);
        if (requiredType != null && !allAttachedCards) {
            attached.removeIf(energy -> !requiredType.equalsIgnoreCase(energy.energyType()));
        }
        boolean toolOnly = !allAttachedCards && discardsToolCards(effectText(context));
        if (toolOnly) {
            String attachedTool = context.game().toolAtSlot(player, targetSlot);
            if (attachedTool != null) {
                context.game().clearTool(player, targetSlot);
                context.game().discardPile().computeIfAbsent(player, ignored -> new ArrayList<>()).add(attachedTool);
                context.audit("AttackEffect:tool_discarded=" + attachedTool + ",target=" + (fromOpponent ? "defender" : "attacker"));
            } else {
                context.audit("AttackEffect:tool_discard_none");
            }
            return;
        }
        String tool = allAttachedCards ? context.game().toolAtSlot(player, targetSlot) : null;
        if (attached.isEmpty() && tool == null) {
            context.audit("AttackEffect:energy_discard_none");
            return;
        }
        int count = discardsAllEnergy(text) || allAttachedCards ? attached.size() : discardCount(text);
        List<String> selected = selectedEnergyIds(context, attached, count);
        List<AttachedEnergy> discarded = context.game().removeAttachedEnergies(player, targetSlot, selected);
        discarded.forEach(energy -> context.game().discardPile()
            .computeIfAbsent(player, ignored -> new ArrayList<>())
            .add(energy.cardId()));
        if (tool != null) {
            context.game().clearTool(player, targetSlot);
            context.game().discardPile().computeIfAbsent(player, ignored -> new ArrayList<>()).add(tool);
        }
        context.audit("AttackEffect:energy_discarded=" + discarded.size() + ",target=" + (fromOpponent ? "defender" : "attacker"));
        if (tool != null) {
            context.audit("AttackEffect:attached_cards_discarded=" + (discarded.size() + 1) + ",target=" + (fromOpponent ? "defender" : "attacker"));
        }
    }

    private String targetSlot(AttackContext context, java.util.UUID player, boolean fromOpponent) {
        String requested = context.game().normalizeSlot(context.targetSlot());
        if (fromOpponent && context.targetPlayerId() != null && context.targetPlayerId().equals(player) && context.game().isOccupiedSlot(player, requested)) {
            return requested;
        }
        return "ACTIVE";
    }

    /**
     * Detect a required Energy type for the discard, in either bracket notation ("[D] Energy")
     * or word form ("Darkness Energy"). Returns null for an unrestricted discard
     * ("Discard an Energy") so the general behaviour is unchanged. Colorless is treated as
     * unrestricted (any Energy can pay a Colorless requirement).
     */
    private String requiredEnergyType(String text) {
        if (containsAny(text, "[d] energy", "darkness energy", "dark energy")) return "Darkness";
        if (containsAny(text, "[w] energy", "water energy")) return "Water";
        if (containsAny(text, "[r] energy", "fire energy")) return "Fire";
        if (containsAny(text, "[g] energy", "grass energy")) return "Grass";
        if (containsAny(text, "[l] energy", "lightning energy")) return "Lightning";
        if (containsAny(text, "[p] energy", "psychic energy")) return "Psychic";
        if (containsAny(text, "[f] energy", "fighting energy")) return "Fighting";
        if (containsAny(text, "[m] energy", "metal energy")) return "Metal";
        if (containsAny(text, "[y] energy", "fairy energy")) return "Fairy";
        if (containsAny(text, "[n] energy", "dragon energy")) return "Dragon";
        return null;
    }

    private boolean discardsAllEnergy(String text) {
        return containsAny(text, "all energy", "all energies");
    }

    private boolean discardsAllAttachedCards(String text) {
        return containsAny(text, "all cards attached", "all attached cards");
    }

    private boolean discardsToolCards(String text) {
        return containsAny(text, "tool cards", "tool card", "pokemon tool", "pokémon tool");
    }

    private int discardCount(String text) {
        if (containsAny(text, " 4 ", " four ")) return 4;
        if (containsAny(text, " 3 ", " three ")) return 3;
        if (containsAny(text, " 2 ", " two ")) return 2;
        return 1;
    }

    private List<String> selectedEnergyIds(AttackContext context, List<AttachedEnergy> attached, int count) {
        if (count >= attached.size()) {
            return attached.stream().map(AttachedEnergy::cardId).toList();
        }
        if (context.selectedEnergyCardIds().isEmpty()) {
            return attached.stream().limit(count).map(AttachedEnergy::cardId).toList();
        }
        return context.selectedEnergyCardIds().stream()
            .filter(id -> attached.stream().anyMatch(e -> e.cardId().equals(id)))
            .limit(count)
            .toList();
    }
}
