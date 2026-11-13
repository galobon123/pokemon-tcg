package com.utn.pokemontcg.card.infrastructure;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.card.infrastructure.persistence.CardEntity;
import com.utn.pokemontcg.card.infrastructure.persistence.CardJpaRepository;
import com.utn.pokemontcg.card.infrastructure.persistence.CardSetEntity;
import com.utn.pokemontcg.card.infrastructure.persistence.CardSetJpaRepository;

@Service
public class CardImportService {

    private static final Logger log = LoggerFactory.getLogger(CardImportService.class);

    private final PokemonTcgClient pokemonTcgClient;
    private final CardJpaRepository cardJpaRepository;
    private final CardSetJpaRepository cardSetJpaRepository;
    private final ObjectMapper objectMapper;

    public CardImportService(PokemonTcgClient pokemonTcgClient,
                             CardJpaRepository cardJpaRepository,
                             CardSetJpaRepository cardSetJpaRepository,
                             ObjectMapper objectMapper) {
        this.pokemonTcgClient = pokemonTcgClient;
        this.cardJpaRepository = cardJpaRepository;
        this.cardSetJpaRepository = cardSetJpaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int importSet(String setId) {
        return importOrUpdateSet(setId);
    }

    @Transactional
    public int importOrUpdateSet(String setId) {
        Map<String, Object> setData = map(pokemonTcgClient.getSet(setId).get("data"));
        return importOrUpdateSet(setId, setData);
    }

    @Transactional
    public int importOrUpdateSet(String setId, Map<String, Object> setData) {
        log.info("Importando set: {}", setId);
        Map<String, Object> result = pokemonTcgClient.search("set.id:" + setId, 250);
        List<Map<String, Object>> data = maps(result.get("data"));

        if (data.isEmpty()) {
            log.warn("No se encontraron cartas para el set: {}", setId);
            return 0;
        }

        List<CardEntity> entities = data.stream()
            .map(this::upsertCard)
            .toList();

        cardJpaRepository.saveAll(entities);
        updateSetFingerprint(setId, setData, data.size());
        log.info("Importadas {} cartas del set {}", entities.size(), setId);
        return entities.size();
    }

    private CardEntity upsertCard(Map<String, Object> raw) {
        String cardId = text(raw.get("id"));
        CardEntity entity = cardJpaRepository.findById(cardId).orElseGet(CardEntity::new);
        entity.setId(text(raw.get("id")));
        updateEntity(entity, raw);
        return entity;
    }

    private void updateEntity(CardEntity entity, Map<String, Object> raw) {
        entity.setName(text(raw.get("name")));
        entity.setSetId(text(map(raw.get("set")).get("id")));
        entity.setSupertype(text(raw.get("supertype")));
        entity.setHp(nullableInt(raw.get("hp")));
        entity.setImageUrl(text(map(raw.get("images")).get("small")));
        entity.setSubtypes(toJson(raw.get("subtypes")));
        entity.setTypes(toJson(raw.get("types")));
        entity.setAttacks(toJson(raw.get("attacks")));
        entity.setWeaknesses(toJson(raw.get("weaknesses")));
        entity.setResistances(toJson(raw.get("resistances")));
        entity.setRetreatCost(nullableInt(raw.get("convertedRetreatCost")));
        entity.setEvolvesFrom(text(raw.get("evolvesFrom")));
        entity.setAbilities(toJson(raw.get("abilities")));
        entity.setRules(toJson(raw.get("rules")));
        entity.setRawData(toJson(raw));
        entity.setImportedAt(LocalDateTime.now());
    }

    private void updateSetFingerprint(String setId, Map<String, Object> setData, int importedCount) {
        CardSetEntity set = cardSetJpaRepository.findById(setId).orElseGet(CardSetEntity::new);
        set.setId(setId);
        set.setName(text(setData.get("name")));
        set.setApiUpdatedAt(text(setData.get("updatedAt")));
        set.setTotalCardsApi(integerOrDefault(setData.get("total"), importedCount));
        set.setLastSyncAt(Instant.now());
        cardSetJpaRepository.save(set);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer nullableInt(Object value) {
        return integerOrDefault(value, null);
    }

    private Integer integerOrDefault(Object value, Integer fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return fallback;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
        }
        return List.of();
    }
}
