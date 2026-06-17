package com.dejebu.game;

import com.dejebu.entity.Skill;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyBattleAiTest {

    @Test
    void chooseActionFallsBackToAttackWhenNoSkills() {
        BattleUnit attacker = wildUnit(101, "野狼", 0);
        BattleUnit defender = allyUnit(1, "旅人", 7);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        EnemyBattleAi.EnemyAction action = EnemyBattleAi.chooseAction(
                attacker, List.of(attacker), List.of(defender), random);

        assertNotNull(action);
        assertEquals("attack", action.type());
        assertEquals(defender.getId(), action.targetId());
    }

    @Test
    void chooseActionCanPickReadyOffensiveSkill() {
        BattleUnit attacker = wildUnit(101, "幽影", 0);
        attacker.setSkills(List.of(BattleSkillRuntime.from(fireballSkill(), 1)));

        BattleUnit defender = allyUnit(1, "旅人", 7);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        EnemyBattleAi.EnemyAction action = null;
        for (int i = 0; i < 50; i++) {
            action = EnemyBattleAi.chooseAction(attacker, List.of(attacker), List.of(defender), random);
            if ("skill".equals(action.type())) {
                break;
            }
        }

        assertNotNull(action);
        assertEquals("skill", action.type());
        assertEquals(3L, action.skillId());
        assertEquals(defender.getId(), action.targetId());
    }

    @Test
    void chooseActionUsesHealOnDamagedAlly() {
        BattleUnit healer = wildUnit(101, "治療師", 1);
        healer.setSkills(List.of(BattleSkillRuntime.from(healSkill(), 1)));

        BattleUnit damagedAlly = wildUnit(102, "受傷狼", 2);
        damagedAlly.setHp(20);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        EnemyBattleAi.EnemyAction action = null;
        for (int i = 0; i < 50; i++) {
            action = EnemyBattleAi.chooseAction(
                    healer, List.of(healer, damagedAlly), List.of(allyUnit(1, "旅人", 7)), random);
            if ("skill".equals(action.type())) {
                break;
            }
        }

        assertNotNull(action);
        assertEquals("skill", action.type());
        assertEquals(5L, action.skillId());
        assertEquals(damagedAlly.getId(), action.targetId());
    }

    @Test
    void chooseActionSkipsHealWhenAlliesAreFullHp() {
        BattleUnit healer = wildUnit(101, "治療師", 1);
        healer.setSkills(List.of(BattleSkillRuntime.from(healSkill(), 1)));

        BattleUnit fullHpAlly = wildUnit(102, "健康狼", 2);
        BattleUnit defender = allyUnit(1, "旅人", 7);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        EnemyBattleAi.EnemyAction action = EnemyBattleAi.chooseAction(
                healer, List.of(healer, fullHpAlly), List.of(defender), random);

        assertNotNull(action);
        assertEquals("attack", action.type());
        assertEquals(defender.getId(), action.targetId());
    }

    @Test
    void chooseActionUsesReviveOnDeadAlly() {
        BattleUnit reviver = wildUnit(101, "復活者", 1);
        reviver.setSkills(List.of(BattleSkillRuntime.from(reviveSkill(), 1)));

        BattleUnit deadAlly = wildUnit(102, "倒地的狼", 2);
        deadAlly.setHp(0);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        EnemyBattleAi.EnemyAction action = null;
        for (int i = 0; i < 50; i++) {
            action = EnemyBattleAi.chooseAction(
                    reviver, List.of(reviver, deadAlly), List.of(allyUnit(1, "旅人", 7)), random);
            if ("skill".equals(action.type())) {
                break;
            }
        }

        assertNotNull(action);
        assertEquals("skill", action.type());
        assertEquals(20L, action.skillId());
        assertEquals(deadAlly.getId(), action.targetId());
    }

    @Test
    void chooseActionSkipsBuffWhenTargetAlreadyBuffed() {
        BattleUnit buffer = wildUnit(101, "輔助者", 1);
        buffer.setSkills(List.of(BattleSkillRuntime.from(buffSkill(), 1)));

        BattleUnit buffedAlly = wildUnit(102, "強化狼", 2);
        buffedAlly.applyBuff(21L);

        BattleUnit defender = allyUnit(1, "旅人", 7);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        EnemyBattleAi.EnemyAction action = EnemyBattleAi.chooseAction(
                buffer, List.of(buffer, buffedAlly), List.of(defender), random);

        assertNotNull(action);
        assertEquals("attack", action.type());
    }

    @Test
    void chooseActionUsesBuffOnUnbuffedAlly() {
        BattleUnit buffer = wildUnit(101, "輔助者", 1);
        buffer.setSkills(List.of(BattleSkillRuntime.from(buffSkill(), 1)));

        BattleUnit ally = wildUnit(102, "待強化狼", 2);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        EnemyBattleAi.EnemyAction action = null;
        for (int i = 0; i < 50; i++) {
            action = EnemyBattleAi.chooseAction(
                    buffer, List.of(buffer, ally), List.of(allyUnit(1, "旅人", 7)), random);
            if ("skill".equals(action.type())) {
                break;
            }
        }

        assertNotNull(action);
        assertEquals("skill", action.type());
        assertEquals(21L, action.skillId());
        assertEquals(ally.getId(), action.targetId());
    }

    @Test
    void pickValidTargetRejectsHealOnFullHpUnit() {
        BattleUnit fullHpAlly = wildUnit(102, "健康狼", 2);
        assertTrue(EnemyBattleAi.pickValidTarget(
                BattleSkillRuntime.from(healSkill(), 1),
                List.of(fullHpAlly),
                List.of(),
                ThreadLocalRandom.current()
        ).isEmpty());
    }

    @Test
    void chooseActionReturnsNullWhenNoTargets() {
        BattleUnit attacker = wildUnit(101, "野狼", 0);
        EnemyBattleAi.EnemyAction action = EnemyBattleAi.chooseAction(
                attacker, List.of(attacker), List.of(), ThreadLocalRandom.current());
        assertNull(action);
    }

    private static Skill fireballSkill() {
        Skill skill = new Skill();
        setSkillId(skill, 3L);
        skill.setName("火球術");
        skill.setElement(SkillElement.FIRE);
        skill.setMightCoefficient(BigDecimal.valueOf(0.2));
        skill.setIntelligenceCoefficient(BigDecimal.valueOf(1.2));
        skill.setMaxLevel(5);
        skill.setCooldownTurns(0);
        skill.setMpCost(8);
        skill.setTargetSide(SkillTargetSide.ENEMY);
        skill.setTargetRange(SkillTargetRange.SINGLE);
        skill.setEffectType(SkillEffectType.DAMAGE);
        return skill;
    }

    private static Skill healSkill() {
        Skill skill = new Skill();
        setSkillId(skill, 5L);
        skill.setName("治療術");
        skill.setElement(SkillElement.WATER);
        skill.setMightCoefficient(BigDecimal.ZERO);
        skill.setIntelligenceCoefficient(BigDecimal.valueOf(0.8));
        skill.setMaxLevel(5);
        skill.setCooldownTurns(0);
        skill.setMpCost(12);
        skill.setTargetSide(SkillTargetSide.ALLY);
        skill.setTargetRange(SkillTargetRange.SINGLE);
        skill.setEffectType(SkillEffectType.HEAL);
        skill.setComboEligible(false);
        return skill;
    }

    private static Skill reviveSkill() {
        Skill skill = new Skill();
        setSkillId(skill, 20L);
        skill.setName("復活術");
        skill.setElement(SkillElement.UNIVERSAL);
        skill.setMightCoefficient(BigDecimal.ZERO);
        skill.setIntelligenceCoefficient(BigDecimal.ZERO);
        skill.setMaxLevel(1);
        skill.setCooldownTurns(3);
        skill.setMpCost(20);
        skill.setTargetSide(SkillTargetSide.ALLY);
        skill.setTargetRange(SkillTargetRange.SINGLE);
        skill.setEffectType(SkillEffectType.REVIVE);
        skill.setComboEligible(false);
        return skill;
    }

    private static Skill buffSkill() {
        Skill skill = new Skill();
        setSkillId(skill, 21L);
        skill.setName("強化術");
        skill.setElement(SkillElement.UNIVERSAL);
        skill.setMightCoefficient(BigDecimal.ZERO);
        skill.setIntelligenceCoefficient(BigDecimal.ZERO);
        skill.setMaxLevel(3);
        skill.setCooldownTurns(2);
        skill.setMpCost(10);
        skill.setTargetSide(SkillTargetSide.ALLY);
        skill.setTargetRange(SkillTargetRange.SINGLE);
        skill.setEffectType(SkillEffectType.BUFF);
        skill.setComboEligible(false);
        return skill;
    }

    private static void setSkillId(Skill skill, long id) {
        try {
            var field = Skill.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(skill, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static BattleUnit wildUnit(int id, String name, int slot) {
        return BattleUnit.wild(
                id,
                slot,
                "wild_wolf",
                name,
                Element.WIND,
                1,
                CharacterStats.zeroBase(),
                60,
                60,
                20,
                20,
                true
        );
    }

    private static BattleUnit allyUnit(int id, String name, int slot) {
        return BattleUnit.player(id, slot, name, Element.FIRE, 50, 50, 20, 20, 1, CharacterStats.zeroBase());
    }
}
