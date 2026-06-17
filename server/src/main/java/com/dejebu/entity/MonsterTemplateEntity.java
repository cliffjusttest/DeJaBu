package com.dejebu.entity;

import com.dejebu.game.Element;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

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

    @Column(name = "gold_drop_min", nullable = false)
    private int goldDropMin = 0;

    @Column(name = "gold_drop_max", nullable = false)
    private int goldDropMax = 0;

    @Column(name = "min_level", nullable = false)
    private int minLevel = 1;

    @Column(name = "max_level", nullable = false)
    private int maxLevel = 99;

    @Column(name = "visible_spawn", nullable = false)
    private boolean visibleSpawn = false;

    @Column(name = "dark_spawn", nullable = false)
    private boolean darkSpawn = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "monster_template_spawn_maps",
            joinColumns = @JoinColumn(name = "template_id")
    )
    @Column(name = "map_id", length = 32)
    private Set<String> spawnMaps = new HashSet<>();

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

    public int getGoldDropMin() {
        return goldDropMin;
    }

    public int getGoldDropMax() {
        return goldDropMax;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public boolean isVisibleSpawn() {
        return visibleSpawn;
    }

    public boolean isDarkSpawn() {
        return darkSpawn;
    }

    public Set<String> getSpawnMaps() {
        return spawnMaps;
    }

    public boolean canSpawnOnMap(String mapId) {
        return spawnMaps.contains(mapId);
    }

    public boolean canVisibleSpawnOnMap(String mapId) {
        return visibleSpawn && canSpawnOnMap(mapId);
    }

    public boolean canDarkSpawnOnMap(String mapId) {
        return darkSpawn && canSpawnOnMap(mapId);
    }

    public int rollLevel(ThreadLocalRandom random) {
        return random.nextInt(minLevel, maxLevel + 1);
    }
}
