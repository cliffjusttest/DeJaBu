package com.dejebu.game;

public record MonsterTemplate(String id, String name, int maxHp, Element element) {

    public static final MonsterTemplate WILD_WOLF = new MonsterTemplate("wild_wolf", "野狼", 60, Element.WIND);
    /** 保留：特定怪物可使用「無」元素，不受克制影響 */
    public static final MonsterTemplate SHADOW_WISP = new MonsterTemplate("shadow_wisp", "幽影", 80, Element.NONE);

    public static MonsterTemplate defaultEncounter() {
        return WILD_WOLF;
    }
}
