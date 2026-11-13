package com.utn.pokemontcg.game.presentation;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;

import com.utn.pokemontcg.game.application.service.deck.DeckService;
import com.utn.pokemontcg.game.model.User;
import com.utn.pokemontcg.game.presentation.controller.DeckController;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckResponse;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckUpsertSelfRequest;
import com.utn.pokemontcg.game.presentation.dto.deck.DeckValidationResult;
import com.utn.pokemontcg.game.security.CurrentUserService;

class DeckControllerCoverageTest {

    @Test
    void delegatesCrudOperationsToDeckService() {
        DeckService service = mock(DeckService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckController controller = new DeckController(service, currentUserService);
        UUID deckId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        User user = new User();
        user.setPlayerId(playerId);
        when(currentUserService.requireUser(null)).thenReturn(user);
        DeckUpsertSelfRequest request = new DeckUpsertSelfRequest("Deck", List.of());
        DeckResponse response = new DeckResponse(deckId, playerId, "Deck", List.of(), new DeckValidationResult(true, List.of()));
        when(service.createForPlayer(playerId, request)).thenReturn(response);
        when(service.updateForPlayer(playerId, deckId, request)).thenReturn(response);
        when(service.getForPlayer(playerId, deckId)).thenReturn(response);
        when(service.listByPlayer(playerId)).thenReturn(List.of(response));

        assertEquals(response, controller.create(request, null).getBody());
        assertEquals(response, controller.update(deckId, request, null).getBody());
        assertEquals(response, controller.get(deckId, null).getBody());
        assertEquals(List.of(response), controller.listMine(null).getBody());
        assertEquals(HttpStatus.NO_CONTENT, controller.delete(deckId, null).getStatusCode());
        verify(service).deleteForPlayer(playerId, deckId);
    }
}
