package com.dejebu.service;

import com.dejebu.entity.DialogueNodeEntity;
import com.dejebu.entity.NpcEntity;
import com.dejebu.entity.PlayerQuestEntity;
import com.dejebu.entity.QuestEntity;
import com.dejebu.repository.DialogueNodeRepository;
import com.dejebu.repository.NpcRepository;
import com.dejebu.repository.QuestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class NpcService {

    private final NpcRepository npcRepository;
    private final DialogueNodeRepository dialogueNodeRepository;
    private final QuestRepository questRepository;
    private final QuestService questService;
    private final ObjectMapper objectMapper;

    public NpcService(NpcRepository npcRepository,
                      DialogueNodeRepository dialogueNodeRepository,
                      QuestRepository questRepository,
                      QuestService questService,
                      ObjectMapper objectMapper) {
        this.npcRepository = npcRepository;
        this.dialogueNodeRepository = dialogueNodeRepository;
        this.questRepository = questRepository;
        this.questService = questService;
        this.objectMapper = objectMapper;
    }

    public boolean npcExistsInMap(String npcId, String mapId) {
        return npcRepository.existsByIdAndMapId(npcId, mapId);
    }

    public boolean hasNpcAt(String mapId, int x, int y) {
        return npcRepository.findByMapId(mapId).stream()
                .anyMatch(n -> n.getGridX() == x && n.getGridY() == y);
    }

    @Transactional(readOnly = true)
    public ArrayNode getNpcsJson(String mapId) {
        ArrayNode array = objectMapper.createArrayNode();
        for (NpcEntity npc : npcRepository.findByMapId(mapId)) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("id", npc.getId());
            node.put("name", npc.getName());
            node.put("x", npc.getGridX());
            node.put("y", npc.getGridY());
            node.put("spriteKey", npc.getSpriteKey());
            array.add(node);
        }
        return array;
    }

    @Transactional
    public ObjectNode interact(Long userId, String npcId, String mapId) {
        NpcEntity npc = npcRepository.findById(npcId)
                .orElseThrow(() -> new IllegalArgumentException("找不到 NPC: " + npcId));

        if (!npc.getMapId().equals(mapId)) {
            throw new IllegalArgumentException("NPC 不在當前地圖");
        }

        String nodeKey = resolveStartingNode(userId, npc);
        return buildDialogueResponse(npc, nodeKey, List.of());
    }

    @Transactional
    public ObjectNode choose(Long userId, String npcId, String nodeKey, int choiceIndex) {
        NpcEntity npc = npcRepository.findById(npcId)
                .orElseThrow(() -> new IllegalArgumentException("找不到 NPC: " + npcId));

        DialogueNodeEntity node = dialogueNodeRepository.findByNpcIdAndNodeKey(npcId, nodeKey)
                .orElseThrow(() -> new IllegalArgumentException("找不到對話節點: " + nodeKey));

        JsonNode choices;
        try {
            choices = objectMapper.readTree(node.getChoicesJson());
        } catch (Exception e) {
            throw new IllegalStateException("對話資料錯誤");
        }

        if (!choices.isArray() || choiceIndex < 0 || choiceIndex >= choices.size()) {
            throw new IllegalArgumentException("無效的選項");
        }

        JsonNode choice = choices.get(choiceIndex);
        List<QuestService.ClaimResult> rewards = List.of();

        if (choice.has("questAccept") && !choice.get("questAccept").isNull()) {
            long questId = choice.get("questAccept").asLong();
            questService.acceptQuest(userId, questId);
        }

        if (choice.has("questComplete") && !choice.get("questComplete").isNull()) {
            long questId = choice.get("questComplete").asLong();
            Optional<QuestService.ClaimResult> reward = questService.claimReward(userId, questId);
            rewards = reward.map(List::of).orElse(List.of());
        }

        if (choice.has("action") && "open_shop".equals(choice.get("action").asText())) {
            return buildShopOpenResponse(npc);
        }

        JsonNode nextKeyNode = choice.get("nextKey");
        if (nextKeyNode == null || nextKeyNode.isNull()) {
            return buildFinishedResponse(npc, rewards);
        }

        String nextKey = nextKeyNode.asText();
        return buildDialogueResponse(npc, nextKey, rewards);
    }

    private String resolveStartingNode(Long userId, NpcEntity npc) {
        List<QuestEntity> giverQuests = questRepository.findByGiverNpcId(npc.getId());
        for (QuestEntity quest : giverQuests) {
            Optional<PlayerQuestEntity> pqOpt = questService.getPlayerQuest(userId, quest.getId());
            if (pqOpt.isPresent()) {
                PlayerQuestEntity pq = pqOpt.get();
                if (PlayerQuestEntity.STATUS_COMPLETED.equals(pq.getStatus())) continue;
                if (pq.isReadyToClaim(quest.getRequiredCount())) {
                    return "quest_complete";
                }
                return "quest_already";
            }
        }
        return npc.getRootNodeKey();
    }

    private ObjectNode buildDialogueResponse(NpcEntity npc, String nodeKey, List<QuestService.ClaimResult> rewards) {
        DialogueNodeEntity node = dialogueNodeRepository.findByNpcIdAndNodeKey(npc.getId(), nodeKey)
                .orElseThrow(() -> new IllegalStateException("找不到對話節點: " + nodeKey));

        ObjectNode response = objectMapper.createObjectNode();
        response.put("finished", false);
        response.put("npcId", npc.getId());
        response.put("npcName", npc.getName());
        response.put("nodeKey", nodeKey);
        response.put("text", node.getText());

        ArrayNode choicesArray = objectMapper.createArrayNode();
        try {
            JsonNode raw = objectMapper.readTree(node.getChoicesJson());
            if (raw.isArray()) {
                int idx = 0;
                for (JsonNode c : raw) {
                    ObjectNode choiceNode = objectMapper.createObjectNode();
                    choiceNode.put("index", idx++);
                    choiceNode.put("text", c.path("text").asText());
                    choicesArray.add(choiceNode);
                }
            }
        } catch (Exception ignored) {}

        response.set("choices", choicesArray);
        appendRewards(response, rewards);
        return response;
    }

    private ObjectNode buildFinishedResponse(NpcEntity npc, List<QuestService.ClaimResult> rewards) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("finished", true);
        response.put("npcId", npc.getId());
        response.put("npcName", npc.getName());
        response.put("message", "再見！");
        appendRewards(response, rewards);
        return response;
    }

    private ObjectNode buildShopOpenResponse(NpcEntity npc) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("finished", true);
        response.put("openShop", true);
        response.put("npcId", npc.getId());
        response.put("npcName", npc.getName());
        response.put("message", "歡迎光臨！");
        return response;
    }

    private void appendRewards(ObjectNode response, List<QuestService.ClaimResult> rewards) {
        if (rewards.isEmpty()) return;
        ArrayNode rewardsArray = objectMapper.createArrayNode();
        for (QuestService.ClaimResult r : rewards) {
            ObjectNode rNode = objectMapper.createObjectNode();
            rNode.put("questName", r.questName());
            rNode.put("expGained", r.expGained());
            rNode.put("skillPointsGained", r.skillPointsGained());
            rewardsArray.add(rNode);
        }
        response.set("questRewards", rewardsArray);
    }
}
