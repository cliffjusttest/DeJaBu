package com.dejebu.game;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public final class CompanionStatGrowth {

    public static final int POINTS_PER_LEVEL = 5;
    private static final double[] RANK_WEIGHTS = {0.32, 0.24, 0.16, 0.12, 0.06, 0.05, 0.05};

    private CompanionStatGrowth() {
    }

    public static CharacterStats rollGrowth(CharacterStats rankStats, ThreadLocalRandom random) {
        int[] values = {
                rankStats.might(),
                rankStats.intelligence(),
                rankStats.vitality(),
                rankStats.defense(),
                rankStats.spirit(),
                rankStats.luck(),
                rankStats.agility()
        };
        int[] deltas = new int[7];
        int remaining = POINTS_PER_LEVEL;

        while (remaining > 0) {
            int chosen = pickWeightedStat(values, random);
            int gain = random.nextInt(2) + 1;
            int actual = Math.min(gain, remaining);
            values[chosen] += actual;
            deltas[chosen] += actual;
            remaining -= actual;
        }

        return new CharacterStats(
                deltas[0], deltas[1], deltas[2], deltas[3], deltas[4], deltas[5], deltas[6]
        );
    }

    static int pickWeightedStat(int[] values, ThreadLocalRandom random) {
        int[] sortedIndices = sortedIndicesByValueDesc(values);
        double roll = random.nextDouble();
        double cumulative = 0.0;
        for (int rank = 0; rank < sortedIndices.length; rank++) {
            cumulative += RANK_WEIGHTS[rank];
            if (roll < cumulative) {
                return sortedIndices[rank];
            }
        }
        return sortedIndices[sortedIndices.length - 1];
    }

    static int[] sortedIndicesByValueDesc(int[] values) {
        Integer[] indices = {0, 1, 2, 3, 4, 5, 6};
        Arrays.sort(indices, (a, b) -> {
            int cmp = Integer.compare(values[b], values[a]);
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(a, b);
        });
        int[] result = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            result[i] = indices[i];
        }
        return result;
    }
}
