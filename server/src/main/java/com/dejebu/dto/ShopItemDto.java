package com.dejebu.dto;

public record ShopItemDto(
        Long itemId,
        String name,
        String description,
        String type,
        String slot,
        String slotDisplayName,
        int requiredLevel,
        int healHp,
        int price
) {}
