package com.utn.pokemontcg.game.presentation;

import com.utn.pokemontcg.game.domain.attack.AttackPatternAnalyzer;
import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.application.service.GameNotFoundException;
import com.utn.pokemontcg.game.application.service.PlayerNotInGameException;
import com.utn.pokemontcg.game.presentation.controller.GameController;
import com.utn.pokemontcg.game.security.AuthenticatedPlayerResolver;
import com.utn.pokemontcg.game.security.JwtUtil;
import com.utn.pokemontcg.game.security.CurrentUserService;
import com.utn.pokemontcg.game.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
@AutoConfigureMockMvc(addFilters = false)
class GameControllerHandTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameApplicationService gameService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private AuthenticatedPlayerResolver authenticatedPlayerResolver;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AttackPatternAnalyzer attackPatternAnalyzer;

    @Test
    void shouldReturnPlayerHand() throws Exception {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        when(authenticatedPlayerResolver.requireMatching(null, playerId)).thenReturn(playerId);
        when(gameService.getHandCardIds(gameId, playerId)).thenReturn(List.of("xy1-1", "xy1-2"));

        mockMvc.perform(get("/api/games/{gameId}/players/{playerId}/hand", gameId, playerId)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gameId").value(gameId.toString()))
            .andExpect(jsonPath("$.playerId").value(playerId.toString()))
            .andExpect(jsonPath("$.cardIds[0]").value("xy1-1"))
            .andExpect(jsonPath("$.cardIds[1]").value("xy1-2"))
            .andExpect(jsonPath("$.handSize").value(2));
    }

    @Test
    void shouldReturnForbiddenWhenPlayerDoesNotBelongToGame() throws Exception {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        when(authenticatedPlayerResolver.requireMatching(null, playerId)).thenReturn(playerId);
        when(gameService.getHandCardIds(gameId, playerId))
            .thenThrow(new PlayerNotInGameException(gameId, playerId));

        mockMvc.perform(get("/api/games/{gameId}/players/{playerId}/hand", gameId, playerId)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnNotFoundWhenGameDoesNotExist() throws Exception {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        when(authenticatedPlayerResolver.requireMatching(null, playerId)).thenReturn(playerId);
        when(gameService.getHandCardIds(gameId, playerId)).thenThrow(new GameNotFoundException(gameId));

        mockMvc.perform(get("/api/games/{gameId}/players/{playerId}/hand", gameId, playerId)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }
}
