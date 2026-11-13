package com.utn.pokemontcg.card.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.card.application.dto.CardAbilityResponse;
import com.utn.pokemontcg.card.application.dto.CardAttackResponse;
import com.utn.pokemontcg.card.application.dto.CardSearchResponse;
import com.utn.pokemontcg.card.application.dto.CardSummaryResponse;
import com.utn.pokemontcg.card.application.dto.TypeModifierResponse;
import com.utn.pokemontcg.card.infrastructure.PokemonTcgClient;
import com.utn.pokemontcg.card.infrastructure.persistence.CardEntity;
import com.utn.pokemontcg.card.infrastructure.persistence.CardJpaRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class CardApiService {

    private static final Duration TTL = Duration.ofDays(7);
    private static final String SET_QUERY_PREFIX = "set.id:";

    private final PokemonTcgClient pokemonTcgClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CardNormalizer normalizer;
    private final CardJpaRepository cardJpaRepository;
    private final ObjectMapper objectMapper;

    public CardApiService(PokemonTcgClient pokemonTcgClient,
                          RedisTemplate<String, Object> redisTemplate,
                          CardNormalizer normalizer,
                          CardJpaRepository cardJpaRepository,
                          ObjectMapper objectMapper) {
        this.pokemonTcgClient = pokemonTcgClient;
        this.redisTemplate = redisTemplate;
        this.normalizer = normalizer;
        this.cardJpaRepository = cardJpaRepository;
        this.objectMapper = objectMapper;
    }

    public CardSearchResponse search(@NonNull String query, int pageSize, int page) {
        int safePageSize = Math.max(1, Math.min(pageSize, 250));
        int safePage = Math.max(1, page);
        String key = "cards:search:v2:" + query + ":" + safePageSize + ":" + safePage;

        Object cache = readCache(key);
        if (cache instanceof CardSearchResponse response) {
            return response;
        }

        if (query.contains(SET_QUERY_PREFIX)) {
            String setId = queryTokenValue(query, SET_QUERY_PREFIX);
            List<CardEntity> entities = cardJpaRepository.findBySetId(setId);
            if (entities != null && !entities.isEmpty()) {
                CardSearchResponse response = toSearchResponse(query, safePageSize, safePage, applyLocalFilters(query, entities));
                writeCache(key, response);
                return response;
            }
        }

        Map<String, Object> result = Objects.requireNonNull(pokemonTcgClient.search(query, safePageSize, safePage));
        CardSearchResponse response = normalizer.searchResponse(query, safePageSize, result);
        writeCache(key, response);
        return response;
    }

    public CardSummaryResponse byId(@NonNull String id) {
        String key = "cards:id:" + id;

        Object cache = readCache(key);
        if (cache instanceof CardSummaryResponse response) {
            return response;
        }

        CardEntity entity = cardJpaRepository.findById(id).orElse(null);
        if (entity != null) {
            CardSummaryResponse response = toSummaryResponse(entity);
            writeCache(key, response);
            return response;
        }

        Map<String, Object> result = Objects.requireNonNull(pokemonTcgClient.getById(id));
        CardSummaryResponse response = normalizer.byIdResponse(result);
        writeCache(key, response);
        return response;
    }

    private CardSearchResponse toSearchResponse(String query, int pageSize, int page, List<CardEntity> entities) {
        List<CardEntity> sorted = entities.stream()
            .sorted(Comparator.comparing(CardEntity::getId))
            .toList();
        int totalCount = sorted.size();
        long offset = (long) (page - 1) * pageSize;
        List<CardSummaryResponse> cards = sorted.stream()
            .skip(offset)
            .limit(pageSize)
            .map(this::toSummaryResponse)
            .toList();
        return new CardSearchResponse(query, pageSize, cards.size(), totalCount, cards);
    }

    private List<CardEntity> applyLocalFilters(String query, List<CardEntity> entities) {
        String name = queryTokenValue(query, "name:");
        String supertype = queryTokenValue(query, "supertype:");
        String type = queryTokenValue(query, "types:");

        return entities.stream()
            .filter(card -> matchesName(card, name))
            .filter(card -> matchesSupertype(card, supertype))
            .filter(card -> matchesType(card, type))
            .toList();
    }

    private String queryTokenValue(String query, String prefix) {
        int start = query.indexOf(prefix);
        if (start < 0) {
            return "";
        }
        int valueStart = start + prefix.length();
        int valueEnd = query.length();
        for (String nextPrefix : List.of(" set.id:", " name:", " supertype:", " types:")) {
            int next = query.indexOf(nextPrefix, valueStart);
            if (next >= 0 && next < valueEnd) valueEnd = next;
        }
        return query.substring(valueStart, valueEnd).replace("*", "").trim();
    }

    private boolean matchesName(CardEntity card, String name) {
        if (name == null || name.isBlank()) return true;
        return normalize(card.getName()).contains(normalize(name));
    }

    private boolean matchesSupertype(CardEntity card, String supertype) {
        if (supertype == null || supertype.isBlank()) return true;
        return normalize(card.getSupertype()).equals(normalize(supertype));
    }

    private boolean matchesType(CardEntity card, String type) {
        if (type == null || type.isBlank()) return true;
        List<String> types = fromJson(card.getTypes(), new TypeReference<List<String>>() {});
        String normalizedType = normalize(type);
        return types.stream().anyMatch(cardType -> normalize(cardType).equals(normalizedType));
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }

    private CardSummaryResponse toSummaryResponse(CardEntity entity) {
        return new CardSummaryResponse(
            entity.getId(),
            entity.getName(),
            entity.getSetId(),
            entity.getSupertype(),
            fromJson(entity.getSubtypes(), new TypeReference<List<String>>() {}),
            fromJson(entity.getTypes(), new TypeReference<List<String>>() {}),
            entity.getHp(),
            fromJson(entity.getAttacks(), new TypeReference<List<CardAttackResponse>>() {}),
            fromJson(entity.getWeaknesses(), new TypeReference<List<TypeModifierResponse>>() {}),
            fromJson(entity.getResistances(), new TypeReference<List<TypeModifierResponse>>() {}),
            entity.getRetreatCost(),
            entity.getEvolvesFrom(),
            abilityObjects(entity.getAbilities()),
            fromJson(entity.getRules(), new TypeReference<List<String>>() {}),
            entity.getImageUrl()
        );
    }

    private List<CardAbilityResponse> abilityObjects(String abilitiesJson) {
        List<Map<String, Object>> abilities = fromJson(abilitiesJson, new TypeReference<List<Map<String, Object>>>() {});
        return abilities.stream()
            .map(ability -> new CardAbilityResponse(
                String.valueOf(ability.getOrDefault("name", "")),
                String.valueOf(ability.getOrDefault("text", ""))))
            .filter(a -> a.name() != null && !a.name().isBlank())
            .toList();
    }

    private <T> List<T> fromJson(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Object readCache(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void writeCache(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value, TTL);
        } catch (RuntimeException ignored) {
            // Card lookup must keep working even if Redis is temporarily unavailable.
        }
    }
}
