package com.dejebu.dto;

import com.dejebu.game.CharacterStats;

public record StatAllocationResponse(
        CharacterStats stats,
        int statPoints,
        int playerMaxHp,
        int playerMaxMp,
        int playerCurrentHp,
        int playerCurrentMp,
        String message
) {
}
