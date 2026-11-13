package com.utn.pokemontcg.game.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig(new ObjectMapper(), "http://localhost:4200");

    @Test
    void passwordEncoderExists() {
        assertNotNull(config.passwordEncoder());
        assertInstanceOf(BCryptPasswordEncoder.class, config.passwordEncoder());
    }

    @Test
    void corsConfigurationSourceAllowsConfiguredOrigin() {
        var source = config.corsConfigurationSource();
        var corsConfig = source.getCorsConfiguration(new MockHttpServletRequest());
        assertNotNull(corsConfig);
        assertTrue(corsConfig.getAllowedOrigins().contains("http://localhost:4200"));
    }

    @Test
    void corsConfigurationSourceAllowsAuthorizationHeader() {
        var source = config.corsConfigurationSource();
        var corsConfig = source.getCorsConfiguration(new MockHttpServletRequest());
        assertNotNull(corsConfig);
        assertTrue(corsConfig.getAllowedHeaders().contains("Authorization"));
        assertTrue(corsConfig.getAllowedHeaders().contains("Content-Type"));
    }

    @Test
    void corsConfigurationSourceAllowsHttpMethods() {
        var source = config.corsConfigurationSource();
        var corsConfig = source.getCorsConfiguration(new MockHttpServletRequest());
        assertNotNull(corsConfig);
        assertTrue(corsConfig.getAllowedMethods().contains("GET"));
        assertTrue(corsConfig.getAllowedMethods().contains("POST"));
        assertTrue(corsConfig.getAllowedMethods().contains("PUT"));
        assertTrue(corsConfig.getAllowedMethods().contains("DELETE"));
        assertTrue(corsConfig.getAllowedMethods().contains("OPTIONS"));
    }

    @Test
    void corsConfigurationSourceAllowsCredentials() {
        var source = config.corsConfigurationSource();
        var corsConfig = source.getCorsConfiguration(new MockHttpServletRequest());
        assertNotNull(corsConfig);
        assertTrue(corsConfig.getAllowCredentials());
    }
}
