package com.dejebu.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EncounterCooldownService {

    public static final long VISIBLE_ENEMY_MASK_MS = 20_000L;
    public static final long NO_VISIBLE_ENCOUNTER_MS = 5_000L;
    public static final long NO_CHASE_MS = 5_000L;
    public static final long NORMAL_DARK_COOLDOWN_MS = 5_000L;
    public static final long DANGER_ZONE_DARK_COOLDOWN_MS = 3_000L;

    private final Map<Long, CooldownState> states = new ConcurrentHashMap<>();

    public void applyBattleEndCooldown(Long leaderId, String visibleEnemyId, boolean fromDangerZone) {
        if (leaderId == null) {
            return;
        }
        CooldownState state = states.computeIfAbsent(leaderId, ignored -> new CooldownState());
        long now = System.currentTimeMillis();
        state.noVisibleEncounterUntil = now + NO_VISIBLE_ENCOUNTER_MS;
        state.noChaseUntil = now + NO_CHASE_MS;
        state.noDarkEncounterUntil = now + (
                fromDangerZone ? DANGER_ZONE_DARK_COOLDOWN_MS : NORMAL_DARK_COOLDOWN_MS
        );
        if (visibleEnemyId != null && !visibleEnemyId.isBlank()) {
            state.maskedVisibleEnemies.put(visibleEnemyId, now + VISIBLE_ENEMY_MASK_MS);
        }
    }

    public boolean canTriggerVisibleEncounter(Long leaderId, String enemyId) {
        if (leaderId == null) {
            return false;
        }
        CooldownState state = states.get(leaderId);
        if (state == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now < state.noVisibleEncounterUntil) {
            return false;
        }
        if (enemyId != null) {
            Long maskedUntil = state.maskedVisibleEnemies.get(enemyId);
            if (maskedUntil != null && now < maskedUntil) {
                return false;
            }
        }
        return true;
    }

    public boolean canBeChased(Long leaderId) {
        if (leaderId == null) {
            return false;
        }
        CooldownState state = states.get(leaderId);
        if (state == null) {
            return true;
        }
        return System.currentTimeMillis() >= state.noChaseUntil;
    }

    public boolean canTriggerDarkEncounter(Long leaderId) {
        if (leaderId == null) {
            return false;
        }
        CooldownState state = states.get(leaderId);
        if (state == null) {
            return true;
        }
        return System.currentTimeMillis() >= state.noDarkEncounterUntil;
    }

    public CooldownSnapshot snapshot(Long leaderId) {
        if (leaderId == null) {
            return new CooldownSnapshot(0, 0, 0, Map.of());
        }
        CooldownState state = states.get(leaderId);
        if (state == null) {
            return new CooldownSnapshot(0, 0, 0, Map.of());
        }
        long now = System.currentTimeMillis();
        Map<String, Long> masked = new ConcurrentHashMap<>();
        for (Map.Entry<String, Long> entry : state.maskedVisibleEnemies.entrySet()) {
            if (entry.getValue() > now) {
                masked.put(entry.getKey(), entry.getValue() - now);
            }
        }
        return new CooldownSnapshot(
                Math.max(0, state.noVisibleEncounterUntil - now),
                Math.max(0, state.noChaseUntil - now),
                Math.max(0, state.noDarkEncounterUntil - now),
                masked
        );
    }

    public void clear(Long leaderId) {
        if (leaderId != null) {
            states.remove(leaderId);
        }
    }

    private static class CooldownState {
        private long noVisibleEncounterUntil = 0L;
        private long noChaseUntil = 0L;
        private long noDarkEncounterUntil = 0L;
        private final Map<String, Long> maskedVisibleEnemies = new ConcurrentHashMap<>();
    }

    public record CooldownSnapshot(
            long noVisibleEncounterMs,
            long noChaseMs,
            long noDarkEncounterMs,
            Map<String, Long> maskedVisibleEnemyMs
    ) {}
}
