package com.utn.pokemontcg.card.infrastructure;

import com.utn.pokemontcg.card.infrastructure.persistence.CardSetJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CardDiffChecker {

    private final CardSetJpaRepository cardSetJpaRepository;

    public CardDiffChecker(CardSetJpaRepository cardSetJpaRepository) {
        this.cardSetJpaRepository = cardSetJpaRepository;
    }

    public boolean hasChanges(String setId, String apiUpdatedAt, Integer apiTotal) {
        return cardSetJpaRepository.findById(setId)
            .map(local -> !Objects.equals(apiUpdatedAt, local.getApiUpdatedAt())
                || !Objects.equals(apiTotal, local.getTotalCardsApi()))
            .orElse(true);
    }
}
