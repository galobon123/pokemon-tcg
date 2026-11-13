package com.utn.pokemontcg.game.domain.model;

import com.utn.pokemontcg.game.infrastructure.client.CardServiceClient.AttackData;
import java.util.List;
import java.util.Set;

public record GameCard(
    String id,
    String name,
    String supertype,
    Set<String> subtypes,
    int hp,
    int attackDamage,
    int attackRequiredEnergy,
    String attackName,
    String attackText,
    String energyType,
    List<String> attackCost,
    int retreatCost,
    String evolvesFrom,
    List<String> abilities,
    String weaknessType,
    Integer weaknessAmount,
    String resistanceType,
    Integer resistanceAmount,
    List<AttackData> attacks
) {
    public GameCard(
        String id,
        String name,
        String supertype,
        Set<String> subtypes,
        int hp,
        int attackDamage,
        int attackRequiredEnergy
    ) {
        this(id, name, supertype, subtypes, hp, attackDamage, attackRequiredEnergy, null, null, null, List.of(), 0, null, List.of(), null, null, null, null, List.of());
    }

    public GameCard(
        String id,
        String name,
        String supertype,
        Set<String> subtypes,
        int hp,
        int attackDamage,
        int attackRequiredEnergy,
        String energyType,
        List<String> attackCost,
        int retreatCost
    ) {
        this(id, name, supertype, subtypes, hp, attackDamage, attackRequiredEnergy, null, null, energyType, attackCost, retreatCost, null, List.of(), null, null, null, null, List.of());
    }

    public GameCard(
        String id,
        String name,
        String supertype,
        Set<String> subtypes,
        int hp,
        int attackDamage,
        int attackRequiredEnergy,
        String energyType,
        List<String> attackCost,
        int retreatCost,
        String evolvesFrom,
        List<String> abilities
    ) {
        this(id, name, supertype, subtypes, hp, attackDamage, attackRequiredEnergy, null, null, energyType, attackCost, retreatCost, evolvesFrom, abilities, null, null, null, null, List.of());
    }


    public GameCard(
        String id,
        String name,
        String supertype,
        Set<String> subtypes,
        int hp,
        int attackDamage,
        int attackRequiredEnergy,
        String attackName,
        String attackText,
        String energyType,
        List<String> attackCost,
        int retreatCost,
        String evolvesFrom,
        List<String> abilities
    ) {
        this(id, name, supertype, subtypes, hp, attackDamage, attackRequiredEnergy, attackName, attackText, energyType, attackCost, retreatCost, evolvesFrom, abilities, null, null, null, null, List.of());
    }

    public GameCard {
        subtypes = subtypes == null ? Set.of() : Set.copyOf(subtypes);
        attackCost = attackCost == null ? List.of() : List.copyOf(attackCost);
        abilities = abilities == null ? List.of() : List.copyOf(abilities);
        attacks = attacks == null ? List.of() : List.copyOf(attacks);
    }

    public boolean isPokemon() {
        return "Pokemon".equalsIgnoreCase(supertype) || "Pokémon".equalsIgnoreCase(supertype);
    }

    public boolean isBasicPokemon() {
        return isPokemon() && subtypes.contains("Basic");
    }

    public boolean isPokemonEx() {
        return isPokemon() && (name.endsWith("-EX") || subtypes.contains("EX"));
    }

    public boolean isBasicEnergy() {
        return "Energy".equalsIgnoreCase(supertype) && subtypes.contains("Basic");
    }

    public boolean isEnergy() {
        return "Energy".equalsIgnoreCase(supertype);
    }

    public boolean isSpecialEnergy() {
        return "Energy".equalsIgnoreCase(supertype) && subtypes.contains("Special");
    }

    public boolean isTrainer() {
        return "Trainer".equalsIgnoreCase(supertype);
    }

    public boolean hasSubtype(String expected) {
        return subtypes.stream().anyMatch(subtype ->
            stripDiacritics(subtype).equalsIgnoreCase(stripDiacritics(expected))
        );
    }

    public boolean hasRuleContaining(String text) {
        if (text == null || text.isBlank()) return false;
        String normalized = stripDiacritics(text).toLowerCase();
        if (name != null && stripDiacritics(name).toLowerCase().contains(normalized)) {
            return true;
        }
        return subtypes.stream().anyMatch(subtype ->
            stripDiacritics(subtype).toLowerCase().contains(normalized)
        );
    }

    private static String stripDiacritics(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    }
}
