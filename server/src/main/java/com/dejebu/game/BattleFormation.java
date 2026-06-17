package com.dejebu.game;

/**
 * 5x2 陣型格位（共 10 格）：
 * <pre>
 * [0] [1] [2] [3] [4]
 * [5] [6] [7] [8] [9]
 * </pre>
 * 單人：角色在 7，夥伴依隊伍索引在 2、1、3、0、4。
 * 組隊：隊長在 7，其他玩家依序在 6、8、5、9；各玩家的夥伴在其角色正前方（角色格 - 5）。
 */
public final class BattleFormation {

    public static final int PLAYER_SLOT = 7;

    /** 單人：夥伴出戰槽依隊伍索引對應的戰鬥格 */
    private static final int[] PARTY_BATTLE_SLOTS = {2, 1, 3, 0, 4};

    /** 組隊：每位玩家的角色格（索引 0 = 隊長） */
    private static final int[] MULTIPLAYER_PLAYER_SLOTS = {7, 6, 8, 5, 9};

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

    public static int multiplayerPlayerSlot(int partyIndex) {
        if (partyIndex < 0 || partyIndex >= MULTIPLAYER_PLAYER_SLOTS.length) {
            throw new IllegalArgumentException("Invalid multiplayer party index: " + partyIndex);
        }
        return MULTIPLAYER_PLAYER_SLOTS[partyIndex];
    }

    /** 角色正前方的夥伴格（下排角色格 - 5 = 上排夥伴格） */
    public static int companionSlotInFrontOf(int playerSlot) {
        return playerSlot - 5;
    }

    public static int multiplayerCompanionSlot(int partyIndex) {
        return companionSlotInFrontOf(multiplayerPlayerSlot(partyIndex));
    }

    public static int maxMultiplayerPartySize() {
        return MULTIPLAYER_PLAYER_SLOTS.length;
    }

    /** 組隊模式下每位玩家最多出戰的夥伴數 */
    public static int maxCompanionsPerPlayerInParty() {
        return 1;
    }
}
