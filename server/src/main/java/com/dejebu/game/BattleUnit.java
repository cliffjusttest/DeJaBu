package com.dejebu.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleUnit {

    public static final int SLOTS_PER_ROW = 5;
    public static final int ROW_COUNT = 2;
    public static final int MAX_UNITS_PER_SIDE = SLOTS_PER_ROW * ROW_COUNT;

    private final int id;
    private final int slot;
    private final String name;
    private final Element element;
    private final int maxHp;
    private int hp;
    private final String templateId;
    private final int level;
    private final CharacterStats stats;
    private final boolean wild;
    private final boolean capturable;
    private final List<BattleSkillRuntime> skills = new ArrayList<>();

    private BattleUnit(
            int id,
            int slot,
            String name,
            Element element,
            int maxHp,
            int hp,
            String templateId,
            int level,
            CharacterStats stats,
            boolean wild,
            boolean capturable
    ) {
        if (slot < 0 || slot >= MAX_UNITS_PER_SIDE) {
            throw new IllegalArgumentException("Invalid battle slot: " + slot);
        }
        this.id = id;
        this.slot = slot;
        this.name = name;
        this.element = element;
        this.maxHp = maxHp;
        this.hp = hp;
        this.templateId = templateId;
        this.level = level;
        this.stats = stats;
        this.wild = wild;
        this.capturable = capturable;
    }

    public static BattleUnit player(int id, int slot, String name, Element element, int maxHp, int level, CharacterStats stats) {
        return player(id, slot, name, element, maxHp, maxHp, level, stats);
    }

    public static BattleUnit player(
            int id,
            int slot,
            String name,
            Element element,
            int maxHp,
            int currentHp,
            int level,
            CharacterStats stats
    ) {
        return new BattleUnit(id, slot, name, element, maxHp, currentHp, null, level, stats, false, false);
    }

    public static BattleUnit companion(
            int id,
            int slot,
            String templateId,
            String name,
            Element element,
            int level,
            CharacterStats stats,
            int maxHp,
            int currentHp
    ) {
        return new BattleUnit(id, slot, name, element, maxHp, currentHp, templateId, level, stats, false, false);
    }

    public static BattleUnit wild(
            int id,
            int slot,
            String templateId,
            String name,
            Element element,
            int level,
            CharacterStats stats,
            int maxHp,
            int currentHp,
            boolean capturable
    ) {
        return new BattleUnit(id, slot, name, element, maxHp, currentHp, templateId, level, stats, true, capturable);
    }

    public int getId() {
        return id;
    }

    public int getSlot() {
        return slot;
    }

    public String getName() {
        return name;
    }

    public Element getElement() {
        return element;
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

    public void setHpRaw(int hp) {
        this.hp = Math.min(maxHp, hp); // allow negative (combo overflow)
    }

    public String getTemplateId() {
        return templateId;
    }

    public int getLevel() {
        return level;
    }

    public CharacterStats getStats() {
        return stats;
    }

    public boolean isWild() {
        return wild;
    }

    public boolean isCapturable() {
        return capturable;
    }

    public boolean isAlive() {
        return hp > 0;
    }

    public boolean isCompanion() {
        return templateId != null && !wild;
    }

    public List<BattleSkillRuntime> getSkills() {
        return Collections.unmodifiableList(skills);
    }

    public void setSkills(List<BattleSkillRuntime> battleSkills) {
        skills.clear();
        if (battleSkills != null) {
            skills.addAll(battleSkills);
        }
    }

    public BattleSkillRuntime findSkill(long skillId) {
        return skills.stream()
                .filter(skill -> skill.getSkillId() == skillId)
                .findFirst()
                .orElse(null);
    }

    public void tickSkillCooldowns() {
        for (BattleSkillRuntime skill : skills) {
            skill.tickCooldown();
        }
    }

    public ObjectNode toJsonNode(ObjectMapper objectMapper) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("slot", slot);
        node.put("name", name);
        node.put("hp", hp);
        node.put("maxHp", maxHp);
        node.put("level", level);
        node.put("element", element.getCode());
        node.put("elementName", element.getDisplayName());
        node.put("alive", isAlive());
        node.put("wild", wild);
        node.put("capturable", capturable);
        node.put("companion", isCompanion());
        if (templateId != null) {
            node.put("templateId", templateId);
        }
        if (stats != null) {
            node.set("stats", stats.toJsonNode(objectMapper));
        }
        ArrayNode skillsNode = objectMapper.createArrayNode();
        for (BattleSkillRuntime skill : skills) {
            skillsNode.add(skill.toJsonNode(objectMapper));
        }
        node.set("skills", skillsNode);
        return node;
    }
}
