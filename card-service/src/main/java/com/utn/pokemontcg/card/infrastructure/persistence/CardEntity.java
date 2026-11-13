package com.utn.pokemontcg.card.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "cards")
public class CardEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "set_id", nullable = false)
    private String setId;

    private String supertype;

    @Column(columnDefinition = "TEXT")
    private String subtypes;

    @Column(columnDefinition = "TEXT")
    private String types;

    private Integer hp;

    @Column(columnDefinition = "TEXT")
    private String attacks;

    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    @Column(columnDefinition = "TEXT")
    private String resistances;

    @Column(name = "retreat_cost")
    private Integer retreatCost;

    @Column(name = "evolves_from")
    private String evolvesFrom;

    @Column(columnDefinition = "TEXT")
    private String abilities;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String rules;

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    @Column(name = "imported_at")
    private LocalDateTime importedAt = LocalDateTime.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSetId() { return setId; }
    public void setSetId(String setId) { this.setId = setId; }

    public String getSupertype() { return supertype; }
    public void setSupertype(String supertype) { this.supertype = supertype; }

    public String getSubtypes() { return subtypes; }
    public void setSubtypes(String subtypes) { this.subtypes = subtypes; }

    public String getTypes() { return types; }
    public void setTypes(String types) { this.types = types; }

    public Integer getHp() { return hp; }
    public void setHp(Integer hp) { this.hp = hp; }

    public String getAttacks() { return attacks; }
    public void setAttacks(String attacks) { this.attacks = attacks; }

    public String getWeaknesses() { return weaknesses; }
    public void setWeaknesses(String weaknesses) { this.weaknesses = weaknesses; }

    public String getResistances() { return resistances; }
    public void setResistances(String resistances) { this.resistances = resistances; }

    public Integer getRetreatCost() { return retreatCost; }
    public void setRetreatCost(Integer retreatCost) { this.retreatCost = retreatCost; }

    public String getEvolvesFrom() { return evolvesFrom; }
    public void setEvolvesFrom(String evolvesFrom) { this.evolvesFrom = evolvesFrom; }

    public String getAbilities() { return abilities; }
    public void setAbilities(String abilities) { this.abilities = abilities; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getRules() { return rules; }
    public void setRules(String rules) { this.rules = rules; }

    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }

    public LocalDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
}
