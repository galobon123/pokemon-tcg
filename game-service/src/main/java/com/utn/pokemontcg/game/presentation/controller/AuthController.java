package com.utn.pokemontcg.game.presentation.controller;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.utn.pokemontcg.game.model.User;
import com.utn.pokemontcg.game.presentation.dto.auth.AuthResponse;
import com.utn.pokemontcg.game.presentation.dto.auth.LoginRequest;
import com.utn.pokemontcg.game.presentation.dto.auth.RegisterRequest;
import com.utn.pokemontcg.game.repository.UserRepository;
import com.utn.pokemontcg.game.security.JwtUtil;
import com.utn.pokemontcg.game.security.RegistrationDeckSeeder;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RegistrationDeckSeeder registrationDeckSeeder;
    private final long jwtExpirationMillis;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          RegistrationDeckSeeder registrationDeckSeeder,
                          @Value("${app.jwt.expiration}") long jwtExpirationMillis) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.registrationDeckSeeder = registrationDeckSeeder;
        this.jwtExpirationMillis = jwtExpirationMillis;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "El email ya esta registrado"));
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole("ROLE_USER");
        user.setPlayerId(UUID.randomUUID());

        User saved = userRepository.save(user);

        // Assign starter decks to the newly created player.
        registrationDeckSeeder.seedFor(saved.getPlayerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        // 1) Authenticate credentials first
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Credenciales inválidas"));
        }

        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() ->
                new org.springframework.security.authentication.BadCredentialsException("Credenciales inválidas"));

        // 2) Check for active session
        if (user.getLastLoginAt() != null && !request.force()) {
            long elapsed = System.currentTimeMillis() - user.getLastLoginAt().toEpochMilli();
            if (elapsed < jwtExpirationMillis) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                        "error", "Ya tienes una sesión activa de esta cuenta",
                        "username", user.displayUsername(),
                        "avatar", user.getAvatar()
                    ));
            }
        }

        // 3) Grant session
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return ResponseEntity.ok(toResponse(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@AuthenticationPrincipal User user) {
        user.setLastLoginAt(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(toResponse(user));
    }

    @PatchMapping("/avatar")
    public ResponseEntity<?> updateAvatar(@RequestBody Map<String, String> body, @AuthenticationPrincipal User user) {
        String avatar = body.get("avatar");
        if (avatar == null || avatar.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Avatar URL is required"));
        }
        
        user.setAvatar(avatar);
        userRepository.save(user);
        return ResponseEntity.ok(toResponse(user));
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(
            jwtUtil.generateToken(user),
            user.getId(),
            user.getPlayerId(),
            user.displayUsername(),
            user.getEmail(),
            user.getAvatar()
        );
    }
}
