package com.dejebu.dto;

import java.util.List;

public record ShopListResponse(
        String npcId,
        String npcName,
        int playerGold,
        List<ShopItemDto> items,
        List<ShopSellItemDto> sellableItems,
        String message
) {}
