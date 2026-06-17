package com.dejebu.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SkillEffectType {
    DAMAGE("傷害"),
    HEAL("治療"),
    REVIVE("復活"),
    BUFF("輔助");

    private final String displayName;

    SkillEffectType(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getCode() {
        return name();
    }

    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static SkillEffectType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return DAMAGE;
        }
        return SkillEffectType.valueOf(code.trim().toUpperCase());
    }
}
