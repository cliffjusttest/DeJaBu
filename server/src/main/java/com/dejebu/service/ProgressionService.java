package com.dejebu.service;

import com.dejebu.entity.User;
import com.dejebu.game.WildMonsterInstance;
import com.dejebu.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProgressionService {

    public static final int MAX_LEVEL = 99;
    public static final int SKILL_POINTS_PER_LEVEL = 2;

    private final UserRepository userRepository;

    public ProgressionService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
}
