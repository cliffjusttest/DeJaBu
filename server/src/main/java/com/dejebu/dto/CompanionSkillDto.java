package com.dejebu.dto;

public record CompanionSkillDto(
        long skillId,
        String name,
        String element,
        String elementName,
        int skillLevel,
        int maxLevel,
        boolean canUpgrade
) {
}
