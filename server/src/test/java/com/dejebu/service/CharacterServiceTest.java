package com.dejebu.service;

import com.dejebu.entity.User;
import com.dejebu.game.CharacterStats;
import com.dejebu.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterServiceTest {

    @Mock
    private UserRepository userRepository;

    private CharacterService characterService;

    @BeforeEach
    void setUp() {
        characterService = new CharacterService(userRepository);
    }

    @Test
    void allocateStatPointSpendsOnePointAndIncreasesStat() {
        User user = userWithStats(3);
        user.setStatPoints(2);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = characterService.allocateStatPoint(user, "vitality");

        assertEquals(1, response.statPoints());
        assertEquals(4, response.stats().vitality());
        assertEquals(80, response.playerMaxHp());
        verify(userRepository).save(user);
    }

    @Test
    void allocateStatPointRejectsWhenNoPointsRemain() {
        User user = userWithStats(3);
        user.setStatPoints(0);

        assertThrows(IllegalArgumentException.class,
                () -> characterService.allocateStatPoint(user, "might"));
    }

    @Test
    void allocateStatPointRejectsInvalidStatCode() {
        User user = userWithStats(3);
        user.setStatPoints(1);

        assertThrows(IllegalArgumentException.class,
                () -> characterService.allocateStatPoint(user, "invalid"));
    }

    private static User userWithStats(int vitality) {
        User user = new User();
        user.setHasCharacter(true);
        user.setStatVitality(vitality);
        user.setPlayerCurrentHp(vitality * 20);
        user.setPlayerCurrentMp(0);
        return user;
    }
}
