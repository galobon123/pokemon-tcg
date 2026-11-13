package com.utn.pokemontcg.game.security;

import com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckCardEntity;
import com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckEntity;
import com.utn.pokemontcg.game.infrastructure.persistence.deck.DeckJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RegistrationDeckSeeder {

    private static final UUID SEED_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final DeckJpaRepository deckRepository;

    public RegistrationDeckSeeder(DeckJpaRepository deckRepository) {
        this.deckRepository = deckRepository;
    }

    public void seedFor(UUID playerId) {
        if (playerId == null) {
            return;
        }

        // Make it idempotent: if the player already has decks, don't duplicate.
        if (!deckRepository.findByPlayerId(playerId).isEmpty()) {
            return;
        }

        List<DeckEntity> seeds = deckRepository.findByPlayerId(SEED_PLAYER_ID);
        for (DeckEntity seed : seeds) {
            deckRepository.save(cloneDeck(seed, playerId));
        }
    }

    private DeckEntity cloneDeck(DeckEntity seed, UUID playerId) {
        DeckEntity clone = new DeckEntity();
        clone.setPlayerId(playerId);
        clone.setName(seed.getName());

        for (var seedCard : seed.getCards()) {
            DeckCardEntity card = new DeckCardEntity();
            card.setDeck(clone);
            card.setCardId(seedCard.getCardId());
            card.setName(seedCard.getName());
            card.setSetId(seedCard.getSetId());
            card.setQuantity(seedCard.getQuantity());
            card.setType(seedCard.getType());
            card.setSubtype(seedCard.getSubtype());
            card.setBasicEnergy(seedCard.isBasicEnergy());
            card.setBasicPokemon(seedCard.isBasicPokemon());
            card.setAceSpec(seedCard.isAceSpec());
            card.setHp(seedCard.getHp());
            card.setAttackDamage(seedCard.getAttackDamage());
            card.setAttackRequiredEnergy(seedCard.getAttackRequiredEnergy());
            card.setAttackName(seedCard.getAttackName());
            card.setAttackText(seedCard.getAttackText());
            card.setEnergyType(seedCard.getEnergyType());
            card.setAttackCost(seedCard.getAttackCost());
            card.setRetreatCost(seedCard.getRetreatCost());
            card.setEvolvesFrom(seedCard.getEvolvesFrom());
            card.setAbilities(seedCard.getAbilities());
            clone.getCards().add(card);
        }
        return clone;
    }
}
