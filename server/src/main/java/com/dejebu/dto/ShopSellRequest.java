package com.dejebu.dto;

public record ShopSellRequest(
        String token,
        String npcId,
        Long itemId
) {}
