package com.utn.pokemontcg.game.domain.attack;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AttackPatternAnalyzer {

    public AttackSpec analyze(String attackName, String attackText, int baseDamage) {
        String text = normalize((attackName == null ? "" : attackName) + " " + (attackText == null ? "" : attackText));
        List<AttackRequirementSpec> requirements = new ArrayList<>();
        List<AttackEffectSpec> effects = new ArrayList<>();

        addTargetRequirement(text, requirements);
        addHandRequirement(text, requirements);
        addDeckRequirement(text, requirements);
        addEnergySelectionRequirement(text, requirements);
        addConditionSelectionRequirement(text, requirements);
        addOptionalEffectRequirement(text, requirements);
        addOpponentAttackRequirement(text, requirements);

        addEffectPreviews(text, baseDamage, effects);

        return new AttackSpec(requirements, effects, coinFlipCount(text));
    }

    private void addTargetRequirement(String text, List<AttackRequirementSpec> requirements) {
        if (needsOpponentAttackSelection(text)) {
            return;
        }
        String scope = targetRequirementScope(text);
        if (scope == null) {
            return;
        }
        int max = targetCount(text);
        requirements.add(new AttackRequirementSpec(
            "SELECT_TARGET",
            scope,
            1,
            max,
            max,
            true,
            max > 1 ? "Selecciona los objetivos para el ataque" : "Selecciona un objetivo para el ataque"
        ));
    }

    /**
     * Number of Pokémon the attack targets. Two for effects like Hydro Bombard
     * ("60 damage to 2 of your opponent's Benched Pokémon") and Geomancy
     * ("attach up to 2 Fairy Energy ... to your Benched Pokémon"); one otherwise.
     */
    private int targetCount(String text) {
        if (text.contains("2 of your opponent")) {
            return 2;
        }
        if (text.contains("up to 2") && text.contains("benched") && text.contains("attach")) {
            return 2;
        }
        return 1;
    }

    private void addDeckRequirement(String text, List<AttackRequirementSpec> requirements) {
        if (needsOpponentDeckTopView(text)) {
            int count = opponentDeckTopCount(text);
            requirements.add(new AttackRequirementSpec(
                "VIEW_DECK_TOP",
                "OPPONENT_DECK_TOP",
                0,
                count,
                count,
                true,
                "Revisa las cartas superiores del mazo rival antes de resolver el ataque"
            ));
            return;
        }
        if (isTopDeckOrderText(text)) {
            int count = topDeckLookCount(text);
            requirements.add(new AttackRequirementSpec(
                "ORDER_DECK_TOP",
                "OWN_DECK_TOP",
                count,
                count,
                count,
                true,
                "Ordena las cartas superiores del mazo"
            ));
            return;
        }
        if (needsDeckSelection(text)) {
            int count = deckSelectionCount(text);
            requirements.add(new AttackRequirementSpec(
                "SELECT_DECK_CARDS",
                "OWN_DECK",
                1,
                count,
                count,
                true,
                "Selecciona cartas del mazo para el ataque"
            ));
        }
    }

    private void addHandRequirement(String text, List<AttackRequirementSpec> requirements) {
        if (needsOpponentHandSelection(text)) {
            int count = opponentHandSelectionCount(text);
            requirements.add(new AttackRequirementSpec(
                "SELECT_HAND_CARD",
                "OPPONENT_HAND",
                1,
                count,
                count,
                true,
                "Elige cartas de la mano rival para resolver el ataque"
            ));
            return;
        }
        if (needsOpponentHandView(text)) {
            requirements.add(new AttackRequirementSpec(
                "VIEW_HAND",
                "OPPONENT_HAND",
                0,
                0,
                0,
                true,
                "Revisa la mano rival antes de resolver el ataque"
            ));
            return;
        }
        if (needsOpponentHandConfirmation(text)) {
            requirements.add(new AttackRequirementSpec(
                "CONFIRM_HAND_EFFECT",
                "OPPONENT_HAND",
                0,
                0,
                0,
                true,
                "Confirma el efecto sobre la mano rival antes de resolver el ataque"
            ));
        }
    }

    private void addEnergySelectionRequirement(String text, List<AttackRequirementSpec> requirements) {
        if (!text.contains("discard") || !containsAny(text, "energy", "cards attached", "attached cards")) {
            return;
        }
        if (containsAny(text, "all energy", "all energies", "all cards attached", "all attached cards")) {
            return;
        }
        if (text.contains("top card of your deck") || text.contains("top card of the deck")) {
            return;
        }
        if (containsAny(text, "pokemon tool", "pokémon tool", "tool card", "item card", "stadium card")) {
            return;
        }
        if (!containsAny(text, "this pokemon", "this pokémon", "attached to this",
            "opponent's active", "opponent's pokemon", "opponent's pokémon",
            "defending pokemon", "defending pokémon", "that pokemon", "that pokémon")) {
            return;
        }
        String discardText = text.substring(text.indexOf("discard"));
        boolean fromOpponent = containsAny(discardText, "opponent's active", "opponent's pokemon", "opponent's pokémon", "defending pokemon", "defending pokémon", "that pokemon", "that pokémon");
        int count = discardCount(text);
        requirements.add(new AttackRequirementSpec(
            "SELECT_ATTACHED_ENERGY",
            fromOpponent ? "OPPONENT_ACTIVE" : "OWN_ACTIVE",
            count,
            count,
            count,
            true,
            "Elige energia adjunta para descartar"
        ));
    }

    private void addConditionSelectionRequirement(String text, List<AttackRequirementSpec> requirements) {
        if (!text.contains("choose either") || !containsAny(text, "poison", "burn", "sleep", "asleep", "confus", "paraly")) {
            return;
        }
        List<String> conditionOptions = new ArrayList<>();
        if (text.contains("poison")) conditionOptions.add("POISONED");
        if (text.contains("burn")) conditionOptions.add("BURNED");
        if (text.contains("sleep") || text.contains("asleep")) conditionOptions.add("ASLEEP");
        if (text.contains("confus")) conditionOptions.add("CONFUSED");
        if (text.contains("paraly")) conditionOptions.add("PARALYZED");
        if (conditionOptions.isEmpty()) {
            conditionOptions.addAll(List.of("POISONED", "BURNED", "ASLEEP", "CONFUSED", "PARALYZED"));
        }
        requirements.add(new AttackRequirementSpec(
            "SELECT_CONDITION",
            "STATUS_CONDITION",
            1,
            1,
            1,
            true,
            "Elige la condicion especial que aplicara el ataque",
            conditionOptions
        ));
    }

    private void addOptionalEffectRequirement(String text, List<AttackRequirementSpec> requirements) {
        if (!text.contains("you may")) {
            return;
        }
        requirements.add(new AttackRequirementSpec(
            "CONFIRM_OPTIONAL_EFFECT",
            "OPTIONAL_EFFECT",
            0,
            1,
            1,
            false,
            "Confirma si queres aplicar el efecto opcional"
        ));
    }

    private void addOpponentAttackRequirement(String text, List<AttackRequirementSpec> requirements) {
        if (!needsOpponentAttackSelection(text)) {
            return;
        }
        requirements.add(new AttackRequirementSpec(
            "SELECT_OPPONENT_ATTACK",
            "OPPONENT_ACTIVE",
            1,
            1,
            1,
            true,
            "Elige un ataque del Pokemon Activo rival"
        ));
    }

    private void addEffectPreviews(String text, int baseDamage, List<AttackEffectSpec> effects) {
        if (baseDamage > 0) addEffect(effects, "DAMAGE", "Daño base al Pokemon Activo rival: " + baseDamage);
        if (text.contains("flip")) addEffect(effects, "COIN_FLIP", "Lanzamiento de moneda x" + coinFlipCount(text));
        if (text.contains("bench") && text.contains("damage")) addEffect(effects, "BENCH_DAMAGE", "Daño a Pokemon en Banca");
        if (containsAny(text, "poison", "burn", "sleep", "asleep", "confus", "paraly")) addEffect(effects, "STATUS", "Aplica condicion especial");
        if (containsAny(text, "more damage", "+") || text.contains("damage times") || text.contains("for each damage counter") || text.contains("for each different type")) addEffect(effects, "CONDITIONAL_DAMAGE", "Modifica el daño por condicion");
        if (text.contains("discard") && containsAny(text, "energy", "cards attached", "attached cards")) addEffect(effects, "DISCARD_ENERGY", "Descarta energia o cartas adjuntas");
        if (isEnergyAttachText(text)) addEffect(effects, "ATTACH_ENERGY", "Une energia por efecto del ataque");
        if (containsAny(text, "heal", "remove") && containsAny(text, "damage", "special conditions")) addEffect(effects, "HEAL", "Cura daño o remueve condiciones");
        if (containsAny(text, "switch", "switches")) addEffect(effects, "SWITCH", "Cambia Pokemon Activo con Banca");
        if (text.contains("move an energy")) addEffect(effects, "MOVE_ENERGY", "Mueve energia adjunta");
        if (text.contains("draw")) addEffect(effects, "DRAW", "Roba cartas");
        if (text.contains("search your deck")) addEffect(effects, "SEARCH", "Busca cartas en el mazo");
        if (needsOpponentHandSelection(text) || needsOpponentHandView(text) || needsOpponentHandConfirmation(text)) addEffect(effects, "OPPONENT_HAND", "Revisa o afecta la mano rival");
        if (needsOpponentDeckTopView(text)) addEffect(effects, "OPPONENT_DECK", "Revisa o afecta el mazo rival");
        if (needsOpponentAttackSelection(text)) addEffect(effects, "PERSISTENT_EFFECT", "Elige un ataque rival para bloquear en el proximo turno");
        if (containsAny(text, "during your opponent's next turn", "during your next turn", "can't retreat", "can't attack", "cannot attack", "prevent all damage", "prevent all effects", "can't play any supporter", "has no abilities")) addEffect(effects, "PERSISTENT_EFFECT", "Deja un efecto persistente");
        if (containsAny(text, "damage to itself", "damage to this pokemon", "damage to this pokémon")) addEffect(effects, "SELF_DAMAGE", "Hace daño al atacante");
        if (containsAny(text, "put 2 damage counters each of your opponent", "damage counters each of your opponent")) addEffect(effects, "OPPONENT_DAMAGE_ALL", "Daño a todos los Pokemon del rival");
        if (text.contains("remaining hp")) addEffect(effects, "HP_SET", "Modifica el HP restante de los Activos");
        if (containsAny(text, "put a card from your discard", "from your discard pile into your hand")) addEffect(effects, "DISCARD_RECOVERY", "Recupera de la pila de descarte");
        if (text.contains("for each heads") && baseDamage > 0) addEffect(effects, "CONDITIONAL_DAMAGE", "Daño condicional por lanzamiento de moneda");
    }

    private void addEffect(List<AttackEffectSpec> effects, String type, String label) {
        if (effects.stream().noneMatch(effect -> effect.type().equals(type))) {
            effects.add(new AttackEffectSpec(type, label));
        }
    }

    private String targetRequirementScope(String text) {
        if (text.contains("number of your benched")) return null;
        boolean hitsAll = text.contains("each of your") || text.contains("each of your opponent")
            || text.contains("all of your") || text.contains("cada uno de tus") || text.contains("cada uno de los");
        // Heal one of your own Pokémon (active or bench), e.g. Sweet Scent "Heal 30 damage from 1 of
        // your Pokémon." Bench-only heals fall through to the OWN_BENCH rule below.
        if (!hitsAll
            && containsAny(text, "heal", "remove")
            && containsAny(text, "from 1 of your pokemon", "from one of your pokemon")
            && !text.contains("benched")) {
            return "OWN_POKEMON";
        }
        // Attach Energy to one of your own Pokémon (active or bench), e.g. Gather Energy.
        if (!hitsAll
            && isEnergyAttachText(text)
            && containsAny(text, "to 1 of your pokemon", "to one of your pokemon")
            && !text.contains("benched")) {
            return "OWN_POKEMON";
        }
        if (containsAny(text, "one of your benched", "1 of your benched", "your benched pokemon", "your benched pokémon", "switch this pokemon", "move an energy from this", "tu banca", "tus pokemon en banca", "tus pokémon en banca")) return hitsAll ? null : "OWN_BENCH";
        if (containsAny(text, "choose 1 of your opponent", "choose one of your opponent")) {
            String scope = text.contains("benched") ? "OPPONENT_BENCH" : "OPPONENT_POKEMON";
            return hitsAll && "OPPONENT_BENCH".equals(scope) ? null : scope;
        }
        if (containsAny(text, "opponent's benched", "opponent benched", "switch 1 of your opponent", "your opponent switches", "move an energy attached to your opponent", "banca de tu oponente", "pokemon en banca de tu oponente", "pokémon en banca de tu oponente")) return hitsAll ? null : "OPPONENT_BENCH";
        if (containsAny(text, "choose 1 pokemon", "choose one pokemon")) return "ANY_POKEMON";
        return null;
    }

    private boolean needsDeckSelection(String text) {
        return containsAny(text, "search your deck", "from your deck");
    }

    private boolean isEnergyAttachText(String text) {
        return text.contains("attach")
            && text.contains("energy")
            && containsAny(text, "from your deck", "search your deck", "from your discard", "from your hand", "from the discard", "from discard");
    }

    private boolean needsOpponentHandSelection(String text) {
        if (!containsAny(text, "opponent's hand", "opponent hand", "hand of your opponent", "mano de tu oponente", "mano rival")) {
            return false;
        }
        if (text.contains("random") || text.contains("for each tails")) {
            return false;
        }
        return containsAny(
            text,
            "choose a card",
            "choose 1 card",
            "choose one card",
            "discard a card",
            "discard 1 card",
            "discard one card",
            "discards a card",
            "shuffle a card",
            "elige una carta",
            "elige 1 carta",
            "una carta de la mano",
            "una cartas de la mano",
            "1 carta de la mano",
            "descarta una carta",
            "descarta 1 carta"
        );
    }

    private boolean needsOpponentHandView(String text) {
        return containsAny(text, "opponent's hand", "opponent hand", "hand of your opponent", "mano de tu oponente", "mano rival")
            && containsAny(text, "look at", "reveal", "reveals", "mira", "revela");
    }

    private boolean needsOpponentHandConfirmation(String text) {
        return containsAny(
            text,
            "random card from your opponent",
            "random card from your opponent's hand",
            "discards a card from your opponent's hand",
            "discard a random card",
            "for each tails",
            "carta al azar de la mano"
        ) && (text.contains("opponent") || text.contains("rival") || text.contains("oponente"));
    }

    private int opponentHandSelectionCount(String text) {
        Matcher explicit = Pattern.compile("(\\d+|one|two|three|four|five)\\s+cards?\\s+from\\s+your\\s+opponent'?s?\\s+hand").matcher(text);
        if (explicit.find()) return numberWord(explicit.group(1));
        Matcher spanish = Pattern.compile("(\\d+)\\s+cartas?\\s+de\\s+la\\s+mano").matcher(text);
        if (spanish.find()) return Integer.parseInt(spanish.group(1));
        return 1;
    }

    private boolean needsOpponentDeckTopView(String text) {
        return containsAny(
            text,
            "top card of your opponent's deck",
            "top cards of your opponent's deck",
            "top card of the opponent's deck",
            "top cards of the opponent's deck",
            "top card of your opponent deck",
            "top cards of your opponent deck",
            "discard the top card of your opponent",
            "mazo de tu oponente",
            "mazo rival"
        ) && containsAny(text, "discard", "look", "reveal", "mira", "descarta", "revela");
    }

    private boolean needsOpponentAttackSelection(String text) {
        boolean mentionsOpponent = text.contains("opponent") || text.contains("rival") || text.contains("oponente");
        if (!mentionsOpponent) return false;
        boolean mentionsAttack = text.contains("attack") || text.contains("ataque");
        if (!mentionsAttack) return false;
        boolean mentionsBlock = text.contains("can't be used") || text.contains("cannot be used")
            || text.contains("can't use that attack") || text.contains("cannot use that attack")
            || text.contains("that pokémon can't use") || text.contains("that pokemon can't use")
            || text.contains("that pokémon cannot use") || text.contains("that pokemon cannot use")
            || text.contains("no puede usar ese ataque") || text.contains("no puede ser usado");
        return mentionsBlock;
    }

    private int opponentDeckTopCount(String text) {
        Matcher explicit = Pattern.compile("top\\s+(\\d+|one|two|three|four|five)\\s+cards?").matcher(text);
        if (explicit.find()) return numberWord(explicit.group(1));
        Matcher spanish = Pattern.compile("(\\d+)\\s+cartas?").matcher(text);
        if (spanish.find() && containsAny(text, "mazo de tu oponente", "mazo rival")) return Integer.parseInt(spanish.group(1));
        return 1;
    }

    private int deckSelectionCount(String text) {
        if (containsAny(text, "up to 2", "two ", " 2 ")) return 2;
        if (containsAny(text, "3 different", "three ", " 3 ")) return 3;
        return 1;
    }

    private boolean isTopDeckOrderText(String text) {
        boolean english = text.contains("look at the top")
            && text.contains("cards of your deck")
            && text.contains("put them back");
        boolean spanish = containsAny(text, "mira las", "mira los", "mira la")
            && text.contains("cartas")
            && text.contains("mazo")
            && containsAny(text, "arriba", "superiores")
            && text.contains("orden");
        return english || spanish;
    }

    private int topDeckLookCount(String text) {
        Matcher spanish = Pattern.compile("(\\d+)\\s+cartas?").matcher(text);
        if (spanish.find() && text.contains("mazo")) return Integer.parseInt(spanish.group(1));
        Matcher english = Pattern.compile("top\\s+(\\d+|one|two|three|four|five)\\s+cards?\\s+of\\s+your\\s+deck").matcher(text);
        if (!english.find()) return 1;
        return numberWord(english.group(1));
    }

    private int numberWord(String value) {
        return switch (value) {
            case "one" -> 1;
            case "two" -> 2;
            case "three" -> 3;
            case "four" -> 4;
            case "five" -> 5;
            default -> Integer.parseInt(value);
        };
    }

    private int coinFlipCount(String text) {
        if (!text.contains("flip")) return 0;
        Matcher explicit = Pattern.compile("flips?\\s+(\\d+)\\s+coins").matcher(text);
        if (explicit.find()) return Integer.parseInt(explicit.group(1));
        return 1;
    }

    private int discardCount(String text) {
        if (containsAny(text, " 4 ", " four ")) return 4;
        if (containsAny(text, " 3 ", " three ")) return 3;
        if (containsAny(text, " 2 ", " two ")) return 2;
        return 1;
    }

    private boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) return true;
        }
        return false;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('’', '\'')
            .toLowerCase(Locale.ROOT);
    }
}
