package com.dejebu.service;

import com.dejebu.game.DangerZone;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DangerZoneService {

    private final Map<Long, DangerState> states = new ConcurrentHashMap<>();

    public DangerUpdate updatePosition(Long leaderId, String mapId, int x, int y, MapService mapService) {
        if (leaderId == null) {
            return new DangerUpdate(false, false, 0, null);
        }
        Optional<DangerZone> zoneOptional = mapService.findDangerZoneAt(mapId, x, y);
        DangerState state = states.computeIfAbsent(leaderId, ignored -> new DangerState());

        if (zoneOptional.isEmpty()) {
            boolean wasInZone = state.inZone;
            state.inZone = false;
            state.dangerZoneId = null;
            state.dangerValue = 0;
            return new DangerUpdate(false, wasInZone, 0, null);
        }

        DangerZone zone = zoneOptional.get();
        boolean entered = !state.inZone || !zone.id().equals(state.dangerZoneId);
        state.inZone = true;
        state.dangerZoneId = zone.id();
        return new DangerUpdate(true, entered, state.dangerValue, zone.id());
    }

    public void addStepDanger(Long leaderId) {
        if (leaderId == null) {
            return;
        }
        DangerState state = states.get(leaderId);
        if (state != null && state.inZone) {
            state.dangerValue += 1;
        }
    }

    public void addBattleDanger(Long leaderId) {
        if (leaderId == null) {
            return;
        }
        DangerState state = states.get(leaderId);
        if (state != null && state.inZone) {
            state.dangerValue += 3;
        }
    }

    public int getDangerValue(Long leaderId) {
        DangerState state = states.get(leaderId);
        if (state == null || !state.inZone) {
            return 0;
        }
        return state.dangerValue;
    }

    public boolean isInDangerZone(Long leaderId) {
        DangerState state = states.get(leaderId);
        return state != null && state.inZone;
    }

    public int rollDarkEncounterChance(int dangerValue, ThreadLocalRandomHolder random) {
        int chance = Math.min(80, dangerValue * 3);
        return chance;
    }

    public void clear(Long leaderId) {
        if (leaderId != null) {
            states.remove(leaderId);
        }
    }

    private static class DangerState {
        private boolean inZone = false;
        private String dangerZoneId = null;
        private int dangerValue = 0;
    }

    public record DangerUpdate(
            boolean inDangerZone,
            boolean enteredDangerZone,
            int dangerValue,
            String dangerZoneId
    ) {}

    public static class ThreadLocalRandomHolder {
        public int nextInt(int bound) {
            return java.util.concurrent.ThreadLocalRandom.current().nextInt(bound);
        }
    }
}
