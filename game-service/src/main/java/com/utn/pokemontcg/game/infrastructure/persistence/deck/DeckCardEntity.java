package com.utn.pokemontcg.game.infrastructure.persistence.deck;

import jakarta.persistence.*;

@Entity
@Table(name = "deck_cards")
public class DeckCardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "deck_id")
    private DeckEntity deck;

    @Column(nullable = false)
    private String cardId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String setId = "xy1";

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private String type;

    @Column
    private String subtype;

    @Column(nullable = false)
    private boolean basicEnergy;

    @Column(nullable = false)
    private boolean basicPokemon;

    @Column(nullable = false)
    private boolean aceSpec;

    @Column
    private Integer hp;

    @Column
    private Integer attackDamage;

    @Column
    private Integer attackRequiredEnergy;

    @Column
    private String attackName;

    @Column(columnDefinition = "TEXT")
    private String attackText;

    @Column
    private String energyType;

    @Column(columnDefinition = "TEXT")
    private String attackCost;

    @Column
    private Integer retreatCost;

    @Column
    private String evolvesFrom;

    @Column(columnDefinition = "TEXT")
    private String abilities;

    @Column
    private String weaknessType;

    @Column
    private Integer weaknessAmount;

    @Column
    private String resistanceType;

    @Column
    private Integer resistanceAmount;

    @Column(columnDefinition = "TEXT")
    private String attacksJson;

    public Long getId() { return id; }
    public DeckEntity getDeck() { return deck; }
    public void setDeck(DeckEntity deck) { this.deck = deck; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSetId() { return setId; }
    public void setSetId(String setId) { this.setId = setId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSubtype() { return subtype; }
    public void setSubtype(String subtype) { this.subtype = subtype; }
    public boolean isBasicEnergy() { return basicEnergy; }
    public void setBasicEnergy(boolean basicEnergy) { this.basicEnergy = basicEnergy; }
    public boolean isBasicPokemon() { return basicPokemon; }
    public void setBasicPokemon(boolean basicPokemon) { this.basicPokemon = basicPokemon; }
    public boolean isAceSpec() { return aceSpec; }
    public void setAceSpec(boolean aceSpec) { this.aceSpec = aceSpec; }
    public Integer getHp() { return hp; }
    public void setHp(Integer hp) { this.hp = hp; }
    public Integer getAttackDamage() { return attackDamage; }
    public void setAttackDamage(Integer attackDamage) { this.attackDamage = attackDamage; }
    public Integer getAttackRequiredEnergy() { return attackRequiredEnergy; }
    public void setAttackRequiredEnergy(Integer attackRequiredEnergy) { this.attackRequiredEnergy = attackRequiredEnergy; }
    public String getAttackName() { return attackName; }
    public void setAttackName(String attackName) { this.attackName = attackName; }
    public String getAttackText() { return attackText; }
    public void setAttackText(String attackText) { this.attackText = attackText; }
    public String getEnergyType() { return energyType; }
    public void setEnergyType(String energyType) { this.energyType = energyType; }
    public String getAttackCost() { return attackCost; }
    public void setAttackCost(String attackCost) { this.attackCost = attackCost; }
    public Integer getRetreatCost() { return retreatCost; }
    public void setRetreatCost(Integer retreatCost) { this.retreatCost = retreatCost; }
    public String getEvolvesFrom() { return evolvesFrom; }
    public void setEvolvesFrom(String evolvesFrom) { this.evolvesFrom = evolvesFrom; }
    public String getAbilities() { return abilities; }
    public void setAbilities(String abilities) { this.abilities = abilities; }
    public String getWeaknessType() { return weaknessType; }
    public void setWeaknessType(String weaknessType) { this.weaknessType = weaknessType; }
    public Integer getWeaknessAmount() { return weaknessAmount; }
    public void setWeaknessAmount(Integer weaknessAmount) { this.weaknessAmount = weaknessAmount; }
    public String getResistanceType() { return resistanceType; }
    public void setResistanceType(String resistanceType) { this.resistanceType = resistanceType; }
    public Integer getResistanceAmount() { return resistanceAmount; }
    public void setResistanceAmount(Integer resistanceAmount) { this.resistanceAmount = resistanceAmount; }
    public String getAttacksJson() { return attacksJson; }
    public void setAttacksJson(String attacksJson) { this.attacksJson = attacksJson; }
}
