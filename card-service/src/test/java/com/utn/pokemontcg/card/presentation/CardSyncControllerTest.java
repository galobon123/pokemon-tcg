package com.utn.pokemontcg.card.presentation;

import com.utn.pokemontcg.card.infrastructure.CardSetSyncService;
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

@WebMvcTest(CardSyncController.class)
class CardSyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardSetSyncService cardSetSyncService;

    @Test
    void triggersSetSync() throws Exception {
        when(cardSetSyncService.syncSet("xy1")).thenReturn(146);

        mockMvc.perform(post("/api/admin/sync/sets/xy1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.setId").value("xy1"))
            .andExpect(jsonPath("$.imported").value(146));

        verify(cardSetSyncService).syncSet("xy1");
    }
}
