package com.dejebu.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SkillTargetSide {
    ALLY("我方"),
    ENEMY("敵方"),
    ANY("皆可");

    private final String displayName;

    SkillTargetSide(String displayName) {
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
    public static SkillTargetSide fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("技能目標陣營不可為空");
        }
        return SkillTargetSide.valueOf(code.trim().toUpperCase());
    }
}
