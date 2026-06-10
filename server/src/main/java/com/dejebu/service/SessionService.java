package com.dejebu.service;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> playerNames = new ConcurrentHashMap<>();
    private final Map<String, Long> userIds = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session.getId());
        playerNames.remove(session.getId());
        userIds.remove(session.getId());
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
}
