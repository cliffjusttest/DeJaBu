package com.dejebu.service;

import com.dejebu.entity.DialogueNodeEntity;
import com.dejebu.entity.NpcEntity;
import com.dejebu.entity.PlayerQuestEntity;
import com.dejebu.entity.QuestEntity;
import com.dejebu.entity.User;
import com.dejebu.repository.DialogueNodeRepository;
import com.dejebu.repository.NpcRepository;
import com.dejebu.repository.QuestRepository;
import com.dejebu.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NpcService {

    private final NpcRepository npcRepository;
    private final DialogueNodeRepository dialogueNodeRepository;
    private final QuestRepository questRepository;
    private final QuestService questService;
    private final CompanionService companionService;
    private final UserRepository userRepository;
    private final StoryContextService storyContextService;
    private final PlayerPartyService playerPartyService;
    private final ObjectMapper objectMapper;

    public NpcService(NpcRepository npcRepository,
                      DialogueNodeRepository dialogueNodeRepository,
                      QuestRepository questRepository,
                      QuestService questService,
                      CompanionService companionService,
                      UserRepository userRepository,
                      StoryContextService storyContextService,
                      PlayerPartyService playerPartyService,
                      ObjectMapper objectMapper) {
        this.npcRepository = npcRepository;
        this.dialogueNodeRepository = dialogueNodeRepository;
        this.questRepository = questRepository;
        this.questService = questService;
        this.companionService = companionService;
        this.userRepository = userRepository;
        this.storyContextService = storyContextService;
        this.playerPartyService = playerPartyService;
        this.objectMapper = objectMapper;
    }

    public record DialogueOutcome(ObjectNode response, Map<Long, QuestService.ClaimResult> claimResultsByUser) {}

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
    public DialogueOutcome interact(Long actorUserId, String npcId, String mapId) {
        NpcEntity npc = npcRepository.findById(npcId)
                .orElseThrow(() -> new IllegalArgumentException("找不到 NPC: " + npcId));

        if (!npc.getMapId().equals(mapId)) {
            throw new IllegalArgumentException("NPC 不在當前地圖");
        }

        Long storyUserId = storyContextService.resolveStoryUserId(actorUserId);
        String nodeKey = resolveStartingNode(storyUserId, npc);
        ObjectNode response = buildDialogueResponse(npc, nodeKey, List.of(), actorUserId);
        return new DialogueOutcome(response, Map.of());
    }

    @Transactional
    public DialogueOutcome choose(Long actorUserId, String npcId, String nodeKey, int choiceIndex) {
        NpcEntity npc = npcRepository.findById(npcId)
                .orElseThrow(() -> new IllegalArgumentException("找不到 NPC: " + npcId));

        DialogueNodeEntity node = dialogueNodeRepository.findByNpcIdAndNodeKey(npcId, nodeKey)
                .orElseThrow(() -> new IllegalArgumentException("找不到對話節點: " + nodeKey));

        JsonNode choices = parseChoices(node.getChoicesJson());
        JsonNode choice = resolveVisibleChoice(choices, actorUserId, choiceIndex);

        Map<Long, QuestService.ClaimResult> claimResultsByUser = new LinkedHashMap<>();

        if (choice.has("questAccept") && !choice.get("questAccept").isNull()) {
            long questId = choice.get("questAccept").asLong();
            questService.acceptQuest(actorUserId, questId);
        }

        if (choice.has("questComplete") && !choice.get("questComplete").isNull()) {
            long questId = choice.get("questComplete").asLong();
            List<Long> partyMembers = playerPartyService.getMemberIdsIfInParty(actorUserId);
            claimResultsByUser = questService.claimRewardForReadyPartyMembers(actorUserId, questId, partyMembers);
        }

        if (choice.has("action") && "open_shop".equals(choice.get("action").asText())) {
            return new DialogueOutcome(buildShopOpenResponse(npc), Map.of());
        }

        if (choice.has("action") && "hospital_revive".equals(choice.get("action").asText())) {
            if (!npc.isHospital()) {
                throw new IllegalArgumentException("此 NPC 無法提供治療");
            }
            return new DialogueOutcome(buildHospitalReviveResponse(npc, actorUserId), Map.of());
        }

        JsonNode nextKeyNode = choice.get("nextKey");
        if (nextKeyNode == null || nextKeyNode.isNull()) {
            List<QuestService.ClaimResult> actorRewards = claimResultsByUser.containsKey(actorUserId)
                    ? List.of(claimResultsByUser.get(actorUserId))
                    : List.of();
            return new DialogueOutcome(buildFinishedResponse(npc, actorRewards, actorUserId), claimResultsByUser);
        }

        String nextKey = nextKeyNode.asText();
        List<QuestService.ClaimResult> actorRewards = claimResultsByUser.containsKey(actorUserId)
                ? List.of(claimResultsByUser.get(actorUserId))
                : List.of();
        return new DialogueOutcome(buildDialogueResponse(npc, nextKey, actorRewards, actorUserId), claimResultsByUser);
    }

    public ObjectNode personalizeForViewer(ObjectNode response, Long viewerUserId, Map<Long, QuestService.ClaimResult> claimResultsByUser) {
        ObjectNode personalized = response.deepCopy();
        personalized.put("observer", !storyContextService.isStoryActor(viewerUserId));

        if (personalized.path("observer").asBoolean(false)) {
            personalized.remove("openShop");
            personalized.remove("hospitalRevive");
        }

        if (!personalized.path("finished").asBoolean(false) || claimResultsByUser.isEmpty()) {
            return personalized;
        }

        QuestService.ClaimResult viewerReward = claimResultsByUser.get(viewerUserId);
        if (viewerReward == null) {
            personalized.remove("questRewards");
            return personalized;
        }

        ArrayNode rewardsArray = objectMapper.createArrayNode();
        ObjectNode rNode = objectMapper.createObjectNode();
        rNode.put("questName", viewerReward.questName());
        rNode.put("expGained", viewerReward.expGained());
        rNode.put("skillPointsGained", viewerReward.skillPointsGained());
        rewardsArray.add(rNode);
        personalized.set("questRewards", rewardsArray);
        return personalized;
    }

    private JsonNode parseChoices(String choicesJson) {
        try {
            JsonNode choices = objectMapper.readTree(choicesJson);
            if (!choices.isArray()) {
                throw new IllegalStateException("對話資料錯誤");
            }
            return choices;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception e) {
            throw new IllegalStateException("對話資料錯誤");
        }
    }

    private JsonNode resolveVisibleChoice(JsonNode choices, Long actorUserId, int visibleChoiceIndex) {
        int visible = 0;
        for (JsonNode choice : choices) {
            if (!isChoiceAvailable(choice, actorUserId)) {
                continue;
            }
            if (visible == visibleChoiceIndex) {
                return choice;
            }
            visible++;
        }
        throw new IllegalArgumentException("無效的選項");
    }

    private String resolveStartingNode(Long storyUserId, NpcEntity npc) {
        List<QuestEntity> giverQuests = questRepository.findByGiverNpcId(npc.getId());
        for (QuestEntity quest : giverQuests) {
            if (!questService.isQuestAvailableForStoryUser(storyUserId, quest)) {
                continue;
            }
            Optional<PlayerQuestEntity> pqOpt = questService.getPlayerQuest(storyUserId, quest.getId());
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

    private ObjectNode buildDialogueResponse(NpcEntity npc, String nodeKey, List<QuestService.ClaimResult> rewards, Long actorUserId) {
        DialogueNodeEntity node = dialogueNodeRepository.findByNpcIdAndNodeKey(npc.getId(), nodeKey)
                .orElseThrow(() -> new IllegalStateException("找不到對話節點: " + nodeKey));

        ObjectNode response = objectMapper.createObjectNode();
        response.put("finished", false);
        response.put("npcId", npc.getId());
        response.put("npcName", npc.getName());
        response.put("nodeKey", nodeKey);
        response.put("text", node.getText());
        response.put("observer", !storyContextService.isStoryActor(actorUserId));

        ArrayNode choicesArray = objectMapper.createArrayNode();
        JsonNode raw = parseChoices(node.getChoicesJson());
        int idx = 0;
        for (JsonNode c : raw) {
            if (!isChoiceAvailable(c, actorUserId)) {
                continue;
            }
            ObjectNode choiceNode = objectMapper.createObjectNode();
            choiceNode.put("index", idx++);
            choiceNode.put("text", c.path("text").asText());
            choicesArray.add(choiceNode);
        }

        response.set("choices", choicesArray);
        appendRewards(response, rewards);
        return response;
    }

    private boolean isChoiceAvailable(JsonNode choice, Long actorUserId) {
        if (choice.has("questAccept") && !choice.get("questAccept").isNull()) {
            long questId = choice.get("questAccept").asLong();
            return questService.canAcceptQuest(actorUserId, questId);
        }
        if (choice.has("questComplete") && !choice.get("questComplete").isNull()) {
            long questId = choice.get("questComplete").asLong();
            return questService.canClaimQuest(actorUserId, questId);
        }
        return true;
    }

    private ObjectNode buildFinishedResponse(NpcEntity npc, List<QuestService.ClaimResult> rewards, Long actorUserId) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("finished", true);
        response.put("npcId", npc.getId());
        response.put("npcName", npc.getName());
        response.put("message", "再見！");
        response.put("observer", !storyContextService.isStoryActor(actorUserId));
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

    private ObjectNode buildHospitalReviveResponse(NpcEntity npc, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("玩家不存在"));
        CompanionService.HospitalReviveResult reviveResult = companionService.reviveCompanionsAtHospital(user);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("finished", true);
        response.put("hospitalRevive", reviveResult.revived());
        response.put("npcId", npc.getId());
        response.put("npcName", npc.getName());
        response.put("message", reviveResult.message());
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
