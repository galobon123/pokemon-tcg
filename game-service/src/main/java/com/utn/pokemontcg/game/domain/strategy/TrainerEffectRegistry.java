package com.utn.pokemontcg.game.domain.strategy;

import com.utn.pokemontcg.game.domain.model.GameCard;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TrainerEffectRegistry {

    private final Map<String, TrainerEffectStrategy> byCardName;

    public TrainerEffectRegistry(List<TrainerEffectStrategy> strategies) {
        this.byCardName = strategies.stream()
            .collect(Collectors.toUnmodifiableMap(
                strategy -> normalize(strategy.cardName()),
                Function.identity(),
                (first, ignored) -> first
            ));
    }

    public Optional<TrainerEffectStrategy> find(GameCard card) {
        if (card == null || card.name() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCardName.get(normalize(card.name())));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
