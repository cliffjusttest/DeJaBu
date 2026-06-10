package com.dejebu.game;

import java.util.concurrent.ThreadLocalRandom;

public final class SkillCombatCalculator {

    private SkillCombatCalculator() {
    }

    public static int calculateDamage(
            CharacterStats stats,
            BattleSkillRuntime skill,
            ThreadLocalRandom random
    ) {
        double levelMultiplier = 1.0 + 0.15 * (skill.getSkillLevel() - 1);
        double mightPart = skill.getMightCoefficient().doubleValue() * stats.might() * levelMultiplier;
        double intPart = skill.getIntelligenceCoefficient().doubleValue() * stats.intelligence() * levelMultiplier;
        int base = (int) Math.round(mightPart + intPart);
        base = Math.max(1, base + random.nextInt(-1, 4));
        return base;
    }

    public static int calculateHeal(
            CharacterStats stats,
            BattleSkillRuntime skill,
            ThreadLocalRandom random
    ) {
        double levelMultiplier = 1.0 + 0.15 * (skill.getSkillLevel() - 1);
        int base = (int) Math.round(
                skill.getIntelligenceCoefficient().doubleValue() * stats.intelligence() * levelMultiplier * 0.9
        );
        base = Math.max(5, base + random.nextInt(0, 4));
        return base;
    }

    public static Element resolveAttackElement(SkillElement skillElement, Element actorElement) {
        if (skillElement == SkillElement.UNIVERSAL) {
            return actorElement;
        }
        return switch (skillElement) {
            case FIRE -> Element.FIRE;
            case WIND -> Element.WIND;
            case EARTH -> Element.EARTH;
            case THUNDER -> Element.THUNDER;
            case WATER -> Element.WATER;
            case UNIVERSAL -> actorElement;
        };
    }
}
