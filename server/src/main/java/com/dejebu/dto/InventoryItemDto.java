package com.dejebu.dto;

import com.dejebu.entity.UserInventory;
import com.dejebu.entity.Item;

public record InventoryItemDto(
        Long id,
        String name,
        String description,
        String type,
        String slot,
        String slotDisplayName,
        int requiredLevel,
        int bonusMight,
        int bonusIntelligence,
        int bonusVitality,
        int bonusDefense,
        int bonusSpirit,
        int bonusLuck,
        int bonusAgility,
        int quantity
) {
    public static InventoryItemDto from(UserInventory inv) {
        Item item = inv.getItem();
        return new InventoryItemDto(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getType().name(),
                item.getSlot().name(),
                item.getSlot().getDisplayName(),
                item.getRequiredLevel(),
                item.getBonusMight(),
                item.getBonusIntelligence(),
                item.getBonusVitality(),
                item.getBonusDefense(),
                item.getBonusSpirit(),
                item.getBonusLuck(),
                item.getBonusAgility(),
                inv.getQuantity()
        );
    }
}
