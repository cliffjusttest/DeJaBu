package com.dejebu.game;

import com.dejebu.entity.MonsterTemplateEntity;

public final class MonsterStatsFactory {

    private MonsterStatsFactory() {
    }

    public static CharacterStats statsForLevel(MonsterTemplateEntity template, int level) {
        int safeLevel = Math.max(1, level);
        int bonus = safeLevel - 1;
        return new CharacterStats(
                template.getBaseMight() + bonus * 2,
                template.getBaseIntelligence() + bonus,
                template.getBaseVitality() + bonus * 2,
                template.getBaseDefense() + bonus,
                template.getBaseSpirit() + bonus,
                template.getBaseLuck() + bonus,
                template.getBaseAgility() + bonus
        );
    }

    public static int maxHpForStats(CharacterStats stats) {
        return stats.maxHp();
    }
}
