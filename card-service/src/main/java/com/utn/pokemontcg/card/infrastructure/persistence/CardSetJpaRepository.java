package com.utn.pokemontcg.card.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CardSetJpaRepository extends JpaRepository<CardSetEntity, String> {
}
