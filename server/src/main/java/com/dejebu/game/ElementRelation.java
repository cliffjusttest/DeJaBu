package com.dejebu.game;

/**
 * 元素克制：火 &gt; 風 &gt; 土 &gt; 雷 &gt; 水 &gt; 火。「無」不參與克制。
 */
public final class ElementRelation {

    public static final double ADVANTAGE_MULTIPLIER = 1.5;
    public static final double DISADVANTAGE_MULTIPLIER = 0.75;

    private ElementRelation() {
    }

    public static double damageMultiplier(Element attacker, Element defender) {
        if (attacker == null || defender == null
                || attacker == Element.NONE || defender == Element.NONE) {
            return 1.0;
        }
        if (beats(attacker, defender)) {
            return ADVANTAGE_MULTIPLIER;
        }
        if (beats(defender, attacker)) {
            return DISADVANTAGE_MULTIPLIER;
        }
        return 1.0;
    }

    public static ElementMatchup matchup(Element attacker, Element defender) {
        double multiplier = damageMultiplier(attacker, defender);
        if (multiplier > 1.0) {
            return ElementMatchup.ADVANTAGE;
        }
        if (multiplier < 1.0) {
            return ElementMatchup.DISADVANTAGE;
        }
        return ElementMatchup.NEUTRAL;
    }

    private static boolean beats(Element attacker, Element defender) {
        return switch (attacker) {
            case FIRE -> defender == Element.WIND;
            case WIND -> defender == Element.EARTH;
            case EARTH -> defender == Element.THUNDER;
            case THUNDER -> defender == Element.WATER;
            case WATER -> defender == Element.FIRE;
            case NONE -> false;
        };
    }

    public enum ElementMatchup {
        ADVANTAGE,
        DISADVANTAGE,
        NEUTRAL
    }
}
