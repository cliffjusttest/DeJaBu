package com.dejebu.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SkillElement {
    FIRE("火"),
    WIND("風"),
    EARTH("土"),
    THUNDER("雷"),
    WATER("水"),
    UNIVERSAL("通用");

    private final String displayName;

    SkillElement(String displayName) {
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
    public static SkillElement fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("技能元素不可為空");
        }
        return SkillElement.valueOf(code.trim().toUpperCase());
    }
}
