package com.utn.pokemontcg.game.presentation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.utn.pokemontcg.game.model.User;
import com.utn.pokemontcg.game.presentation.controller.AuthController;
import com.utn.pokemontcg.game.presentation.dto.auth.AuthResponse;
import com.utn.pokemontcg.game.presentation.dto.auth.LoginRequest;
import com.utn.pokemontcg.game.presentation.dto.auth.RegisterRequest;
import com.utn.pokemontcg.game.repository.UserRepository;
import com.utn.pokemontcg.game.security.JwtUtil;
import com.utn.pokemontcg.game.security.RegistrationDeckSeeder;

class AuthControllerCoverageTest {

    @Test
    void registerCreatesUserAndReturnsToken() {
        UserRepository repository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        RegistrationDeckSeeder deckSeeder = mock(RegistrationDeckSeeder.class);
        AuthController controller = new AuthController(repository, encoder, authenticationManager, jwtUtil(), deckSeeder, 604800000L);
        when(repository.existsByEmail("ash@example.com")).thenReturn(false);
        when(encoder.encode("secret1")).thenReturn("encoded");
        when(repository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        var response = controller.register(new RegisterRequest("Ash", "ash@example.com", "secret1"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        AuthResponse body = assertInstanceOf(AuthResponse.class, response.getBody());
        assertEquals(1L, body.id());
        assertNotNull(body.playerId());
        assertEquals("Ash", body.username());
        assertNotNull(body.token());
        verify(encoder).encode("secret1");
        verify(deckSeeder).seedFor(body.playerId());
    }

    @Test
    void registerRejectsDuplicatedEmail() {
        UserRepository repository = mock(UserRepository.class);
        AuthController controller = new AuthController(repository, mock(PasswordEncoder.class), mock(AuthenticationManager.class), jwtUtil(), mock(RegistrationDeckSeeder.class), 604800000L);
        when(repository.existsByEmail("ash@example.com")).thenReturn(true);

        var response = controller.register(new RegisterRequest("Ash", "ash@example.com", "secret1"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void loginReturnsTokenWhenNoActiveSession() {
        UserRepository repository = mock(UserRepository.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        JwtUtil jwt = jwtUtil();
        AuthController controller = new AuthController(repository, mock(PasswordEncoder.class), authenticationManager, jwt, mock(RegistrationDeckSeeder.class), 604800000L);
        User user = new User();
        user.setId(2L);
        user.setUsername("Misty");
        user.setEmail("misty@example.com");
        user.setPassword("encoded");
        user.setTokenVersion(0);
        when(repository.findByEmail("misty@example.com")).thenReturn(Optional.of(user));
        when(repository.save(any(User.class))).thenReturn(user);

        var ok = controller.login(new LoginRequest("misty@example.com", "secret1", false));

        assertEquals(HttpStatus.OK, ok.getStatusCode());
        AuthResponse body = assertInstanceOf(AuthResponse.class, ok.getBody());
        assertEquals("Misty", body.username());
        assertEquals(1, user.getTokenVersion());
        assertNotNull(user.getLastLoginAt());

        String token = body.token();
        int tokenVersion = jwt.extractTokenVersion(token);
        assertEquals(1, tokenVersion);
    }

    @Test
    void loginReturnsUnauthorizedOnBadCredentials() {
        UserRepository repository = mock(UserRepository.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        JwtUtil jwt = jwtUtil();
        AuthController controller = new AuthController(repository, mock(PasswordEncoder.class), authenticationManager, jwt, mock(RegistrationDeckSeeder.class), 604800000L);
        User user = new User();
        user.setEmail("misty@example.com");
        user.setPassword("encoded");
        when(repository.findByEmail("misty@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        var unauthorized = controller.login(new LoginRequest("misty@example.com", "bad", false));

        assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.getStatusCode());
    }

    @Test
    void loginRejectsWhenActiveSessionExists() {
        UserRepository repository = mock(UserRepository.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        JwtUtil jwt = jwtUtil();
        AuthController controller = new AuthController(repository, mock(PasswordEncoder.class), authenticationManager, jwt, mock(RegistrationDeckSeeder.class), 604800000L);
        User user = new User();
        user.setId(1L);
        user.setUsername("Misty");
        user.setEmail("misty@example.com");
        user.setPassword("encoded");
        user.setLastLoginAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(repository.findByEmail("misty@example.com")).thenReturn(Optional.of(user));

        var conflict = controller.login(new LoginRequest("misty@example.com", "secret1", false));

        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
    }

    @Test
    void forceLoginOverridesActiveSession() {
        UserRepository repository = mock(UserRepository.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        JwtUtil jwt = jwtUtil();
        AuthController controller = new AuthController(repository, mock(PasswordEncoder.class), authenticationManager, jwt, mock(RegistrationDeckSeeder.class), 604800000L);
        User user = new User();
        user.setId(3L);
        user.setUsername("Brock");
        user.setEmail("brock@example.com");
        user.setPassword("encoded");
        user.setTokenVersion(2);
        user.setLastLoginAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(repository.findByEmail("brock@example.com")).thenReturn(Optional.of(user));
        when(repository.save(any(User.class))).thenReturn(user);

        var ok = controller.login(new LoginRequest("brock@example.com", "secret1", true));

        assertEquals(HttpStatus.OK, ok.getStatusCode());
        AuthResponse body = assertInstanceOf(AuthResponse.class, ok.getBody());
        assertEquals("Brock", body.username());
        assertEquals(3, user.getTokenVersion());
        assertNotNull(user.getLastLoginAt());

        String token = body.token();
        assertEquals(3, jwt.extractTokenVersion(token));
    }

    @Test
    void logoutClearsLastLoginAt() {
        UserRepository repository = mock(UserRepository.class);
        AuthController controller = new AuthController(repository, mock(PasswordEncoder.class), mock(AuthenticationManager.class), jwtUtil(), mock(RegistrationDeckSeeder.class), 604800000L);
        User user = new User();
        user.setEmail("misty@example.com");
        user.setLastLoginAt(Instant.now());
        when(repository.save(any(User.class))).thenReturn(user);

        var response = controller.logout(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(user.getLastLoginAt());
        verify(repository).save(user);
    }

    private JwtUtil jwtUtil() {
        return new JwtUtil("this_is_a_test_secret_with_more_than_32_chars", 604800000);
    }
}
