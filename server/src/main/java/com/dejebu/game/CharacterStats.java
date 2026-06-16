package com.dejebu.game;

import com.dejebu.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.concurrent.ThreadLocalRandom;

public record CharacterStats(
        int might,
        int intelligence,
        int vitality,
        int defense,
        int spirit,
        int luck,
        int agility
) {

    public static final int BASE_VALUE = 0;
    public static final int FREE_POINTS = 10;
    public static final int MIN_VALUE = 0;

    public static CharacterStats zeroBase() {
        return new CharacterStats(BASE_VALUE, BASE_VALUE, BASE_VALUE, BASE_VALUE, BASE_VALUE, BASE_VALUE, BASE_VALUE);
    }

    public static CharacterStats fromUser(User user) {
        return new CharacterStats(
                user.getStatMight(),
                user.getStatIntelligence(),
                user.getStatVitality(),
                user.getStatDefense(),
                user.getStatSpirit(),
                user.getStatLuck(),
                user.getStatAgility()
        );
    }

    public int totalPoints() {
        return might + intelligence + vitality + defense + spirit + luck + agility;
    }

    public void validate() {
        validateStat("武力", might);
        validateStat("智力", intelligence);
        validateStat("體力", vitality);
        validateStat("防禦", defense);
        validateStat("精神", spirit);
        validateStat("幸運", luck);
        validateStat("敏捷", agility);
    }

    public void validateCreation() {
        validate();
        int total = totalPoints();
        if (total != FREE_POINTS) {
            throw new IllegalArgumentException(
                    "請分配完 " + FREE_POINTS + " 點能力點數（目前 " + total + " 點）"
            );
        }
    }

    public int maxHp() {
        return vitality * 20;
    }

    public int maxMp() {
        return spirit * 5;
    }

    public int attackDamage() {
        return Math.max(1, might);
    }

    public int mitigateDamage(int rawDamage, boolean defending) {
        int reduction = defense;
        if (defending) {
            reduction += defense;
        }
        return Math.max(1, rawDamage - reduction);
    }

    public boolean rollCritical(ThreadLocalRandom random, int defenderLuck) {
        int critRate = Math.max(0, luck - defenderLuck);
        return random.nextInt(100) < critRate;
    }

    public double fleeChance() {
        return Math.min(0.85, 0.35 + luck * 0.02);
    }

    public CharacterStats withBonus(CharacterStats bonus) {
        return new CharacterStats(
                might + bonus.might(),
                intelligence + bonus.intelligence(),
                vitality + bonus.vitality(),
                defense + bonus.defense(),
                spirit + bonus.spirit(),
                luck + bonus.luck(),
                agility + bonus.agility()
        );
    }

    public ObjectNode toJsonNode(ObjectMapper objectMapper) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("might", might);
        node.put("intelligence", intelligence);
        node.put("vitality", vitality);
        node.put("defense", defense);
        node.put("spirit", spirit);
        node.put("luck", luck);
        node.put("agility", agility);
        return node;
    }

    private void validateStat(String label, int value) {
        if (value < MIN_VALUE) {
            throw new IllegalArgumentException(label + "不可小於 " + MIN_VALUE);
        }
    }
}
