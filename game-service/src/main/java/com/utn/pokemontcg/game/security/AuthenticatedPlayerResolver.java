package com.utn.pokemontcg.game.security;

import com.utn.pokemontcg.game.model.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthenticatedPlayerResolver {

    public UUID resolve(UserDetails userDetails) {
        if (userDetails instanceof User user) {
            return user.getPlayerId();
        }
        throw new AccessDeniedException("No se pudo identificar al jugador autenticado");
    }

    public UUID resolveOrFallback(UserDetails userDetails, UUID fallbackPlayerId) {
        if (userDetails == null) {
            return fallbackPlayerId;
        }
        return resolve(userDetails);
    }

    public UUID requireMatching(UserDetails userDetails, UUID requestedPlayerId) {
        UUID authenticatedPlayerId = resolveOrFallback(userDetails, requestedPlayerId);
        if (userDetails != null && requestedPlayerId != null && !authenticatedPlayerId.equals(requestedPlayerId)) {
            throw new AccessDeniedException("El playerId no pertenece al usuario autenticado");
        }
        return authenticatedPlayerId;
    }

    public void assertOwner(UserDetails userDetails, UUID ownerPlayerId) {
        if (userDetails == null) {
            return;
        }
        UUID authenticatedPlayerId = resolve(userDetails);
        if (!authenticatedPlayerId.equals(ownerPlayerId)) {
            throw new AccessDeniedException("No tenes permiso para acceder a este recurso");
        }
    }
}
