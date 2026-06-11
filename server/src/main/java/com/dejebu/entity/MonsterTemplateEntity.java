package com.dejebu.entity;

import com.dejebu.game.Element;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "monster_templates")
public class MonsterTemplateEntity {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 32)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Element element;

    @Column(name = "base_might", nullable = false)
    private int baseMight = 5;

    @Column(name = "base_intelligence", nullable = false)
    private int baseIntelligence = 5;

    @Column(name = "base_vitality", nullable = false)
    private int baseVitality = 5;

    @Column(name = "base_defense", nullable = false)
    private int baseDefense = 5;

    @Column(name = "base_spirit", nullable = false)
    private int baseSpirit = 5;

    @Column(name = "base_luck", nullable = false)
    private int baseLuck = 5;

    @Column(name = "base_agility", nullable = false)
    private int baseAgility = 5;

    @Column(nullable = false)
    private boolean capturable = true;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Element getElement() {
        return element;
    }

    public int getBaseMight() {
        return baseMight;
    }

    public int getBaseIntelligence() {
        return baseIntelligence;
    }

    public int getBaseVitality() {
        return baseVitality;
    }

    public int getBaseDefense() {
        return baseDefense;
    }

    public int getBaseSpirit() {
        return baseSpirit;
    }

    public int getBaseLuck() {
        return baseLuck;
    }

    public int getBaseAgility() {
        return baseAgility;
    }

    public boolean isCapturable() {
        return capturable;
    }
}
