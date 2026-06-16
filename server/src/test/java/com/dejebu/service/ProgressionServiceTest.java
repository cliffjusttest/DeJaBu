package com.dejebu.service;

import com.dejebu.entity.User;
import com.dejebu.game.WildMonsterInstance;
import com.dejebu.repository.UserCompanionRepository;
import com.dejebu.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserCompanionRepository userCompanionRepository;

    private ProgressionService progressionService;

    @BeforeEach
    void setUp() {
        progressionService = new ProgressionService(userRepository, userCompanionRepository);
    }

    @Test
    void expFromEncounterSumsMonsterLevels() {
        List<WildMonsterInstance> monsters = List.of(
                monster(1),
                monster(3),
                monster(2)
        );

        assertEquals(30, ProgressionService.expFromEncounter(monsters));
    }

    @Test
    void expToNextLevelScalesWithCurrentLevel() {
        assertEquals(100, ProgressionService.expToNextLevel(1));
        assertEquals(500, ProgressionService.expToNextLevel(5));
    }

    @Test
    void applyVictoryExpAccumulatesWithoutLevelUp() {
        User user = user(1, 0, 10);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProgressionResult result = progressionService.applyVictoryExp(1L, 15);

        assertEquals(15, result.playerExp());
        assertEquals(1, result.playerLevel());
        assertFalse(result.leveledUp());
        assertEquals(10, result.skillPoints());
    }

    @Test
    void applyVictoryExpLevelsUpAndGrantsSkillPoints() {
        User user = user(1, 90, 10);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProgressionResult result = progressionService.applyVictoryExp(1L, 15);

        assertEquals(5, result.playerExp());
        assertEquals(2, result.playerLevel());
        assertTrue(result.leveledUp());
        assertEquals(1, result.levelsGained());
        assertEquals(2, result.skillPointsGained());
        assertEquals(12, result.skillPoints());
        assertEquals(200, result.expToNextLevel());
    }

    @Test
    void applyVictoryExpCanGainMultipleLevels() {
        User user = user(1, 0, 10);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProgressionResult result = progressionService.applyVictoryExp(1L, 350);

        assertEquals(50, result.playerExp());
        assertEquals(3, result.playerLevel());
        assertEquals(2, result.levelsGained());
        assertEquals(4, result.skillPointsGained());
    }

    @Test
    void applyVictoryExpPersistsUser() {
        User user = user(1, 0, 10);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        progressionService.applyVictoryExp(1L, 15);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(15, captor.getValue().getExp());
    }

    @Test
    void applyExpLossReducesCurrentExpWithoutLevelDown() {
        User user = user(5, 80, 10);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int expLost = progressionService.applyExpLoss(user);

        assertEquals(8, expLost);
        assertEquals(72, user.getExp());
        assertEquals(5, user.getLevel());
    }

    @Test
    void calculateExpLossUsesTenPercentFloor() {
        assertEquals(0, ProgressionService.calculateExpLoss(0));
        assertEquals(1, ProgressionService.calculateExpLoss(15));
        assertEquals(9, ProgressionService.calculateExpLoss(90));
    }

    private static User user(int level, int exp, int skillPoints) {
        User user = new User();
        user.setLevel(level);
        user.setExp(exp);
        user.setSkillPoints(skillPoints);
        return user;
    }

    private static WildMonsterInstance monster(int level) {
        return new WildMonsterInstance(
                101,
                0,
                "wild_wolf",
                "野狼",
                com.dejebu.game.Element.NONE,
                level,
                com.dejebu.game.CharacterStats.zeroBase(),
                50,
                false
        );
    }
}
