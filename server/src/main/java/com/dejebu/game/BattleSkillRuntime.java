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
    private final SkillTargetSide targetSide;
    private final SkillTargetRange targetRange;
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
            SkillTargetSide targetSide,
            SkillTargetRange targetRange
    ) {
        this.skillId = skillId;
        this.name = name;
        this.element = element;
        this.mightCoefficient = mightCoefficient;
        this.intelligenceCoefficient = intelligenceCoefficient;
        this.skillLevel = skillLevel;
        this.maxLevel = maxLevel;
        this.cooldownTurns = cooldownTurns;
        this.targetSide = targetSide;
        this.targetRange = targetRange;
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
                skill.getTargetSide(),
                skill.getTargetRange()
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

    public SkillTargetSide getTargetSide() {
        return targetSide;
    }

    public SkillTargetRange getTargetRange() {
        return targetRange;
    }

    public int getCooldownRemaining() {
        return cooldownRemaining;
    }

    public boolean isReady() {
        return cooldownRemaining <= 0;
    }

    public boolean isHealSkill() {
        return targetSide == SkillTargetSide.ALLY
                && mightCoefficient.compareTo(BigDecimal.ZERO) == 0
                && intelligenceCoefficient.compareTo(BigDecimal.ZERO) > 0;
    }

    public void markUsed() {
        cooldownRemaining = cooldownTurns;
    }

    public void tickCooldown() {
        if (cooldownRemaining > 0) {
            cooldownRemaining--;
        }
    }

    public ObjectNode toJsonNode(ObjectMapper objectMapper) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("skillId", skillId);
        node.put("name", name);
        node.put("element", element.getCode());
        node.put("elementName", element.getDisplayName());
        node.put("skillLevel", skillLevel);
        node.put("maxLevel", maxLevel);
        node.put("cooldownTurns", cooldownTurns);
        node.put("cooldownRemaining", cooldownRemaining);
        node.put("targetSide", targetSide.getCode());
        node.put("targetSideName", targetSide.getDisplayName());
        node.put("targetRange", targetRange.getCode());
        node.put("targetRangeName", targetRange.getDisplayName());
        node.put("canUse", isReady());
        node.put("heal", isHealSkill());
        return node;
    }
}
