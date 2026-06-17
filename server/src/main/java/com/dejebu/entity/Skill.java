package com.dejebu.entity;

import com.dejebu.game.SkillEffectType;
import com.dejebu.game.SkillElement;
import com.dejebu.game.SkillTargetRange;
import com.dejebu.game.SkillTargetSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "skills")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SkillElement element;

    @Column(name = "might_coefficient", nullable = false, precision = 5, scale = 2)
    private BigDecimal mightCoefficient;

    @Column(name = "intelligence_coefficient", nullable = false, precision = 5, scale = 2)
    private BigDecimal intelligenceCoefficient;

    @Column(name = "required_level", nullable = false)
    private int requiredLevel = 1;

    @Column(name = "max_level", nullable = false)
    private int maxLevel = 1;

    @Column(name = "cooldown_turns", nullable = false)
    private int cooldownTurns = 0;

    @Column(name = "mp_cost", nullable = false)
    private int mpCost = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_side", nullable = false, length = 16)
    private SkillTargetSide targetSide = SkillTargetSide.ENEMY;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_range", nullable = false, length = 32)
    private SkillTargetRange targetRange = SkillTargetRange.SINGLE;

    @Column(name = "combo_eligible", nullable = false)
    private boolean comboEligible = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect_type", nullable = false, length = 16)
    private SkillEffectType effectType = SkillEffectType.DAMAGE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "skill_prerequisites",
            joinColumns = @JoinColumn(name = "skill_id"),
            inverseJoinColumns = @JoinColumn(name = "prerequisite_skill_id")
    )
    private Set<Skill> prerequisites = new HashSet<>();

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SkillElement getElement() {
        return element;
    }

    public void setElement(SkillElement element) {
        this.element = element;
    }

    public BigDecimal getMightCoefficient() {
        return mightCoefficient;
    }

    public void setMightCoefficient(BigDecimal mightCoefficient) {
        this.mightCoefficient = mightCoefficient;
    }

    public BigDecimal getIntelligenceCoefficient() {
        return intelligenceCoefficient;
    }

    public void setIntelligenceCoefficient(BigDecimal intelligenceCoefficient) {
        this.intelligenceCoefficient = intelligenceCoefficient;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public void setRequiredLevel(int requiredLevel) {
        this.requiredLevel = requiredLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public int getCooldownTurns() {
        return cooldownTurns;
    }

    public void setCooldownTurns(int cooldownTurns) {
        this.cooldownTurns = cooldownTurns;
    }

    public int getMpCost() {
        return mpCost;
    }

    public void setMpCost(int mpCost) {
        this.mpCost = mpCost;
    }

    public SkillTargetSide getTargetSide() {
        return targetSide;
    }

    public void setTargetSide(SkillTargetSide targetSide) {
        this.targetSide = targetSide;
    }

    public SkillTargetRange getTargetRange() {
        return targetRange;
    }

    public void setTargetRange(SkillTargetRange targetRange) {
        this.targetRange = targetRange;
    }

    public boolean isComboEligible() {
        return comboEligible;
    }

    public void setComboEligible(boolean comboEligible) {
        this.comboEligible = comboEligible;
    }

    public SkillEffectType getEffectType() {
        return effectType;
    }

    public void setEffectType(SkillEffectType effectType) {
        this.effectType = effectType != null ? effectType : SkillEffectType.DAMAGE;
    }

    public Set<Skill> getPrerequisites() {
        return prerequisites;
    }

    public void setPrerequisites(Set<Skill> prerequisites) {
        this.prerequisites = prerequisites;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
