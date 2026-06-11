package com.dejebu.game;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterStatsTest {

    @Test
    void zeroBaseHasNoPoints() {
        assertEquals(0, CharacterStats.zeroBase().totalPoints());
        assertEquals(50, CharacterStats.zeroBase().maxHp());
    }

    @Test
    void higherVitalityIncreasesMaxHp() {
        CharacterStats stats = new CharacterStats(0, 0, 20, 0, 0, 0, 0);
        assertEquals(150, stats.maxHp());
    }

    @Test
    void fullMightAllocationProducesExpectedAttackRange() {
        CharacterStats stats = new CharacterStats(10, 0, 0, 0, 0, 0, 0);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < 100; i++) {
            int damage = stats.rollAttackDamage(random);
            assertTrue(damage >= 8 && damage <= 19, "damage out of range: " + damage);
        }
    }

    @Test
    void validateCreationRequiresExactlyTenPoints() {
        CharacterStats incomplete = new CharacterStats(5, 0, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, incomplete::validateCreation);

        CharacterStats complete = new CharacterStats(4, 2, 1, 1, 1, 1, 0);
        complete.validateCreation();
    }

    @Test
    void defenseAndSpiritReduceIncomingDamage() {
        CharacterStats stats = new CharacterStats(0, 0, 0, 20, 20, 0, 0);
        int mitigated = stats.mitigateDamage(12, false);
        assertTrue(mitigated < 12);
        assertTrue(stats.mitigateDamage(12, true) < mitigated);
    }

    @Test
    void validateRejectsNegativeStats() {
        CharacterStats invalid = new CharacterStats(-1, 0, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, invalid::validate);
    }
}
