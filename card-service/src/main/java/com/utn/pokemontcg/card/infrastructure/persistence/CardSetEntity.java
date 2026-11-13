package com.utn.pokemontcg.card.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "card_sets")
public class CardSetEntity {

    @Id
    private String id;

    private String name;

    @Column(name = "api_updated_at")
    private String apiUpdatedAt;

    @Column(name = "total_cards_api")
    private Integer totalCardsApi;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getApiUpdatedAt() { return apiUpdatedAt; }
    public void setApiUpdatedAt(String apiUpdatedAt) { this.apiUpdatedAt = apiUpdatedAt; }

    public Integer getTotalCardsApi() { return totalCardsApi; }
    public void setTotalCardsApi(Integer totalCardsApi) { this.totalCardsApi = totalCardsApi; }

    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }
}
