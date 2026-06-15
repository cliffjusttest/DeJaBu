package com.dejebu.dto;

public record ShopPurchaseRequest(
        String token,
        String npcId,
        Long itemId
) {}
