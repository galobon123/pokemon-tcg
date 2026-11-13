package com.utn.pokemontcg.game.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.game.model.User;
import com.utn.pokemontcg.game.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityConfigMoreTest {

    private final SecurityConfig config = new SecurityConfig(new ObjectMapper(), "*");

    @Test
    void userDetailsServiceReturnsUserWhenFound() {
        UserRepository repo = mock(UserRepository.class);
        User user = new User();
        user.setEmail("test@test.com");
        when(repo.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        UserDetailsService service = config.userDetailsService(repo);
        assertNotNull(service.loadUserByUsername("test@test.com"));
    }

    @Test
    void userDetailsServiceThrowsWhenNotFound() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findByEmail("missing@test.com")).thenReturn(Optional.empty());
        UserDetailsService service = config.userDetailsService(repo);
        assertThrows(UsernameNotFoundException.class, () ->
            service.loadUserByUsername("missing@test.com"));
    }

    @Test
    void authenticationProviderIsConfigured() {
        UserRepository repo = mock(UserRepository.class);
        UserDetailsService userDetailsService = config.userDetailsService(repo);
        DaoAuthenticationProvider provider = config.authenticationProvider(
            userDetailsService, config.passwordEncoder());
        assertNotNull(provider);
    }

    @Test
    void passwordEncoderEncodesCorrectly() {
        BCryptPasswordEncoder encoder = (BCryptPasswordEncoder) config.passwordEncoder();
        String raw = "password123";
        String encoded = encoder.encode(raw);
        assertTrue(encoder.matches(raw, encoded));
    }
}
