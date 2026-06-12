package com.dejebu.dto;

import java.util.List;
import java.util.Map;

public record BackpackResponse(
        List<InventoryItemDto> inventory,
        Map<String, ItemDto> playerEquipped,
        List<CompanionEquipmentDto> companions,
        int playerCurrentHp,
        int playerMaxHp,
        String message
) {}
