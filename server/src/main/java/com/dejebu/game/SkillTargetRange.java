package com.dejebu.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SkillTargetRange {
    SINGLE("一人"),
    ROW_ADJACENT_THREE("一行相鄰三人"),
    CROSS("十字"),
    ROW("一整行"),
    ALL("全部");

    private final String displayName;

    SkillTargetRange(String displayName) {
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
    public static SkillTargetRange fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("技能目標範圍不可為空");
        }
        return SkillTargetRange.valueOf(code.trim().toUpperCase());
    }
}
