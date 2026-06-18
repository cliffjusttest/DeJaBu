package com.dejebu.game;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 元素特性：各元素 5% 機率觸發的戰鬥加成。
 */
public final class ElementTrait {

    public static final double TRIGGER_CHANCE = 0.05;
    public static final int WIND_AGILITY_DIFF_THRESHOLD = 50;
    public static final double FIRE_DAMAGE_BONUS = 0.05;
    public static final double EARTH_DAMAGE_REDUCTION = 0.05;
    public static final int THUNDER_CRIT_RATE_BONUS = 5;
    public static final double WATER_RECOVERY_RATIO = 0.05;

    private ElementTrait() {
    }

    public static boolean rollTrigger(ThreadLocalRandom random) {
        return random.nextDouble() < TRIGGER_CHANCE;
    }

    public static boolean rollCritical(
            CharacterStats attackerStats,
            int defenderLuck,
            Element attackerElement,
            ThreadLocalRandom random
    ) {
        return rollCritical(attackerStats, defenderLuck, attackerElement, rollTrigger(random), random);
    }

    static boolean rollCritical(
            CharacterStats attackerStats,
            int defenderLuck,
            Element attackerElement,
            boolean thunderTriggered,
            ThreadLocalRandom random
    ) {
        return random.nextInt(100) < resolveCritRate(attackerStats, defenderLuck, attackerElement, thunderTriggered);
    }

    static int resolveCritRate(
            CharacterStats attackerStats,
            int defenderLuck,
            Element attackerElement,
            boolean thunderTriggered
    ) {
        int critRate = Math.max(0, attackerStats.luck() - defenderLuck);
        if (attackerElement == Element.THUNDER && thunderTriggered) {
            critRate += THUNDER_CRIT_RATE_BONUS;
        }
        return critRate;
    }

    public static int applyFireBonus(int damage, Element attackerElement, ThreadLocalRandom random) {
        return applyFireBonus(damage, attackerElement, rollTrigger(random));
    }

    static int applyFireBonus(int damage, Element attackerElement, boolean triggered) {
        if (attackerElement != Element.FIRE || !triggered) {
            return damage;
        }
        return Math.max(1, (int) Math.round(damage * (1.0 + FIRE_DAMAGE_BONUS)));
    }

    public static int applyEarthReduction(int damage, Element defenderElement, ThreadLocalRandom random) {
        return applyEarthReduction(damage, defenderElement, rollTrigger(random));
    }

    static int applyEarthReduction(int damage, Element defenderElement, boolean triggered) {
        if (defenderElement != Element.EARTH || !triggered) {
            return damage;
        }
        return Math.max(1, (int) Math.round(damage * (1.0 - EARTH_DAMAGE_REDUCTION)));
    }

    public static Optional<int[]> applyWaterRecovery(BattleUnit unit, ThreadLocalRandom random) {
        return applyWaterRecovery(unit, rollTrigger(random));
    }

    static Optional<int[]> applyWaterRecovery(BattleUnit unit, boolean triggered) {
        if (unit.getElement() != Element.WATER || !unit.isAlive() || !triggered) {
            return Optional.empty();
        }
        int hpRecover = Math.max(1, (int) Math.round(unit.getMaxHp() * WATER_RECOVERY_RATIO));
        int mpRecover = Math.max(1, (int) Math.round(unit.getMaxMp() * WATER_RECOVERY_RATIO));
        int hpBefore = unit.getHp();
        int mpBefore = unit.getMp();
        unit.setHp(Math.min(unit.getMaxHp(), unit.getHp() + hpRecover));
        unit.setMp(Math.min(unit.getMaxMp(), unit.getMp() + mpRecover));
        return Optional.of(new int[]{unit.getHp() - hpBefore, unit.getMp() - mpBefore});
    }
}
