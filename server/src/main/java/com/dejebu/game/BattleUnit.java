package com.dejebu.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final int maxMp;
    private int mp;
    private final String templateId;
    private final int level;
    private final CharacterStats stats;
    private final boolean wild;
    private final boolean capturable;
    private Long ownerUserId;
    private final List<BattleSkillRuntime> skills = new ArrayList<>();
    private final Set<Long> activeBuffSkillIds = new HashSet<>();

    private BattleUnit(
            int id,
            int slot,
            String name,
            Element element,
            int maxHp,
            int hp,
            int maxMp,
            int mp,
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
        this.maxMp = maxMp;
        this.mp = Math.max(0, Math.min(maxMp, mp));
        this.templateId = templateId;
        this.level = level;
        this.stats = stats;
        this.wild = wild;
        this.capturable = capturable;
    }

    public static BattleUnit player(
            int id,
            int slot,
            String name,
            Element element,
            int maxHp,
            int currentHp,
            int maxMp,
            int currentMp,
            int level,
            CharacterStats stats
    ) {
        return new BattleUnit(
                id, slot, name, element, maxHp, currentHp, maxMp, currentMp,
                null, level, stats, false, false
        );
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
            int currentHp,
            int maxMp,
            int currentMp
    ) {
        return new BattleUnit(
                id, slot, name, element, maxHp, currentHp, maxMp, currentMp,
                templateId, level, stats, false, false
        );
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
            int maxMp,
            int currentMp,
            boolean capturable
    ) {
        return new BattleUnit(
                id, slot, name, element, maxHp, currentHp, maxMp, currentMp,
                templateId, level, stats, true, capturable
        );
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

    public int getMaxMp() {
        return maxMp;
    }

    public int getMp() {
        return mp;
    }

    public void setMp(int mp) {
        this.mp = Math.max(0, Math.min(maxMp, mp));
    }

    public void consumeMp(int amount) {
        if (amount <= 0) {
            return;
        }
        setMp(mp - amount);
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

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
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

    public boolean hasActiveBuff() {
        return !activeBuffSkillIds.isEmpty();
    }

    public boolean hasBuff(long skillId) {
        return activeBuffSkillIds.contains(skillId);
    }

    public void applyBuff(long skillId) {
        activeBuffSkillIds.clear();
        activeBuffSkillIds.add(skillId);
    }

    public ObjectNode toJsonNode(ObjectMapper objectMapper) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("slot", slot);
        node.put("name", name);
        node.put("hp", hp);
        node.put("maxHp", maxHp);
        node.put("mp", mp);
        node.put("maxMp", maxMp);
        node.put("level", level);
        node.put("element", element.getCode());
        node.put("elementName", element.getDisplayName());
        node.put("alive", isAlive());
        node.put("wild", wild);
        node.put("capturable", capturable);
        node.put("companion", isCompanion());
        if (ownerUserId != null) {
            node.put("ownerUserId", ownerUserId);
        }
        if (templateId != null) {
            node.put("templateId", templateId);
        }
        if (stats != null) {
            node.set("stats", stats.toJsonNode(objectMapper));
        }
        ArrayNode skillsNode = objectMapper.createArrayNode();
        for (BattleSkillRuntime skill : skills) {
            skillsNode.add(skill.toJsonNode(objectMapper, mp));
        }
        node.set("skills", skillsNode);
        return node;
    }
}
