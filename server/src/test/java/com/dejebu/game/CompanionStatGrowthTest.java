package com.dejebu.game;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionStatGrowthTest {

    @RepeatedTest(50)
    void rollGrowthAllocatesExactlyFivePoints() {
        CharacterStats growth = CompanionStatGrowth.rollGrowth(
                new CharacterStats(10, 8, 12, 6, 5, 4, 7),
                ThreadLocalRandom.current()
        );

        assertEquals(CompanionStatGrowth.POINTS_PER_LEVEL, growth.might()
                + growth.intelligence()
                + growth.vitality()
                + growth.defense()
                + growth.spirit()
                + growth.luck()
                + growth.agility());
    }

    @RepeatedTest(50)
    void rollGrowthEachStatGainIsOneOrTwo() {
        CharacterStats growth = CompanionStatGrowth.rollGrowth(
                CharacterStats.zeroBase(),
                ThreadLocalRandom.current()
        );

        assertStatGainInRange(growth.might());
        assertStatGainInRange(growth.intelligence());
        assertStatGainInRange(growth.vitality());
        assertStatGainInRange(growth.defense());
        assertStatGainInRange(growth.spirit());
        assertStatGainInRange(growth.luck());
        assertStatGainInRange(growth.agility());
    }

    @Test
    void pickWeightedStatPrefersHighestValue() {
        int[] values = {1, 2, 20, 3, 4, 5, 6};
        int highestCount = 0;
        int trials = 10_000;

        for (int i = 0; i < trials; i++) {
            if (CompanionStatGrowth.pickWeightedStat(values, ThreadLocalRandom.current()) == 2) {
                highestCount++;
            }
        }

        assertTrue(highestCount > trials * 0.25,
                "Expected highest stat to be picked often, got " + highestCount);
    }

    @Test
    void sortedIndicesBreaksTiesByStatOrder() {
        int[] values = {5, 5, 5, 5, 5, 5, 5};
        int[] indices = CompanionStatGrowth.sortedIndicesByValueDesc(values);
        assertEquals(0, indices[0]);
        assertEquals(1, indices[1]);
        assertEquals(6, indices[6]);
    }

    private static void assertStatGainInRange(int value) {
        assertTrue(value >= 0 && value <= 2, "Unexpected gain: " + value);
    }
}
