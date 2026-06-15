package com.dejebu.protocol;

public enum MessageType {
    PING,
    PONG,
    LOGIN,
    LOGIN_OK,
    MOVE,
    MOVE_OK,
    BATTLE_START,
    BATTLE_ACTION,
    BATTLE_RESULT,
    NPC_INTERACT,
    NPC_INTERACT_OK,
    DIALOGUE_CHOICE,
    QUEST_LIST,
    QUEST_LIST_OK,
    PLAYER_JOIN,
    PLAYER_LEAVE,
    PLAYER_MOVE,
    ERROR
}
