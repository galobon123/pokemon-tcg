package com.utn.pokemontcg.game.security;

import com.utn.pokemontcg.game.application.repository.GameStateRepository;
import com.utn.pokemontcg.game.application.service.GameApplicationService;
import com.utn.pokemontcg.game.presentation.controller.HistoryController;
import com.utn.pokemontcg.game.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test that exercises the full SecurityConfig.securityFilterChain
 * (CSRF, CORS, session policy, request matchers, exception handling)
 * by driving MockMvc through a controller endpoint.
 *
 * This is the only way to push coverage on the lambda bodies inside
 * securityFilterChain(): they only run when the actual filter chain executes.
 *
 * Tests use @WebMvcTest with a real Spring context — Database-bound beans
 * (JpaGameStateRepository etc.) are NOT loaded, so this slice is light.
 */
@WebMvcTest(controllers = HistoryController.class)
@Import(SecurityConfig.class)
@ContextConfiguration(classes = {
    HistoryController.class,
    SecurityConfig.class,
    JwtAuthFilter.class,
    JwtUtil.class
})
@AutoConfigureMockMvc
class SecurityConfigFilterChainIntegrationTest {

    @Configuration
    static class TestSecurityBeansConfig {
        // SecurityConfig provides passwordEncoder and userDetailsService; we
        // don't need to redefine them here.
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameApplicationService gameApplicationService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private GameStateRepository gameStateRepository;

    @Test
    void unauthenticatedRequestTriggersAuthenticationEntryPoint() throws Exception {
        // No Authorization header -> SecurityConfig's authenticationEntryPoint
        // is invoked, which calls writeError(...) and returns 401 + JSON.
        mockMvc.perform(get("/api/history"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("No autorizado"));
    }

    @Test
    void optionsRequestIsPermittedByCorsConfig() throws Exception {
        // OPTIONS preflight is a CORS request — SecurityConfig's CORS configuration
        // allows it without authentication.
        mockMvc.perform(options("/api/history")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk());
    }

    @Test
    void pokemonPublicEndpointDoesNotRequireAuth() throws Exception {
        // /api/pokemon/** is permitAll() in SecurityConfig.
        // We hit a non-existent path; SecurityConfig lets the request through and
        // the dispatcher returns 404 (or 401 if controller throws).
        // The point of this test is to exercise the request matchers, NOT the
        // controller itself — so we just assert the request is not 401.
        mockMvc.perform(get("/api/pokemon/nonexistent"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status == 401) {
                    throw new AssertionError("Expected pokemon endpoint to be permitAll, got 401");
                }
            });
    }
}
