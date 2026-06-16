package com.dejebu.service;

import com.dejebu.entity.Item;
import com.dejebu.game.CharacterStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EquipmentServiceTest {

    @Test
    void sumItemBonusesAggregatesAllStatFields() {
        Item helmet = new Item();
        helmet.setBonusVitality(2);
        helmet.setBonusDefense(5);

        Item gloves = new Item();
        gloves.setBonusMight(3);
        gloves.setBonusAgility(2);

        CharacterStats bonus = EquipmentService.sumItemBonuses(List.of(helmet, gloves));

        assertEquals(3, bonus.might());
        assertEquals(2, bonus.vitality());
        assertEquals(5, bonus.defense());
        assertEquals(2, bonus.agility());
        assertEquals(40, bonus.withBonus(CharacterStats.zeroBase()).maxHp());
    }

    @Test
    void sumItemBonusesReturnsZeroForEmptyList() {
        assertEquals(CharacterStats.zeroBase(), EquipmentService.sumItemBonuses(List.of()));
    }
}
