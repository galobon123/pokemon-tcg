package com.utn.pokemontcg.game.application.service.deck;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.utn.pokemontcg.game.infrastructure.client.CardServiceClient;
import com.utn.pokemontcg.game.infrastructure.client.CardServiceClient.AttackData;
import com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckCardEntity;
import com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckEntity;
import com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckJpaRepository;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckCardInput;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckUpsertSelfRequest;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult;

@Service
@Transactional
public class DeckService {

    private static final Logger log = LoggerFactory.getLogger(DeckService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DeckJpaRepository deckRepository;
    private final DeckValidationService deckValidationService;
    private final CardServiceClient cardServiceClient;

    public DeckService(DeckJpaRepository deckRepository,
                       DeckValidationService deckValidationService,
                       CardServiceClient cardServiceClient) {
        this.deckRepository = deckRepository;
        this.deckValidationService = deckValidationService;
        this.cardServiceClient = cardServiceClient;
    }

    public DeckResponse createForPlayer(UUID playerId, DeckUpsertSelfRequest request) {
        List<DeckCardInput> enriched = enrichWithCardService(request.cards());
        ensureValid(enriched);
        DeckEntity entity = new DeckEntity();
        applyRequest(entity, playerId, request.name(), enriched);
        DeckEntity saved = deckRepository.save(entity);
        return toResponse(saved);
    }

    public DeckResponse updateForPlayer(UUID playerId, UUID deckId, DeckUpsertSelfRequest request) {
        List<DeckCardInput> enriched = enrichWithCardService(request.cards());
        ensureValid(enriched);
        DeckEntity entity = deckRepository.findById(deckId)
            .orElseThrow(() -> new IllegalArgumentException("Deck no encontrado: " + deckId));
        if (!playerId.equals(entity.getPlayerId())) {
            throw new IllegalArgumentException("Deck no encontrado: " + deckId);
        }
        applyRequest(entity, playerId, request.name(), enriched);
        DeckEntity saved = deckRepository.save(entity);
        return toResponse(saved);
    }

    public DeckResponse getForPlayer(UUID playerId, UUID deckId) {
        return deckRepository.findById(deckId)
            .filter(deck -> playerId.equals(deck.getPlayerId()))
            .map(this::toResponse)
            .orElseThrow(() -> new IllegalArgumentException("Deck no encontrado: " + deckId));
    }

    @Transactional(readOnly = true)
    public DeckResponse get(UUID deckId) {
        return deckRepository.findById(deckId)
            .map(this::toResponse)
            .orElseThrow(() -> new IllegalArgumentException("Deck no encontrado: " + deckId));
    }

    public void deleteForPlayer(UUID playerId, UUID deckId) {
        DeckEntity entity = deckRepository.findById(deckId)
            .orElseThrow(() -> new IllegalArgumentException("Deck no encontrado: " + deckId));
        if (!playerId.equals(entity.getPlayerId())) {
            throw new IllegalArgumentException("Deck no encontrado: " + deckId);
        }
        deckRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<DeckResponse> listByPlayer(UUID playerId) {
        return deckRepository.findByPlayerId(playerId).stream().map(this::toResponse).toList();
    }

    public DeckResponse getEnrichedForGame(UUID playerId, UUID deckId) {
        DeckResponse raw = getForPlayer(playerId, deckId);
        List<DeckCardInput> enriched = enrichWithCardService(raw.cards());
        return new DeckResponse(raw.id(), raw.playerId(), raw.name(), enriched, raw.validation());
    }

    private List<DeckCardInput> enrichWithCardService(List<DeckCardInput> cards) {
        return cards.stream()
            .map(this::enrichOne)
            .toList();
    }

    private DeckCardInput enrichOne(DeckCardInput input) {
        return cardServiceClient.findById(input.cardId())
            .map(data -> {
                log.debug("Carta enriquecida desde card-service: {}", input.cardId());

                String subtype = data.subtypes() != null && !data.subtypes().isBlank() ? data.subtypes() : input.subtype();
                boolean basicPokemon = isPokemon(data.supertype(), input.type()) && containsSubtype(subtype, "Basic");
                boolean basicEnergy = isEnergy(data.supertype(), input.type());
                boolean aceSpec = containsSubtype(subtype, "ACE SPEC");

                return new DeckCardInput(
                    input.cardId(),
                    data.name() != null ? data.name() : input.name(),
                    input.setId(),
                    input.quantity(),
                    data.supertype() != null ? data.supertype() : input.type(),
                    subtype,
                    basicEnergy,
                    basicPokemon,
                    aceSpec,
                    data.hp() != null ? data.hp() : input.hp(),
                    data.attackDamage() != null ? data.attackDamage() : input.attackDamage(),
                    data.attackRequiredEnergy() != null ? data.attackRequiredEnergy() : input.attackRequiredEnergy(),
                    data.attackName() != null ? data.attackName() : input.attackName(),
                    data.attackText() != null ? data.attackText() : input.attackText(),
                    data.energyType() != null ? data.energyType() : inferEnergyType(input.name(), input.energyType()),
                    data.attackCost().isEmpty() ? input.attackCost() : data.attackCost(),
                    data.retreatCost() != null ? data.retreatCost() : input.retreatCost(),
                    data.evolvesFrom() != null ? data.evolvesFrom() : input.evolvesFrom(),
                    data.abilities().isEmpty() ? input.abilities() : data.abilities(),
                    data.weaknessType() != null ? data.weaknessType() : input.weaknessType(),
                    data.weaknessAmount() != null ? data.weaknessAmount() : input.weaknessAmount(),
                    data.resistanceType() != null ? data.resistanceType() : input.resistanceType(),
                    data.resistanceAmount() != null ? data.resistanceAmount() : input.resistanceAmount(),
                    data.attacks()
                );
            })
            .orElseGet(() -> {
                log.warn("No se pudo enriquecer carta desde card-service: {}. Usando datos del frontend.",
                    input.cardId());
                return input;
            });
    }

    private boolean isPokemon(String resolvedType, String fallbackType) {
        String type = resolvedType != null ? resolvedType : fallbackType;
        return type != null && ("Pokemon".equalsIgnoreCase(type) || "Pokémon".equalsIgnoreCase(type));
    }

    private boolean isEnergy(String resolvedType, String fallbackType) {
        String type = resolvedType != null ? resolvedType : fallbackType;
        return type != null && "Energy".equalsIgnoreCase(type);
    }

    private boolean containsSubtype(String subtype, String expected) {
        return subtype != null && subtype.toLowerCase().contains(expected.toLowerCase());
    }

    public static String inferEnergyType(String cardName, String currentType) {
        // "Energy" es el supertype, no un tipo de elemento valido: si llega como
        // tipo (o viene vacio) hay que deducir el tipo real a partir del nombre.
        if (currentType != null && !currentType.isBlank() && !"Energy".equalsIgnoreCase(currentType.trim())) {
            return currentType.trim();
        }
        if (cardName == null) return "Colorless";
        String lower = cardName.toLowerCase();
        if (lower.contains("double")) return "Double";
        if (lower.contains("rainbow")) return "Rainbow";
        if (lower.contains("fire")) return "Fire";
        if (lower.contains("water")) return "Water";
        if (lower.contains("grass")) return "Grass";
        if (lower.contains("lightning")) return "Lightning";
        if (lower.contains("psychic")) return "Psychic";
        if (lower.contains("fighting")) return "Fighting";
        if (lower.contains("darkness")) return "Darkness";
        if (lower.contains("metal")) return "Metal";
        if (lower.contains("fairy")) return "Fairy";
        if (lower.contains("dragon")) return "Dragon";
        return "Colorless";
    }

    private void applyRequest(DeckEntity entity, UUID playerId, String name, List<DeckCardInput> cards) {
        entity.setPlayerId(playerId);
        entity.setName(name);
        entity.getCards().clear();
        for (DeckCardInput input : cards) {
            DeckCardEntity card = new DeckCardEntity();
            card.setDeck(entity);
            card.setCardId(input.cardId());
            card.setName(input.name());
            card.setSetId(setIdOf(input));
            card.setQuantity(input.quantity());
            card.setType(input.type());
            card.setSubtype(input.subtype());
            card.setBasicEnergy(Boolean.TRUE.equals(input.basicEnergy()));
            card.setBasicPokemon(Boolean.TRUE.equals(input.basicPokemon()));
            card.setAceSpec(Boolean.TRUE.equals(input.aceSpec()));
            card.setHp(input.hp());
            card.setAttackDamage(input.attackDamage());
            card.setAttackRequiredEnergy(input.attackRequiredEnergy());
            card.setAttackName(input.attackName());
            card.setAttackText(input.attackText());
            // Para cartas de Energia nunca persistimos el supertype "Energy" ni un
            // valor vacio: se normaliza al tipo real (Fire, Water, Lightning, ...).
            boolean isEnergyCard = "Energy".equalsIgnoreCase(input.type());
            card.setEnergyType(isEnergyCard
                ? inferEnergyType(input.name(), input.energyType())
                : input.energyType());
            card.setAttackCost(joinCost(input.attackCost()));
            card.setRetreatCost(input.retreatCost());
            card.setEvolvesFrom(input.evolvesFrom());
            card.setAbilities(joinCost(input.abilities()));
            card.setWeaknessType(input.weaknessType());
            card.setWeaknessAmount(input.weaknessAmount());
            card.setResistanceType(input.resistanceType());
            card.setResistanceAmount(input.resistanceAmount());
            card.setAttacksJson(writeAttacksJson(input.attacks()));
            entity.getCards().add(card);
	        }
    }

    private DeckResponse toResponse(DeckEntity entity) {
        List<DeckCardInput> cards = entity.getCards().stream()
            .map(card -> new DeckCardInput(card.getCardId(), card.getName(), card.getSetId(), card.getQuantity(), card.getType(),
                card.getSubtype(), card.isBasicEnergy(), card.isBasicPokemon(), card.isAceSpec(),
                card.getHp(), card.getAttackDamage(), card.getAttackRequiredEnergy(),
                card.getAttackName(), card.getAttackText(),
                card.getEnergyType(), splitCost(card.getAttackCost()), card.getRetreatCost(),
                card.getEvolvesFrom(), splitCost(card.getAbilities()),
                card.getWeaknessType(), card.getWeaknessAmount(), card.getResistanceType(), card.getResistanceAmount(),
                readAttacks(card)))
            .toList();
        return new DeckResponse(entity.getId(), entity.getPlayerId(), entity.getName(), cards, deckValidationService.validate(cards));
    }

    private String writeAttacksJson(List<AttackData> attacks) {
        if (attacks == null || attacks.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attacks);
        } catch (Exception ex) {
            log.warn("No se pudo serializar attacks para deck card: {}", ex.getMessage());
            return null;
        }
    }

    private List<AttackData> readAttacks(DeckCardEntity card) {
        if (card.getAttacksJson() != null && !card.getAttacksJson().isBlank()) {
            try {
                return OBJECT_MAPPER.readValue(card.getAttacksJson(), new TypeReference<List<AttackData>>() {});
            } catch (Exception ex) {
                log.warn("No se pudo deserializar attacks_json para {}: {}", card.getCardId(), ex.getMessage());
            }
        }
        if (card.getAttackName() != null || card.getAttackText() != null) {
            return List.of(new AttackData(
                card.getAttackName() != null ? card.getAttackName() : "Ataque",
                card.getAttackText() != null ? card.getAttackText() : "",
                card.getAttackDamage() != null ? String.valueOf(card.getAttackDamage()) : "0",
                splitCost(card.getAttackCost()),
                card.getAttackRequiredEnergy() != null ? card.getAttackRequiredEnergy() : 0
            ));
        }
        return List.of();
    }

    private void ensureValid(List<DeckCardInput> cards) {
        DeckValidationResult result = deckValidationService.validate(cards);
        if (!result.valid()) {
            throw new IllegalArgumentException(String.join(" ", result.errors()));
        }
    }

    private String setIdOf(DeckCardInput card) {
        if (card.setId() != null && !card.setId().isBlank()) {
            return card.setId();
        }
        int separator = card.cardId().indexOf('-');
        return separator > 0 ? card.cardId().substring(0, separator) : "";
    }

    private String joinCost(List<String> attackCost) {
        return attackCost == null || attackCost.isEmpty() ? null : String.join(",", attackCost);
    }

    private List<String> splitCost(String attackCost) {
        if (attackCost == null || attackCost.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(attackCost.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
    }
}
