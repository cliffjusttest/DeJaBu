package com.dejebu.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Set;

public enum Element {
    FIRE("火"),
    WIND("風"),
    EARTH("土"),
    THUNDER("雷"),
    WATER("水"),
    NONE("無");

    private static final Set<Element> SELECTABLE = Set.of(FIRE, WIND, EARTH, THUNDER, WATER);

    private final String displayName;

    Element(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getCode() {
        return name();
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isSelectable() {
        return SELECTABLE.contains(this);
    }

    @JsonCreator
    public static Element fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("元素不可為空");
        }
        return Element.valueOf(code.trim().toUpperCase());
    }

    public static boolean isSelectableCode(String code) {
        try {
            return fromCode(code).isSelectable();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static Element[] selectableValues() {
        return Arrays.stream(values())
                .filter(Element::isSelectable)
                .toArray(Element[]::new);
    }
}
