package com.utn.pokemontcg.card.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CardJpaRepository extends JpaRepository<CardEntity, String> {

    List<CardEntity> findBySetId(String setId);

    @Query("SELECT c FROM CardEntity c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<CardEntity> findByNameContaining(@Param("name") String name);

    boolean existsBySetId(String setId);
}
