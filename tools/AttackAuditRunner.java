import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standalone audit: reads xy1_cards.json from card-service API,
 * runs ALL attacks through AttackPatternAnalyzer logic,
 * generates attack-audit.md report.
 * 
 * Usage: java AttackAuditRunner.java [input.json]
 */
public class AttackAuditRunner {

    static ObjectMapper mapper = new ObjectMapper();

    record AttackData(String name, String text, String damage) {}
    record CardData(String id, String name, String supertype, List<AttackData> attacks) {}
    record AuditEntry(String cardId, String cardName, String attackName, String attackText, int baseDamage,
                      List<String> requirements, List<String> effects, int coinFlipCount, List<String> warnings) {}

    public static void main(String[] args) throws Exception {
        String inputPath = args.length > 0 ? args[0] : System.getenv("TEMP") + "\\xy1_cards.json";
        String json = Files.readString(Path.of(inputPath));

        Map<String, Object> response = mapper.readValue(json, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawCards = (List<Map<String, Object>>) response.get("cards");

        List<CardData> cards = new ArrayList<>();
        for (Map<String, Object> rc : rawCards) {
            String supertype = (String) rc.get("supertype");
            if (!"Pokémon".equalsIgnoreCase(supertype)) continue;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawAttacks = (List<Map<String, Object>>) rc.get("attacks");
            if (rawAttacks == null || rawAttacks.isEmpty()) continue;
            List<AttackData> attacks = new ArrayList<>();
            for (Map<String, Object> ra : rawAttacks) {
                attacks.add(new AttackData(
                    (String) ra.get("name"),
                    (String) ra.get("text"),
                    (String) ra.get("damage")
                ));
            }
            cards.add(new CardData((String) rc.get("id"), (String) rc.get("name"), supertype, attacks));
        }

        List<AuditEntry> entries = new ArrayList<>();
        Set<String> orphanAttacks = new LinkedHashSet<>();
        int totalAttacks = 0;

        for (CardData card : cards) {
            for (AttackData atk : card.attacks) {
                totalAttacks++;
                String normText = normalize(atk.name + " " + (atk.text != null ? atk.text : ""));
                int baseDmg = parseDamage(atk.damage);
                List<String> reqs = detectRequirements(normText);
                List<String> effs = detectEffects(normText, baseDmg);
                int flips = coinFlipCount(normText);
                List<String> warnings = new ArrayList<>();

                if (reqs.isEmpty() && effs.isEmpty() && baseDmg == 0) {
                    warnings.add("ORPHAN: no requirements, no effects, no damage");
                    orphanAttacks.add(card.name + " | " + atk.name + " | " + atk.text);
                }
                if (normText.contains("flip") && flips == 0) {
                    warnings.add("Has 'flip' but coinFlipCount=0");
                }
                if (atk.text != null && atk.text.toLowerCase().contains("discard")
                    && reqs.stream().noneMatch(r -> r.contains("DECK") || r.contains("ENERGY") || r.contains("HAND"))
                    && effs.stream().noneMatch(e -> e.equals("DISCARD_ENERGY") || e.equals("OPPONENT_DECK"))) {
                    warnings.add("Has 'discard' but no discard-related requirement/effect detected");
                }
                if (normText.contains("draw") && effs.stream().noneMatch(e -> e.equals("DRAW"))) {
                    warnings.add("Has 'draw' but no DRAW effect");
                }
                if ((normText.contains("heal") || normText.contains("remove")) && effs.stream().noneMatch(e -> e.equals("HEAL"))) {
                    warnings.add("Has 'heal'/'remove' but no HEAL effect");
                }

                entries.add(new AuditEntry(card.id, card.name, atk.name, atk.text, baseDmg, reqs, effs, flips, warnings));
            }
        }

        // ─── Generate markdown ──────────────────────────────────────────────
        List<AuditEntry> warned = entries.stream().filter(e -> !e.warnings().isEmpty()).toList();
        Path out = Path.of("attack-audit.md");
        try (PrintWriter w = new PrintWriter(new FileWriter(out.toFile()))) {
            w.println("# Attack Audit Report — xy1 Set");
            w.println();
            w.println("**Pokémon cards:** " + cards.size());
            w.println("**Total attacks:** " + totalAttacks);
            w.println("**Orphan attacks:** " + orphanAttacks.size());
            w.println("**Generated:** " + java.time.Instant.now());
            w.println();

            if (!orphanAttacks.isEmpty()) {
                w.println("## ⚠ Orphan Attacks (no requirements, no effects, no damage)");
                w.println();
                w.println("| # | Card | Attack | Text |");
                w.println("|---|---|---|---|");
                int oi = 0;
                for (String oa : orphanAttacks) {
                    oi++;
                    String[] parts = oa.split(" \\| ", 3);
                    w.println("| " + oi + " | " + parts[0] + " | " + parts[1] + " | " + escapeMd(parts.length > 2 ? parts[2] : "") + " |");
                }
                w.println();
            }

            // ─── Warnings summary ───────────────────────────────────────────
            if (!warned.isEmpty()) {
                w.println("## ⚡ Attacks with Warnings");
                w.println();
                for (AuditEntry e : warned) {
                    w.println("### " + e.cardName() + " — " + e.attackName());
                    w.println("- Dmg: " + e.baseDamage() + " | Flips: " + e.coinFlipCount());
                    w.println("- Reqs: " + (e.requirements().isEmpty() ? "—" : String.join(", ", e.requirements())));
                    w.println("- Effects: " + (e.effects().isEmpty() ? "—" : String.join(", ", e.effects())));
                    for (String warn : e.warnings()) {
                        w.println("- ⚠ " + warn);
                    }
                    w.println("> \"" + escapeMd(e.attackText() != null ? e.attackText() : "(no text)") + "\"");
                    w.println();
                }
            }

            // ─── Full table ─────────────────────────────────────────────────
            w.println("## Complete Attack Inventory");
            w.println();
            w.println("| # | Card | Attack | Dmg | Requirements | Effects | Flips | Warnings |");
            w.println("|---|---|---|---|---|---|---|---|");
            int idx = 0;
            for (AuditEntry e : entries) {
                idx++;
                String reqStr = e.requirements().isEmpty() ? "—" : String.join("<br>", e.requirements());
                String effStr = e.effects().isEmpty() ? "—" : String.join("<br>", e.effects());
                String warnStr = e.warnings().isEmpty() ? "" : "⚠ " + String.join("<br>⚠ ", e.warnings());
                w.println("| " + idx + " | " + e.cardName() + " | " + e.attackName()
                    + " | " + e.baseDamage() + " | " + reqStr + " | " + effStr
                    + " | " + e.coinFlipCount() + " | " + warnStr + " |");
            }
        }

        System.out.println("=== ATTACK AUDIT COMPLETE ===");
        System.out.println("Cards: " + cards.size());
        System.out.println("Attacks: " + totalAttacks);
        System.out.println("Orphans: " + orphanAttacks.size());
        System.out.println("Warnings: " + warned.size());
        System.out.println("Report: " + out.toAbsolutePath());

        if (!orphanAttacks.isEmpty()) {
            System.out.println("\n--- ORPHAN ATTACKS ---");
            for (String oa : orphanAttacks) System.out.println("  " + oa);
        }
    }

    // ─── Detection logic (mirrors AttackPatternAnalyzer) ────────────────────

    static String normalize(String v) {
        return Normalizer.normalize(v == null ? "" : v, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replace('’', '\'').toLowerCase(Locale.ROOT);
    }

    static boolean containsAny(String v, String... frags) {
        for (String f : frags) if (v.contains(f)) return true;
        return false;
    }

    static int parseDamage(String d) {
        if (d == null || d.isBlank()) return 0;
        try { return Integer.parseInt(d.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    static List<String> detectRequirements(String text) {
        List<String> reqs = new ArrayList<>();
        String scope = detectTargetScope(text);
        if (scope != null) reqs.add("SELECT_TARGET(" + scope + ")");
        if (needsOpponentDeckTopView(text)) reqs.add("VIEW_DECK_TOP(OPPONENT_DECK_TOP)");
        if (isTopDeckOrderText(text)) reqs.add("ORDER_DECK_TOP(OWN_DECK_TOP)");
        if (needsDeckSelection(text)) reqs.add("SELECT_DECK_CARDS(OWN_DECK)");
        if (needsOpponentHandSelection(text)) reqs.add("SELECT_HAND_CARD(OPPONENT_HAND)");
        if (needsOpponentHandView(text)) reqs.add("VIEW_HAND(OPPONENT_HAND)");
        if (needsOpponentHandConfirmation(text)) reqs.add("CONFIRM_HAND_EFFECT(OPPONENT_HAND)");
        if (needsEnergySelection(text)) {
            boolean fromOpp = detectEnergyTarget(text);
            reqs.add("SELECT_ATTACHED_ENERGY(" + (fromOpp ? "OPPONENT_ACTIVE" : "OWN_ACTIVE") + ")");
        }
        if (needsConditionSelection(text)) reqs.add("SELECT_CONDITION(STATUS_CONDITION)");
        if (text.contains("you may")) reqs.add("CONFIRM_OPTIONAL_EFFECT(OPTIONAL_EFFECT)");
        if (needsOpponentAttackSelection(text)) reqs.add("SELECT_OPPONENT_ATTACK(OPPONENT_ACTIVE)");
        return reqs;
    }

    static List<String> detectEffects(String text, int baseDamage) {
        List<String> effs = new ArrayList<>();
        if (baseDamage > 0) effs.add("DAMAGE");
        if (text.contains("flip")) effs.add("COIN_FLIP");
        if (text.contains("bench") && text.contains("damage")) effs.add("BENCH_DAMAGE");
        if (containsAny(text, "poison", "burn", "sleep", "asleep", "confus", "paraly")) effs.add("STATUS");
        if (containsAny(text, "more damage", "+") || text.contains("damage times")
            || text.contains("for each damage counter") || text.contains("for each different type")
            || text.contains("for each heads"))
            effs.add("CONDITIONAL_DAMAGE");
        if (text.contains("discard") && containsAny(text, "energy", "cards attached", "attached cards"))
            effs.add("DISCARD_ENERGY");
        if (text.contains("attach") && text.contains("energy")) effs.add("ATTACH_ENERGY");
        if (containsAny(text, "heal", "remove") && containsAny(text, "damage", "special conditions"))
            effs.add("HEAL");
        if (containsAny(text, "switch", "switches")) effs.add("SWITCH");
        if (text.contains("move an energy")) effs.add("MOVE_ENERGY");
        if (text.contains("draw")) effs.add("DRAW");
        if (text.contains("search your deck")) effs.add("SEARCH");
        if (needsOpponentHandSelection(text) || needsOpponentHandView(text) || needsOpponentHandConfirmation(text))
            effs.add("OPPONENT_HAND");
        if (needsOpponentDeckTopView(text)) effs.add("OPPONENT_DECK");
        if (containsAny(text, "during your opponent's next turn", "during your next turn",
            "can't retreat", "can't attack", "cannot attack", "prevent all damage",
            "prevent all effects", "can't play any supporter", "has no abilities"))
            effs.add("PERSISTENT_EFFECT");
        if (needsOpponentAttackSelection(text)) effs.add("PERSISTENT_EFFECT");
        if (containsAny(text, "damage to itself", "damage to this pokemon", "damage to this pokémon"))
            effs.add("SELF_DAMAGE");
        if (containsAny(text, "put 2 damage counters each of your opponent", "damage counters each of your opponent"))
            effs.add("OPPONENT_DAMAGE_ALL");
        if (text.contains("remaining hp")) effs.add("HP_SET");
        if (containsAny(text, "put a card from your discard", "from your discard pile into your hand"))
            effs.add("DISCARD_RECOVERY");
        return effs;
    }

    static int coinFlipCount(String text) {
        if (!text.contains("flip")) return 0;
        Matcher m = Pattern.compile("flip\\s+(\\d+)\\s+coins").matcher(text);
        if (m.find()) return Integer.parseInt(m.group(1));
        return 1;
    }

    // ─── Target ─────────────────────────────────────────────────────────────

    static String detectTargetScope(String text) {
        if (needsOpponentAttackSelection(text)) return null;
        boolean hitsAll = text.contains("each of your") || text.contains("each of your opponent")
            || text.contains("all of your") || text.contains("cada uno de tus");
        if (containsAny(text, "one of your benched", "1 of your benched", "your benched pokemon",
            "your benched pokémon", "switch this pokemon", "move an energy from this",
            "tu banca", "tus pokemon en banca", "tus pokémon en banca"))
            return hitsAll ? null : "OWN_BENCH";
        if (containsAny(text, "choose 1 of your opponent", "choose one of your opponent")) {
            String s = text.contains("benched") ? "OPPONENT_BENCH" : "OPPONENT_POKEMON";
            return hitsAll && "OPPONENT_BENCH".equals(s) ? null : s;
        }
        if (containsAny(text, "opponent's benched", "opponent benched", "switch 1 of your opponent",
            "your opponent switches", "move an energy attached to your opponent",
            "banca de tu oponente", "pokemon en banca de tu oponente"))
            return hitsAll ? null : "OPPONENT_BENCH";
        if (containsAny(text, "choose 1 pokemon", "choose one pokemon")) return "ANY_POKEMON";
        return null;
    }

    // ─── Deck ───────────────────────────────────────────────────────────────

    static boolean needsDeckSelection(String text) {
        return containsAny(text, "search your deck", "from your deck");
    }

    static boolean needsOpponentDeckTopView(String text) {
        return containsAny(text,
            "top card of your opponent's deck", "top cards of your opponent's deck",
            "top card of the opponent's deck", "top cards of the opponent's deck",
            "top card of your opponent deck", "top cards of your opponent deck",
            "discard the top card of your opponent",
            "mazo de tu oponente", "mazo rival")
            && containsAny(text, "discard", "look", "reveal", "mira", "descarta", "revela");
    }

    static boolean isTopDeckOrderText(String text) {
        return text.contains("look at the top") && text.contains("cards of your deck") && text.contains("put them back")
            || containsAny(text, "mira las", "mira los", "mira la") && text.contains("cartas")
                && text.contains("mazo") && containsAny(text, "arriba", "superiores") && text.contains("orden");
    }

    // ─── Hand ───────────────────────────────────────────────────────────────

    static boolean needsOpponentHandSelection(String text) {
        if (!containsAny(text, "opponent's hand", "opponent hand", "hand of your opponent",
            "mano de tu oponente", "mano rival")) return false;
        if (text.contains("random") || text.contains("for each tails")) return false;
        return containsAny(text, "choose a card", "choose 1 card", "choose one card",
            "discard a card", "discard 1 card", "discard one card", "discards a card",
            "shuffle a card", "elige una carta", "elige 1 carta",
            "descarta una carta", "descarta 1 carta");
    }

    static boolean needsOpponentHandView(String text) {
        return containsAny(text, "opponent's hand", "opponent hand", "hand of your opponent",
            "mano de tu oponente", "mano rival")
            && containsAny(text, "look at", "reveal", "reveals", "mira", "revela");
    }

    static boolean needsOpponentHandConfirmation(String text) {
        return containsAny(text,
            "random card from your opponent", "random card from your opponent's hand",
            "discards a card from your opponent's hand", "discard a random card",
            "for each tails", "carta al azar de la mano")
            && (text.contains("opponent") || text.contains("rival") || text.contains("oponente"));
    }

    // ─── Energy ─────────────────────────────────────────────────────────────

    static boolean needsEnergySelection(String text) {
        if (!text.contains("discard") || !containsAny(text, "energy", "cards attached", "attached cards"))
            return false;
        if (containsAny(text, "all energy", "all energies", "all cards attached", "all attached cards"))
            return false;
        if (text.contains("top card of your deck") || text.contains("top card of the deck"))
            return false;
        return containsAny(text, "this pokemon", "this pokémon", "attached to this",
            "opponent's active", "opponent's pokemon", "opponent's pokémon",
            "defending pokemon", "defending pokémon", "that pokemon", "that pokémon");
    }

    static boolean detectEnergyTarget(String text) {
        String dt = text.substring(text.indexOf("discard"));
        return containsAny(dt, "opponent's active", "opponent's pokemon", "opponent's pokémon",
            "defending pokemon", "defending pokémon", "that pokemon", "that pokémon");
    }

    // ─── Condition ──────────────────────────────────────────────────────────

    static boolean needsConditionSelection(String text) {
        return text.contains("choose either")
            && containsAny(text, "poison", "burn", "sleep", "asleep", "confus", "paraly");
    }

    // ─── Opponent attack ────────────────────────────────────────────────────

    static boolean needsOpponentAttackSelection(String text) {
        if (!text.contains("opponent") && !text.contains("rival") && !text.contains("oponente")) return false;
        if (!text.contains("attack") && !text.contains("ataque")) return false;
        return containsAny(text, "can't be used", "cannot be used",
            "can't use that attack", "cannot use that attack",
            "that pokémon can't use", "that pokemon can't use",
            "that pokémon cannot use", "that pokemon cannot use",
            "no puede usar ese ataque", "no puede ser usado");
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    static String escapeMd(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }
}
