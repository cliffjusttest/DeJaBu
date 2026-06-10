package com.dejebu.dto;

import java.util.List;

public record SkillTreeResponse(
        int skillPoints,
        int playerLevel,
        List<SkillNodeDto> skills,
        String message
) {
}
