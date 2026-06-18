package com.dejebu.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementTraitTest {

    @Test
    void fireBonusIncreasesDamageByFivePercent() {
        assertEquals(105, ElementTrait.applyFireBonus(100, Element.FIRE, true));
    }

    @Test
    void fireBonusDoesNotApplyToOtherElements() {
        assertEquals(100, ElementTrait.applyFireBonus(100, Element.WATER, true));
    }

    @Test
    void earthReductionReducesDamageByFivePercent() {
        assertEquals(95, ElementTrait.applyEarthReduction(100, Element.EARTH, true));
    }

    @Test
    void earthReductionDoesNotApplyToOtherElements() {
        assertEquals(100, ElementTrait.applyEarthReduction(100, Element.FIRE, true));
    }

    @Test
    void thunderBoostsCritRateByFivePoints() {
        CharacterStats attacker = new CharacterStats(10, 0, 0, 0, 0, 10, 0);
        assertEquals(10, ElementTrait.resolveCritRate(attacker, 5, Element.FIRE, true));
        assertEquals(15, ElementTrait.resolveCritRate(attacker, 5, Element.THUNDER, true));
    }

    @Test
    void thunderDoesNotBoostCritForOtherElements() {
        CharacterStats attacker = new CharacterStats(10, 0, 0, 0, 0, 4, 0);
        assertEquals(4, ElementTrait.resolveCritRate(attacker, 0, Element.FIRE, true));
    }

    @Test
    void waterRecoveryRestoresFivePercentHpAndMp() {
        BattleUnit unit = BattleUnit.player(
                1, 7, "Tester", Element.WATER,
                200, 100, 100, 20,
                1, CharacterStats.zeroBase()
        );
        var recovered = ElementTrait.applyWaterRecovery(unit, true);
        assertTrue(recovered.isPresent());
        assertEquals(10, recovered.get()[0]);
        assertEquals(5, recovered.get()[1]);
        assertEquals(110, unit.getHp());
        assertEquals(25, unit.getMp());
    }
}
