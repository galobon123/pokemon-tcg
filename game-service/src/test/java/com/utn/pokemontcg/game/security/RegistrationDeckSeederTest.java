package com.utn.pokemontcg.game.security;

import com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckCardEntity;
import com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckEntity;
import com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckJpaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class RegistrationDeckSeederTest {

    @Test
    void seedForNullPlayerIdDoesNothing() {
        DeckJpaRepository repo = mock(DeckJpaRepository.class);
        RegistrationDeckSeeder seeder = new RegistrationDeckSeeder(repo);
        seeder.seedFor(null);
        verifyNoInteractions(repo);
    }

    @Test
    void seedForPlayerWithExistingDecksDoesNothing() {
        DeckJpaRepository repo = mock(DeckJpaRepository.class);
        RegistrationDeckSeeder seeder = new RegistrationDeckSeeder(repo);
        UUID playerId = UUID.randomUUID();
        when(repo.findByPlayerId(playerId)).thenReturn(List.of(new DeckEntity()));
        seeder.seedFor(playerId);
        verify(repo).findByPlayerId(playerId);
        verifyNoMoreInteractions(repo);
    }

    @Test
    void seedForClonesSeedDeck() {
        DeckJpaRepository repo = mock(DeckJpaRepository.class);
        RegistrationDeckSeeder seeder = new RegistrationDeckSeeder(repo);
        UUID playerId = UUID.randomUUID();
        UUID seedPlayerId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        DeckEntity seedDeck = new DeckEntity();
        seedDeck.setName("Starter Deck");
        DeckCardEntity card = new DeckCardEntity();
        card.setCardId("card-1");
        card.setName("Pikachu");
        card.setSetId("base1");
        card.setType("Pokemon");
        card.setSubtype("Basic");
        card.setQuantity(1);
        seedDeck.getCards().add(card);

        when(repo.findByPlayerId(playerId)).thenReturn(List.of());
        when(repo.findByPlayerId(seedPlayerId)).thenReturn(List.of(seedDeck));

        seeder.seedFor(playerId);

        verify(repo).findByPlayerId(playerId);
        verify(repo).findByPlayerId(seedPlayerId);
        verify(repo).save(any(DeckEntity.class));
    }
}
