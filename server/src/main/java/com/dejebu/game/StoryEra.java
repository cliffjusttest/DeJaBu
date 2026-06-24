package com.dejebu.game;

public enum StoryEra {
    E1("中平", "184–189"),
    E2("初平", "190–195"),
    E3("建安初", "196–200"),
    E4("建安中", "201–219"),
    E5("建安末", "220–229"),
    E6("太和", "226–237"),
    E7("正始", "240–265"),
    E8("太康", "280");

    private final String displayName;
    private final String yearRange;

    StoryEra(String displayName, String yearRange) {
        this.displayName = displayName;
        this.yearRange = yearRange;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getYearRange() {
        return yearRange;
    }

    public static StoryEra fromCode(String code) {
        if (code == null || code.isBlank()) {
            return E1;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return E1;
        }
    }
}
