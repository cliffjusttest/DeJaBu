package com.dejebu.websocket;

import com.dejebu.entity.User;
import com.dejebu.game.BattleFormation;
import com.dejebu.game.CharacterStats;
import com.dejebu.game.Element;
import com.dejebu.protocol.GameMessage;
import com.dejebu.protocol.MessageType;
import com.dejebu.game.MapTeleportTarget;
import com.dejebu.service.AuthService;
import com.dejebu.service.BattleService;
import com.dejebu.service.EncounterService;
import com.dejebu.service.EncounterService.PendingEncounter;
import com.dejebu.service.MapService;
import com.dejebu.service.NpcService;
import com.dejebu.service.PlayerPartyService;
import com.dejebu.service.ProgressionService;
import com.dejebu.service.DangerZoneService;
import com.dejebu.service.EncounterCooldownService;
import com.dejebu.service.EquipmentService;
import com.dejebu.service.PlayerPresence;
import com.dejebu.service.QuestService;
import com.dejebu.service.SessionService;
import com.dejebu.service.VisibleEnemyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final SessionService sessionService;
    private final AuthService authService;
    private final BattleService battleService;
    private final EncounterService encounterService;
    private final MapService mapService;
    private final NpcService npcService;
    private final QuestService questService;
    private final EquipmentService equipmentService;
    private final PlayerPartyService playerPartyService;
    private final VisibleEnemyService visibleEnemyService;
    private final EncounterCooldownService encounterCooldownService;
    private final DangerZoneService dangerZoneService;

    public GameWebSocketHandler(ObjectMapper objectMapper,
                                SessionService sessionService,
                                AuthService authService,
                                BattleService battleService,
                                EncounterService encounterService,
                                MapService mapService,
                                NpcService npcService,
                                QuestService questService,
                                EquipmentService equipmentService,
                                PlayerPartyService playerPartyService,
                                VisibleEnemyService visibleEnemyService,
                                EncounterCooldownService encounterCooldownService,
                                DangerZoneService dangerZoneService) {
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        this.authService = authService;
        this.battleService = battleService;
        this.encounterService = encounterService;
        this.mapService = mapService;
        this.npcService = npcService;
        this.questService = questService;
        this.equipmentService = equipmentService;
        this.playerPartyService = playerPartyService;
        this.visibleEnemyService = visibleEnemyService;
        this.encounterCooldownService = encounterCooldownService;
        this.dangerZoneService = dangerZoneService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionService.register(session);
        log.info("Client connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        GameMessage incoming = objectMapper.readValue(message.getPayload(), GameMessage.class);
        log.debug("Received {} from {}", incoming.getType(), session.getId());

        GameMessage response = switch (incoming.getType()) {
            case PING -> pong();
            case LOGIN -> handleLogin(session, incoming.getPayload());
            case MOVE -> handleMove(session, incoming.getPayload());
            case BATTLE_START -> handleBattleStart(session);
            case BATTLE_ACTION -> handleBattleAction(session, incoming.getPayload());
            case NPC_INTERACT -> handleNpcInteract(session, incoming.getPayload());
            case DIALOGUE_CHOICE -> handleDialogueChoice(session, incoming.getPayload());
            case QUEST_LIST -> handleQuestList(session);
            case PARTY_INVITE -> handlePartyInvite(session, incoming.getPayload());
            case PARTY_ACCEPT -> handlePartyAccept(session);
            case PARTY_DECLINE -> handlePartyDecline(session);
            case PARTY_LEAVE -> handlePartyLeave(session);
            case PARTY_KICK -> handlePartyKick(session, incoming.getPayload());
            default -> error("Unsupported message type: " + incoming.getType());
        };

        send(session, response);
    }

	@Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionService.getUserId(session).ifPresent(userId -> {
            if (playerPartyService.isInParty(userId)) {
                encounterService.clearEncounter(playerPartyService.partyBattleId(playerPartyService.getLeaderId(userId)));
            }
            playerPartyService.onDisconnect(userId);
        });
        encounterService.clearEncounter(session.getId());
        sessionService.getPresence(session).ifPresent(presence ->
                broadcastPlayerLeave(session, presence.mapId(), presence.playerId()));
        sessionService.unregister(session);
        log.info("Client disconnected: {} ({})", session.getId(), status);
    }

    private GameMessage pong() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("message", "pong");
        return new GameMessage(MessageType.PONG, payload);
    }

    private GameMessage handleLogin(WebSocketSession session, JsonNode payload) {
        String token = payload.path("token").asText("");
        if (token.isBlank()) {
            return error("請先登入取得 token");
        }

        Optional<User> userOptional = authService.validateToken(token);
        if (userOptional.isEmpty()) {
            return error("登入已失效，請重新登入");
        }

        User user = userOptional.get();
        if (!user.isHasCharacter() || user.getElement() == null) {
            return error("請先創建角色");
        }

        sessionService.bindUser(session, user.getId(), user.getDisplayName());

        String appearanceCode = user.getAppearance() != null ? user.getAppearance().getCode() : null;
        sessionService.setPresence(
                session,
                user.getId(),
                user.getDisplayName(),
                user.getPlayerMapId(),
                user.getPlayerX(),
                user.getPlayerY(),
                "down",
                appearanceCode,
                user.getLevel()
        );

        ObjectNode response = objectMapper.createObjectNode();
        response.put("token", token);
        response.put("playerId", user.getId());
        response.put("playerName", user.getDisplayName());
        response.put("playerX", user.getPlayerX());
        response.put("playerY", user.getPlayerY());
        response.put("playerMapId", user.getPlayerMapId());
        response.put("playerLevel", user.getLevel());
        response.put("playerExp", user.getExp());
        response.put("expToNextLevel", ProgressionService.expToNextLevel(user.getLevel()));
        response.put("skillPoints", user.getSkillPoints());
        response.put("playerGold", user.getGold());
        response.put("playerElement", user.getElement().getCode());
        response.put("playerElementName", user.getElement().getDisplayName());
        if (user.getAppearance() != null) {
            response.put("playerAppearance", user.getAppearance().getCode());
        }
        if (user.isHasCharacter()) {
            response.set("playerStats", CharacterStats.fromUser(user).toJsonNode(objectMapper));
            response.put("playerCurrentHp", user.resolveCurrentHp());
            response.put("playerMaxHp", user.resolveMaxHp());
            response.put("playerCurrentMp", user.resolveCurrentMp());
            response.put("playerMaxMp", user.resolveMaxMp());
        }
        response.put("sessionId", session.getId());
        response.put("onlineCount", sessionService.getOnlineCount());
        response.set("otherPlayers", buildOtherPlayersArray(user.getPlayerMapId(), session.getId()));
        response.set("party", buildPartyStateJson(user.getId()));
        visibleEnemyService.ensureMapLoaded(user.getPlayerMapId());
        response.set("visibleEnemies", visibleEnemyService.buildEnemyArray(user.getPlayerMapId()));
        appendEncounterCooldown(response, user.getId());
        response.put("message", "歡迎回來，" + user.getDisplayName());
        broadcastPlayerJoin(session);
        return new GameMessage(MessageType.LOGIN_OK, response);
    }

    private GameMessage handleMove(WebSocketSession session, JsonNode payload) {
        if (!sessionService.isAuthenticated(session)) {
            return error("尚未登入");
        }

        Long userId = sessionService.getUserId(session).orElse(null);
        if (userId != null && playerPartyService.isInParty(userId) && !playerPartyService.isLeader(userId)) {
            return error("組隊時只有隊長可以移動");
        }

        int x = payload.path("x").asInt();
        int y = payload.path("y").asInt();
        String direction = payload.path("direction").asText("down");
        String mapId = payload.path("mapId").asText(mapService.getDefaultMapId());

        if (!mapService.mapExists(mapId)) {
            return error("未知地圖: " + mapId);
        }
        if (!mapService.isWalkable(mapId, x, y)) {
            return error("無法移動至該位置");
        }

        String resultMapId = mapId;
        int resultX = x;
        int resultY = y;
        boolean mapChanged = false;

        Optional<MapTeleportTarget> teleport = mapService.resolveTeleport(mapId, x, y);
        if (teleport.isPresent()) {
            MapTeleportTarget target = teleport.get();
            if (!mapService.mapExists(target.mapId()) || !mapService.isWalkable(target.mapId(), target.x(), target.y())) {
                return error("傳點目標無效");
            }
            resultMapId = target.mapId();
            resultX = target.x();
            resultY = target.y();
            mapChanged = true;
        }

        Optional<Long> userIdOptional = sessionService.getUserId(session);
        String previousMapId = sessionService.getPresence(session)
                .map(PlayerPresence::mapId)
                .orElse(mapId);
        String finalMapId = resultMapId;
        int finalX = resultX;
        int finalY = resultY;
        userIdOptional.ifPresent(uid -> authService.updatePlayerPosition(uid, finalMapId, finalX, finalY));
        sessionService.updatePresence(session, resultMapId, resultX, resultY, direction);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("x", resultX);
        response.put("y", resultY);
        response.put("mapId", resultMapId);
        response.put("mapChanged", mapChanged);
        response.put("direction", direction);
        response.put("playerName", sessionService.getPlayerName(session).orElse("旅人"));

        if (mapChanged) {
            response.put("message", "傳送至 " + mapService.getMapName(resultMapId));
            response.put("encounter", false);
            encounterService.clearEncounter(resolveEncounterKey(session, userIdOptional.orElse(null)));
            dangerZoneService.clear(resolveLeaderId(userIdOptional.orElse(null)));
            broadcastPlayerLeave(session, previousMapId, userIdOptional.orElse(null));
            broadcastPlayerJoin(session);
            response.set("otherPlayers", buildOtherPlayersArray(resultMapId, session.getId()));
            visibleEnemyService.ensureMapLoaded(resultMapId);
            response.set("visibleEnemies", visibleEnemyService.buildEnemyArray(resultMapId));
            response.put("inDangerZone", false);
            response.put("dangerValue", 0);
            userIdOptional.ifPresent(uid -> {
                if (playerPartyService.isLeader(uid)) {
                    syncPartyFollowers(session, uid, finalMapId, finalX, finalY, direction, true, false, null);
                }
            });
            return new GameMessage(MessageType.MOVE_OK, response);
        }

        Long leaderId = resolveLeaderId(userIdOptional.orElse(null));
        DangerZoneService.DangerUpdate dangerUpdate = dangerZoneService.updatePosition(
                leaderId, resultMapId, resultX, resultY, mapService
        );
        if (dangerUpdate.inDangerZone()) {
            dangerZoneService.addStepDanger(leaderId);
            dangerUpdate = new DangerZoneService.DangerUpdate(
                    true,
                    dangerUpdate.enteredDangerZone(),
                    dangerZoneService.getDangerValue(leaderId),
                    dangerUpdate.dangerZoneId()
            );
        }

        boolean encounter = false;
        String encounterMessage = null;
        String visibleEnemyId = null;
        String encounterKey = resolveEncounterKey(session, userIdOptional.orElse(null));
        ThreadLocalRandom random = ThreadLocalRandom.current();

        List<VisibleEnemyService.PlayerTarget> chaseTargets = buildChaseTargets(resultMapId);
        VisibleEnemyService.EncounterContactResult contact = visibleEnemyService.processMove(
                resultMapId,
                leaderId,
                resultX,
                resultY,
                chaseTargets,
                encounterCooldownService,
                battleService,
                playerPartyService
        );

        if (contact.triggered()) {
            encounter = true;
            visibleEnemyId = contact.enemyId();
            encounterMessage = "遭遇野生怪物！";
            ObjectNode encounterData = encounterService.createVisibleEncounter(
                    encounterKey,
                    contact.templateId(),
                    contact.enemyId(),
                    resultMapId,
                    random
            );
            response.set("wildMonsters", encounterData.get("wildMonsters"));
            response.put("visibleEnemyId", contact.enemyId());
            visibleEnemyService.releaseChaseTarget(leaderId);
        } else if (dangerUpdate.inDangerZone()
                && encounterCooldownService.canTriggerDarkEncounter(leaderId)) {
            int chance = dangerZoneService.rollDarkEncounterChance(dangerUpdate.dangerValue(), new DangerZoneService.ThreadLocalRandomHolder());
            if (chance > 0 && random.nextInt(100) < chance) {
                encounter = true;
                encounterMessage = "遭遇野生怪物！";
                ObjectNode encounterData = encounterService.createDarkEncounter(
                        encounterKey,
                        resultMapId,
                        random
                );
                response.set("wildMonsters", encounterData.get("wildMonsters"));
                response.put("fromDangerZone", true);
            }
        }

        response.put("encounter", encounter);
        if (encounterMessage != null) {
            response.put("message", encounterMessage);
        }
        if (!encounter) {
            encounterService.clearEncounter(encounterKey);
        }

        response.set("visibleEnemies", visibleEnemyService.buildEnemyArray(resultMapId));
        response.put("inDangerZone", dangerUpdate.inDangerZone());
        response.put("dangerValue", dangerUpdate.dangerValue());
        if (dangerUpdate.enteredDangerZone() && !encounter) {
            response.put("dangerZoneEntered", true);
            response.put("message", "這裡充滿危險氣息...");
        }
        appendEncounterCooldown(response, leaderId);
        broadcastVisibleEnemyUpdate(resultMapId);

        if (userIdOptional.isPresent() && playerPartyService.isLeader(userIdOptional.get())) {
            syncPartyFollowers(
                    session,
                    userIdOptional.get(),
                    resultMapId,
                    resultX,
                    resultY,
                    direction,
                    mapChanged,
                    encounter,
                    visibleEnemyId
            );
        }

        broadcastPlayerMove(session);
        return new GameMessage(MessageType.MOVE_OK, response);
    }

    private GameMessage handleBattleStart(WebSocketSession session) {
        if (!sessionService.isAuthenticated(session)) {
            return error("尚未登入");
        }

        try {
            Long userId = sessionService.getUserId(session)
                    .orElseThrow(() -> new IllegalStateException("尚未登入"));

            if (playerPartyService.isInParty(userId) && !playerPartyService.isLeader(userId)) {
                return error("只有隊長可以發起戰鬥");
            }

            String battleId = resolveBattleId(session, userId);
            String encounterKey = resolveEncounterKey(session, userId);
            Long leaderId = resolveLeaderId(userId);
            visibleEnemyService.releaseChaseTarget(leaderId);

            if (playerPartyService.isInParty(userId) && playerPartyService.isLeader(userId)) {
                Long partyLeaderId = playerPartyService.getLeaderId(userId);
                List<Long> memberIds = playerPartyService.getMemberIds(partyLeaderId);
                List<BattleService.PartyMemberBattleContext> members = new ArrayList<>();
                int partyIndex = 0;
                for (Long memberId : memberIds) {
                    WebSocketSession memberSession = sessionService.findSessionByUserId(memberId)
                            .orElseThrow(() -> new IllegalStateException("隊友尚未連線"));
                    String memberName = sessionService.getPlayerName(memberSession).orElse("旅人");
                    Element memberElement = authService.findUserElement(memberId).orElse(Element.FIRE);
                    CharacterStats baseStats = authService.findUserStats(memberId).orElse(CharacterStats.zeroBase());
                    CharacterStats equipBonus = equipmentService.getTotalEquipmentBonus(memberId);
                    CharacterStats memberStats = baseStats.withBonus(equipBonus);
                    int memberLevel = authService.findUserLevel(memberId).orElse(1);
                    User memberUser = authService.findUserById(memberId)
                            .orElseThrow(() -> new IllegalStateException("玩家不存在"));
                    members.add(new BattleService.PartyMemberBattleContext(
                            memberId,
                            memberSession.getId(),
                            memberName,
                            memberElement,
                            memberStats,
                            memberLevel,
                            memberUser.resolveCurrentHp(),
                            memberUser.resolveCurrentMp(),
                            partyIndex++
                    ));
                }

                PendingEncounter encounter = encounterService.consumeEncounter(encounterKey);
                battleService.startPartyBattle(
                        battleId,
                        session.getId(),
                        members,
                        encounter
                );

                ObjectNode response = objectMapper.createObjectNode();
                response.set("battle", battleService.getBattleSnapshot(battleId, userId));
                response.put("message", "進入組隊戰鬥！");
                GameMessage startMessage = new GameMessage(MessageType.BATTLE_START, response);

                for (BattleService.PartyMemberBattleContext member : members) {
                    if (member.sessionId().equals(session.getId())) {
                        continue;
                    }
                    sessionService.getSession(member.sessionId()).ifPresent(memberSession -> {
                        ObjectNode memberResponse = objectMapper.createObjectNode();
                        memberResponse.set("battle", battleService.getBattleSnapshot(battleId, member.userId()));
                        memberResponse.put("message", "隊長遭遇戰鬥，進入組隊戰鬥！");
                        sendQuiet(memberSession, new GameMessage(MessageType.BATTLE_START, memberResponse));
                    });
                }
                return startMessage;
            }

            String playerName = sessionService.getPlayerName(session).orElse("旅人");
            Element playerElement = authService.findUserElement(userId).orElse(Element.FIRE);
            CharacterStats baseStats = authService.findUserStats(userId).orElse(CharacterStats.zeroBase());
            CharacterStats equipBonus = equipmentService.getTotalEquipmentBonus(userId);
            CharacterStats playerStats = baseStats.withBonus(equipBonus);
            int playerLevel = authService.findUserLevel(userId).orElse(1);

            ObjectNode battle = battleService.startBattle(
                    battleId,
                    userId,
                    playerName,
                    playerElement,
                    playerStats,
                    playerLevel
            );
            ObjectNode response = objectMapper.createObjectNode();
            response.set("battle", battle);
            response.put("message", "進入戰鬥！");
            return new GameMessage(MessageType.BATTLE_START, response);
        } catch (RuntimeException ex) {
            log.warn("Battle start failed: {}", ex.getMessage());
            return error(ex.getMessage() != null ? ex.getMessage() : "戰鬥啟動失敗");
        }
    }

    private GameMessage handleBattleAction(WebSocketSession session, JsonNode payload) {
        if (!sessionService.isAuthenticated(session)) {
            return error("尚未登入");
        }

        try {
            Long userId = sessionService.getUserId(session)
                    .orElseThrow(() -> new IllegalStateException("尚未登入"));
            String battleId = resolveBattleId(session, userId);
            String action = payload.path("action").asText();
            Integer targetId = payload.has("targetId") ? payload.get("targetId").asInt() : null;
            Integer actorId = payload.has("actorId") ? payload.get("actorId").asInt() : null;
            Long skillId = payload.has("skillId") ? payload.get("skillId").asLong() : null;
            Long itemId = payload.has("itemId") ? payload.get("itemId").asLong() : null;
            ObjectNode result = battleService.resolveAction(battleId, userId, action, targetId, actorId, skillId, itemId);

            if (result.path("battleOver").asBoolean() && result.path("victory").asBoolean()) {
                JsonNode killedNode = result.get("killedTemplateIds");
                if (killedNode != null && killedNode.isArray()) {
                    List<String> templateIds = new ArrayList<>();
                    for (JsonNode id : killedNode) templateIds.add(id.asText());
                    for (Long memberId : playerPartyService.getMemberIdsIfInParty(userId)) {
                        List<QuestService.KillProgress> progress = questService.recordKills(memberId, templateIds);
                        if (!progress.isEmpty() && memberId.equals(userId)) {
                            ArrayNode progressArray = objectMapper.createArrayNode();
                            for (QuestService.KillProgress kp : progress) {
                                ObjectNode kpNode = objectMapper.createObjectNode();
                                kpNode.put("questName", kp.questName());
                                kpNode.put("progress", kp.progress());
                                kpNode.put("requiredCount", kp.requiredCount());
                                kpNode.put("readyToClaim", kp.readyToClaim());
                                progressArray.add(kpNode);
                            }
                            result.set("questProgress", progressArray);
                        }
                    }
                }
            }

            personalizeBattleResult(result, userId);
            applyDeathTeleportPresence(session, userId, result);
            broadcastBattleResultToOthers(session.getId(), battleId, userId, result);

            if (result.path("battleOver").asBoolean() || result.path("escaped").asBoolean()) {
                Long leaderId = playerPartyService.isInParty(userId)
                        ? playerPartyService.getLeaderId(userId)
                        : userId;
                String visibleEnemyId = result.has("visibleEnemyId")
                        ? result.get("visibleEnemyId").asText()
                        : null;
                boolean fromDangerZone = result.path("fromDangerZone").asBoolean();
                encounterCooldownService.applyBattleEndCooldown(leaderId, visibleEnemyId, fromDangerZone);
                if (fromDangerZone) {
                    dangerZoneService.addBattleDanger(leaderId);
                }
                visibleEnemyService.releaseChaseTarget(leaderId);
            }

            return new GameMessage(MessageType.BATTLE_RESULT, result);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return error(ex.getMessage());
        }
    }

    private GameMessage handleNpcInteract(WebSocketSession session, JsonNode payload) {
        if (!sessionService.isAuthenticated(session)) {
            return error("尚未登入");
        }
        try {
            String npcId = payload.path("npcId").asText("");
            if (npcId.isBlank()) return error("未指定 NPC");
            Long userId = sessionService.getUserId(session)
                    .orElseThrow(() -> new IllegalStateException("尚未登入"));
            String mapId = payload.path("mapId").asText("village");
            ObjectNode result = npcService.interact(userId, npcId, mapId);
            return new GameMessage(MessageType.NPC_INTERACT_OK, result);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return error(ex.getMessage());
        }
    }

    private GameMessage handleDialogueChoice(WebSocketSession session, JsonNode payload) {
        if (!sessionService.isAuthenticated(session)) {
            return error("尚未登入");
        }
        try {
            String npcId = payload.path("npcId").asText("");
            String nodeKey = payload.path("nodeKey").asText("");
            int choiceIndex = payload.path("choiceIndex").asInt(0);
            if (npcId.isBlank() || nodeKey.isBlank()) return error("對話參數不完整");
            Long userId = sessionService.getUserId(session)
                    .orElseThrow(() -> new IllegalStateException("尚未登入"));
            ObjectNode result = npcService.choose(userId, npcId, nodeKey, choiceIndex);
            return new GameMessage(MessageType.NPC_INTERACT_OK, result);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return error(ex.getMessage());
        }
    }

    private GameMessage handleQuestList(WebSocketSession session) {
        if (!sessionService.isAuthenticated(session)) {
            return error("尚未登入");
        }
        Long userId = sessionService.getUserId(session).orElse(null);
        if (userId == null) return error("尚未登入");
        ObjectNode response = objectMapper.createObjectNode();
        response.set("quests", questService.getPlayerQuestsJson(userId));
        return new GameMessage(MessageType.QUEST_LIST_OK, response);
    }

    private GameMessage error(String message) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("message", message);
        return new GameMessage(MessageType.ERROR, payload);
    }

    private void send(WebSocketSession session, GameMessage message) throws IOException {
        String json = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(json));
    }

    private void sendQuiet(WebSocketSession session, GameMessage message) {
        try {
            send(session, message);
        } catch (IOException ex) {
            log.warn("Failed to send {} to {}: {}", message.getType(), session.getId(), ex.getMessage());
        }
    }

    private ArrayNode buildOtherPlayersArray(String mapId, String excludeSessionId) {
        ArrayNode players = objectMapper.createArrayNode();
        for (PlayerPresence presence : sessionService.getOtherPlayersOnMap(mapId, excludeSessionId)) {
            players.add(presenceToJson(presence));
        }
        return players;
    }

    private ObjectNode presenceToJson(PlayerPresence presence) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("playerId", presence.playerId());
        node.put("playerName", presence.playerName());
        node.put("mapId", presence.mapId());
        node.put("x", presence.x());
        node.put("y", presence.y());
        node.put("direction", presence.direction());
        node.put("playerLevel", presence.level());
        if (presence.appearance() != null) {
            node.put("playerAppearance", presence.appearance());
        }
        return node;
    }

    private void broadcastToMap(String mapId, String excludeSessionId, GameMessage message) {
        for (var entry : sessionService.getSessionsOnMap(mapId)) {
            if (entry.getKey().equals(excludeSessionId)) {
                continue;
            }
            sendQuiet(entry.getValue(), message);
        }
    }

    private void broadcastPlayerJoin(WebSocketSession session) {
        sessionService.getPresence(session).ifPresent(presence -> {
            ObjectNode payload = presenceToJson(presence);
            broadcastToMap(presence.mapId(), session.getId(), new GameMessage(MessageType.PLAYER_JOIN, payload));
        });
    }

    private void broadcastPlayerLeave(WebSocketSession session, String mapId, Long playerId) {
        if (mapId == null || mapId.isBlank() || playerId == null) {
            return;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("playerId", playerId);
        payload.put("mapId", mapId);
        broadcastToMap(mapId, session.getId(), new GameMessage(MessageType.PLAYER_LEAVE, payload));
    }

    private void broadcastPlayerMove(WebSocketSession session) {
        sessionService.getPresence(session).ifPresent(presence -> {
            ObjectNode payload = presenceToJson(presence);
            broadcastToMap(presence.mapId(), session.getId(), new GameMessage(MessageType.PLAYER_MOVE, payload));
        });
    }

    private GameMessage handlePartyInvite(WebSocketSession session, JsonNode payload) {
        if (!sessionService.isAuthenticated(session)) {
            return error("尚未登入");
        }
        try {
            Long inviterId = sessionService.getUserId(session)
                    .orElseThrow(() -> new IllegalStateException("尚未登入"));
            long inviteeId = payload.path("playerId").asLong(0);
            if (inviteeId <= 0) {
                return error("請指定要邀請的玩家");
            }
            WebSocketSession inviteeSession = sessionService.findSessionByUserId(inviteeId)
                    .orElseThrow(() -> new IllegalArgumentException("對方不在線上"));
            validateSameMap(session, inviteeSession);

            playerPartyService.invite(inviterId, inviteeId);

            ObjectNode notify = objectMapper.createObjectNode();
            notify.put("inviterId", inviterId);
            notify.put("inviterName", sessionService.getPlayerName(session).orElse("旅人"));
            sendQuiet(inviteeSession, new GameMessage(MessageType.PARTY_INVITE_OK, notify));

            ObjectNode response = objectMapper.createObjectNode();
            response.put("message", "已送出組隊邀請");
            return new GameMessage(MessageType.PARTY_INVITE_OK, response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return error(ex.getMessage());
        }
    }

    private GameMessage handlePartyAccept(WebSocketSession session) {
        if (!sessionService.isAuthenticated(session)) {
            return error("尚未登入");
        }
        try {
            Long userId = sessionService.getUserId(session)
                    .orElseThrow(() -> new IllegalStateException("尚未登入"));
            Long inviterId = playerPartyService.getPendingInvite(userId)
                    .orElseThrow(() -> new IllegalArgumentException("沒有待接受的組隊邀請"));
            WebSocketSession inviterSession = sessionService.findSessionByUserId(inviterId)
                    .orElseThrow(() -> new IllegalArgumentException("邀請者不在線上"));
            validateSameMap(session, inviterSession);

            List<Long> memberIds = playerPartyService.acceptInvite(userId);
            Long leaderId = playerPartyService.getLeaderId(userId);
            broadcastPartyUpdate(memberIds, leaderId);

            ObjectNode response = objectMapper.createObjectNode();
            response.set("party", buildPartyStateJson(userId));
            response.put("message", "已加入組隊");
            return new GameMessage(MessageType.PARTY_ACCEPT_OK, response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return error(ex.getMessage());
        }
    }

    private GameMessage handlePartyDecline(WebSocketSession session) {
        if (!sessionService.isAuthenticated(session)) {
            return error("尚未登入");
        }
        Long userId = sessionService.getUserId(session).orElse(null);
        if (userId != null) {
            playerPartyService.declineInvite(userId);
        }
        ObjectNode response = objectMapper.createObjectNode();
        response.put("message", "已拒絕組隊邀請");
        return new GameMessage(MessageType.PARTY_DECLINE, response);
    }

    private GameMessage handlePartyLeave(WebSocketSession session) {
        if (!sessionService.isAuthenticated(session)) {
            return error("尚未登入");
        }
        try {
            Long userId = sessionService.getUserId(session)
                    .orElseThrow(() -> new IllegalStateException("尚未登入"));
            List<Long> remaining = playerPartyService.leave(userId);
            if (!remaining.isEmpty()) {
                broadcastPartyUpdate(remaining, playerPartyService.getLeaderId(remaining.get(0)));
            }
            ObjectNode soloParty = buildPartyStateJson(userId);
            sendQuiet(session, new GameMessage(MessageType.PARTY_UPDATE, soloParty));

            ObjectNode response = objectMapper.createObjectNode();
            response.set("party", soloParty);
            response.put("message", "已離開組隊");
            return new GameMessage(MessageType.PARTY_LEAVE_OK, response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return error(ex.getMessage());
        }
    }

    private GameMessage handlePartyKick(WebSocketSession session, JsonNode payload) {
        if (!sessionService.isAuthenticated(session)) {
            return error("尚未登入");
        }
        try {
            Long leaderId = sessionService.getUserId(session)
                    .orElseThrow(() -> new IllegalStateException("尚未登入"));
            long targetId = payload.path("playerId").asLong(0);
            if (targetId <= 0) {
                return error("請指定要踢出的玩家");
            }
            playerPartyService.kick(leaderId, targetId);
            List<Long> remaining = playerPartyService.getMemberIds(leaderId);
            broadcastPartyUpdate(remaining, leaderId);

            sessionService.findSessionByUserId(targetId).ifPresent(targetSession -> {
                ObjectNode soloParty = buildPartyStateJson(targetId);
                sendQuiet(targetSession, new GameMessage(MessageType.PARTY_UPDATE, soloParty));
            });

            ObjectNode response = objectMapper.createObjectNode();
            response.set("party", buildPartyStateJson(leaderId));
            response.put("message", "已將玩家移出隊伍");
            return new GameMessage(MessageType.PARTY_KICK, response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return error(ex.getMessage());
        }
    }

    private ObjectNode buildPartyStateJson(Long userId) {
        ObjectNode party = objectMapper.createObjectNode();
        boolean inParty = playerPartyService.isInParty(userId);
        party.put("inParty", inParty);
        party.put("isLeader", playerPartyService.isLeader(userId));
        party.put("maxSize", PlayerPartyService.MAX_PARTY_SIZE);
        party.put("maxCompanionsPerPlayer", BattleFormation.maxCompanionsPerPlayerInParty());

        ArrayNode members = objectMapper.createArrayNode();
        if (inParty) {
            Long leaderId = playerPartyService.getLeaderId(userId);
            party.put("leaderId", leaderId);
            for (Long memberId : playerPartyService.getMemberIds(leaderId)) {
                ObjectNode memberNode = objectMapper.createObjectNode();
                memberNode.put("playerId", memberId);
                sessionService.findSessionByUserId(memberId).ifPresent(memberSession ->
                        memberNode.put("playerName", sessionService.getPlayerName(memberSession).orElse("旅人")));
                authService.findUserLevel(memberId).ifPresent(level -> memberNode.put("playerLevel", level));
                memberNode.put("isLeader", memberId.equals(leaderId));
                members.add(memberNode);
            }
        }
        party.set("members", members);

        playerPartyService.getPendingInvite(userId).ifPresent(inviterId -> {
            party.put("pendingInviteFrom", inviterId);
            sessionService.findSessionByUserId(inviterId).ifPresent(inviterSession ->
                    party.put("pendingInviteFromName", sessionService.getPlayerName(inviterSession).orElse("旅人")));
        });

        return party;
    }

    private void broadcastPartyUpdate(List<Long> memberIds, Long leaderId) {
        for (Long memberId : memberIds) {
            sessionService.findSessionByUserId(memberId).ifPresent(memberSession -> {
                ObjectNode partyState = buildPartyStateJson(memberId);
                sendQuiet(memberSession, new GameMessage(MessageType.PARTY_UPDATE, partyState));
            });
        }
    }

    private void syncPartyFollowers(
            WebSocketSession leaderSession,
            Long leaderId,
            String mapId,
            int x,
            int y,
            String direction,
            boolean mapChanged,
            boolean encounter,
            String visibleEnemyId
    ) {
        for (Long memberId : playerPartyService.getMemberIds(leaderId)) {
            if (memberId.equals(leaderId)) {
                continue;
            }
            sessionService.findSessionByUserId(memberId).ifPresent(memberSession -> {
                authService.updatePlayerPosition(memberId, mapId, x, y);
                sessionService.updatePresence(memberSession, mapId, x, y, direction);

                ObjectNode syncPayload = objectMapper.createObjectNode();
                syncPayload.put("x", x);
                syncPayload.put("y", y);
                syncPayload.put("mapId", mapId);
                syncPayload.put("direction", direction);
                syncPayload.put("mapChanged", mapChanged);
                syncPayload.put("encounter", encounter);
                syncPayload.put("message", encounter ? "隊長遭遇野生怪物！" : "跟隨隊長移動");
                if (visibleEnemyId != null) {
                    syncPayload.put("visibleEnemyId", visibleEnemyId);
                }
                if (mapChanged) {
                    syncPayload.set("otherPlayers", buildOtherPlayersArray(mapId, memberSession.getId()));
                    syncPayload.set("visibleEnemies", visibleEnemyService.buildEnemyArray(mapId));
                }
                appendEncounterCooldown(syncPayload, leaderId);
                sendQuiet(memberSession, new GameMessage(MessageType.PARTY_SYNC, syncPayload));
                broadcastPlayerMove(memberSession);
            });
        }
    }

    private String resolveBattleId(WebSocketSession session, Long userId) {
        if (userId != null && playerPartyService.isInParty(userId)) {
            return playerPartyService.partyBattleId(playerPartyService.getLeaderId(userId));
        }
        return session.getId();
    }

    private String resolveEncounterKey(WebSocketSession session, Long userId) {
        if (userId != null && playerPartyService.isInParty(userId)) {
            return playerPartyService.partyBattleId(playerPartyService.getLeaderId(userId));
        }
        return session.getId();
    }

    private void personalizeBattleResult(ObjectNode result, Long userId) {
        String battleId = playerPartyService.isInParty(userId)
                ? playerPartyService.partyBattleId(playerPartyService.getLeaderId(userId))
                : null;

        if (battleId != null && result.has("battle")) {
            ObjectNode battleSnapshot = battleService.getBattleSnapshot(battleId, userId);
            if (battleSnapshot != null) {
                result.set("battle", battleSnapshot);
            }
        }

        if (result.has("playerExpResults")) {
            for (JsonNode entry : result.get("playerExpResults")) {
                if (entry.path("playerId").asLong() == userId) {
                    result.put("expGained", entry.path("expGained").asInt());
                    result.put("playerExp", entry.path("playerExp").asInt());
                    result.put("expToNextLevel", entry.path("expToNextLevel").asInt());
                    result.put("playerLevel", entry.path("playerLevel").asInt());
                    result.put("skillPoints", entry.path("skillPoints").asInt());
                    if (entry.path("leveledUp").asBoolean()) {
                        result.put("leveledUp", true);
                        result.put("previousLevel", entry.path("previousLevel").asInt());
                        result.put("levelsGained", entry.path("levelsGained").asInt());
                        result.put("skillPointsGained", entry.path("skillPointsGained").asInt());
                    }
                }
            }
        }

        if (result.has("lootResults")) {
            for (JsonNode entry : result.get("lootResults")) {
                if (entry.path("playerId").asLong() == userId) {
                    result.put("goldGained", entry.path("goldGained").asInt());
                    result.put("playerGold", entry.path("playerGold").asInt());
                    if (entry.has("itemDrops")) {
                        result.set("itemDrops", entry.get("itemDrops"));
                    }
                }
            }
        }

        if (result.has("companionExpResults")) {
            ArrayNode myCompanions = objectMapper.createArrayNode();
            for (JsonNode entry : result.get("companionExpResults")) {
                if (entry.path("playerId").asLong() == userId) {
                    myCompanions.add(entry);
                }
            }
            if (!myCompanions.isEmpty()) {
                result.set("companionExpResults", myCompanions);
            }
        }

        if (result.has("deathOutcomes")) {
            for (JsonNode entry : result.get("deathOutcomes")) {
                if (entry.path("playerId").asLong() == userId) {
                    result.set("deathResult", entry);
                    break;
                }
            }
        }
    }

    private void applyDeathTeleportPresence(WebSocketSession session, Long userId, ObjectNode result) {
        if (!result.has("deathResult")) {
            return;
        }
        JsonNode deathResult = result.get("deathResult");
        if (!deathResult.path("playerDied").asBoolean()) {
            return;
        }
        String mapId = deathResult.path("teleportMapId").asText("");
        if (mapId.isBlank()) {
            return;
        }
        int x = deathResult.path("teleportX").asInt();
        int y = deathResult.path("teleportY").asInt();
        sessionService.updatePresence(session, mapId, x, y, "down");
    }

    private void validateSameMap(WebSocketSession sessionA, WebSocketSession sessionB) {
        PlayerPresence presenceA = sessionService.getPresence(sessionA)
                .orElseThrow(() -> new IllegalStateException("無法確認你的位置"));
        PlayerPresence presenceB = sessionService.getPresence(sessionB)
                .orElseThrow(() -> new IllegalStateException("無法確認對方位置"));
        if (!presenceA.mapId().equals(presenceB.mapId())) {
            throw new IllegalArgumentException("須與對方在同一張地圖才能組隊");
        }
    }

    private void broadcastBattleResultToOthers(String requesterSessionId, String battleId, Long userId, ObjectNode result) {
        Set<String> sessionIds = new HashSet<>(battleService.getParticipantSessionIds(battleId));
        if (sessionIds.isEmpty() && playerPartyService.isInParty(userId)) {
            for (Long memberId : playerPartyService.getMemberIdsIfInParty(userId)) {
                sessionService.findSessionByUserId(memberId).ifPresent(s -> sessionIds.add(s.getId()));
            }
        }

        for (String sessionId : sessionIds) {
            if (sessionId.equals(requesterSessionId)) {
                continue;
            }
            sessionService.getSession(sessionId).ifPresent(otherSession -> {
                Long otherUserId = sessionService.getUserId(otherSession).orElse(null);
                if (otherUserId == null) {
                    return;
                }
                ObjectNode otherResult = result.deepCopy();
                personalizeBattleResult(otherResult, otherUserId);
                sendQuiet(otherSession, new GameMessage(MessageType.BATTLE_RESULT, otherResult));
            });
        }
    }

    private Long resolveLeaderId(Long userId) {
        if (userId == null) {
            return null;
        }
        if (playerPartyService.isInParty(userId)) {
            return playerPartyService.getLeaderId(userId);
        }
        return userId;
    }

    private List<VisibleEnemyService.PlayerTarget> buildChaseTargets(String mapId) {
        List<VisibleEnemyService.PlayerTarget> targets = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (var entry : sessionService.getSessionsOnMap(mapId)) {
            Optional<PlayerPresence> presenceOptional = sessionService.getPresence(entry.getValue());
            if (presenceOptional.isEmpty()) {
                continue;
            }
            PlayerPresence presence = presenceOptional.get();
            Long leaderId = resolveLeaderId(presence.playerId());
            if (leaderId == null || seen.contains(leaderId)) {
                continue;
            }
            seen.add(leaderId);
            targets.add(new VisibleEnemyService.PlayerTarget(leaderId, presence.x(), presence.y()));
        }
        return targets;
    }

    private void appendEncounterCooldown(ObjectNode node, Long leaderId) {
        EncounterCooldownService.CooldownSnapshot snapshot = encounterCooldownService.snapshot(leaderId);
        node.put("noVisibleEncounterMs", snapshot.noVisibleEncounterMs());
        node.put("chaseCooldownMs", snapshot.noChaseMs());
        node.put("darkEncounterCooldownMs", snapshot.noDarkEncounterMs());
        if (!snapshot.maskedVisibleEnemyMs().isEmpty()) {
            ObjectNode masked = objectMapper.createObjectNode();
            snapshot.maskedVisibleEnemyMs().forEach(masked::put);
            node.set("maskedVisibleEnemies", masked);
        }
    }

    private void broadcastVisibleEnemyUpdate(String mapId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("mapId", mapId);
        payload.set("visibleEnemies", visibleEnemyService.buildEnemyArray(mapId));
        broadcastToMap(mapId, null, new GameMessage(MessageType.VISIBLE_ENEMY_UPDATE, payload));
    }
}
