package com.utn.pokemontcg.game.infrastructure.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_action_logs")
public class GameActionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID gameId;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String entry;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 64)
    private com.utn.pokemontcg.game.domain.model.GameActionType action;

    @Column(updatable = false)
    private UUID playerId;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public UUID getGameId() { return gameId; }
    public void setGameId(UUID gameId) { this.gameId = gameId; }
    public String getEntry() { return entry; }
    public void setEntry(String entry) { this.entry = entry; }
    public com.utn.pokemontcg.game.domain.model.GameActionType getAction() { return action; }
    public void setAction(com.utn.pokemontcg.game.domain.model.GameActionType action) { this.action = action; }
    public UUID getPlayerId() { return playerId; }
    public void setPlayerId(UUID playerId) { this.playerId = playerId; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
