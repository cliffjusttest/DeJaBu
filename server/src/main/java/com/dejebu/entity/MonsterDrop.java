package com.dejebu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "monster_drops")
public class MonsterDrop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monster_template_id", nullable = false)
    private MonsterTemplateEntity monsterTemplate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "drop_chance", nullable = false)
    private double dropChance;

    public Long getId() {
        return id;
    }

    public MonsterTemplateEntity getMonsterTemplate() {
        return monsterTemplate;
    }

    public Item getItem() {
        return item;
    }

    public double getDropChance() {
        return dropChance;
    }
}
