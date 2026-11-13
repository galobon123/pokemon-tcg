package com.utn.pokemontcg.game.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.mockito.Mockito.*;

class CorsConfigTest {

    @Test
    void addCorsMappingsAppliesWildcardConfig() {
        CorsConfig config = new CorsConfig();
        CorsRegistry registry = mock(CorsRegistry.class);
        CorsRegistration registration = mock(CorsRegistration.class);
        when(registry.addMapping("/**")).thenReturn(registration);
        when(registration.allowedOriginPatterns(anyString())).thenReturn(registration);
        when(registration.allowedMethods(anyString())).thenReturn(registration);
        when(registration.allowedHeaders(anyString())).thenReturn(registration);

        config.addCorsMappings(registry);

        verify(registry).addMapping("/**");
        verify(registration).allowedOriginPatterns("*");
        verify(registration).allowedMethods("*");
        verify(registration).allowedHeaders("*");
    }
}
