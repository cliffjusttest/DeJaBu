package com.dejebu.service;

import com.dejebu.entity.User;
import com.dejebu.entity.UserCompanion;
import com.dejebu.game.WildMonsterInstance;
import com.dejebu.repository.UserCompanionRepository;
import com.dejebu.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProgressionService {

    public static final int MAX_LEVEL = 99;
    public static final int SKILL_POINTS_PER_LEVEL = 2;

    private final UserRepository userRepository;
    private final UserCompanionRepository userCompanionRepository;

    public ProgressionService(UserRepository userRepository, UserCompanionRepository userCompanionRepository) {
        this.userRepository = userRepository;
        this.userCompanionRepository = userCompanionRepository;
    }

    public static int expToNextLevel(int level) {
        return Math.max(1, level) * 100;
    }

    public static int expFromEncounter(List<WildMonsterInstance> monsters) {
        int total = 0;
        for (WildMonsterInstance monster : monsters) {
            total += Math.max(1, monster.getLevel()) * 5;
        }
        return total;
    }

    @Transactional
    public ProgressionResult applyVictoryExp(Long userId, int expGained) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("玩家不存在"));

        int previousLevel = user.getLevel();
        int skillPointsGained = 0;
        user.setExp(user.getExp() + expGained);

        while (user.getLevel() < MAX_LEVEL && user.getExp() >= expToNextLevel(user.getLevel())) {
            user.setExp(user.getExp() - expToNextLevel(user.getLevel()));
            user.setLevel(user.getLevel() + 1);
            user.setSkillPoints(user.getSkillPoints() + SKILL_POINTS_PER_LEVEL);
            skillPointsGained += SKILL_POINTS_PER_LEVEL;
        }

        userRepository.save(user);

        return new ProgressionResult(
                expGained,
                user.getExp(),
                expToNextLevel(user.getLevel()),
                user.getLevel(),
                previousLevel,
                user.getLevel() - previousLevel,
                skillPointsGained,
                user.getSkillPoints()
        );
    }

    @Transactional
    public Optional<CompanionExpResult> applyExpToCompanion(Long userId, int partySlot, int expGained) {
        return userCompanionRepository.findByUserIdAndPartySlot(userId, partySlot)
                .map(companion -> {
                    int previousLevel = companion.getLevel();
                    companion.setExp(companion.getExp() + expGained);

                    while (companion.getLevel() < MAX_LEVEL
                            && companion.getExp() >= expToNextLevel(companion.getLevel())) {
                        companion.setExp(companion.getExp() - expToNextLevel(companion.getLevel()));
                        companion.setLevel(companion.getLevel() + 1);
                        applyCompanionLevelUpStats(companion);
                    }

                    userCompanionRepository.save(companion);
                    return new CompanionExpResult(
                            companion.getId(),
                            companion.getNickname(),
                            expGained,
                            previousLevel,
                            companion.getLevel(),
                            companion.getLevel() > previousLevel
                    );
                });
    }

    private void applyCompanionLevelUpStats(UserCompanion companion) {
        companion.setStatMight(companion.getStatMight() + 2);
        companion.setStatIntelligence(companion.getStatIntelligence() + 1);
        companion.setStatVitality(companion.getStatVitality() + 2);
        companion.setStatDefense(companion.getStatDefense() + 1);
        companion.setStatSpirit(companion.getStatSpirit() + 1);
        companion.setStatLuck(companion.getStatLuck() + 1);
        companion.setStatAgility(companion.getStatAgility() + 1);
        companion.setMaxHp(50 + companion.getStatVitality() * 5);
    }
}
