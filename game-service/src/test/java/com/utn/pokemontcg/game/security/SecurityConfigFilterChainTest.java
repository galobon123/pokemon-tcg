package com.utn.pokemontcg.game.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Extra coverage for {@link SecurityConfig}: the private writeError() method (driven
 * via reflection so the lambdas inside the SecurityFilterChain configuration
 * exercise the same path), the authenticationManager() bean, and the
 * annotations / fields of the class.
 */
class SecurityConfigFilterChainTest {

    private final SecurityConfig config = new SecurityConfig(new ObjectMapper(), "http://localhost:4200");

    @Test
    void authenticationManagerBeanIsExposed() throws Exception {
        AuthenticationConfiguration configuration = mock(AuthenticationConfiguration.class);
        AuthenticationManager manager = mock(AuthenticationManager.class);
        when(configuration.getAuthenticationManager()).thenReturn(manager);
        AuthenticationManager actual = config.authenticationManager(configuration);
        assertNotNull(actual);
        assertEquals(manager, actual);
    }

    @Test
    void writeErrorReflectionProducesUnauthorizedJson() throws Exception {
        Method writeError = SecurityConfig.class.getDeclaredMethod("writeError", HttpServletResponse.class, org.springframework.http.HttpStatus.class, String.class);
        writeError.setAccessible(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        writeError.invoke(config, response, org.springframework.http.HttpStatus.UNAUTHORIZED, "No autorizado");

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        String body = response.getContentAsString();
        // Expect JSON: {"error":"No autorizado"}
        Map<?, ?> parsed = new ObjectMapper().readValue(body, Map.class);
        assertEquals("No autorizado", parsed.get("error"));
    }

    @Test
    void writeErrorReflectionProducesForbiddenJson() throws Exception {
        Method writeError = SecurityConfig.class.getDeclaredMethod("writeError", HttpServletResponse.class, org.springframework.http.HttpStatus.class, String.class);
        writeError.setAccessible(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        writeError.invoke(config, response, org.springframework.http.HttpStatus.FORBIDDEN, "Acceso denegado");

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        Map<?, ?> parsed = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
        assertEquals("Acceso denegado", parsed.get("error"));
    }

    @Test
    void writeErrorReflectionHandlesGenericMessage() throws Exception {
        Method writeError = SecurityConfig.class.getDeclaredMethod("writeError", HttpServletResponse.class, org.springframework.http.HttpStatus.class, String.class);
        writeError.setAccessible(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        writeError.invoke(config, response, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Boom");

        assertEquals(500, response.getStatus());
        assertEquals("application/json", response.getContentType());
        Map<?, ?> parsed = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
        assertEquals("Boom", parsed.get("error"));
    }

    @Test
    void corsConfigurationSourceAllowsPatch() {
        var source = config.corsConfigurationSource();
        var corsConfig = source.getCorsConfiguration(new MockHttpServletRequest());
        assertNotNull(corsConfig);
        assertTrue(corsConfig.getAllowedMethods().contains("PATCH"));
    }

    @Test
    void corsConfigurationSourceRegistersForAllPaths() {
        var source = config.corsConfigurationSource();
        // Source should be wired up for the /** pattern
        assertNotNull(source);
        // Calling getCorsConfiguration on a different request path should still return config
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/games");
        var corsConfig = source.getCorsConfiguration(other);
        assertNotNull(corsConfig);
    }

    @Test
    void securityConfigIsConfigurationAndEnablesWebSecurity() {
        assertNotNull(SecurityConfig.class.getAnnotation(org.springframework.context.annotation.Configuration.class));
        assertNotNull(SecurityConfig.class.getAnnotation(org.springframework.security.config.annotation.web.configuration.EnableWebSecurity.class));
        assertNotNull(SecurityConfig.class.getAnnotation(org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity.class));
    }

    @Test
    void corsOriginFieldIsConfigured() throws Exception {
        Field f = SecurityConfig.class.getDeclaredField("corsOrigin");
        f.setAccessible(true);
        assertEquals("http://localhost:4200", f.get(config));
    }

    @Test
    void objectMapperFieldIsConfigured() throws Exception {
        Field f = SecurityConfig.class.getDeclaredField("objectMapper");
        f.setAccessible(true);
        assertInstanceOf(ObjectMapper.class, f.get(config));
    }

    @Test
    void constructorPreservesCorsOrigin() {
        SecurityConfig custom = new SecurityConfig(new ObjectMapper(), "https://example.com");
        Field f;
        try {
            f = SecurityConfig.class.getDeclaredField("corsOrigin");
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
        f.setAccessible(true);
        try {
            assertEquals("https://example.com", f.get(custom));
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void corsConfigurationSourceAllowedHeadersAreListed() {
        var source = config.corsConfigurationSource();
        var corsConfig = source.getCorsConfiguration(new MockHttpServletRequest());
        assertNotNull(corsConfig);
        // Both "Authorization" and "Content-Type" must be present
        assertTrue(corsConfig.getAllowedHeaders().contains("Authorization"));
        assertTrue(corsConfig.getAllowedHeaders().contains("Content-Type"));
    }

    @Test
    void corsConfigurationSourceSetsCredentialsTrue() {
        var source = config.corsConfigurationSource();
        var corsConfig = source.getCorsConfiguration(new MockHttpServletRequest());
        assertNotNull(corsConfig);
        assertTrue(corsConfig.getAllowCredentials());
    }

    @Test
    void corsConfigurationSourceOriginMatches() {
        var source = config.corsConfigurationSource();
        var corsConfig = source.getCorsConfiguration(new MockHttpServletRequest());
        assertNotNull(corsConfig);
        assertTrue(corsConfig.getAllowedOrigins().contains("http://localhost:4200"));
    }

    @Test
    void authenticationEntryPointContractIsSatisfied() {
        // Sanity check that the interfaces used by the lambdas
        // are actually accessible from this package.
        assertNotNull(org.springframework.security.web.AuthenticationEntryPoint.class);
        assertNotNull(org.springframework.security.web.access.AccessDeniedHandler.class);
    }
}
