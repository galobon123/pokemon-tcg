package com.utn.pokemontcg.game.deck;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.game.application.service.deck.DeckService;
import com.utn.pokemontcg.game.application.service.deck.DeckValidationService;
import com.utn.pokemontcg.game.infrastructure.client.CardServiceClient;
import com.utn.pokemontcg.game.infrastructure.client.CardServiceClient.AttackData;
import com.utn.pokemontcg.game.infrastructure.client.CardServiceClient.CardData;
import com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckCardEntity;
import com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckEntity;
import com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckJpaRepository;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckCardInput;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckUpsertSelfRequest;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch coverage push for DeckService and DeckValidationService.
 * Targets the 9/179 missed DeckService branches and 3/22 missed validation
 * branches called out in the JaCoCo audit.
 */
class DeckServiceBranchCoverageTest {

    private final DeckJpaRepository repository = mock(DeckJpaRepository.class);
    private final DeckValidationService validationService = mock(DeckValidationService.class);
    private final CardServiceClient cardServiceClient = mock(CardServiceClient.class);
    private final DeckService service = new DeckService(repository, validationService, cardServiceClient);

    // ====================================================================
    // enrichOne branches: subtypes null/blank, energy type inference,
    // ace-spec detection, fallback paths.
    // ====================================================================

    @Test
    void enrichOneNormalizesBlankSubtype() {
        // data.subtypes() blank -> falls back to input.subtype()
        when(cardServiceClient.findById("xy1-1")).thenReturn(Optional.of(
            new CardData("xy1-1", "Pikachu", "Pokemon", "  ", 60, 10, 1)));
        when(validationService.validate(any())).thenReturn(validResult());
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        var cards = List.of(deckCard("xy1-1", "Pikachu", "Pokemon", "Basic", 1, null, null, null));
        var response = service.createForPlayer(UUID.randomUUID(), new DeckUpsertSelfRequest("Deck", cards));

        assertEquals("Basic", response.cards().get(0).subtype());
    }

    @Test
    void enrichOneDetectsAceSpecFromSubtype() {
        when(cardServiceClient.findById("xy1-1")).thenReturn(Optional.of(
            new CardData("xy1-1", "AceSpec Tool", "Trainer", "ACE SPEC", 0, 0, 0)));
        when(validationService.validate(any())).thenReturn(validResult());
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        var cards = List.of(deckCard("xy1-1", "AceSpec Tool", "Trainer", "ACE SPEC", 1, null, null, null));
        var response = service.createForPlayer(UUID.randomUUID(), new DeckUpsertSelfRequest("Deck", cards));

        assertTrue(response.cards().get(0).aceSpec());
    }

    @Test
    void enrichOneFallsBackToInputEnergyTypeWhenDataMissing() {
        when(cardServiceClient.findById("xy1-1")).thenReturn(Optional.of(
            new CardData("xy1-1", "Fire Energy", "Energy", "Basic", 0, 0, 0)));
        when(validationService.validate(any())).thenReturn(validResult());
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        var cards = List.of(deckCard("xy1-1", "Fire Energy", "Energy", "Basic", 1, null, null, null));
        var response = service.createForPlayer(UUID.randomUUID(), new DeckUpsertSelfRequest("Deck", cards));

        assertEquals("Fire", response.cards().get(0).energyType());
    }

    @Test
    void enrichOnePreservesInputAttacksWhenDataAttacksEmpty() {
        when(cardServiceClient.findById("xy1-1")).thenReturn(Optional.of(
            new CardData("xy1-1", "Pikachu", "Pokemon", "Basic", 60, 10, 1)));
        when(validationService.validate(any())).thenReturn(validResult());
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        AttackData inputAttack = new AttackData("Thunder Shock", "Flip a coin.", "20",
            List.of("Lightning"), 1);
        var cards = List.of(new DeckCardInput("xy1-1", "Pikachu", "xy1", 1, "Pokemon", "Basic",
            false, true, false, null, null, null,
            "Thunder Shock", "Flip a coin.", "Lightning", List.of("Lightning"),
            0, null, List.of(), null, null, null, null, List.of(inputAttack)));
        var response = service.createForPlayer(UUID.randomUUID(), new DeckUpsertSelfRequest("Deck", cards));

        assertEquals(1, response.cards().get(0).attacks().size());
        assertEquals("Thunder Shock", response.cards().get(0).attacks().get(0).name());
    }

