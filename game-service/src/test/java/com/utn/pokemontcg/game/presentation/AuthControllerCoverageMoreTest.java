package com.utn.pokemontcg.game.presentation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.utn.pokemontcg.game.model.User;
import com.utn.pokemontcg.game.presentation.controller.AuthController;
import com.utn.pokemontcg.game.presentation.dto.auth.AuthResponse;
import com.utn.pokemontcg.game.repository.UserRepository;
import com.utn.pokemontcg.game.security.JwtUtil;
import com.utn.pokemontcg.game.security.RegistrationDeckSeeder;

/**
 * Extra branch coverage for {@link AuthController}:
 * <ul>
 *   <li>GET /me</li>
 *   <li>PATCH /avatar with valid / null / blank / missing "avatar" key</li>
 * </ul>
 */
class AuthControllerCoverageMoreTest {

    @Test
    void meReturnsAuthResponseWithUserData() {
        UserRepository repository = mock(UserRepository.class);
        AuthController controller = new AuthController(
            repository,
            mock(PasswordEncoder.class),
            mock(AuthenticationManager.class),
            jwtUtil(),
            mock(RegistrationDeckSeeder.class),
            604800000L);

        User user = new User();
        user.setId(10L);
        user.setUsername("Red");
        user.setEmail("red@example.com");
        user.setPassword("encoded");
        user.setAvatar("https://example.com/red.png");

        var response = controller.me(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        AuthResponse body = assertInstanceOf(AuthResponse.class, response.getBody());
        assertEquals(10L, body.id());
        assertEquals("Red", body.username());
        assertEquals("red@example.com", body.email());
        assertEquals("https://example.com/red.png", body.avatar());
        assertNotNull(body.token());
        assertNotNull(body.playerId());
    }

    @Test
    void updateAvatarWithValidUrlReturnsUpdatedAuthResponse() {
        UserRepository repository = mock(UserRepository.class);
        AuthController controller = new AuthController(
            repository,
            mock(PasswordEncoder.class),
            mock(AuthenticationManager.class),
            jwtUtil(),
            mock(RegistrationDeckSeeder.class),
            604800000L);
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = new User();
        user.setId(11L);
        user.setUsername("Blue");
        user.setEmail("blue@example.com");
        user.setPassword("encoded");

        Map<String, String> body = new HashMap<>();
        body.put("avatar", "https://example.com/blue.png");

        var response = controller.updateAvatar(body, user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        AuthResponse authBody = assertInstanceOf(AuthResponse.class, response.getBody());
        assertEquals("https://example.com/blue.png", authBody.avatar());
        assertEquals("https://example.com/blue.png", user.getAvatar());
        verify(repository).save(user);
    }

    @Test
    void updateAvatarWithNullValueReturnsBadRequest() {
        UserRepository repository = mock(UserRepository.class);
        AuthController controller = new AuthController(
            repository,
            mock(PasswordEncoder.class),
            mock(AuthenticationManager.class),
            jwtUtil(),
            mock(RegistrationDeckSeeder.class),
            604800000L);

        User user = new User();
        user.setEmail("blue@example.com");
        user.setAvatar("https://old.example.com/old.png");

        Map<String, String> body = new HashMap<>();
        body.put("avatar", null);

        var response = controller.updateAvatar(body, user);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> respBody = (Map<String, String>) response.getBody();
        assertNotNull(respBody);
        assertTrue(respBody.get("error").contains("Avatar URL is required"));
        // User avatar was NOT updated
        assertEquals("https://old.example.com/old.png", user.getAvatar());
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void updateAvatarWithBlankValueReturnsBadRequest() {
        UserRepository repository = mock(UserRepository.class);
        AuthController controller = new AuthController(
            repository,
            mock(PasswordEncoder.class),
            mock(AuthenticationManager.class),
            jwtUtil(),
            mock(RegistrationDeckSeeder.class),
            604800000L);

        User user = new User();
        user.setEmail("blue@example.com");
        user.setAvatar("https://old.example.com/old.png");

        Map<String, String> body = new HashMap<>();
        body.put("avatar", "");

        var response = controller.updateAvatar(body, user);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> respBody = (Map<String, String>) response.getBody();
        assertNotNull(respBody);
        assertTrue(respBody.get("error").contains("Avatar URL is required"));
        assertEquals("https://old.example.com/old.png", user.getAvatar());
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void updateAvatarWithMissingKeyReturnsBadRequest() {
        UserRepository repository = mock(UserRepository.class);
        AuthController controller = new AuthController(
            repository,
            mock(PasswordEncoder.class),
            mock(AuthenticationManager.class),
            jwtUtil(),
            mock(RegistrationDeckSeeder.class),
            604800000L);

        User user = new User();
        user.setEmail("blue@example.com");
        user.setAvatar("https://old.example.com/old.png");

        // body has no "avatar" key at all
        Map<String, String> body = new HashMap<>();

        var response = controller.updateAvatar(body, user);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> respBody = (Map<String, String>) response.getBody();
        assertNotNull(respBody);
        assertTrue(respBody.get("error").contains("Avatar URL is required"));
        assertEquals("https://old.example.com/old.png", user.getAvatar());
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void updateAvatarWithWhitespaceOnlyValueReturnsBadRequest() {
        UserRepository repository = mock(UserRepository.class);
        AuthController controller = new AuthController(
            repository,
            mock(PasswordEncoder.class),
            mock(AuthenticationManager.class),
            jwtUtil(),
            mock(RegistrationDeckSeeder.class),
            604800000L);

        User user = new User();
        user.setEmail("blue@example.com");
        user.setAvatar("https://old.example.com/old.png");

        Map<String, String> body = new HashMap<>();
        body.put("avatar", "   ");

        var response = controller.updateAvatar(body, user);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> respBody = (Map<String, String>) response.getBody();
        assertNotNull(respBody);
        assertTrue(respBody.get("error").contains("Avatar URL is required"));
        verify(repository, never()).save(any(User.class));
    }

    private JwtUtil jwtUtil() {
        return new JwtUtil("this_is_a_test_secret_with_more_than_32_chars", 604800000);
    }
}
