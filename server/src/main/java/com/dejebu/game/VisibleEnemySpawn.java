package com.dejebu.game;

public record VisibleEnemySpawn(
        String id,
        String templateId,
        int x,
        int y,
        int chaseRange,
        int loseRange
) {
    public VisibleEnemySpawn(String id, String templateId, int x, int y) {
        this(id, templateId, x, y, 5, 8);
    }
}
