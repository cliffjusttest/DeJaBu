package com.dejebu.dto;

import java.util.Map;

public record CompanionEquipmentDto(
        Long companionId,
        String nickname,
        int level,
        Map<String, ItemDto> equipped
) {}
