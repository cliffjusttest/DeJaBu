package com.dejebu.dto;

import java.math.BigDecimal;
import java.util.List;

public record SkillNodeDto(
        long id,
        String name,
        String element,
        String elementName,
        BigDecimal mightCoefficient,
        BigDecimal intelligenceCoefficient,
        int requiredLevel,
        int maxLevel,
        int cooldownTurns,
        int mpCost,
        String targetSide,
        String targetSideName,
        String targetRange,
        String targetRangeName,
        List<Long> prerequisiteIds,
        boolean learned,
        int skillLevel,
        boolean canLearn,
        boolean canUpgrade,
        String statusText
) {
}
