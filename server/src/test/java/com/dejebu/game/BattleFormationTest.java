package com.dejebu.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleFormationTest {

    @Test
    void soloCompanionSlotsFollowPartyIndex() {
        assertEquals(2, BattleFormation.partySlotToBattleSlot(0));
        assertEquals(1, BattleFormation.partySlotToBattleSlot(1));
        assertEquals(3, BattleFormation.partySlotToBattleSlot(2));
        assertEquals(0, BattleFormation.partySlotToBattleSlot(3));
        assertEquals(4, BattleFormation.partySlotToBattleSlot(4));
    }

    @Test
    void multiplayerCompanionIsInFrontOfPlayer() {
        assertEquals(7, BattleFormation.multiplayerPlayerSlot(0));
        assertEquals(2, BattleFormation.multiplayerCompanionSlot(0));

        assertEquals(6, BattleFormation.multiplayerPlayerSlot(1));
        assertEquals(1, BattleFormation.multiplayerCompanionSlot(1));

        assertEquals(8, BattleFormation.multiplayerPlayerSlot(2));
        assertEquals(3, BattleFormation.multiplayerCompanionSlot(2));

        assertEquals(5, BattleFormation.multiplayerPlayerSlot(3));
        assertEquals(0, BattleFormation.multiplayerCompanionSlot(3));

        assertEquals(9, BattleFormation.multiplayerPlayerSlot(4));
        assertEquals(4, BattleFormation.multiplayerCompanionSlot(4));
    }

    @Test
    void companionSlotInFrontOfUsesTopRowOffset() {
        assertEquals(2, BattleFormation.companionSlotInFrontOf(7));
        assertEquals(1, BattleFormation.companionSlotInFrontOf(6));
    }
}
