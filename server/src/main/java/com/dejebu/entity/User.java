package com.dejebu.entity;

import com.dejebu.game.CharacterAppearance;
import com.dejebu.game.CharacterStats;
import com.dejebu.game.Element;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 32)
    private String displayName;

    @Column(name = "player_x", nullable = false)
    private int playerX = 5;

    @Column(name = "player_y", nullable = false)
    private int playerY = 5;

    @Column(name = "player_map_id", nullable = false, length = 32)
    private String playerMapId = "village";

    @Column(name = "has_character", nullable = false)
    private boolean hasCharacter = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Element element;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private CharacterAppearance appearance;

    @Column(name = "stat_might", nullable = false)
    private int statMight = CharacterStats.BASE_VALUE;

    @Column(name = "stat_intelligence", nullable = false)
    private int statIntelligence = CharacterStats.BASE_VALUE;

    @Column(name = "stat_vitality", nullable = false)
    private int statVitality = CharacterStats.BASE_VALUE;

    @Column(name = "stat_defense", nullable = false)
    private int statDefense = CharacterStats.BASE_VALUE;

    @Column(name = "stat_spirit", nullable = false)
    private int statSpirit = CharacterStats.BASE_VALUE;

    @Column(name = "stat_luck", nullable = false)
    private int statLuck = CharacterStats.BASE_VALUE;

    @Column(name = "stat_agility", nullable = false)
    private int statAgility = CharacterStats.BASE_VALUE;

    @Column(name = "skill_points", nullable = false)
    private int skillPoints = 10;

    @Column(nullable = false)
    private int level = 1;

    @Column(nullable = false)
    private int exp = 0;

    @Column(name = "player_current_hp", nullable = false)
    private int playerCurrentHp = 50;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getPlayerX() {
        return playerX;
    }

    public void setPlayerX(int playerX) {
        this.playerX = playerX;
    }

    public int getPlayerY() {
        return playerY;
    }

    public void setPlayerY(int playerY) {
        this.playerY = playerY;
    }

    public String getPlayerMapId() {
        return playerMapId;
    }

    public void setPlayerMapId(String playerMapId) {
        this.playerMapId = playerMapId;
    }

    public boolean isHasCharacter() {
        return hasCharacter;
    }

    public void setHasCharacter(boolean hasCharacter) {
        this.hasCharacter = hasCharacter;
    }

    public Element getElement() {
        return element;
    }

    public void setElement(Element element) {
        this.element = element;
    }

    public CharacterAppearance getAppearance() {
        return appearance;
    }

    public void setAppearance(CharacterAppearance appearance) {
        this.appearance = appearance;
    }

    public int getStatMight() {
        return statMight;
    }

    public void setStatMight(int statMight) {
        this.statMight = statMight;
    }

    public int getStatIntelligence() {
        return statIntelligence;
    }

    public void setStatIntelligence(int statIntelligence) {
        this.statIntelligence = statIntelligence;
    }

    public int getStatVitality() {
        return statVitality;
    }

    public void setStatVitality(int statVitality) {
        this.statVitality = statVitality;
    }

    public int getStatDefense() {
        return statDefense;
    }

    public void setStatDefense(int statDefense) {
        this.statDefense = statDefense;
    }

    public int getStatSpirit() {
        return statSpirit;
    }

    public void setStatSpirit(int statSpirit) {
        this.statSpirit = statSpirit;
    }

    public int getStatLuck() {
        return statLuck;
    }

    public void setStatLuck(int statLuck) {
        this.statLuck = statLuck;
    }

    public int getStatAgility() {
        return statAgility;
    }

    public void setStatAgility(int statAgility) {
        this.statAgility = statAgility;
    }

    public int getSkillPoints() {
        return skillPoints;
    }

    public void setSkillPoints(int skillPoints) {
        this.skillPoints = skillPoints;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getExp() {
        return exp;
    }

    public void setExp(int exp) {
        this.exp = exp;
    }

    public int getPlayerCurrentHp() {
        return playerCurrentHp;
    }

    public void setPlayerCurrentHp(int playerCurrentHp) {
        this.playerCurrentHp = playerCurrentHp;
    }

    public int resolveMaxHp() {
        return CharacterStats.fromUser(this).maxHp();
    }

    public int resolveCurrentHp() {
        int maxHp = resolveMaxHp();
        return Math.min(Math.max(0, playerCurrentHp), maxHp);
    }

    public void applyStats(CharacterStats stats) {
        setStatMight(stats.might());
        setStatIntelligence(stats.intelligence());
        setStatVitality(stats.vitality());
        setStatDefense(stats.defense());
        setStatSpirit(stats.spirit());
        setStatLuck(stats.luck());
        setStatAgility(stats.agility());
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
