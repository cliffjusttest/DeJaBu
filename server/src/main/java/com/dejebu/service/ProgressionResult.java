package com.dejebu.service;

public record ProgressionResult(
        int expGained,
        int playerExp,
        int expToNextLevel,
        int playerLevel,
        int previousLevel,
        int levelsGained,
        int skillPointsGained,
        int skillPoints
) {
    public boolean leveledUp() {
        return levelsGained > 0;
    }
}
