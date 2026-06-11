package com.dejebu.dto;

import com.dejebu.entity.Item;

public record ItemDto(
        long id,
        String name,
        String description,
        String slot,
        String slotDisplayName,
        int requiredLevel,
        int bonusMight,
        int bonusIntelligence,
        int bonusVitality,
        int bonusDefense,
        int bonusSpirit,
        int bonusLuck,
        int bonusAgility
) {
    public static ItemDto from(Item item) {
        return new ItemDto(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getSlot().name(),
                item.getSlot().getDisplayName(),
                item.getRequiredLevel(),
                item.getBonusMight(),
                item.getBonusIntelligence(),
                item.getBonusVitality(),
                item.getBonusDefense(),
                item.getBonusSpirit(),
                item.getBonusLuck(),
                item.getBonusAgility()
        );
    }
}
