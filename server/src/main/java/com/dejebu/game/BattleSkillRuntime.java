package com.dejebu.game;

import com.dejebu.entity.Skill;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;

public class BattleSkillRuntime {

    private final long skillId;
    private final String name;
    private final SkillElement element;
    private final BigDecimal mightCoefficient;
    private final BigDecimal intelligenceCoefficient;
    private final int skillLevel;
    private final int maxLevel;
    private final int cooldownTurns;
    private final int mpCost;
    private final SkillTargetSide targetSide;
    private final SkillTargetRange targetRange;
    private final SkillEffectType effectType;
    private final boolean comboEligible;
    private int cooldownRemaining;

    private BattleSkillRuntime(
            long skillId,
            String name,
            SkillElement element,
            BigDecimal mightCoefficient,
            BigDecimal intelligenceCoefficient,
            int skillLevel,
            int maxLevel,
            int cooldownTurns,
            int mpCost,
            SkillTargetSide targetSide,
            SkillTargetRange targetRange,
            SkillEffectType effectType,
            boolean comboEligible
    ) {
        this.skillId = skillId;
        this.name = name;
        this.element = element;
        this.mightCoefficient = mightCoefficient;
        this.intelligenceCoefficient = intelligenceCoefficient;
        this.skillLevel = skillLevel;
        this.maxLevel = maxLevel;
        this.cooldownTurns = cooldownTurns;
        this.mpCost = mpCost;
        this.targetSide = targetSide;
        this.targetRange = targetRange;
        this.effectType = effectType != null ? effectType : SkillEffectType.DAMAGE;
        this.comboEligible = comboEligible;
    }

    public static BattleSkillRuntime from(Skill skill, int skillLevel) {
        return new BattleSkillRuntime(
                skill.getId(),
                skill.getName(),
                skill.getElement(),
                skill.getMightCoefficient(),
                skill.getIntelligenceCoefficient(),
                skillLevel,
                skill.getMaxLevel(),
                skill.getCooldownTurns(),
                skill.getMpCost(),
                skill.getTargetSide(),
                skill.getTargetRange(),
                skill.getEffectType(),
                skill.isComboEligible()
        );
    }

    public long getSkillId() {
        return skillId;
    }

    public String getName() {
        return name;
    }

    public SkillElement getElement() {
        return element;
    }

    public BigDecimal getMightCoefficient() {
        return mightCoefficient;
    }

    public BigDecimal getIntelligenceCoefficient() {
        return intelligenceCoefficient;
    }

    public int getSkillLevel() {
        return skillLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getCooldownTurns() {
        return cooldownTurns;
    }

    public int getMpCost() {
        return mpCost;
    }

    public SkillTargetSide getTargetSide() {
        return targetSide;
    }

    public SkillTargetRange getTargetRange() {
        return targetRange;
    }

    public SkillEffectType getEffectType() {
        return effectType;
    }

    public boolean isComboEligible() {
        return comboEligible;
    }

    public int getCooldownRemaining() {
        return cooldownRemaining;
    }

    public boolean isReady() {
        return cooldownRemaining <= 0;
    }

    public boolean canUse(int currentMp) {
        return isReady() && currentMp >= mpCost;
    }

    public boolean isHealSkill() {
        return effectType == SkillEffectType.HEAL;
    }

    public boolean isReviveSkill() {
        return effectType == SkillEffectType.REVIVE;
    }

    public boolean isBuffSkill() {
        return effectType == SkillEffectType.BUFF;
    }

    public boolean isSupportSkill() {
        return isHealSkill() || isReviveSkill() || isBuffSkill();
    }

    public void markUsed() {
        cooldownRemaining = cooldownTurns;
    }

    public void tickCooldown() {
        if (cooldownRemaining > 0) {
            cooldownRemaining--;
        }
    }

    public ObjectNode toJsonNode(ObjectMapper objectMapper, int currentMp) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("skillId", skillId);
        node.put("name", name);
        node.put("element", element.getCode());
        node.put("elementName", element.getDisplayName());
        node.put("skillLevel", skillLevel);
        node.put("maxLevel", maxLevel);
        node.put("cooldownTurns", cooldownTurns);
        node.put("cooldownRemaining", cooldownRemaining);
        node.put("mpCost", mpCost);
        node.put("targetSide", targetSide.getCode());
        node.put("targetSideName", targetSide.getDisplayName());
        node.put("targetRange", targetRange.getCode());
        node.put("targetRangeName", targetRange.getDisplayName());
        node.put("canUse", canUse(currentMp));
        node.put("heal", isHealSkill());
        node.put("comboEligible", comboEligible);
        return node;
    }
}
