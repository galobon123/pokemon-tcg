package com.utn.pokemontcg.game.security;

import com.utn.pokemontcg.game.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage push for {@link AuthenticatedPlayerResolver} (85.7% branch).
 * Targets the 1/17 missed branch and rounds out the secondary methods.
 */
class AuthenticatedPlayerResolverBranchCoverageTest {

    private final AuthenticatedPlayerResolver resolver = new AuthenticatedPlayerResolver();

    @Test
    void assertOwnerDoesNothingForNullUserDetails() {
        UUID owner = UUID.randomUUID();
        // null userDetails: early return, no exception
        resolver.assertOwner(null, owner);
    }

    @Test
    void assertOwnerThrowsWhenPlayerIdsDiffer() {
        User user = new User();
        user.setPlayerId(UUID.randomUUID());

        UUID otherOwner = UUID.randomUUID();
        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
            () -> resolver.assertOwner(user, otherOwner));
        assertTrue(ex.getMessage().toLowerCase().contains("permiso")
            || ex.getMessage().toLowerCase().contains("recurso"));
    }

    @Test
    void assertOwnerSucceedsWhenPlayerIdsMatch() {
        User user = new User();
        UUID id = UUID.randomUUID();
        user.setPlayerId(id);

        // no exception
        resolver.assertOwner(user, id);
    }

    @Test
    void requireMatchingFallsBackToRequestedWhenUserDetailsIsNull() {
        UUID requested = UUID.randomUUID();
        UUID result = resolver.requireMatching(null, requested);
        assertEquals(requested, result,
            "requireMatching con null userDetails debe devolver requestedPlayerId");
    }

    @Test
    void requireMatchingReturnsAuthenticatedIdWhenRequestedIsNull() {
        User user = new User();
        UUID id = UUID.randomUUID();
        user.setPlayerId(id);

        UUID result = resolver.requireMatching(user, null);
        assertEquals(id, result);
    }

    @Test
    void requireMatchingThrowsWhenIdsDiffer() {
        User user = new User();
        user.setPlayerId(UUID.randomUUID());

        assertThrows(AccessDeniedException.class,
            () -> resolver.requireMatching(user, UUID.randomUUID()));
    }

    @Test
    void resolveOrFallbackReturnsFallbackForNullUser() {
        UUID fallback = UUID.randomUUID();
        UUID result = resolver.resolveOrFallback(null, fallback);
        assertEquals(fallback, result);
    }

    @Test
    void resolveOrFallbackResolvesForValidUser() {
        User user = new User();
        UUID id = UUID.randomUUID();
        user.setPlayerId(id);

        UUID result = resolver.resolveOrFallback(user, UUID.randomUUID());
        assertEquals(id, result);
    }
}
