package com.dejebu.game;

import java.util.ArrayList;
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
            List<BattleUnit> teammates,
            List<BattleUnit> opponents,
            ThreadLocalRandom random
    ) {
        List<BattleSkillRuntime> readySkills = attacker.getSkills().stream()
                .filter(skill -> skill.canUse(attacker.getMp()))
                .toList();

        List<SkillAction> validSkillActions = new ArrayList<>();
        for (BattleSkillRuntime skill : readySkills) {
            pickValidTarget(skill, teammates, opponents, random)
                    .ifPresent(target -> validSkillActions.add(new SkillAction(skill, target)));
        }

        if (!validSkillActions.isEmpty() && random.nextDouble() < SKILL_USE_CHANCE) {
            SkillAction chosen = validSkillActions.get(random.nextInt(validSkillActions.size()));
            return new EnemyAction("skill", chosen.skill().getSkillId(), chosen.target().getId());
        }

        BattleUnit attackTarget = pickRandomAlive(opponents, random);
        if (attackTarget == null) {
            return null;
        }
        return new EnemyAction("attack", null, attackTarget.getId());
    }

    static java.util.Optional<BattleUnit> pickValidTarget(
            BattleSkillRuntime skill,
            List<BattleUnit> teammates,
            List<BattleUnit> opponents,
            ThreadLocalRandom random
    ) {
        List<BattleUnit> pool = targetPool(skill, teammates, opponents);
        if (pool.isEmpty()) {
            return java.util.Optional.empty();
        }

        List<BattleUnit> validAnchors = pool.stream()
                .filter(anchor -> hasValidEffectInRange(skill, anchor, pool))
                .toList();
        if (validAnchors.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(validAnchors.get(random.nextInt(validAnchors.size())));
    }

    private static List<BattleUnit> targetPool(
            BattleSkillRuntime skill,
            List<BattleUnit> teammates,
            List<BattleUnit> opponents
    ) {
        return switch (skill.getTargetSide()) {
            case ALLY -> teammates;
            case ENEMY -> opponents.stream().filter(BattleUnit::isAlive).toList();
            case ANY -> {
                List<BattleUnit> combined = new ArrayList<>(opponents.stream().filter(BattleUnit::isAlive).toList());
                combined.addAll(teammates.stream().filter(BattleUnit::isAlive).toList());
                yield combined;
            }
        };
    }

    private static boolean hasValidEffectInRange(
            BattleSkillRuntime skill,
            BattleUnit anchor,
            List<BattleUnit> pool
    ) {
        List<Integer> affectedSlots = SkillTargetResolver.resolveSlots(anchor.getSlot(), skill.getTargetRange());
        List<BattleUnit> inRange = pool.stream()
                .filter(unit -> affectedSlots.contains(unit.getSlot()))
                .toList();

        if (skill.isHealSkill()) {
            return inRange.stream().anyMatch(EnemyBattleAi::isDamaged);
        }
        if (skill.isReviveSkill()) {
            return inRange.stream().anyMatch(unit -> !unit.isAlive());
        }
        if (skill.isBuffSkill()) {
            return inRange.stream().anyMatch(unit -> canReceiveBuff(unit, skill));
        }
        return inRange.stream().anyMatch(BattleUnit::isAlive);
    }

    private static boolean isDamaged(BattleUnit unit) {
        return unit.isAlive() && unit.getHp() < unit.getMaxHp();
    }

    private static boolean canReceiveBuff(BattleUnit unit, BattleSkillRuntime skill) {
        return unit.isAlive()
                && !unit.hasActiveBuff()
                && !unit.hasBuff(skill.getSkillId());
    }

    private static BattleUnit pickRandomAlive(List<BattleUnit> units, ThreadLocalRandom random) {
        List<BattleUnit> alive = units.stream().filter(BattleUnit::isAlive).toList();
        if (alive.isEmpty()) {
            return null;
        }
        return alive.get(random.nextInt(alive.size()));
    }

    private record SkillAction(BattleSkillRuntime skill, BattleUnit target) {
    }
}
