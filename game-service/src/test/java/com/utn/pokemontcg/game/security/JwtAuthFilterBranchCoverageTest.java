package com.utn.pokemontcg.game.security;

import com.utn.pokemontcg.game.model.User;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch coverage push for {@link JwtAuthFilter} (50% branch).
 * Targets the 2/26 missed branches:
 *  - already-authenticated (SecurityContextHolder.getAuthentication() != null) early return
 *  - token-version mismatch (tokenVersion != user.getTokenVersion()) early return
 */
class JwtAuthFilterBranchCoverageTest {

    private static final String SECRET = "this_is_a_test_secret_with_more_than_32_chars";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void filterSkipsWhenSecurityContextAlreadyAuthenticated() throws Exception {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 604800000);
        UserDetails user = org.springframework.security.core.userdetails.User
            .withUsername("ash@example.com")
            .password("secret")
            .authorities("ROLE_USER")
            .build();
        // pre-populate security context
        SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities()));

        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtUtil.generateToken(user));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        // The filter should not change the existing authentication
        assertEquals(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(chain).doFilter(request, response);
    }

    @Test
    void filterRejectsWhenTokenVersionMismatchesUserVersion() throws Exception {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 604800000);

        User user = new User();
        UUID playerId = UUID.randomUUID();
        user.setPlayerId(playerId);
        user.setEmail("ash@example.com");
        user.setPassword("secret");
        user.setTokenVersion(5);

        // Generate a properly-signed token with tokenVersion=1 that does NOT
        // match the user's version (5). Use the same JwtUtil so the signing
        // key is identical.
        User otherUser = new User();
        otherUser.setPlayerId(playerId);
        otherUser.setEmail("ash@example.com");
        otherUser.setPassword("secret");
        otherUser.setTokenVersion(1);
        String token = jwtUtil.generateToken(otherUser);

        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername(user.getEmail())).thenReturn(user);
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        // No authentication should be set when token version mismatches
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void filterClearsContextOnRuntimeException() throws Exception {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 604800000);

        // Manually built token that will fail signature validation
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername("ash@example.com"))
            .thenThrow(new RuntimeException("user service boom"));

        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtUtil.generateToken(
            org.springframework.security.core.userdetails.User.withUsername("ash@example.com").password("x").authorities("ROLE_USER").build()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
            "RuntimeException debe limpiar el SecurityContext");
        verify(chain).doFilter(request, response);
    }
}
