package com.dejebu.service;

import com.dejebu.entity.PlayerQuestEntity;
import com.dejebu.entity.QuestEntity;
import com.dejebu.entity.User;
import com.dejebu.repository.PlayerQuestRepository;
import com.dejebu.repository.QuestRepository;
import com.dejebu.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestServiceTest {

    @Mock
    private QuestRepository questRepository;
    @Mock
    private PlayerQuestRepository playerQuestRepository;
    @Mock
    private UserRepository userRepository;

    private QuestService questService;

    @BeforeEach
    void setUp() {
        questService = new QuestService(
                questRepository,
                playerQuestRepository,
                userRepository,
                new ObjectMapper()
        );
    }

    @Test
    void canAcceptQuestRequiresMatchingEra() {
        User user = userWithEra("E1");
        QuestEntity quest = mockQuest(2L, "E1", null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(questRepository.findById(2L)).thenReturn(Optional.of(quest));
        when(playerQuestRepository.findByUserIdAndQuestId(1L, 2L)).thenReturn(Optional.empty());

        assertTrue(questService.canAcceptQuest(1L, 2L));
    }

    @Test
    void canAcceptQuestRejectsFutureEraRequirement() {
        User user = userWithEra("E1");
        QuestEntity quest = mockQuest(2L, "E3", null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(questRepository.findById(2L)).thenReturn(Optional.of(quest));
        when(playerQuestRepository.findByUserIdAndQuestId(1L, 2L)).thenReturn(Optional.empty());

        assertFalse(questService.canAcceptQuest(1L, 2L));
    }

    @Test
    void canAcceptQuestRequiresCompletedPrerequisite() {
        User user = userWithEra("E1");
        QuestEntity quest = mockQuest(2L, "E1", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(questRepository.findById(2L)).thenReturn(Optional.of(quest));
        when(playerQuestRepository.findByUserIdAndQuestId(1L, 2L)).thenReturn(Optional.empty());
        when(playerQuestRepository.findByUserIdAndQuestId(1L, 1L)).thenReturn(Optional.empty());

        assertFalse(questService.canAcceptQuest(1L, 2L));
    }

    @Test
    void acceptQuestDoesNotCreateRowWhenPrerequisiteMissing() {
        User user = userWithEra("E1");
        QuestEntity quest = mockQuest(2L, "E1", 1L);

        when(playerQuestRepository.findByUserIdAndQuestId(1L, 2L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(questRepository.findById(2L)).thenReturn(Optional.of(quest));
        when(playerQuestRepository.findByUserIdAndQuestId(1L, 1L)).thenReturn(Optional.empty());

        assertFalse(questService.acceptQuest(1L, 2L));
        verify(playerQuestRepository, never()).save(any(PlayerQuestEntity.class));
    }

    private static User userWithEra(String era) {
        User user = new User();
        user.setStoryEra(era);
        return user;
    }

    private static QuestEntity mockQuest(Long id, String requiredEra, Long prerequisiteId) {
        QuestEntity quest = mock(QuestEntity.class);
        when(quest.getId()).thenReturn(id);
        when(quest.getRequiredEra()).thenReturn(requiredEra);
        when(quest.getPrerequisiteQuestId()).thenReturn(prerequisiteId);
        return quest;
    }
}
