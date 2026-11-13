package com.utn.pokemontcg.game.deck;

import com.utn.pokemontcg.game.application.service.deck.DeckService;
import com.utn.pokemontcg.game.application.service.deck.DeckValidationService;
import com.utn.pokemontcg.game.infrastructure.client.CardServiceClient;
import com.utn.pokemontcg.game.infrastructure.client.CardServiceClient.CardData;
import com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckCardEntity;
import com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckEntity;
import com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckJpaRepository;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckCardInput;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckUpsertSelfRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeckServiceTest {

    private final DeckJpaRepository repository = mock(DeckJpaRepository.class);
    private final DeckValidationService validationService = mock(DeckValidationService.class);
    private final CardServiceClient cardServiceClient = mock(CardServiceClient.class);
    private final DeckService service = new DeckService(repository, validationService, cardServiceClient);

    @Test
    void inferEnergyTypeReturnsCorrectType() {
        assertEquals("Fire", DeckService.inferEnergyType("Fire Energy", null));
        assertEquals("Water", DeckService.inferEnergyType("Water Energy", null));
        assertEquals("Grass", DeckService.inferEnergyType("Grass Energy", null));
        assertEquals("Lightning", DeckService.inferEnergyType("Lightning Energy", null));
        assertEquals("Psychic", DeckService.inferEnergyType("Psychic Energy", null));
        assertEquals("Fighting", DeckService.inferEnergyType("Fighting Energy", null));
        assertEquals("Darkness", DeckService.inferEnergyType("Darkness Energy", null));
        assertEquals("Metal", DeckService.inferEnergyType("Metal Energy", null));
        assertEquals("Fairy", DeckService.inferEnergyType("Fairy Energy", null));
        assertEquals("Dragon", DeckService.inferEnergyType("Dragon Energy", null));
        assertEquals("Colorless", DeckService.inferEnergyType("Unknown", "Colorless"));
        assertEquals("Colorless", DeckService.inferEnergyType(null, null));
        assertEquals("Double", DeckService.inferEnergyType("Double Colorless Energy", null));
        assertEquals("Rainbow", DeckService.inferEnergyType("Rainbow Energy", null));
    }

    @Test
    void inferEnergyTypeUsesCurrentTypeWhenNotEnergy() {
        assertEquals("Fire", DeckService.inferEnergyType("Charizard", "Fire"));
    }

    @Test
    void inferEnergyTypeReturnsColorlessForNullName() {
        assertEquals("Colorless", DeckService.inferEnergyType(null, null));
    }

    @Test
    void inferEnergyTypeReturnsCurrentTypeTrimmedWhenNotEnergy() {
        assertEquals("Water", DeckService.inferEnergyType("Blastoise", "  Water  "));
    }

    @Test
    void createForPlayerEnrichesCardsAndPersistsDeck() {
        UUID playerId = UUID.randomUUID();
        List<DeckCardInput> cards = List.of(
            new DeckCardInput("xy1-1", "Pikachu", "xy1", 4, "Pokemon", "Basic", false, true, false, null, null, null),
            new DeckCardInput("xy1-2", "Electric Energy", "xy1", 56, "Energy", "Basic", true, false, false, null, null, null)
        );
        when(cardServiceClient.findById(any())).thenReturn(Optional.empty());
        when(validationService.validate(any())).thenReturn(
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult(true, List.of()));
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.createForPlayer(playerId, new DeckUpsertSelfRequest("Test Deck", cards));
        assertNotNull(response.cards().get(0).name());
    }

    @Test
    void createForPlayerThrowsOnInvalidDeck() {
        UUID playerId = UUID.randomUUID();
        List<DeckCardInput> cards = List.of(
            new DeckCardInput("xy1-1", "Pikachu", "xy1", 60, "Pokemon", "Basic", false, true, false, null, null, null)
        );
        when(cardServiceClient.findById(any())).thenReturn(Optional.empty());
        when(validationService.validate(any())).thenReturn(
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult(false, List.of("Error")));
        assertThrows(IllegalArgumentException.class, () ->
            service.createForPlayer(playerId, new DeckUpsertSelfRequest("Bad Deck", cards)));
    }

    @Test
    void updateForPlayerUpdatesExistingDeck() {
        UUID playerId = UUID.randomUUID();
        DeckEntity entity = new DeckEntity();
        entity.setPlayerId(playerId);
        when(repository.findById(any())).thenReturn(Optional.of(entity));
        when(cardServiceClient.findById(any())).thenReturn(Optional.empty());
        when(validationService.validate(any())).thenReturn(
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult(true, List.of()));
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        List<DeckCardInput> cards = List.of(
            new DeckCardInput("xy1-1", "Pikachu", "xy1", 4, "Pokemon", "Basic", false, true, false, null, null, null),
            new DeckCardInput("xy1-2", "Electric Energy", "xy1", 56, "Energy", "Basic", true, false, false, null, null, null)
        );
        var response = service.updateForPlayer(playerId, UUID.randomUUID(), new DeckUpsertSelfRequest("Updated Deck", cards));
        assertTrue(response.validation().valid());
    }

    @Test
    void updateForPlayerThrowsWhenDeckNotFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        when(cardServiceClient.findById(any())).thenReturn(Optional.empty());
        when(validationService.validate(any())).thenReturn(
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult(true, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
            service.updateForPlayer(UUID.randomUUID(), UUID.randomUUID(),
                new DeckUpsertSelfRequest("Deck", List.of())));
    }

    @Test
    void updateForPlayerThrowsWhenPlayerDoesNotOwnDeck() {
        DeckEntity entity = new DeckEntity();
        entity.setPlayerId(UUID.randomUUID());
        when(repository.findById(any())).thenReturn(Optional.of(entity));
        when(cardServiceClient.findById(any())).thenReturn(Optional.empty());
        when(validationService.validate(any())).thenReturn(
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult(true, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
            service.updateForPlayer(UUID.randomUUID(), UUID.randomUUID(),
                new DeckUpsertSelfRequest("Deck", List.of())));
    }

    @Test
    void getForPlayerReturnsDeckWhenOwned() {
        UUID playerId = UUID.randomUUID();
        DeckEntity entity = new DeckEntity();
        entity.setPlayerId(playerId);
        when(repository.findById(any())).thenReturn(Optional.of(entity));
        assertNotNull(service.getForPlayer(playerId, UUID.randomUUID()));
    }

    @Test
    void getForPlayerThrowsWhenNotOwned() {
        UUID playerId = UUID.randomUUID();
        DeckEntity entity = new DeckEntity();
        entity.setPlayerId(UUID.randomUUID());
        when(repository.findById(any())).thenReturn(Optional.of(entity));
        assertThrows(IllegalArgumentException.class, () ->
            service.getForPlayer(playerId, UUID.randomUUID()));
    }

    @Test
    void getForPlayerThrowsWhenNotFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () ->
            service.getForPlayer(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void getReturnsDeckWhenFound() {
        DeckEntity entity = new DeckEntity();
        entity.setPlayerId(UUID.randomUUID());
        when(repository.findById(any())).thenReturn(Optional.of(entity));
        assertNotNull(service.get(UUID.randomUUID()));
    }

    @Test
    void getThrowsWhenNotFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.get(UUID.randomUUID()));
    }

    @Test
    void deleteForPlayerDeletesOwnedDeck() {
        UUID playerId = UUID.randomUUID();
        DeckEntity entity = new DeckEntity();
        entity.setPlayerId(playerId);
        when(repository.findById(any())).thenReturn(Optional.of(entity));
        service.deleteForPlayer(playerId, UUID.randomUUID());
        verify(repository).delete(entity);
    }

    @Test
    void deleteForPlayerThrowsWhenNotOwned() {
        DeckEntity entity = new DeckEntity();
        entity.setPlayerId(UUID.randomUUID());
        when(repository.findById(any())).thenReturn(Optional.of(entity));
        assertThrows(IllegalArgumentException.class, () ->
            service.deleteForPlayer(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void deleteForPlayerThrowsWhenNotFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () ->
            service.deleteForPlayer(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void listByPlayerReturnsDecks() {
        UUID playerId = UUID.randomUUID();
        when(repository.findByPlayerId(playerId)).thenReturn(List.of(new DeckEntity()));
        assertEquals(1, service.listByPlayer(playerId).size());
    }

    @Test
    void getEnrichedForGameEnrichesCards() {
        UUID playerId = UUID.randomUUID();
        DeckEntity entity = new DeckEntity();
        entity.setPlayerId(playerId);
        entity.setName("Test");
        entity.getCards().add(createDeckCard("xy1-1", "Pikachu"));

        when(repository.findById(any())).thenReturn(Optional.of(entity));
        when(cardServiceClient.findById(any())).thenReturn(
            Optional.of(new CardData("xy1-1", "Pikachu", "Pokemon", "Basic", 60, 10, 1)));

        var response = service.getEnrichedForGame(playerId, UUID.randomUUID());
        assertNotNull(response);
    }

    @Test
    void createForPlayerEnrichesWithCardServiceData() {
        UUID playerId = UUID.randomUUID();
        when(cardServiceClient.findById("xy1-1")).thenReturn(
            Optional.of(new CardData("xy1-1", "Pikachu", "Pokemon", "Basic", 60, 10, 1,
                "Thunder Shock", "Flip a coin. If heads, the Defending Pokemon is now Paralyzed.",
                "Lightning", List.of("Lightning"), 1, null, List.of())));
        when(validationService.validate(any())).thenReturn(
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult(true, List.of()));
        when(repository.save(any(DeckEntity.class))).thenAnswer(i -> i.getArgument(0));

        List<DeckCardInput> cards = List.of(
            new DeckCardInput("xy1-1", "Pikachu", "xy1", 4, "Pokemon", "", false, true, false, null, null, null,
                null, null, null, List.of(), 0, null, List.of())
        );
        var response = service.createForPlayer(playerId, new DeckUpsertSelfRequest("Test", cards));
        assertTrue(response.validation().valid());
        assertEquals("Pikachu", response.cards().get(0).name());
    }

    @Test
    void getWithCardsReturnsDeckWithCards() {
        DeckEntity entity = new DeckEntity();
        entity.setPlayerId(UUID.randomUUID());
        entity.setName("My Deck");
        entity.getCards().add(createDeckCard("xy1-1", "Pikachu"));

        when(repository.findById(any())).thenReturn(Optional.of(entity));
        when(validationService.validate(any())).thenReturn(
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult(true, List.of()));

        var response = service.get(UUID.randomUUID());
        assertEquals("My Deck", response.name());
        assertEquals(1, response.cards().size());
    }

    @Test
    void toResponseMapsEntityCorrectly() {
        DeckEntity entity = new DeckEntity();
        UUID playerId = UUID.randomUUID();
        entity.setPlayerId(playerId);
        entity.setName("Test Deck");
        entity.getCards().add(createDeckCard("xy1-1", "Charizard"));

        when(repository.findById(any())).thenReturn(Optional.of(entity));
        when(validationService.validate(any())).thenReturn(
            new com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult(true, List.of()));

        var response = service.get(UUID.randomUUID());
        assertEquals("Test Deck", response.name());
        assertEquals(playerId, response.playerId());
        assertEquals(1, response.cards().size());
    }

    private DeckCardEntity createDeckCard(String cardId, String name) {
        DeckCardEntity card = new DeckCardEntity();
        card.setCardId(cardId);
        card.setName(name);
        card.setSetId("xy1");
        card.setQuantity(4);
        card.setType("Pokemon");
        card.setSubtype("Basic");
        card.setBasicPokemon(true);
        return card;
    }

    @Test
    void inferEnergyTypeDetectsAllTypes() {
        assertEquals("Fire", DeckService.inferEnergyType("Fire Energy", "Energy"));
        assertEquals("Water", DeckService.inferEnergyType("Water Energy", "Energy"));
        assertEquals("Grass", DeckService.inferEnergyType("Grass Energy", "Energy"));
        assertEquals("Lightning", DeckService.inferEnergyType("Lightning Energy", "Energy"));
        assertEquals("Psychic", DeckService.inferEnergyType("Psychic Energy", "Energy"));
        assertEquals("Fighting", DeckService.inferEnergyType("Fighting Energy", "Energy"));
        assertEquals("Darkness", DeckService.inferEnergyType("Darkness Energy", "Energy"));
        assertEquals("Metal", DeckService.inferEnergyType("Metal Energy", "Energy"));
        assertEquals("Fairy", DeckService.inferEnergyType("Fairy Energy", "Energy"));
        assertEquals("Dragon", DeckService.inferEnergyType("Dragon Energy", "Energy"));
    }

    @Test
    void inferEnergyTypeReturnsColorlessWhenBlank() {
        assertEquals("Colorless", DeckService.inferEnergyType("Unknown", "  "));
    }
}