    @Test
    void enrichOneOverwritesInputAttacksWithDataAttacks() {
        when(cardServiceClient.findById("xy1-1")).thenReturn(Optional.of(
            new CardData("xy1-1", "Pikachu", "Pokemon", "Basic", 60, 10, 1, "Vine Whip",
                "Flip a coin.", "Grass", List.of("Grass"), 1, null, List.of(),
                null, null, null, null,
                List.of(new AttackData("Vine Whip", "Flip a coin.", "30",
                    List.of("Grass"), 1)))));
        when(validationService.validate(any())).thenReturn(validResult());
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        var cards = List.of(new DeckCardInput("xy1-1", "Pikachu", "xy1", 1, "Pokemon", "Basic",
            false, true, false, null, null, null,
            "Old", "Old.", "Colorless", List.of(),
            0, null, List.of(), null, null, null, null, List.of()));
        var response = service.createForPlayer(UUID.randomUUID(), new DeckUpsertSelfRequest("Deck", cards));

        assertEquals("Vine Whip", response.cards().get(0).attackName());
        assertEquals(1, response.cards().get(0).attacks().size());
    }

    // ====================================================================
    // applyRequest branch: setId derived from cardId when missing
    // ====================================================================

    @Test
    void applyRequestDerivesSetIdFromCardIdWhenBlank() {
        when(cardServiceClient.findById("xy1-1")).thenReturn(Optional.empty());
        when(validationService.validate(any())).thenReturn(validResult());
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        var cards = List.of(new DeckCardInput("xy1-1", "Pikachu", "", 1, "Pokemon", "Basic",
            false, true, false, null, null, null));
        var response = service.createForPlayer(UUID.randomUUID(), new DeckUpsertSelfRequest("Deck", cards));

        assertEquals("xy1", response.cards().get(0).setId());
    }

    // ====================================================================
    // writeAttacksJson / readAttacks branches
    // ====================================================================

    @Test
    void readAttacksRecoversFromInvalidJson() {
        DeckEntity entity = new DeckEntity();
        entity.setPlayerId(UUID.randomUUID());
        entity.setName("Test");
        DeckCardEntity card = new DeckCardEntity();
        card.setCardId("xy1-1");
        card.setName("Pikachu");
        card.setSetId("xy1");
        card.setQuantity(1);
        card.setType("Pokemon");
        card.setSubtype("Basic");
        card.setBasicPokemon(true);
        card.setAttacksJson("{not valid json");
        card.setAttackName("Backup Attack");
        card.setAttackText("Fallback");
        card.setAttackDamage(50);
        card.setAttackRequiredEnergy(2);
        card.setDeck(entity);
        entity.getCards().add(card);

        when(repository.findById(any())).thenReturn(Optional.of(entity));
        when(validationService.validate(any())).thenReturn(validResult());

        var response = service.get(UUID.randomUUID());

        assertEquals(1, response.cards().get(0).attacks().size());
        assertEquals("Backup Attack", response.cards().get(0).attacks().get(0).name());
    }

    @Test
    void readAttacksHandlesBlankJsonAndNoAttackFields() {
        DeckEntity entity = new DeckEntity();
        entity.setPlayerId(UUID.randomUUID());
        entity.setName("Test");
        DeckCardEntity card = new DeckCardEntity();
        card.setCardId("xy1-1");
        card.setName("Pikachu");
        card.setSetId("xy1");
        card.setQuantity(1);
        card.setType("Pokemon");
        card.setSubtype("Basic");
        card.setBasicPokemon(true);
        card.setAttacksJson("   "); // blank -> not parsed
        // no attackName/attackText -> second branch of readAttacks does not apply
        card.setDeck(entity);
        entity.getCards().add(card);

        when(repository.findById(any())).thenReturn(Optional.of(entity));
        when(validationService.validate(any())).thenReturn(validResult());

        var response = service.get(UUID.randomUUID());

        assertTrue(response.cards().get(0).attacks().isEmpty());
    }

    // ====================================================================
    // DeckValidationService: missing basic Pokemon, more than 4 copies
    // ====================================================================

    @Test
    void validationRejectsDeckWithoutBasicPokemon() {
        DeckValidationService realService = new DeckValidationService();
        var cards = List.of(
            new DeckCardInput("xy1-1", "Trainer", "xy1", 4, "Trainer", "Item", false, false, false, 0, 0, 0),
            new DeckCardInput("xy1-2", "Energy", "xy1", 56, "Energy", "Basic", true, false, false, 0, 0, 0)
        );
        var result = realService.validate(cards);
        assertTrue(result.errors().stream().anyMatch(e -> e.toLowerCase().contains("básico") || e.toLowerCase().contains("basico")));
    }

