package com.utn.pokemontcg.game.domain.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AbilityEffectRegistry {

    private final Map<String, AbilityEffectStrategy> byAbilityName;

    public AbilityEffectRegistry(List<AbilityEffectStrategy> strategies) {
        this.byAbilityName = strategies.stream()
            .collect(Collectors.toUnmodifiableMap(
                strategy -> normalize(strategy.abilityName()),
                Function.identity(),
                (first, ignored) -> first
            ));
    }

    public Optional<AbilityEffectStrategy> find(String abilityName) {
        return Optional.ofNullable(byAbilityName.get(normalize(abilityName)));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
