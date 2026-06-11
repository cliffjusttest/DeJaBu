package com.dejebu.game;

public enum EquipmentSlot {
    HEAD("頭"),
    FACE("臉"),
    SHOULDER("肩"),
    HAND("手"),
    BODY("身"),
    LEG("腿"),
    FOOT("腳"),
    BACK("背部"),
    ACCESSORY("飾品");

    private final String displayName;

    EquipmentSlot(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
