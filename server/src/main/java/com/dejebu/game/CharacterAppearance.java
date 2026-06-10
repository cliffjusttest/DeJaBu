package com.dejebu.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CharacterAppearance {
    STYLE_1,
    STYLE_2,
    STYLE_3,
    STYLE_4,
    STYLE_5;

    @JsonValue
    public String getCode() {
        return name();
    }

    @JsonCreator
    public static CharacterAppearance fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("外型不可為空");
        }
        return CharacterAppearance.valueOf(code.trim().toUpperCase());
    }
}
