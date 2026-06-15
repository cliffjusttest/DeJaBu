package com.dejebu.dto;

public record ShopSellItemDto(
        Long itemId,
        String name,
        String description,
        String type,
        int quantity,
        int sellPrice
) {}
