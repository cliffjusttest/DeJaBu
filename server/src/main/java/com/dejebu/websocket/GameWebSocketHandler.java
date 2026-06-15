package com.dejebu.websocket;

import com.dejebu.entity.User;
import com.dejebu.game.CharacterStats;
import com.dejebu.game.Element;
import com.dejebu.protocol.GameMessage;
import com.dejebu.protocol.MessageType;
import com.dejebu.game.MapTeleportTarget;
import com.dejebu.service.AuthService;
import com.dejebu.service.BattleService;
import com.dejebu.service.EncounterService;
import com.dejebu.service.MapService;
import com.dejebu.service.NpcService;
import com.dejebu.service.ProgressionService;
import com.dejebu.service.EquipmentService;
import com.dejebu.service.PlayerPresence;
import com.dejebu.service.QuestService;
import com.dejebu.service.SessionService;
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
import java.util.List;
import java.util.Optional;
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

    public GameWebSocketHandler(ObjectMapper objectMapper,
                                SessionService sessionService,
                                AuthService authService,
                                BattleService battleService,
                                EncounterService encounterService,
                                MapService mapService,
                                NpcService npcService,
                                QuestService questService,
                                EquipmentService equipmentService) {
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        this.authService = authService;
        this.battleService = battleService;
        this.encounterService = encounterService;
        this.mapService = mapService;
        this.npcService = npcService;
        this.questService = questService;
        this.equipmentService = equipmentService;
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
            default -> error("Unsupported message type: " + incoming.getType());
        };

        send(session, response);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
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
        response.put("message", "歡迎回來，" + user.getDisplayName());
        broadcastPlayerJoin(session);
        return new GameMessage(MessageType.LOGIN_OK, response);
    }

    private GameMessage handleMove(WebSocketSession session, JsonNode payload) {
        if (!sessionService.isAuthenticated(session)) {
            return error("尚未登入");
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
        userIdOptional.ifPresent(userId -> authService.updatePlayerPosition(userId, finalMapId, finalX, finalY));
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
            encounterService.clearEncounter(session.getId());
            broadcastPlayerLeave(session, previousMapId, userIdOptional.orElse(null));
            broadcastPlayerJoin(session);
            response.set("otherPlayers", buildOtherPlayersArray(resultMapId, session.getId()));
            return new GameMessage(MessageType.MOVE_OK, response);
        }

        boolean encounter = (x + y) % 5 == 0 && x != 0 && y != 0
                && !npcService.hasNpcAt(resultMapId, resultX, resultY);
        response.put("encounter", encounter);
        if (encounter) {
            int playerLevel = userIdOptional
                    .flatMap(authService::findUserLevel)
                    .orElse(1);
            ObjectNode encounterData = encounterService.createEncounter(
                    session.getId(),
                    playerLevel,
                    ThreadLocalRandom.current()
            );
            response.set("wildMonsters", encounterData.get("wildMonsters"));
            response.put("message", "遭遇野生怪物！");
        } else {
            encounterService.clearEncounter(session.getId());
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
            String playerName = sessionService.getPlayerName(session).orElse("旅人");
            Element playerElement = authService.findUserElement(userId).orElse(Element.FIRE);
            CharacterStats baseStats = authService.findUserStats(userId).orElse(CharacterStats.zeroBase());
            CharacterStats equipBonus = equipmentService.getTotalEquipmentBonus(userId);
            CharacterStats playerStats = baseStats.withBonus(equipBonus);
            int playerLevel = authService.findUserLevel(userId).orElse(1);

            ObjectNode battle = battleService.startBattle(
                    session.getId(),
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
            String action = payload.path("action").asText();
            Integer targetId = payload.has("targetId") ? payload.get("targetId").asInt() : null;
            Integer actorId = payload.has("actorId") ? payload.get("actorId").asInt() : null;
            Long skillId = payload.has("skillId") ? payload.get("skillId").asLong() : null;
            Long itemId = payload.has("itemId") ? payload.get("itemId").asLong() : null;
            ObjectNode result = battleService.resolveAction(session.getId(), action, targetId, actorId, skillId, itemId);

            if (result.path("battleOver").asBoolean() && result.path("victory").asBoolean()) {
                Optional<Long> userIdOpt = sessionService.getUserId(session);
                if (userIdOpt.isPresent()) {
                    JsonNode killedNode = result.get("killedTemplateIds");
                    if (killedNode != null && killedNode.isArray()) {
                        List<String> templateIds = new ArrayList<>();
                        for (JsonNode id : killedNode) templateIds.add(id.asText());
                        List<QuestService.KillProgress> progress = questService.recordKills(userIdOpt.get(), templateIds);
                        if (!progress.isEmpty()) {
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
}
