package com.utn.pokemontcg.card.presentation;

import com.utn.pokemontcg.card.infrastructure.CardImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardImportController.class)
class CardImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardImportService cardImportService;

    @Test
    void shouldImportSetAndReturnSummary() throws Exception {
        when(cardImportService.importSet("xy1")).thenReturn(146);

        mockMvc.perform(post("/api/cards/import/xy1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.setId").value("xy1"))
            .andExpect(jsonPath("$.imported").value(146))
            .andExpect(jsonPath("$.message").value("146 cartas importadas correctamente"));

        verify(cardImportService).importSet("xy1");
    }

    @Test
    void shouldReturnMessageReflectingImportedCount() throws Exception {
        when(cardImportService.importSet("base1")).thenReturn(0);

        mockMvc.perform(post("/api/cards/import/base1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.setId").value("base1"))
            .andExpect(jsonPath("$.imported").value(0))
            .andExpect(jsonPath("$.message").value("0 cartas importadas correctamente"));

        verify(cardImportService).importSet("base1");
    }
}
