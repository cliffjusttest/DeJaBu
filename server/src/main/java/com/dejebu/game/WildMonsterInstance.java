package com.dejebu.game;

public class WildMonsterInstance {

    private final int instanceId;
    private final int slot;
    private final String templateId;
    private final String name;
    private final Element element;
    private final int level;
    private final CharacterStats stats;
    private final int maxHp;
    private int hp;
    private final boolean capturable;

    public WildMonsterInstance(
            int instanceId,
            int slot,
            String templateId,
            String name,
            Element element,
            int level,
            CharacterStats stats,
            int maxHp,
            boolean capturable
    ) {
        this.instanceId = instanceId;
        this.slot = slot;
        this.templateId = templateId;
        this.name = name;
        this.element = element;
        this.level = level;
        this.stats = stats;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.capturable = capturable;
    }

    public int getInstanceId() {
        return instanceId;
    }

    public int getSlot() {
        return slot;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getName() {
        return name;
    }

    public Element getElement() {
        return element;
    }

    public int getLevel() {
        return level;
    }

    public CharacterStats getStats() {
        return stats;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = Math.max(0, Math.min(maxHp, hp));
    }

    public boolean isAlive() {
        return hp > 0;
    }

    public boolean isCapturable() {
        return capturable;
    }

    public BattleUnit toBattleUnit() {
        return BattleUnit.wild(
                instanceId,
                slot,
                templateId,
                name,
                element,
                level,
                stats,
                maxHp,
                hp,
                capturable
        );
    }
}
