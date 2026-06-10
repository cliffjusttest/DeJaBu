package com.dejebu.game;

import com.dejebu.entity.Skill;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnemyBattleAiTest {

    @Test
    void chooseActionFallsBackToAttackWhenNoSkills() {
        BattleUnit attacker = wildUnit(101, "野狼");
        BattleUnit defender = allyUnit(1, "旅人");
        ThreadLocalRandom random = ThreadLocalRandom.current();

        EnemyBattleAi.EnemyAction action = EnemyBattleAi.chooseAction(attacker, List.of(defender), random);

        assertNotNull(action);
        assertEquals("attack", action.type());
        assertEquals(defender.getId(), action.targetId());
    }

    @Test
    void chooseActionCanPickReadySkill() {
        BattleUnit attacker = wildUnit(101, "幽影");
        attacker.setSkills(List.of(BattleSkillRuntime.from(fireballSkill(), 1)));

        BattleUnit defender = allyUnit(1, "旅人");
        ThreadLocalRandom random = ThreadLocalRandom.current();

        EnemyBattleAi.EnemyAction action = null;
        for (int i = 0; i < 50; i++) {
            action = EnemyBattleAi.chooseAction(attacker, List.of(defender), random);
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
    void chooseActionReturnsNullWhenNoTargets() {
        BattleUnit attacker = wildUnit(101, "野狼");
        EnemyBattleAi.EnemyAction action = EnemyBattleAi.chooseAction(attacker, List.of(), ThreadLocalRandom.current());
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
        skill.setTargetSide(SkillTargetSide.ENEMY);
        skill.setTargetRange(SkillTargetRange.SINGLE);
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

    private static BattleUnit wildUnit(int id, String name) {
        return BattleUnit.wild(
                id,
                0,
                "wild_wolf",
                name,
                Element.WIND,
                1,
                CharacterStats.zeroBase(),
                60,
                60,
                true
        );
    }

    private static BattleUnit allyUnit(int id, String name) {
        return BattleUnit.player(id, 7, name, Element.FIRE, 50, 1, CharacterStats.zeroBase());
    }
}
