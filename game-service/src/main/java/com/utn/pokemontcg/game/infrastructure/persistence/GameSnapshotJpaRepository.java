package com.utn.pokemontcg.game.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameSnapshotJpaRepository extends JpaRepository<GameSnapshotEntity, Long> {
    Optional<GameSnapshotEntity> findTopByGameIdOrderByVersionDesc(UUID gameId);
    long countByGameId(UUID gameId);

    @Query("SELECT DISTINCT s.gameId FROM GameSnapshotEntity s")
    List<UUID> findAllDistinctGameIds();
}
