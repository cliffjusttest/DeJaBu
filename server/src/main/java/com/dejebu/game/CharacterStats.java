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
    public static final int MAX_VALUE = 99;

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
        return 50 + vitality * 5;
    }

    public int rollAttackDamage(ThreadLocalRandom random) {
        int min = Math.max(1, might / 2 + 3);
        int max = Math.max(min + 1, might + 9);
        return random.nextInt(min, max) + intelligence / 5;
    }

    public int mitigateDamage(int rawDamage, boolean defending) {
        int reduction = defense / 4 + spirit / 8;
        if (defending) {
            reduction += defense / 2;
        }
        return Math.max(1, rawDamage - reduction);
    }

    public boolean rollCritical(ThreadLocalRandom random) {
        return random.nextInt(100) < luck * 2;
    }

    public double fleeChance() {
        return Math.min(0.85, 0.35 + luck * 0.02);
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
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException(label + "必須介於 " + MIN_VALUE + " 與 " + MAX_VALUE + " 之間");
        }
    }
}
