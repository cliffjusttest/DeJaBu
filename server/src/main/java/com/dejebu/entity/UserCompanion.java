package com.dejebu.entity;

import com.dejebu.game.CharacterStats;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_companions")
public class UserCompanion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private MonsterTemplateEntity template;

    @Column(nullable = false, length = 32)
    private String nickname;

    @Column(nullable = false)
    private int level = 1;

    @Column(name = "stat_might", nullable = false)
    private int statMight;

    @Column(name = "stat_intelligence", nullable = false)
    private int statIntelligence;

    @Column(name = "stat_vitality", nullable = false)
    private int statVitality;

    @Column(name = "stat_defense", nullable = false)
    private int statDefense;

    @Column(name = "stat_spirit", nullable = false)
    private int statSpirit;

    @Column(name = "stat_luck", nullable = false)
    private int statLuck;

    @Column(name = "stat_agility", nullable = false)
    private int statAgility;

    @Column(name = "current_hp", nullable = false)
    private int currentHp;

    @Column(name = "max_hp", nullable = false)
    private int maxHp;

    @Column(name = "party_slot")
    private Integer partySlot;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @PrePersist
    void onCreate() {
        if (capturedAt == null) {
            capturedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public MonsterTemplateEntity getTemplate() {
        return template;
    }

    public void setTemplate(MonsterTemplateEntity template) {
        this.template = template;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
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

    public int getCurrentHp() {
        return currentHp;
    }

    public void setCurrentHp(int currentHp) {
        this.currentHp = currentHp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public void setMaxHp(int maxHp) {
        this.maxHp = maxHp;
    }

    public Integer getPartySlot() {
        return partySlot;
    }

    public void setPartySlot(Integer partySlot) {
        this.partySlot = partySlot;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public CharacterStats toStats() {
        return new CharacterStats(
                statMight,
                statIntelligence,
                statVitality,
                statDefense,
                statSpirit,
                statLuck,
                statAgility
        );
    }
}
