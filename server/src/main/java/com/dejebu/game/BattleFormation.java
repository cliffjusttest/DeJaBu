package com.dejebu.game;

/**
 * 5x2 陣型格位（共 10 格）：
 * <pre>
 * [0] [1] [2] [3] [4]
 * [5] [6] [7] [8] [9]
 * </pre>
 * 角色固定在第 7 格；夥伴出戰槽依序對應前方、左、右、左外、右外。
 */
public final class BattleFormation {

    public static final int PLAYER_SLOT = 7;

    private static final int[] PARTY_BATTLE_SLOTS = {2, 6, 8, 5, 9};

    private BattleFormation() {
    }

    public static int partySlotToBattleSlot(int partySlot) {
        if (partySlot < 0 || partySlot >= PARTY_BATTLE_SLOTS.length) {
            throw new IllegalArgumentException("Invalid party slot: " + partySlot);
        }
        return PARTY_BATTLE_SLOTS[partySlot];
    }

    public static int battleSlotToPartySlot(int battleSlot) {
        for (int i = 0; i < PARTY_BATTLE_SLOTS.length; i++) {
            if (PARTY_BATTLE_SLOTS[i] == battleSlot) {
                return i;
            }
        }
        return -1;
    }

    public static int maxPartyCompanions() {
        return PARTY_BATTLE_SLOTS.length;
    }
}
