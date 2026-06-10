package com.dejebu.game;

import com.dejebu.game.ElementRelation.ElementMatchup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElementRelationTest {

    @Test
    void fireBeatsWind() {
        assertEquals(ElementMatchup.ADVANTAGE, ElementRelation.matchup(Element.FIRE, Element.WIND));
        assertEquals(ElementMatchup.DISADVANTAGE, ElementRelation.matchup(Element.WIND, Element.FIRE));
    }

    @Test
    void cycleBeatsCorrectly() {
        assertEquals(ElementMatchup.ADVANTAGE, ElementRelation.matchup(Element.WIND, Element.EARTH));
        assertEquals(ElementMatchup.ADVANTAGE, ElementRelation.matchup(Element.EARTH, Element.THUNDER));
        assertEquals(ElementMatchup.ADVANTAGE, ElementRelation.matchup(Element.THUNDER, Element.WATER));
        assertEquals(ElementMatchup.ADVANTAGE, ElementRelation.matchup(Element.WATER, Element.FIRE));
    }

    @Test
    void noneIsNeutral() {
        assertEquals(1.0, ElementRelation.damageMultiplier(Element.NONE, Element.FIRE));
        assertEquals(1.0, ElementRelation.damageMultiplier(Element.FIRE, Element.NONE));
        assertEquals(ElementMatchup.NEUTRAL, ElementRelation.matchup(Element.NONE, Element.WATER));
    }
}
