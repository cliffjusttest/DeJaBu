package com.dejebu.game;

public record MapEncounterSettings(int maxVisibleEnemies, int maxDarkEnemies) {

    public static final int MIN_ENEMIES = 1;
    public static final int MAX_ENEMIES = 5;
    public static final MapEncounterSettings DEFAULT = new MapEncounterSettings(1, 3);

    public MapEncounterSettings {
        maxVisibleEnemies = clamp(maxVisibleEnemies);
        maxDarkEnemies = clamp(maxDarkEnemies);
    }

    private static int clamp(int value) {
        return Math.max(MIN_ENEMIES, Math.min(MAX_ENEMIES, value));
    }
}
