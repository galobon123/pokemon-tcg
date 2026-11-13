package com.utn.pokemontcg.game;

import com.utn.pokemontcg.game.model.User;
import com.utn.pokemontcg.game.security.AuthenticatedPlayerResolver;
import com.utn.pokemontcg.game.security.JwtAuthFilter;
import com.utn.pokemontcg.game.security.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityCoverageTest {

    private static final String SECRET = "this_is_a_test_secret_with_more_than_32_chars";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void jwtUtilGeneratesExtractsAndValidatesTokens() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 604800000);
        UserDetails user = org.springframework.security.core.userdetails.User
            .withUsername("ash@example.com")
            .password("secret")
            .authorities("ROLE_USER")
            .build();

        String token = jwtUtil.generateToken(user);

        assertEquals("ash@example.com", jwtUtil.extractUsername(token));
        assertTrue(jwtUtil.validateToken(token, user));
        UserDetails otherUser = org.springframework.security.core.userdetails.User
            .withUsername("misty@example.com")
            .password("secret")
            .authorities("ROLE_USER")
            .build();
        assertFalse(jwtUtil.validateToken(token, otherUser));
    }

    @Test
    void jwtUtilIncludesPlayerIdClaimForDomainUsers() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 604800000);
        User user = new User();
        java.util.UUID playerId = java.util.UUID.randomUUID();
        user.setPlayerId(playerId);
        user.setEmail("ash@example.com");
        user.setPassword("secret");

        String token = jwtUtil.generateToken(user);

        assertEquals(playerId, jwtUtil.extractPlayerId(token).orElseThrow());
    }

    @Test
    void jwtAuthFilterSetsAuthenticationForValidBearerToken() throws Exception {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 604800000);
        UserDetails user = org.springframework.security.core.userdetails.User
            .withUsername("ash@example.com")
            .password("secret")
            .authorities("ROLE_USER")
            .build();
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername("ash@example.com")).thenReturn(user);
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtUtil.generateToken(user));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("ash@example.com", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(chain).doFilter(request, response);
    }

    @Test
    void jwtAuthFilterIgnoresMissingOrInvalidBearerToken() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(new JwtUtil(SECRET, 604800000), mock(UserDetailsService.class));
        MockHttpServletRequest missingHeaderRequest = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(missingHeaderRequest, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());

        MockHttpServletRequest invalidTokenRequest = new MockHttpServletRequest();
        invalidTokenRequest.addHeader("Authorization", "Bearer invalid-token");

        filter.doFilter(invalidTokenRequest, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void userDetailsDefaultsAndAccountFlagsAreStable() {
        User user = new User();
        java.util.UUID playerId = java.util.UUID.randomUUID();
        user.setId(7L);
        user.setPlayerId(playerId);
        user.setUsername("Ash");
        user.setEmail("ash@example.com");
        user.setPassword("secret");
        user.setRole("");

        assertEquals(7L, user.getId());
        assertEquals(playerId, user.getPlayerId());
        assertEquals("Ash", user.displayUsername());
        assertEquals("ash@example.com", user.getEmail());
        assertEquals("ash@example.com", user.getUsername());
        assertEquals("secret", user.getPassword());
        assertEquals("ROLE_USER", user.getRole());
        assertEquals("ROLE_USER", user.getAuthorities().iterator().next().getAuthority());
        assertTrue(user.isAccountNonExpired());
        assertTrue(user.isAccountNonLocked());
        assertTrue(user.isCredentialsNonExpired());
        assertTrue(user.isEnabled());
    }

    @Test
    void authenticatedPlayerResolverUsesUserPlayerIdAndRejectsSpoofing() {
        AuthenticatedPlayerResolver resolver = new AuthenticatedPlayerResolver();
        User user = new User();
        java.util.UUID playerId = java.util.UUID.randomUUID();
        user.setPlayerId(playerId);

        assertEquals(playerId, resolver.resolve(user));
        assertEquals(playerId, resolver.requireMatching(user, playerId));
        assertEquals(playerId, resolver.resolveOrFallback(null, playerId));
        org.junit.jupiter.api.Assertions.assertThrows(
            AccessDeniedException.class,
            () -> resolver.requireMatching(user, java.util.UUID.randomUUID())
        );
        org.junit.jupiter.api.Assertions.assertThrows(
            AccessDeniedException.class,
            () -> resolver.resolve(org.springframework.security.core.userdetails.User.withUsername("mock")
                .password("x")
                .authorities("ROLE_USER")
                .build())
        );
    }
}
