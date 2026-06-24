package com.dejebu.dto;

import com.dejebu.game.CharacterAppearance;
import com.dejebu.game.CharacterStats;
import com.dejebu.game.Element;

public record AuthResponse(
        String token,
        long playerId,
        String playerName,
        int playerX,
        int playerY,
        String playerMapId,
        boolean hasCharacter,
        Element element,
        CharacterAppearance appearance,
        CharacterStats stats,
        String storyEra,
        String message
) {
}
