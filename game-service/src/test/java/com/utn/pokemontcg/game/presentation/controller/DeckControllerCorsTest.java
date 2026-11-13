package com.utn.pokemontcg.game.presentation.controller;

import com.utn.pokemontcg.game.application.service.deck.DeckService;
import com.utn.pokemontcg.game.config.CorsConfig;
import com.utn.pokemontcg.game.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeckController.class)
@ContextConfiguration(classes = {DeckController.class, CorsConfig.class})
@AutoConfigureMockMvc(addFilters = false)
class DeckControllerCorsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeckService deckService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @Test
    void shouldAllowAnyOrigin() throws Exception {
        mockMvc.perform(options("/api/decks")
                .header("Origin", "http://example.test")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(result -> assertEquals(
                "http://example.test",
                result.getResponse().getHeader("Access-Control-Allow-Origin")
            ));
    }
}
