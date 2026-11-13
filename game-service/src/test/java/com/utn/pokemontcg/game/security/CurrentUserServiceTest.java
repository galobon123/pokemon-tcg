package com.utn.pokemontcg.game.security;

import com.utn.pokemontcg.game.model.User;
import com.utn.pokemontcg.game.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserServiceTest {

    @Test
    void requireUserReturnsUserWhenFound() {
        UserRepository repo = mock(UserRepository.class);
        CurrentUserService service = new CurrentUserService(repo);
        User user = new User();
        user.setPlayerId(UUID.randomUUID());
        when(repo.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        UserDetails principal = new org.springframework.security.core.userdetails.User("test@test.com", "", List.of());
        assertSame(user, service.requireUser(principal));
    }

    @Test
    void requireUserThrowsWhenUserDetailsIsNull() {
        UserRepository repo = mock(UserRepository.class);
        CurrentUserService service = new CurrentUserService(repo);
        assertThrows(IllegalStateException.class, () -> service.requireUser(null));
    }

    @Test
    void requireUserThrowsWhenUsernameIsBlank() {
        UserRepository repo = mock(UserRepository.class);
        CurrentUserService service = new CurrentUserService(repo);
        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn("");
        assertThrows(IllegalStateException.class, () -> service.requireUser(principal));
    }

    @Test
    void requireUserThrowsWhenUsernameIsNull() {
        UserRepository repo = mock(UserRepository.class);
        CurrentUserService service = new CurrentUserService(repo);
        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> service.requireUser(principal));
    }

    @Test
    void requireUserThrowsWhenUserNotFound() {
        UserRepository repo = mock(UserRepository.class);
        CurrentUserService service = new CurrentUserService(repo);
        when(repo.findByEmail("missing@test.com")).thenReturn(Optional.empty());
        UserDetails principal = new org.springframework.security.core.userdetails.User("missing@test.com", "", List.of());
        assertThrows(IllegalStateException.class, () -> service.requireUser(principal));
    }
}
