package com.dejebu.service;

import com.dejebu.dto.SkillNodeDto;
import com.dejebu.dto.SkillTreeResponse;
import com.dejebu.entity.Skill;
import com.dejebu.entity.User;
import com.dejebu.entity.UserSkill;
import com.dejebu.game.BattleSkillRuntime;
import com.dejebu.repository.SkillRepository;
import com.dejebu.repository.UserSkillRepository;
import com.dejebu.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SkillService {

    private static final int LEARN_COST = 1;
    private static final int UPGRADE_COST = 1;

    private final SkillRepository skillRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserRepository userRepository;

    public SkillService(SkillRepository skillRepository,
                        UserSkillRepository userSkillRepository,
                        UserRepository userRepository) {
        this.skillRepository = skillRepository;
        this.userSkillRepository = userSkillRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public SkillTreeResponse getSkillTree(User user) {
        return buildSkillTreeResponse(user, "技能樹載入完成");
    }

    @Transactional
    public SkillTreeResponse learnSkill(User user, long skillId) {
        if (!user.isHasCharacter()) {
            throw new IllegalArgumentException("請先創建角色");
        }

        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("技能不存在"));
        skill.getPrerequisites().size();

        if (userSkillRepository.existsByUserIdAndSkillId(user.getId(), skillId)) {
            throw new IllegalArgumentException("已學習此技能");
        }

        Set<Long> learnedSkillIds = loadLearnedSkillIds(user.getId());
        String lockReason = evaluateLockReason(user, skill, learnedSkillIds);
        if (lockReason != null) {
            throw new IllegalArgumentException(lockReason);
        }

        if (user.getSkillPoints() < LEARN_COST) {
            throw new IllegalArgumentException("技能點不足");
        }

        UserSkill userSkill = new UserSkill();
        userSkill.setUser(user);
        userSkill.setSkill(skill);
        userSkill.setSkillLevel(1);
        userSkillRepository.save(userSkill);

        user.setSkillPoints(user.getSkillPoints() - LEARN_COST);
        userRepository.save(user);

        return buildSkillTreeResponse(user, "已學習「" + skill.getName() + "」");
    }

    @Transactional
    public SkillTreeResponse upgradeSkill(User user, long skillId) {
        if (!user.isHasCharacter()) {
            throw new IllegalArgumentException("請先創建角色");
        }

        UserSkill userSkill = userSkillRepository.findByUserIdAndSkillId(user.getId(), skillId)
                .orElseThrow(() -> new IllegalArgumentException("尚未學習此技能"));
        Skill skill = userSkill.getSkill();
        if (userSkill.getSkillLevel() >= skill.getMaxLevel()) {
            throw new IllegalArgumentException("「" + skill.getName() + "」已達等級上限");
        }
        if (user.getSkillPoints() < UPGRADE_COST) {
            throw new IllegalArgumentException("技能點不足");
        }

        userSkill.setSkillLevel(userSkill.getSkillLevel() + 1);
        userSkillRepository.save(userSkill);
        user.setSkillPoints(user.getSkillPoints() - UPGRADE_COST);
        userRepository.save(user);

        return buildSkillTreeResponse(
                user,
                "「" + skill.getName() + "」提升至 Lv." + userSkill.getSkillLevel()
        );
    }

    @Transactional(readOnly = true)
    public List<BattleSkillRuntime> loadRuntimeSkillsForUser(Long userId) {
        return userSkillRepository.findByUserId(userId).stream()
                .map(userSkill -> BattleSkillRuntime.from(userSkill.getSkill(), userSkill.getSkillLevel()))
                .toList();
    }

    private SkillTreeResponse buildSkillTreeResponse(User user, String message) {
        List<Skill> skills = skillRepository.findAllWithPrerequisites();
        Set<Long> learnedSkillIds = loadLearnedSkillIds(user.getId());
        Map<Long, Integer> learnedLevels = loadLearnedLevels(user.getId());

        List<SkillNodeDto> nodes = skills.stream()
                .sorted(Comparator.comparing(Skill::getRequiredLevel).thenComparing(Skill::getName))
                .map(skill -> toSkillNode(user, skill, learnedSkillIds, learnedLevels))
                .toList();

        return new SkillTreeResponse(user.getSkillPoints(), user.getLevel(), nodes, message);
    }

    private SkillNodeDto toSkillNode(User user,
                                     Skill skill,
                                     Set<Long> learnedSkillIds,
                                     Map<Long, Integer> learnedLevels) {
        boolean learned = learnedSkillIds.contains(skill.getId());
        int skillLevel = learnedLevels.getOrDefault(skill.getId(), 0);
        String lockReason = learned ? null : evaluateLockReason(user, skill, learnedSkillIds);
        boolean canLearn = !learned && lockReason == null;
        boolean canUpgrade = learned
                && skillLevel < skill.getMaxLevel()
                && user.getSkillPoints() >= UPGRADE_COST;

        List<Long> prerequisiteIds = skill.getPrerequisites().stream()
                .map(Skill::getId)
                .sorted()
                .toList();

        String statusText = buildStatusText(learned, canLearn, canUpgrade, lockReason, skillLevel, skill.getMaxLevel());

        return new SkillNodeDto(
                skill.getId(),
                skill.getName(),
                skill.getElement().getCode(),
                skill.getElement().getDisplayName(),
                skill.getMightCoefficient(),
                skill.getIntelligenceCoefficient(),
                skill.getRequiredLevel(),
                skill.getMaxLevel(),
                skill.getCooldownTurns(),
                skill.getTargetSide().getCode(),
                skill.getTargetSide().getDisplayName(),
                skill.getTargetRange().getCode(),
                skill.getTargetRange().getDisplayName(),
                prerequisiteIds,
                learned,
                skillLevel,
                canLearn,
                canUpgrade,
                statusText
        );
    }

    private String buildStatusText(
            boolean learned,
            boolean canLearn,
            boolean canUpgrade,
            String lockReason,
            int skillLevel,
            int maxLevel
    ) {
        if (learned) {
            if (canUpgrade) {
                return "Lv.%d / %d · 可強化".formatted(skillLevel, maxLevel);
            }
            if (skillLevel >= maxLevel) {
                return "Lv.%d · 已滿".formatted(skillLevel);
            }
            return "Lv.%d · 技能點不足".formatted(skillLevel);
        }
        if (canLearn) {
            return "可學習";
        }
        return lockReason != null ? lockReason : "未解鎖";
    }

    private String evaluateLockReason(User user, Skill skill, Set<Long> learnedSkillIds) {
        if (user.getLevel() < skill.getRequiredLevel()) {
            return "需要等級 %d".formatted(skill.getRequiredLevel());
        }

        for (Skill prerequisite : skill.getPrerequisites()) {
            if (!learnedSkillIds.contains(prerequisite.getId())) {
                return "需先學習「%s」".formatted(prerequisite.getName());
            }
        }

        if (user.getSkillPoints() < LEARN_COST) {
            return "技能點不足";
        }

        return null;
    }

    private Set<Long> loadLearnedSkillIds(Long userId) {
        return userSkillRepository.findByUserId(userId).stream()
                .map(userSkill -> userSkill.getSkill().getId())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Map<Long, Integer> loadLearnedLevels(Long userId) {
        Map<Long, Integer> levels = new HashMap<>();
        for (UserSkill userSkill : userSkillRepository.findByUserId(userId)) {
            levels.put(userSkill.getSkill().getId(), userSkill.getSkillLevel());
        }
        return levels;
    }
}
