package com.dejebu.service;

import com.dejebu.game.VisibleEnemySpawn;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VisibleEnemyService {

    private final MapService mapService;
    private final ObjectMapper objectMapper;
    private final SessionService sessionService;
    private final Map<String, List<RuntimeEnemy>> enemiesByMap = new ConcurrentHashMap<>();

    public VisibleEnemyService(
            MapService mapService,
            ObjectMapper objectMapper,
            SessionService sessionService
    ) {
        this.mapService = mapService;
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
    }

    public void ensureMapLoaded(String mapId) {
        enemiesByMap.computeIfAbsent(mapId, this::createRuntimeEnemies);
    }

    public EncounterContactResult processMove(
            String mapId,
            Long movingLeaderId,
            int playerX,
            int playerY,
            List<PlayerTarget> targets,
            EncounterCooldownService cooldownService,
            BattleService battleService,
            PlayerPartyService partyService
    ) {
        ensureMapLoaded(mapId);
        List<RuntimeEnemy> enemies = enemiesByMap.get(mapId);
        if (enemies == null || enemies.isEmpty()) {
            return EncounterContactResult.none();
        }

        for (RuntimeEnemy enemy : enemies) {
            if (enemy.chaseTargetLeaderId != null) {
                boolean targetInBattle = isLeaderInBattle(enemy.chaseTargetLeaderId, battleService, partyService);
                PlayerTarget currentTarget = findTarget(targets, enemy.chaseTargetLeaderId);
                if (targetInBattle || currentTarget == null
                        || manhattan(enemy.x, enemy.y, currentTarget.x, currentTarget.y) > enemy.loseRange
                        || !cooldownService.canBeChased(enemy.chaseTargetLeaderId)) {
                    resetEnemy(enemy);
                }
            }

            if (enemy.chaseTargetLeaderId == null) {
                Optional<PlayerTarget> nearest = findNearestChasableTarget(
                        enemy, targets, cooldownService, battleService, partyService
                );
                nearest.ifPresent(target -> enemy.chaseTargetLeaderId = target.leaderId);
            }

            if (enemy.chaseTargetLeaderId != null) {
                PlayerTarget chaseTarget = findTarget(targets, enemy.chaseTargetLeaderId);
                if (chaseTarget != null) {
                    moveEnemyToward(enemy, chaseTarget.x, chaseTarget.y, mapId);
                    if (manhattan(enemy.x, enemy.y, chaseTarget.x, chaseTarget.y) <= 1
                            && movingLeaderId != null
                            && movingLeaderId.equals(chaseTarget.leaderId)
                            && cooldownService.canTriggerVisibleEncounter(movingLeaderId, enemy.id)) {
                        return new EncounterContactResult(true, enemy.id, enemy.templateId);
                    }
                }
            }
        }

        return EncounterContactResult.none();
    }

    public void releaseChaseTarget(Long leaderId) {
        for (List<RuntimeEnemy> enemies : enemiesByMap.values()) {
            for (RuntimeEnemy enemy : enemies) {
                if (enemy.chaseTargetLeaderId != null && enemy.chaseTargetLeaderId.equals(leaderId)) {
                    resetEnemy(enemy);
                }
            }
        }
    }

    public ArrayNode buildEnemyArray(String mapId) {
        ensureMapLoaded(mapId);
        ArrayNode array = objectMapper.createArrayNode();
        List<RuntimeEnemy> enemies = enemiesByMap.getOrDefault(mapId, List.of());
        for (RuntimeEnemy enemy : enemies) {
            array.add(toJson(enemy));
        }
        return array;
    }

    public ObjectNode toJson(RuntimeEnemy enemy) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", enemy.id);
        node.put("templateId", enemy.templateId);
        node.put("x", enemy.x);
        node.put("y", enemy.y);
        node.put("spawnX", enemy.spawnX);
        node.put("spawnY", enemy.spawnY);
        if (enemy.chaseTargetLeaderId != null) {
            node.put("chaseTargetId", enemy.chaseTargetLeaderId);
        }
        return node;
    }

    private List<RuntimeEnemy> createRuntimeEnemies(String mapId) {
        List<RuntimeEnemy> runtime = new ArrayList<>();
        for (VisibleEnemySpawn spawn : mapService.getVisibleEnemies(mapId)) {
            runtime.add(new RuntimeEnemy(
                    spawn.id(),
                    spawn.templateId(),
                    spawn.x(),
                    spawn.y(),
                    spawn.x(),
                    spawn.y(),
                    spawn.chaseRange(),
                    spawn.loseRange()
            ));
        }
        return runtime;
    }

    private void resetEnemy(RuntimeEnemy enemy) {
        enemy.chaseTargetLeaderId = null;
        enemy.x = enemy.spawnX;
        enemy.y = enemy.spawnY;
    }

    private Optional<PlayerTarget> findNearestChasableTarget(
            RuntimeEnemy enemy,
            List<PlayerTarget> targets,
            EncounterCooldownService cooldownService,
            BattleService battleService,
            PlayerPartyService partyService
    ) {
        return targets.stream()
                .filter(t -> !isLeaderInBattle(t.leaderId, battleService, partyService))
                .filter(t -> cooldownService.canBeChased(t.leaderId))
                .filter(t -> manhattan(enemy.x, enemy.y, t.x, t.y) <= enemy.chaseRange)
                .min(Comparator.comparingInt(t -> manhattan(enemy.x, enemy.y, t.x, t.y)));
    }

    private boolean isLeaderInBattle(Long leaderId, BattleService battleService, PlayerPartyService partyService) {
        if (partyService.isInParty(leaderId)) {
            return battleService.hasActiveBattle(partyService.partyBattleId(leaderId));
        }
        return sessionService.findSessionByUserId(leaderId)
                .map(session -> battleService.hasActiveBattle(session.getId()))
                .orElse(false);
    }

    private PlayerTarget findTarget(List<PlayerTarget> targets, Long leaderId) {
        for (PlayerTarget target : targets) {
            if (target.leaderId.equals(leaderId)) {
                return target;
            }
        }
        return null;
    }

    private void moveEnemyToward(RuntimeEnemy enemy, int targetX, int targetY, String mapId) {
        int dx = targetX - enemy.x;
        int dy = targetY - enemy.y;
        if (dx == 0 && dy == 0) {
            return;
        }

        int nextX = enemy.x;
        int nextY = enemy.y;
        if (Math.abs(dx) >= Math.abs(dy)) {
            nextX += dx > 0 ? 1 : -1;
        } else {
            nextY += dy > 0 ? 1 : -1;
        }

        if (mapService.isWalkable(mapId, nextX, nextY)) {
            enemy.x = nextX;
            enemy.y = nextY;
        } else if (mapService.isWalkable(mapId, enemy.x + (dx > 0 ? 1 : dx < 0 ? -1 : 0), enemy.y)) {
            enemy.x += dx > 0 ? 1 : -1;
        } else if (mapService.isWalkable(mapId, enemy.x, enemy.y + (dy > 0 ? 1 : dy < 0 ? -1 : 0))) {
            enemy.y += dy > 0 ? 1 : -1;
        }
    }

    private int manhattan(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    public record PlayerTarget(Long leaderId, int x, int y) {}

    public record EncounterContactResult(boolean triggered, String enemyId, String templateId) {
        public static EncounterContactResult none() {
            return new EncounterContactResult(false, null, null);
        }
    }

    static class RuntimeEnemy {
        final String id;
        final String templateId;
        final int spawnX;
        final int spawnY;
        final int chaseRange;
        final int loseRange;
        int x;
        int y;
        Long chaseTargetLeaderId;

        RuntimeEnemy(
                String id,
                String templateId,
                int spawnX,
                int spawnY,
                int x,
                int y,
                int chaseRange,
                int loseRange
        ) {
            this.id = id;
            this.templateId = templateId;
            this.spawnX = spawnX;
            this.spawnY = spawnY;
            this.x = x;
            this.y = y;
            this.chaseRange = chaseRange;
            this.loseRange = loseRange;
        }
    }
}
