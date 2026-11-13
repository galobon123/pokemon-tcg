package com.utn.pokemontcg.card;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.card.infrastructure.persistence.CardEntity;
import com.utn.pokemontcg.card.infrastructure.persistence.CardJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;

/**
 * Audits ALL attacks from the card database through AttackPatternAnalyzer-like logic.
 * Generates a comprehensive markdown report of all attacks and their detected patterns.
 *
 * This test copies the analyzer's detection logic inline to avoid cross-module dependencies.
 *
 * <p>The static initializer forces UTC timezone because the VS Code Test Runner for Java
 * does not apply the Maven surefire {@code -Duser.timezone=UTC} JVM argument,
 * and PostgreSQL rejects the Windows regional timezone {@code America/Buenos_Aires}.</p>
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.datasource.url=jdbc:postgresql://localhost:5433/pokemontcg",
    "spring.datasource.username=postgres",
    "spring.datasource.password=postgres",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
    "spring.jpa.database=POSTGRESQL"
})
class AttackAuditTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.setProperty("user.timezone", "UTC");
    }

    @Autowired
    private CardJpaRepository cardJpaRepository;

    private static final ObjectMapper mapper = new ObjectMapper();

    // ─── Records matching AttackPatternAnalyzer output ──────────────────────

    record AttackData(String name, String text, String damage, List<String> cost, int convertedEnergyCost) {}

    record AuditEntry(String cardId, String cardName, String attackName, String attackText, int baseDamage,
                      List<String> requirements, List<String> effects, int coinFlipCount,
                      List<String> warnings) {}

    // ─── Detection methods (mirror AttackPatternAnalyzer logic) ─────────────

    private static String normalize(String value) {
        return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('’', '\'')
            .toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... fragments) {
        for (String f : fragments) if (value.contains(f)) return true;
        return false;
    }

    private static int parseDamage(String d) {
        if (d == null || d.isBlank()) return 0;
        try { return Integer.parseInt(d.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    // Pattern detection (simplified version matching analyzer categories)
    private static List<String> detectRequirements(String text, int baseDamage) {
        List<String> reqs = new ArrayList<>();

        // SELECT_TARGET
        String scope = detectTargetScope(text);
        if (scope != null) reqs.add("SELECT_TARGET(" + scope + ")");

        // Deck requirements
        if (needsOpponentDeckTopView(text)) reqs.add("VIEW_DECK_TOP(OPPONENT_DECK_TOP)");
        if (isTopDeckOrderText(text)) reqs.add("ORDER_DECK_TOP(OWN_DECK_TOP)");
        if (needsDeckSelection(text)) reqs.add("SELECT_DECK_CARDS(OWN_DECK)");

        // Hand requirements
        if (needsOpponentHandSelection(text)) reqs.add("SELECT_HAND_CARD(OPPONENT_HAND)");
        if (needsOpponentHandView(text)) reqs.add("VIEW_HAND(OPPONENT_HAND)");
        if (needsOpponentHandConfirmation(text)) reqs.add("CONFIRM_HAND_EFFECT(OPPONENT_HAND)");

        // Energy
        if (needsEnergySelection(text)) {
            boolean fromOpponent = detectEnergyTarget(text);
            reqs.add("SELECT_ATTACHED_ENERGY(" + (fromOpponent ? "OPPONENT_ACTIVE" : "OWN_ACTIVE") + ")");
        }

        // Condition
        if (needsConditionSelection(text)) reqs.add("SELECT_CONDITION(STATUS_CONDITION)");

        // Optional effect
        if (text.contains("you may")) reqs.add("CONFIRM_OPTIONAL_EFFECT(OPTIONAL_EFFECT)");

        // Opponent attack
        if (needsOpponentAttackSelection(text)) reqs.add("SELECT_OPPONENT_ATTACK(OPPONENT_ACTIVE)");

        return reqs;
    }

    private static List<String> detectEffects(String text, int baseDamage) {
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

    private static int coinFlipCount(String text) {
        if (!text.contains("flip")) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("flip\\s+(\\d+)\\s+coins").matcher(text);
        if (m.find()) return Integer.parseInt(m.group(1));
        return 1;
    }

    // ─── Target scope ───────────────────────────────────────────────────────

    private static String detectTargetScope(String text) {
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

    // ─── Deck detection ─────────────────────────────────────────────────────

    private static boolean needsDeckSelection(String text) {
        return containsAny(text, "search your deck", "from your deck");
    }

    private static boolean needsOpponentDeckTopView(String text) {
        return containsAny(text,
            "top card of your opponent's deck", "top cards of your opponent's deck",
            "top card of the opponent's deck", "top cards of the opponent's deck",
            "top card of your opponent deck", "top cards of your opponent deck",
            "discard the top card of your opponent",
            "mazo de tu oponente", "mazo rival")
            && containsAny(text, "discard", "look", "reveal", "mira", "descarta", "revela");
    }

    private static boolean isTopDeckOrderText(String text) {
        boolean english = text.contains("look at the top") && text.contains("cards of your deck")
            && text.contains("put them back");
        boolean spanish = containsAny(text, "mira las", "mira los", "mira la")
            && text.contains("cartas") && text.contains("mazo")
            && containsAny(text, "arriba", "superiores") && text.contains("orden");
        return english || spanish;
    }

    // ─── Hand detection ─────────────────────────────────────────────────────

    private static boolean needsOpponentHandSelection(String text) {
        if (!containsAny(text, "opponent's hand", "opponent hand", "hand of your opponent",
            "mano de tu oponente", "mano rival")) return false;
        if (text.contains("random") || text.contains("for each tails")) return false;
        return containsAny(text, "choose a card", "choose 1 card", "choose one card",
            "discard a card", "discard 1 card", "discard one card", "discards a card",
            "shuffle a card", "elige una carta", "elige 1 carta",
            "descarta una carta", "descarta 1 carta");
    }

    private static boolean needsOpponentHandView(String text) {
        return containsAny(text, "opponent's hand", "opponent hand", "hand of your opponent",
            "mano de tu oponente", "mano rival")
            && containsAny(text, "look at", "reveal", "reveals", "mira", "revela");
    }

    private static boolean needsOpponentHandConfirmation(String text) {
        return containsAny(text,
            "random card from your opponent", "random card from your opponent's hand",
            "discards a card from your opponent's hand", "discard a random card",
            "for each tails", "carta al azar de la mano")
            && (text.contains("opponent") || text.contains("rival") || text.contains("oponente"));
    }

    // ─── Energy detection ───────────────────────────────────────────────────

    private static boolean needsEnergySelection(String text) {
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

    private static boolean detectEnergyTarget(String text) {
        String discardText = text.substring(text.indexOf("discard"));
        return containsAny(discardText, "opponent's active", "opponent's pokemon", "opponent's pokémon",
            "defending pokemon", "defending pokémon", "that pokemon", "that pokémon");
    }

    // ─── Condition detection ────────────────────────────────────────────────

    private static boolean needsConditionSelection(String text) {
        return text.contains("choose either")
            && containsAny(text, "poison", "burn", "sleep", "asleep", "confus", "paraly");
    }

    // ─── Opponent attack detection ──────────────────────────────────────────

    private static boolean needsOpponentAttackSelection(String text) {
        boolean mentionsOpponent = text.contains("opponent") || text.contains("rival") || text.contains("oponente");
        boolean mentionsAttack = text.contains("attack") || text.contains("ataque");
        boolean mentionsBlock = text.contains("can't be used") || text.contains("cannot be used")
            || text.contains("can't use") || text.contains("cannot use")
            || text.contains("no puede usar") || text.contains("no puede ser usado");
        return mentionsOpponent && mentionsAttack && mentionsBlock;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static List<AttackData> parseAttacks(String attacksJson) {
        if (attacksJson == null || attacksJson.isBlank()) return List.of();
        try {
            List<Map<String, Object>> raw = mapper.readValue(attacksJson, new TypeReference<>() {});
            List<AttackData> result = new ArrayList<>();
            for (Map<String, Object> a : raw) {
                String name = (String) a.getOrDefault("name", "");
                String text = (String) a.getOrDefault("text", "");
                String damage = (String) a.getOrDefault("damage", null);
                @SuppressWarnings("unchecked")
                List<String> cost = (List<String>) a.getOrDefault("cost", List.of());
                int conv = a.containsKey("convertedEnergyCost") ? ((Number) a.get("convertedEnergyCost")).intValue() : 0;
                result.add(new AttackData(name, text, damage, cost, conv));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String escapeMd(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }

    // ─── Test ───────────────────────────────────────────────────────────────

    @Test
    void auditAllAttacks() throws Exception {
        List<CardEntity> cards = cardJpaRepository.findBySetId("xy1");
        List<AuditEntry> entries = new ArrayList<>();
        Set<String> emptyAttacks = new LinkedHashSet<>();

        for (CardEntity card : cards) {
            if (!"Pokémon".equalsIgnoreCase(card.getSupertype())) continue;
            List<AttackData> attacks = parseAttacks(card.getAttacks());
            if (attacks.isEmpty()) continue;

            for (AttackData atk : attacks) {
                String normText = normalize(atk.name() + " " + (atk.text() != null ? atk.text() : ""));
                int baseDmg = parseDamage(atk.damage());
                List<String> reqs = detectRequirements(normText, baseDmg);
                List<String> effs = detectEffects(normText, baseDmg);
                int flips = coinFlipCount(normText);
                List<String> warnings = new ArrayList<>();

                if (reqs.isEmpty() && effs.isEmpty() && baseDmg == 0) {
                    warnings.add("ORPHAN: no requirements, no effects, no damage");
                    emptyAttacks.add(card.getName() + " | " + atk.name() + " | " + atk.text());
                }
                if (normText.contains("flip") && flips == 0) {
                    warnings.add("Has 'flip' but coinFlipCount=0");
                }
                if (atk.text() != null && atk.text().toLowerCase().contains("discard")
                    && reqs.stream().noneMatch(r -> r.contains("DECK") || r.contains("ENERGY") || r.contains("HAND"))
                    && effs.stream().noneMatch(e -> e.equals("DISCARD_ENERGY") || e.equals("OPPONENT_DECK"))) {
                    warnings.add("Has 'discard' but no discard requirement/effect detected");
                }

                entries.add(new AuditEntry(
                    card.getId(), card.getName(), atk.name(), atk.text(), baseDmg, reqs, effs, flips, warnings
                ));
            }
        }

        // ─── Generate markdown ──────────────────────────────────────────────
        Path out = Path.of("target", "attack-audit.md");
        try (PrintWriter w = new PrintWriter(new FileWriter(out.toFile()))) {
            w.println("# Attack Audit Report");
            w.println();
            w.println("**Cards analyzed:** " + cards.stream().filter(c -> "Pokémon".equalsIgnoreCase(c.getSupertype())).count());
            w.println("  **Attacks analyzed:** " + entries.size());
            w.println("  **Orphan attacks:** " + emptyAttacks.size());
            w.println();

            // ─── Orphans first ──────────────────────────────────────────────
            if (!emptyAttacks.isEmpty()) {
                w.println("## ⚠ Orphan Attacks (no requirements, no effects, no damage)");
                w.println();
                w.println("| Card | Attack | Text |");
                w.println("|---|---|---|");
                for (String oa : emptyAttacks) {
                    String[] parts = oa.split(" \\| ", 3);
                    w.println("| " + parts[0] + " | " + parts[1] + " | " + escapeMd(parts.length > 2 ? parts[2] : "") + " |");
                }
                w.println();
            }

            // ─── All attacks ────────────────────────────────────────────────
            w.println("## All Attacks");
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
            w.println();
        }

        System.out.println("=== ATTACK AUDIT COMPLETE ===");
        System.out.println("Cards: " + cards.size());
        System.out.println("Attacks: " + entries.size());
        System.out.println("Orphans: " + emptyAttacks.size());
        System.out.println("Report: " + out.toAbsolutePath());

        if (!emptyAttacks.isEmpty()) {
            System.out.println("\n--- ORPHAN ATTACKS (no requirements, no effects, no damage) ---");
            for (String oa : emptyAttacks) {
                System.out.println("  " + oa);
            }
        }
    }
}
