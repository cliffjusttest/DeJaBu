package com.dejebu.dto;

import java.util.List;

public record CompanionDto(
        long id,
        String templateId,
        String nickname,
        String templateName,
        String element,
        String elementName,
        int level,
        int currentHp,
        int maxHp,
        int might,
        int intelligence,
        int vitality,
        int defense,
        int spirit,
        int luck,
        Integer partySlot,
        List<CompanionSkillDto> skills
) {
}
