package com.utn.pokemontcg.game.security;

import com.utn.pokemontcg.game.model.User;
import com.utn.pokemontcg.game.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireUser(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            throw new IllegalStateException("Usuario autenticado faltante");
        }

        return userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado"));
    }
}
