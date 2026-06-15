package com.dejebu.game;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class EnemyBattleAi {

    private static final double SKILL_USE_CHANCE = 0.55;

    private EnemyBattleAi() {
    }

    public record EnemyAction(String type, Long skillId, int targetId) {
    }

    public static EnemyAction chooseAction(
            BattleUnit attacker,
            List<BattleUnit> opponents,
            ThreadLocalRandom random
    ) {
        List<BattleSkillRuntime> readySkills = attacker.getSkills().stream()
                .filter(skill -> skill.canUse(attacker.getMp()))
                .filter(skill -> !skill.isHealSkill())
                .toList();

        if (!readySkills.isEmpty() && random.nextDouble() < SKILL_USE_CHANCE) {
            BattleSkillRuntime skill = readySkills.get(random.nextInt(readySkills.size()));
            BattleUnit target = pickRandomAlive(opponents, random);
            if (target != null) {
                return new EnemyAction("skill", skill.getSkillId(), target.getId());
            }
        }

        BattleUnit attackTarget = pickRandomAlive(opponents, random);
        if (attackTarget == null) {
            return null;
        }
        return new EnemyAction("attack", null, attackTarget.getId());
    }

    private static BattleUnit pickRandomAlive(List<BattleUnit> units, ThreadLocalRandom random) {
        List<BattleUnit> alive = units.stream().filter(BattleUnit::isAlive).toList();
        if (alive.isEmpty()) {
            return null;
        }
        return alive.get(random.nextInt(alive.size()));
    }
}
