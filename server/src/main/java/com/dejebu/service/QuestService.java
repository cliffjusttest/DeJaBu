package com.dejebu.service;

import com.dejebu.entity.PlayerQuestEntity;
import com.dejebu.entity.QuestEntity;
import com.dejebu.entity.User;
import com.dejebu.repository.PlayerQuestRepository;
import com.dejebu.repository.QuestRepository;
import com.dejebu.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
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
    public void acceptQuest(Long userId, Long questId) {
        if (playerQuestRepository.findByUserIdAndQuestId(userId, questId).isPresent()) {
            return;
        }
        PlayerQuestEntity pq = new PlayerQuestEntity();
        pq.setUserId(userId);
        pq.setQuestId(questId);
        pq.setStatus(PlayerQuestEntity.STATUS_IN_PROGRESS);
        pq.setProgress(0);
        playerQuestRepository.save(pq);
    }

    @Transactional
    public Optional<ClaimResult> claimReward(Long userId, Long questId) {
        Optional<PlayerQuestEntity> pqOpt = playerQuestRepository.findByUserIdAndQuestId(userId, questId);
        if (pqOpt.isEmpty()) return Optional.empty();

        PlayerQuestEntity pq = pqOpt.get();
        QuestEntity quest = questRepository.findById(questId).orElse(null);
        if (quest == null) return Optional.empty();

        if (!pq.isReadyToClaim(quest.getRequiredCount())) return Optional.empty();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("玩家不存在"));
        user.setExp(user.getExp() + quest.getRewardExp());
        user.setSkillPoints(user.getSkillPoints() + quest.getRewardSkillPoints());
        userRepository.save(user);

        pq.setStatus(PlayerQuestEntity.STATUS_COMPLETED);
        playerQuestRepository.save(pq);

        return Optional.of(new ClaimResult(quest.getName(), quest.getRewardExp(), quest.getRewardSkillPoints()));
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
                array.add(node);
            });
        }
        return array;
    }

    public Optional<PlayerQuestEntity> getPlayerQuest(Long userId, Long questId) {
        return playerQuestRepository.findByUserIdAndQuestId(userId, questId);
    }

    public record ClaimResult(String questName, int expGained, int skillPointsGained) {}
    public record KillProgress(String questName, int progress, int requiredCount, boolean readyToClaim) {}
}
