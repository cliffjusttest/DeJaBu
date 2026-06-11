package com.dejebu.entity;

import com.dejebu.game.EquipmentSlot;
import com.dejebu.game.ItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "items")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 256)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ItemType type = ItemType.EQUIPMENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EquipmentSlot slot;

    @Column(name = "required_level", nullable = false)
    private int requiredLevel = 1;

    @Column(name = "bonus_might", nullable = false)
    private int bonusMight = 0;

    @Column(name = "bonus_intelligence", nullable = false)
    private int bonusIntelligence = 0;

    @Column(name = "bonus_vitality", nullable = false)
    private int bonusVitality = 0;

    @Column(name = "bonus_defense", nullable = false)
    private int bonusDefense = 0;

    @Column(name = "bonus_spirit", nullable = false)
    private int bonusSpirit = 0;

    @Column(name = "bonus_luck", nullable = false)
    private int bonusLuck = 0;

    @Column(name = "bonus_agility", nullable = false)
    private int bonusAgility = 0;

    public Long getId() { return id; }

    public ItemType getType() { return type; }
    public void setType(ItemType type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public EquipmentSlot getSlot() { return slot; }
    public void setSlot(EquipmentSlot slot) { this.slot = slot; }

    public int getRequiredLevel() { return requiredLevel; }
    public void setRequiredLevel(int requiredLevel) { this.requiredLevel = requiredLevel; }

    public int getBonusMight() { return bonusMight; }
    public void setBonusMight(int bonusMight) { this.bonusMight = bonusMight; }

    public int getBonusIntelligence() { return bonusIntelligence; }
    public void setBonusIntelligence(int bonusIntelligence) { this.bonusIntelligence = bonusIntelligence; }

    public int getBonusVitality() { return bonusVitality; }
    public void setBonusVitality(int bonusVitality) { this.bonusVitality = bonusVitality; }

    public int getBonusDefense() { return bonusDefense; }
    public void setBonusDefense(int bonusDefense) { this.bonusDefense = bonusDefense; }

    public int getBonusSpirit() { return bonusSpirit; }
    public void setBonusSpirit(int bonusSpirit) { this.bonusSpirit = bonusSpirit; }

    public int getBonusLuck() { return bonusLuck; }
    public void setBonusLuck(int bonusLuck) { this.bonusLuck = bonusLuck; }

    public int getBonusAgility() { return bonusAgility; }
    public void setBonusAgility(int bonusAgility) { this.bonusAgility = bonusAgility; }
}
