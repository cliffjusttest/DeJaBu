package com.dejebu.service;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> playerNames = new ConcurrentHashMap<>();
    private final Map<String, Long> userIds = new ConcurrentHashMap<>();
    private final Map<String, PlayerPresence> presenceBySession = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session.getId());
        playerNames.remove(session.getId());
        userIds.remove(session.getId());
        presenceBySession.remove(session.getId());
    }

    public void bindUser(WebSocketSession session, Long userId, String displayName) {
        userIds.put(session.getId(), userId);
        playerNames.put(session.getId(), displayName);
    }

    public void setPlayerName(WebSocketSession session, String playerName) {
        playerNames.put(session.getId(), playerName);
    }

    public Optional<String> getPlayerName(WebSocketSession session) {
        return Optional.ofNullable(playerNames.get(session.getId()));
    }

    public Optional<Long> getUserId(WebSocketSession session) {
        return Optional.ofNullable(userIds.get(session.getId()));
    }

    public boolean isAuthenticated(WebSocketSession session) {
        return userIds.containsKey(session.getId());
    }

    public int getOnlineCount() {
        return sessions.size();
    }

    public void setPresence(WebSocketSession session,
                            Long playerId,
                            String playerName,
                            String mapId,
                            int x,
                            int y,
                            String direction,
                            String appearance,
                            int level) {
        presenceBySession.put(session.getId(), new PlayerPresence(
                playerId,
                playerName,
                mapId,
                x,
                y,
                direction,
                appearance,
                level
        ));
    }

    public void updatePresence(WebSocketSession session, String mapId, int x, int y, String direction) {
        PlayerPresence current = presenceBySession.get(session.getId());
        if (current == null) {
            return;
        }
        presenceBySession.put(session.getId(), new PlayerPresence(
                current.playerId(),
                current.playerName(),
                mapId,
                x,
                y,
                direction,
                current.appearance(),
                current.level()
        ));
    }

    public Optional<PlayerPresence> getPresence(WebSocketSession session) {
        return Optional.ofNullable(presenceBySession.get(session.getId()));
    }

    public List<Map.Entry<String, WebSocketSession>> getSessionsOnMap(String mapId) {
        List<Map.Entry<String, WebSocketSession>> result = new ArrayList<>();
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            PlayerPresence presence = presenceBySession.get(entry.getKey());
            if (presence != null && mapId.equals(presence.mapId())) {
                result.add(entry);
            }
        }
        return result;
    }

    public List<PlayerPresence> getOtherPlayersOnMap(String mapId, String excludeSessionId) {
        List<PlayerPresence> result = new ArrayList<>();
        for (Map.Entry<String, PlayerPresence> entry : presenceBySession.entrySet()) {
            if (entry.getKey().equals(excludeSessionId)) {
                continue;
            }
            PlayerPresence presence = entry.getValue();
            if (mapId.equals(presence.mapId())) {
                result.add(presence);
            }
        }
        return result;
    }
}
