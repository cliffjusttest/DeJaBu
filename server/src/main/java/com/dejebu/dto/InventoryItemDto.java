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
        int healHp,
        int sellPrice,
        int quantity
) {
    public static InventoryItemDto from(UserInventory inv) {
        return fromWithQuantity(inv, inv.getQuantity());
    }

    public static InventoryItemDto fromWithQuantity(UserInventory inv, int quantity) {
        Item item = inv.getItem();
        return new InventoryItemDto(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getType().name(),
                item.getSlot() != null ? item.getSlot().name() : null,
                item.getSlot() != null ? item.getSlot().getDisplayName() : null,
                item.getRequiredLevel(),
                item.getBonusMight(),
                item.getBonusIntelligence(),
                item.getBonusVitality(),
                item.getBonusDefense(),
                item.getBonusSpirit(),
                item.getBonusLuck(),
                item.getBonusAgility(),
                item.getHealHp(),
                item.getSellPrice(),
                quantity
        );
    }
}
