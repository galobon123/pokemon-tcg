package com.utn.pokemontcg.card.infrastructure;

import com.utn.pokemontcg.card.infrastructure.persistence.CardSetEntity;
import com.utn.pokemontcg.card.infrastructure.persistence.CardSetJpaRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CardDiffCheckerTest {

    private final CardSetJpaRepository repository = mock(CardSetJpaRepository.class);
    private final CardDiffChecker checker = new CardDiffChecker(repository);

    @Test
    void hasChangesWhenSetDoesNotExistLocally() {
        when(repository.findById("xy1")).thenReturn(Optional.empty());

        assertTrue(checker.hasChanges("xy1", "2024/01/01", 146));
    }

    @Test
    void hasNoChangesWhenFingerprintMatches() {
        CardSetEntity local = new CardSetEntity();
        local.setId("xy1");
        local.setApiUpdatedAt("2024/01/01");
        local.setTotalCardsApi(146);
        when(repository.findById("xy1")).thenReturn(Optional.of(local));

        assertFalse(checker.hasChanges("xy1", "2024/01/01", 146));
    }

    @Test
    void hasChangesWhenApiUpdatedAtDiffers() {
        CardSetEntity local = new CardSetEntity();
        local.setId("xy1");
        local.setApiUpdatedAt("2024/01/01");
        local.setTotalCardsApi(146);
        when(repository.findById("xy1")).thenReturn(Optional.of(local));

        assertTrue(checker.hasChanges("xy1", "2024/02/01", 146));
    }
}
