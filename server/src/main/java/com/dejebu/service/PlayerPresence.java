package com.dejebu.service;

public record PlayerPresence(
        Long playerId,
        String playerName,
        String mapId,
        int x,
        int y,
        String direction,
        String appearance,
        int level
) {
}
