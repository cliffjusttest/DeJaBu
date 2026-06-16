package com.dejebu.game;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterStatsTest {

    @Test
    void zeroBaseHasNoPoints() {
        assertEquals(0, CharacterStats.zeroBase().totalPoints());
        assertEquals(0, CharacterStats.zeroBase().maxHp());
        assertEquals(0, CharacterStats.zeroBase().maxMp());
    }

    @Test
    void higherVitalityIncreasesMaxHp() {
        CharacterStats stats = new CharacterStats(0, 0, 20, 0, 0, 0, 0);
        assertEquals(400, stats.maxHp());
    }

    @Test
    void spiritIncreasesMaxMp() {
        CharacterStats stats = new CharacterStats(0, 0, 0, 0, 10, 0, 0);
        assertEquals(50, stats.maxMp());
    }

    @Test
    void mightAddsOnePointOfBasicAttackDamage() {
        CharacterStats stats = new CharacterStats(10, 0, 0, 0, 0, 0, 0);
        assertEquals(10, stats.attackDamage());
    }

    @Test
    void validateCreationRequiresExactlyTenPoints() {
        CharacterStats incomplete = new CharacterStats(5, 0, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, incomplete::validateCreation);

        CharacterStats complete = new CharacterStats(4, 2, 1, 1, 1, 1, 0);
        complete.validateCreation();
    }

    @Test
    void defenseReducesIncomingDamageByOnePerPoint() {
        CharacterStats stats = new CharacterStats(0, 0, 0, 5, 0, 0, 0);
        assertEquals(7, stats.mitigateDamage(12, false));
        assertEquals(2, stats.mitigateDamage(12, true));
    }

    @Test
    void criticalRateUsesLuckDifference() {
        CharacterStats attacker = new CharacterStats(0, 0, 0, 0, 0, 8, 0);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        assertFalse(attacker.rollCritical(random, 8));

        CharacterStats highLuckAttacker = new CharacterStats(0, 0, 0, 0, 0, 100, 0);
        assertTrue(highLuckAttacker.rollCritical(random, 0));
    }

    @Test
    void validateRejectsNegativeStats() {
        CharacterStats invalid = new CharacterStats(-1, 0, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, invalid::validate);
    }

    @Test
    void withBonusDoesNotCapStats() {
        CharacterStats base = new CharacterStats(100, 0, 0, 0, 0, 0, 0);
        CharacterStats bonus = new CharacterStats(50, 0, 0, 0, 0, 0, 0);
        assertEquals(150, base.withBonus(bonus).might());
    }
}
