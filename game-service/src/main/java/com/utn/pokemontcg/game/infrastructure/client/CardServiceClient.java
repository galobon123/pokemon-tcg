package com.utn.pokemontcg.game.infrastructure.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class CardServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CardServiceClient.class);

    private final RestClient restClient;

    public CardServiceClient(@Value("${card.service.url}") String baseUrl) {
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .build();
    }

    @SuppressWarnings("unchecked")
    public Optional<CardData> findById(String cardId) {
        try {
            Map<String, Object> response = restClient.get()
                .uri("/api/cards/{id}", cardId)
                .retrieve()
                .body(Map.class);

            if (response == null) {
                return Optional.empty();
            }

            return Optional.of(CardData.fromMap(response));
        } catch (RestClientException ex) {
            log.warn("card-service no disponible para cardId={}: {}", cardId, ex.getMessage());
            return Optional.empty();
        }
    }

    public record AttackData(
        String name,
        String text,
        String damage,
        List<String> cost,
        int convertedEnergyCost
    ) {
        public AttackData {
            cost = cost == null ? List.of() : List.copyOf(cost);
        }
    }

    public record CardData(
        String id,
        String name,
        String supertype,
        String subtypes,
        Integer hp,
        Integer attackDamage,
        Integer attackRequiredEnergy,
        String attackName,
        String attackText,
        String energyType,
        List<String> attackCost,
        Integer retreatCost,
        String evolvesFrom,
        List<String> abilities,
        String weaknessType,
        Integer weaknessAmount,
        String resistanceType,
        Integer resistanceAmount,
        List<AttackData> attacks
    ) {
        public CardData(
            String id,
            String name,
            String supertype,
            String subtypes,
            Integer hp,
            Integer attackDamage,
            Integer attackRequiredEnergy
        ) {
            this(id, name, supertype, subtypes, hp, attackDamage, attackRequiredEnergy, null, null, null, List.of(), 0, null, List.of(), null, null, null, null, List.of());
        }


        public CardData(
            String id,
            String name,
            String supertype,
            String subtypes,
            Integer hp,
            Integer attackDamage,
            Integer attackRequiredEnergy,
            String attackName,
            String attackText,
            String energyType,
            List<String> attackCost,
            Integer retreatCost,
            String evolvesFrom,
            List<String> abilities
        ) {
            this(id, name, supertype, subtypes, hp, attackDamage, attackRequiredEnergy, attackName, attackText, energyType, attackCost, retreatCost, evolvesFrom, abilities, null, null, null, null, List.of());
        }

        @SuppressWarnings("unchecked")
        public static CardData fromMap(Map<String, Object> map) {
            Integer damage = null;
            Integer requiredEnergy = null;
            String attackName = null;
            String attackText = null;
            List<String> attackCost = List.of();
            List<AttackData> allAttacks = List.of();

            Object attacksRaw = map.get("attacks");
            if (attacksRaw instanceof List<?> attacksList && !attacksList.isEmpty()) {
                List<AttackData> parsed = new java.util.ArrayList<>();
                for (Object item : attacksList) {
                    if (item instanceof Map<?, ?> attack) {
                        Map<String, Object> attackMap = (Map<String, Object>) attack;
                        String aName = stringOf(attackMap.get("name"));
                        String aText = stringOf(attackMap.get("text"));
                        String aDamage = stringOf(attackMap.get("damage"));
                        List<String> aCost = stringList(attackMap.get("cost"));
                        int aConvEnergy = parseIntSafe(attackMap.get("convertedEnergyCost")) != null
                            ? parseIntSafe(attackMap.get("convertedEnergyCost"))
                            : 0;
                        parsed.add(new AttackData(aName, aText, aDamage, aCost, aConvEnergy));
                    }
                }
                allAttacks = parsed;

                Object first = attacksList.get(0);
                if (first instanceof Map<?, ?> attack) {
                    Map<String, Object> attackMap = (Map<String, Object>) attack;
                    damage = parseIntSafe(attackMap.get("damage"));
                    requiredEnergy = parseIntSafe(attackMap.get("convertedEnergyCost"));
                    attackName = stringOf(attackMap.get("name"));
                    attackText = stringOf(attackMap.get("text"));
                    attackCost = stringList(attackMap.get("cost"));
                }
            }

            return new CardData(
                stringOf(map.get("id")),
                stringOf(map.get("name")),
                stringOf(map.get("supertype")),
                commaSeparated(map.get("subtypes")),
                parseIntSafe(map.get("hp")),
                damage,
                requiredEnergy,
                attackName,
                attackText,
                firstOf(map.get("types")),
                attackCost,
                parseIntSafe(map.get("retreatCost")) != null
                    ? parseIntSafe(map.get("retreatCost"))
                    : parseIntSafe(map.get("convertedRetreatCost")),
                stringOf(map.get("evolvesFrom")),
                abilityNames(map.get("abilities")),
                firstModifierType(map.get("weaknesses")),
                firstModifierAmount(map.get("weaknesses"), 2),
                firstModifierType(map.get("resistances")),
                firstModifierAmount(map.get("resistances"), -20),
                allAttacks
            );
        }

	        public CardData {
	            attackCost = attackCost == null ? List.of() : List.copyOf(attackCost);
	            abilities = abilities == null ? List.of() : List.copyOf(abilities);
	            attacks = attacks == null ? List.of() : List.copyOf(attacks);
	        }

	        private static String firstModifierType(Object value) {
            if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> map) {
                return stringOf(map.get("type"));
            }
            return null;
        }

        private static Integer firstModifierAmount(Object value, int defaultAmount) {
            if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> map) {
                Integer parsed = parseSignedIntSafe(map.get("value"));
                return parsed == null ? defaultAmount : parsed;
            }
            return null;
        }

        private static List<String> abilityNames(Object value) {
	            if (value instanceof List<?> list) {
	                return list.stream()
	                    .map(item -> {
	                        if (item instanceof Map<?, ?> map) {
	                            return stringOf(map.get("name"));
	                        }
	                        return stringOf(item);
	                    })
	                    .filter(name -> name != null && !name.isBlank())
	                    .toList();
	            }
	            return List.of();
	        }

        private static String commaSeparated(Object value) {
            if (value instanceof List<?> list) {
                return list.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            }
            return stringOf(value);
        }

        private static String firstOf(Object value) {
            if (value instanceof List<?> list) {
                return list.isEmpty() ? null : stringOf(list.get(0));
            }
            return stringOf(value);
        }

        private static List<String> stringList(Object value) {
            if (value instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
            return List.of();
        }

        private static String stringOf(Object value) {
            return value == null ? null : String.valueOf(value);
        }

        private static Integer parseSignedIntSafe(Object value) {
            if (value instanceof Number n) {
                return n.intValue();
            }
            if (value instanceof String s && !s.isBlank()) {
                String clean = s.replace("×", "x").replaceAll("[^0-9-]", "");
                if (!clean.isEmpty() && !"-".equals(clean)) {
                    try {
                        return Integer.parseInt(clean);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
            }
            return null;
        }

        private static Integer parseIntSafe(Object value) {
            if (value instanceof Number n) {
                return n.intValue();
            }
            if (value instanceof String s && !s.isBlank()) {
                String clean = s.replaceAll("[^0-9]", "");
                if (!clean.isEmpty()) {
                    try {
                        return Integer.parseInt(clean);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
            }
            return null;
        }
    }
}
