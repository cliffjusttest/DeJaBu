package com.dejebu.dto;

import java.util.List;
import java.util.Map;

public record EquipmentResponse(
        Map<String, ItemDto> equipped,
        List<ItemDto> availableItems,
        String message
) {}
