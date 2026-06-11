package com.dejebu.service;

public record CompanionExpResult(
        long companionId,
        String name,
        int expGained,
        int previousLevel,
        int newLevel,
        boolean leveledUp
) {}