    @Test
    void validationRejectsMoreThanFourCopiesOfNonEnergy() {
        DeckValidationService realService = new DeckValidationService();
        var cards = List.of(
            new DeckCardInput("xy1-1", "Pikachu", "xy1", 6, "Pokemon", "Basic", false, true, false, 60, 10, 1),
            new DeckCardInput("xy1-2", "Basic", "xy1", 54, "Pokemon", "Basic", false, true, false, 60, 10, 1)
        );
        var result = realService.validate(cards);
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("4 copias")));
    }

    @Test
    void validationAcceptsValidDeck() {
        DeckValidationService realService = new DeckValidationService();
        var cards = List.of(
            new DeckCardInput("xy1-1", "Pikachu", "xy1", 4, "Pokemon", "Basic", false, true, false, 60, 10, 1),
            new DeckCardInput("xy1-2", "Fire Energy", "xy1", 56, "Energy", "Basic", true, false, false, 0, 0, 0)
        );
        var result = realService.validate(cards);
        assertTrue(result.valid(), "Deck valido debe pasar: " + result.errors());
    }

    // ====================================================================
    // enrichOne: input fields used as fallback when CardData values are null
    // ====================================================================

    @Test
    void enrichOne_fallsBackToInputValuesWhenCardDataIsMostlyNull() {
        // CardData has null name, null subtypes, null supertype, null hp
        // -> enrichOne should fall back to the input values for these fields
        when(cardServiceClient.findById("xy1-1")).thenReturn(Optional.of(
            new CardData("xy1-1", null, null, null, null, null, null)));
        when(validationService.validate(any())).thenReturn(validResult());
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        var cards = List.of(new DeckCardInput("xy1-1", "Pikachu", "xy1", 4, "Pokemon", "Basic",
            false, true, false, 60, 10, 1));
        var response = service.createForPlayer(UUID.randomUUID(), new DeckUpsertSelfRequest("Deck", cards));

        assertEquals("Pikachu", response.cards().get(0).name());
        assertEquals("Basic", response.cards().get(0).subtype());
        assertEquals("Pokemon", response.cards().get(0).type());
        assertEquals(60, response.cards().get(0).hp());
    }

    // ====================================================================
    // enrichOne: CardData provides non-null retreatCost, evolvesFrom, weakness,
    // resistance -> input values are overridden
    // ====================================================================

    @Test
    void enrichOne_overridesInputValuesWithNonNullCardDataFields() {
        when(cardServiceClient.findById("xy1-1")).thenReturn(Optional.of(
            new CardData("xy1-1", "Raichu", "Pokemon", "Stage 1", 90, 50, 2,
                "Thunder", "Flip a coin. If heads, the Defending Pokemon is now Paralyzed.",
                "Lightning", List.of("Lightning", "Colorless"),
                2,                // retreatCost from data
                "Pikachu",        // evolvesFrom from data
                List.of(),
                "Fire",           // weaknessType from data
                2,                // weaknessAmount from data
                "Metal",          // resistanceType from data
                -20,              // resistanceAmount from data
                List.of())));
        when(validationService.validate(any())).thenReturn(validResult());
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Input has different/blank values that should be overridden by CardData
        var cards = List.of(new DeckCardInput("xy1-1", "OldName", "xy1", 1, "Pokemon", "Stage 1",
            false, false, false, 60, 10, 1,
            null, null, "Colorless", List.of(),
            0, null, List.of(),
            null, null, null, null, List.of()));
        var response = service.createForPlayer(UUID.randomUUID(), new DeckUpsertSelfRequest("Deck", cards));

        var saved = response.cards().get(0);
        assertEquals("Raichu", saved.name());
        assertEquals(2, saved.retreatCost());
        assertEquals("Pikachu", saved.evolvesFrom());
        assertEquals("Fire", saved.weaknessType());
        assertEquals(2, saved.weaknessAmount());
        assertEquals("Metal", saved.resistanceType());
        assertEquals(-20, saved.resistanceAmount());
    }

    // ====================================================================
    // enrichOne: supertype null in CardData + input type "Pokemon" -> isPokemon true
    // ====================================================================

    @Test
    void enrichOne_isPokemonViaFallbackTypeWhenDataSupertypeIsNull() {
        when(cardServiceClient.findById("xy1-1")).thenReturn(Optional.of(
            new CardData("xy1-1", "Pikachu", null, "Basic", 60, 10, 1)));
        when(validationService.validate(any())).thenReturn(validResult());
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        var cards = List.of(new DeckCardInput("xy1-1", "Pikachu", "xy1", 4, "Pokemon", "Basic",
            false, true, false, 60, 10, 1));
        var response = service.createForPlayer(UUID.randomUUID(), new DeckUpsertSelfRequest("Deck", cards));

        // isPokemon resolves via fallback to input.type "Pokemon" -> basicPokemon true
        assertTrue(response.cards().get(0).basicPokemon());
        assertFalse(response.cards().get(0).basicEnergy());
    }

    // ====================================================================
    // enrichOne: supertype "Pokémon" (with accent) -> isPokemon true
    // ====================================================================

    @Test
    void enrichOne_isPokemonWhenDataSupertypeUsesAccent() {
        when(cardServiceClient.findById("xy1-1")).thenReturn(Optional.of(
            new CardData("xy1-1", "Pikachu", "Pokémon", "Basic", 60, 10, 1)));
        when(validationService.validate(any())).thenReturn(validResult());
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        var cards = List.of(new DeckCardInput("xy1-1", "Pikachu", "xy1", 4, "Pokemon", "Basic",
            false, true, false, 60, 10, 1));
        var response = service.createForPlayer(UUID.randomUUID(), new DeckUpsertSelfRequest("Deck", cards));

        assertTrue(response.cards().get(0).basicPokemon());
    }

    // ====================================================================
    // enrichOne: supertype null in CardData + input type "Energy" -> isEnergy true
    // ====================================================================

    @Test
    void enrichOne_isEnergyViaFallbackTypeWhenDataSupertypeIsNull() {
        when(cardServiceClient.findById("xy1-1")).thenReturn(Optional.of(
            new CardData("xy1-1", "Fire Energy", null, "Basic", 0, 0, 0)));
        when(validationService.validate(any())).thenReturn(validResult());
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        var cards = List.of(new DeckCardInput("xy1-1", "Fire Energy", "xy1", 4, "Energy", "Basic",
            true, false, false, 0, 0, 0));
        var response = service.createForPlayer(UUID.randomUUID(), new DeckUpsertSelfRequest("Deck", cards));

        // isEnergy resolves via fallback to input.type "Energy" -> basicEnergy true
        assertTrue(response.cards().get(0).basicEnergy());
    }

    // ====================================================================
    // setIdOf / applyRequest: cardId without "-" -> setId becomes blank
    // ====================================================================

    @Test
    void applyRequest_withCardIdWithoutDash_yieldsBlankSetId() {
        // setIdOf sees no dash in cardId, separator <= 0 -> returns ""
        when(cardServiceClient.findById("Pikachu")).thenReturn(Optional.empty());
        when(validationService.validate(any())).thenReturn(validResult());
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        var cards = List.of(new DeckCardInput("Pikachu", "Pikachu", "", 4, "Pokemon", "Basic",
            false, true, false, 60, 10, 1));
        var response = service.createForPlayer(UUID.randomUUID(), new DeckUpsertSelfRequest("Deck", cards));

        assertEquals("", response.cards().get(0).setId());
    }

    @Test
    void setIdOf_withCardIdWithoutDash_returnsBlank() {
        // Verify setIdOf when the input has no dash at all (not even a separator)
        when(cardServiceClient.findById("Pikachu")).thenReturn(Optional.empty());
        when(validationService.validate(any())).thenReturn(validResult());
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        var cards = List.of(new DeckCardInput("Pikachu", "Pikachu", null, 4, "Pokemon", "Basic",
            false, true, false, 60, 10, 1));
        var response = service.createForPlayer(UUID.randomUUID(), new DeckUpsertSelfRequest("Deck", cards));

        assertEquals("", response.cards().get(0).setId());
    }

    // ====================================================================
    // readAttacks: blank json + null attackDamage/attackRequiredEnergy fallback
    // ====================================================================

    @Test
    void readAttacks_blankJsonWithNullDamageAndRequiredEnergy_buildsZeroDefaults() {
        // Blank attacksJson triggers the fallback branch which uses null-coalesce defaults:
        //   attackDamage null -> "0"
        //   attackRequiredEnergy null -> 0
        DeckEntity entity = new DeckEntity();
        entity.setPlayerId(UUID.randomUUID());
        entity.setName("Test");
        DeckCardEntity card = new DeckCardEntity();
        card.setCardId("xy1-1");
        card.setName("MysteryMon");
        card.setSetId("xy1");
        card.setQuantity(1);
        card.setType("Pokemon");
        card.setSubtype("Basic");
        card.setBasicPokemon(true);
        card.setAttacksJson("   "); // blank -> not parsed, falls to second branch
        card.setAttackName("Mystery Attack");
        card.setAttackText("Does something.");
        card.setAttackDamage(null);              // -> "0"
        card.setAttackRequiredEnergy(null);      // -> 0
        card.setDeck(entity);
        entity.getCards().add(card);

        when(repository.findById(any())).thenReturn(Optional.of(entity));
        when(validationService.validate(any())).thenReturn(validResult());

        var response = service.get(UUID.randomUUID());

        assertEquals(1, response.cards().get(0).attacks().size());
        var attack = response.cards().get(0).attacks().get(0);
        assertEquals("Mystery Attack", attack.name());
        assertEquals("Does something.", attack.text());
        assertEquals("0", attack.damage());
        assertEquals(0, attack.convertedEnergyCost());
    }

    // ====================================================================
    // containsSubtype: subtype that does not contain "Basic" or "ACE SPEC"
    // ====================================================================

    @Test
    void enrichOne_nonBasicSubtype_yieldsBasicPokemonFalseAndAceSpecFalse() {
        // Subtype "Stage 1" doesn't contain "Basic" nor "ACE SPEC" -> both containSubtype
        // calls return false (false branches).
        when(cardServiceClient.findById("xy1-1")).thenReturn(Optional.of(
            new CardData("xy1-1", "Ivysaur", "Pokemon", "Stage 1", 60, 30, 1)));
        when(validationService.validate(any())).thenReturn(validResult());
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        var cards = List.of(new DeckCardInput("xy1-1", "Ivysaur", "xy1", 4, "Pokemon", "Stage 1",
            false, false, false, 60, 30, 1));
        var response = service.createForPlayer(UUID.randomUUID(), new DeckUpsertSelfRequest("Deck", cards));

        // isPokemon true (data supertype is "Pokemon"), but containsSubtype("Stage 1", "Basic") = false
        // so basicPokemon should be false. Also aceSpec should be false.
        assertFalse(response.cards().get(0).basicPokemon());
        assertFalse(response.cards().get(0).aceSpec());
    }

    // ====================================================================
    // enrichOne: data.energyType() is non-null -> true branch of the ternary
    // ====================================================================

    @Test
    void enrichOne_usesDataEnergyTypeWhenNonNull() {
        // data.energyType() = "Fire" (non-null) -> ternary returns "Fire" directly
        // even though input.energyType is "Water"
        when(cardServiceClient.findById("xy1-1")).thenReturn(Optional.of(
            new CardData("xy1-1", "Fire Energy", "Energy", "Basic", 0, 0, 0,
                null, null, "Fire", List.of(), 0, null, List.of())));
        when(validationService.validate(any())).thenReturn(validResult());
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        var cards = List.of(new DeckCardInput("xy1-1", "Fire Energy", "xy1", 4, "Energy", "Basic",
            true, false, false, 0, 0, 0,
            null, null, "Water", List.of(), 0, null, List.of(),
            null, null, null, null, List.of()));
        var response = service.createForPlayer(UUID.randomUUID(), new DeckUpsertSelfRequest("Deck", cards));

        // data.energyType() is non-null -> result is "Fire" (not "Water" from input)
        assertEquals("Fire", response.cards().get(0).energyType());
    }

    // ====================================================================
    // helpers
    // ====================================================================

    private DeckCardInput deckCard(String cardId, String name, String type, String subtype,
                                   int quantity, Integer hp, Integer damage, Integer energy) {
        return new DeckCardInput(cardId, name, "xy1", quantity, type, subtype,
            "Energy".equalsIgnoreCase(type), "Basic".equalsIgnoreCase(subtype), false,
            hp, damage, energy, null, null, null, List.of(), 0, null, List.of(),
            null, null, null, null, List.of());
    }

    private DeckValidationResult validResult() {
        return new DeckValidationResult(true, List.of());
    }
}
