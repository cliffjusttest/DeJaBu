package com.dejebu.service;

import com.dejebu.entity.PlayerQuestEntity;
import com.dejebu.entity.QuestEntity;
import com.dejebu.entity.User;
import com.dejebu.game.StoryEra;
import com.dejebu.repository.PlayerQuestRepository;
import com.dejebu.repository.QuestRepository;
import com.dejebu.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class QuestService {

    private final QuestRepository questRepository;
    private final PlayerQuestRepository playerQuestRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public QuestService(QuestRepository questRepository,
                        PlayerQuestRepository playerQuestRepository,
                        UserRepository userRepository,
                        ObjectMapper objectMapper) {
        this.questRepository = questRepository;
        this.playerQuestRepository = playerQuestRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public boolean acceptQuest(Long userId, Long questId) {
        if (playerQuestRepository.findByUserIdAndQuestId(userId, questId).isPresent()) {
            return false;
        }
        if (!canAcceptQuest(userId, questId)) {
            return false;
        }
        PlayerQuestEntity pq = new PlayerQuestEntity();
        pq.setUserId(userId);
        pq.setQuestId(questId);
        pq.setStatus(PlayerQuestEntity.STATUS_IN_PROGRESS);
        pq.setProgress(0);
        playerQuestRepository.save(pq);
        return true;
    }

    @Transactional(readOnly = true)
    public boolean canAcceptQuest(Long userId, Long questId) {
        QuestEntity quest = questRepository.findById(questId).orElse(null);
        if (quest == null) {
            return false;
        }
        if (playerQuestRepository.findByUserIdAndQuestId(userId, questId).isPresent()) {
            return false;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        if (!meetsEraRequirement(user.getStoryEra(), quest.getRequiredEra())) {
            return false;
        }
        Long prerequisiteId = quest.getPrerequisiteQuestId();
        if (prerequisiteId == null) {
            return true;
        }
        return playerQuestRepository.findByUserIdAndQuestId(userId, prerequisiteId)
                .map(pq -> PlayerQuestEntity.STATUS_COMPLETED.equals(pq.getStatus()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canClaimQuest(Long userId, Long questId) {
        Optional<PlayerQuestEntity> pqOpt = playerQuestRepository.findByUserIdAndQuestId(userId, questId);
        if (pqOpt.isEmpty()) {
            return false;
        }
        QuestEntity quest = questRepository.findById(questId).orElse(null);
        if (quest == null) {
            return false;
        }
        return pqOpt.get().isReadyToClaim(quest.getRequiredCount());
    }

    @Transactional
    public Optional<ClaimResult> claimReward(Long userId, Long questId) {
        if (!canClaimQuest(userId, questId)) {
            return Optional.empty();
        }

        PlayerQuestEntity pq = playerQuestRepository.findByUserIdAndQuestId(userId, questId)
                .orElseThrow();
        QuestEntity quest = questRepository.findById(questId)
                .orElseThrow(() -> new IllegalStateException("任務不存在"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("玩家不存在"));
        user.setExp(user.getExp() + quest.getRewardExp());
        user.setSkillPoints(user.getSkillPoints() + quest.getRewardSkillPoints());
        advanceStoryEraIfNeeded(user, quest);
        userRepository.save(user);

        pq.setStatus(PlayerQuestEntity.STATUS_COMPLETED);
        playerQuestRepository.save(pq);

        return Optional.of(new ClaimResult(quest.getName(), quest.getRewardExp(), quest.getRewardSkillPoints()));
    }

    @Transactional
    public Map<Long, ClaimResult> claimRewardForReadyPartyMembers(Long actorUserId, Long questId, List<Long> partyMemberIds) {
        Map<Long, ClaimResult> results = new LinkedHashMap<>();
        for (Long memberId : partyMemberIds) {
            if (memberId.equals(actorUserId) || canClaimQuest(memberId, questId)) {
                claimReward(memberId, questId).ifPresent(reward -> results.put(memberId, reward));
            }
        }
        return results;
    }

    @Transactional
    public List<KillProgress> recordKills(Long userId, List<String> killedTemplateIds) {
        if (killedTemplateIds.isEmpty()) return List.of();

        List<KillProgress> results = new ArrayList<>();
        for (String templateId : killedTemplateIds) {
            List<QuestEntity> quests = questRepository.findByQuestTypeAndTargetId("KILL", templateId);
            for (QuestEntity quest : quests) {
                Optional<PlayerQuestEntity> pqOpt = playerQuestRepository.findByUserIdAndQuestId(userId, quest.getId());
                if (pqOpt.isEmpty()) continue;

                PlayerQuestEntity pq = pqOpt.get();
                if (!PlayerQuestEntity.STATUS_IN_PROGRESS.equals(pq.getStatus())) continue;
                if (pq.getProgress() >= quest.getRequiredCount()) continue;

                pq.setProgress(pq.getProgress() + 1);
                playerQuestRepository.save(pq);

                boolean readyToClaim = pq.getProgress() >= quest.getRequiredCount();
                results.add(new KillProgress(quest.getName(), pq.getProgress(), quest.getRequiredCount(), readyToClaim));
            }
        }
        return results;
    }

    @Transactional(readOnly = true)
    public ArrayNode getPlayerQuestsJson(Long userId) {
        List<PlayerQuestEntity> pqs = playerQuestRepository.findByUserId(userId);
        ArrayNode array = objectMapper.createArrayNode();
        for (PlayerQuestEntity pq : pqs) {
            questRepository.findById(pq.getQuestId()).ifPresent(quest -> {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("questId", quest.getId());
                node.put("name", quest.getName());
                node.put("description", quest.getDescription());
                node.put("type", quest.getQuestType());
                node.put("status", pq.getStatus());
                node.put("progress", pq.getProgress());
                node.put("requiredCount", quest.getRequiredCount());
                node.put("rewardExp", quest.getRewardExp());
                node.put("rewardSkillPoints", quest.getRewardSkillPoints());
                node.put("readyToClaim", pq.isReadyToClaim(quest.getRequiredCount()));
                node.put("requiredEra", quest.getRequiredEra());
                array.add(node);
            });
        }
        return array;
    }

    public Optional<PlayerQuestEntity> getPlayerQuest(Long userId, Long questId) {
        return playerQuestRepository.findByUserIdAndQuestId(userId, questId);
    }

    @Transactional(readOnly = true)
    public boolean isQuestAvailableForStoryUser(Long storyUserId, QuestEntity quest) {
        User user = userRepository.findById(storyUserId).orElse(null);
        if (user == null) {
            return false;
        }
        return meetsEraRequirement(user.getStoryEra(), quest.getRequiredEra());
    }

    private void advanceStoryEraIfNeeded(User user, QuestEntity quest) {
        String unlocksEra = quest.getUnlocksEra();
        if (unlocksEra == null || unlocksEra.isBlank()) {
            return;
        }
        StoryEra current = StoryEra.fromCode(user.getStoryEra());
        StoryEra unlocked = StoryEra.fromCode(unlocksEra);
        if (unlocked.ordinal() > current.ordinal()) {
            user.setStoryEra(unlocked.name());
        }
    }

    private boolean meetsEraRequirement(String playerEraCode, String requiredEraCode) {
        StoryEra playerEra = StoryEra.fromCode(playerEraCode);
        StoryEra requiredEra = StoryEra.fromCode(requiredEraCode);
        return playerEra.ordinal() >= requiredEra.ordinal();
    }

    public record ClaimResult(String questName, int expGained, int skillPointsGained) {}
    public record KillProgress(String questName, int progress, int requiredCount, boolean readyToClaim) {}
}
