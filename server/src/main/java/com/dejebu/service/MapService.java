package com.dejebu.service;

import com.dejebu.game.DangerZone;
import com.dejebu.game.MapEncounterSettings;
import com.dejebu.game.MapTeleportTarget;
import com.dejebu.game.VisibleEnemySpawn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class MapService {

    private static final Set<String> WALKABLE_CHARS = Set.of(".", "P", "=", "@");
    private static final int MIN_VISIBLE_ENEMY_SPACING = 4;

    private final ObjectMapper objectMapper;
    private final Map<String, List<String>> mapLines = new HashMap<>();
    private final Map<String, String> mapNames = new HashMap<>();
    private final Map<String, MapTeleportTarget> teleports = new HashMap<>();
    private final Map<String, List<VisibleEnemySpawn>> visibleEnemies = new HashMap<>();
    private final Map<String, List<DangerZone>> dangerZones = new HashMap<>();
    private final Map<String, MapEncounterSettings> encounterSettings = new HashMap<>();
    private String defaultMapId = "village";

    public MapService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadMaps() throws IOException {
        ClassPathResource configResource = new ClassPathResource("maps/maps.json");
        JsonNode config;
        try (InputStream input = configResource.getInputStream()) {
            config = objectMapper.readTree(input);
        }

        defaultMapId = config.path("defaultMap").asText("village");

        JsonNode mapsNode = config.path("maps");
        mapsNode.fieldNames().forEachRemaining(mapId -> {
            JsonNode mapNode = mapsNode.get(mapId);
            String fileName = mapNode.path("file").asText(mapId + ".txt");
            mapNames.put(mapId, mapNode.path("name").asText(mapId));
            mapLines.put(mapId, readMapFile(fileName));
            visibleEnemies.put(mapId, parseVisibleEnemies(mapId, mapNode.path("visibleEnemies")));
            dangerZones.put(mapId, parseDangerZones(mapNode.path("dangerZones")));
            encounterSettings.put(mapId, parseEncounterSettings(mapNode));
        });

        JsonNode teleportsNode = config.path("teleports");
        teleportsNode.fieldNames().forEachRemaining(key -> {
            JsonNode target = teleportsNode.get(key);
            teleports.put(
                    key,
                    new MapTeleportTarget(
                            target.path("map").asText(),
                            target.path("x").asInt(),
                            target.path("y").asInt()
                    )
            );
        });
    }

    public String getDefaultMapId() {
        return defaultMapId;
    }

    public String getMapName(String mapId) {
        return mapNames.getOrDefault(mapId, mapId);
    }

    public boolean mapExists(String mapId) {
        return mapLines.containsKey(mapId);
    }

    public boolean isWalkable(String mapId, int x, int y) {
        List<String> lines = mapLines.get(mapId);
        if (lines == null || y < 0 || y >= lines.size()) {
            return false;
        }
        String row = lines.get(y);
        if (x < 0 || x >= row.length()) {
            return false;
        }
        return WALKABLE_CHARS.contains(String.valueOf(row.charAt(x)));
    }

    public Optional<MapTeleportTarget> resolveTeleport(String mapId, int x, int y) {
        return Optional.ofNullable(teleports.get(teleportKey(mapId, x, y)));
    }

    public List<VisibleEnemySpawn> getVisibleEnemies(String mapId) {
        return visibleEnemies.getOrDefault(mapId, List.of());
    }

    public List<DangerZone> getDangerZones(String mapId) {
        return dangerZones.getOrDefault(mapId, List.of());
    }

    public MapEncounterSettings getEncounterSettings(String mapId) {
        return encounterSettings.getOrDefault(mapId, MapEncounterSettings.DEFAULT);
    }

    public Set<String> getMapIds() {
        return Set.copyOf(mapLines.keySet());
    }

    public Optional<DangerZone> findDangerZoneAt(String mapId, int x, int y) {
        for (DangerZone zone : getDangerZones(mapId)) {
            if (zone.contains(x, y)) {
                return Optional.of(zone);
            }
        }
        return Optional.empty();
    }

    public boolean isInDangerZone(String mapId, int x, int y) {
        return findDangerZoneAt(mapId, x, y).isPresent();
    }

    private List<VisibleEnemySpawn> parseVisibleEnemies(String mapId, JsonNode enemiesNode) {
        if (!enemiesNode.isArray()) {
            return List.of();
        }
        List<VisibleEnemySpawn> spawns = new ArrayList<>();
        for (JsonNode enemyNode : enemiesNode) {
            String id = enemyNode.path("id").asText("");
            String templateId = enemyNode.path("templateId").asText("wild_wolf");
            int x = enemyNode.path("x").asInt();
            int y = enemyNode.path("y").asInt();
            int chaseRange = enemyNode.path("chaseRange").asInt(5);
            int loseRange = enemyNode.path("loseRange").asInt(8);
            if (id.isBlank()) {
                continue;
            }
            if (!isWalkable(mapId, x, y)) {
                throw new IllegalStateException("明雷出生點不可行走: " + mapId + " " + id);
            }
            spawns.add(new VisibleEnemySpawn(id, templateId, x, y, chaseRange, loseRange));
        }
        validateVisibleEnemySpacing(spawns);
        return spawns;
    }

    private void validateVisibleEnemySpacing(List<VisibleEnemySpawn> spawns) {
        for (int i = 0; i < spawns.size(); i++) {
            VisibleEnemySpawn a = spawns.get(i);
            for (int j = i + 1; j < spawns.size(); j++) {
                VisibleEnemySpawn b = spawns.get(j);
                int dist = Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
                if (dist < MIN_VISIBLE_ENEMY_SPACING) {
                    throw new IllegalStateException(
                            "明雷間距不足: " + a.id() + " 與 " + b.id() + " 距離 " + dist
                    );
                }
            }
        }
    }

    private MapEncounterSettings parseEncounterSettings(JsonNode mapNode) {
        int maxVisibleEnemies = mapNode.path("maxVisibleEnemies").asInt(MapEncounterSettings.DEFAULT.maxVisibleEnemies());
        int maxDarkEnemies = mapNode.path("maxDarkEnemies").asInt(MapEncounterSettings.DEFAULT.maxDarkEnemies());
        return new MapEncounterSettings(maxVisibleEnemies, maxDarkEnemies);
    }

    private List<DangerZone> parseDangerZones(JsonNode zonesNode) {
        if (!zonesNode.isArray()) {
            return List.of();
        }
        List<DangerZone> zones = new ArrayList<>();
        for (JsonNode zoneNode : zonesNode) {
            String id = zoneNode.path("id").asText("");
            if (id.isBlank()) {
                continue;
            }
            Set<DangerZone.DangerZoneCell> cells = new HashSet<>();
            JsonNode cellsNode = zoneNode.path("cells");
            if (cellsNode.isArray()) {
                for (JsonNode cellNode : cellsNode) {
                    cells.add(new DangerZone.DangerZoneCell(
                            cellNode.path("x").asInt(),
                            cellNode.path("y").asInt()
                    ));
                }
            }
            JsonNode rectNode = zoneNode.path("rect");
            if (rectNode.isObject()) {
                int x1 = rectNode.path("x1").asInt();
                int y1 = rectNode.path("y1").asInt();
                int x2 = rectNode.path("x2").asInt();
                int y2 = rectNode.path("y2").asInt();
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                        cells.add(new DangerZone.DangerZoneCell(x, y));
                    }
                }
            }
            if (!cells.isEmpty()) {
                zones.add(new DangerZone(id, cells));
            }
        }
        return zones;
    }

    private List<String> readMapFile(String fileName) {
        ClassPathResource resource = new ClassPathResource("maps/" + fileName);
        try (InputStream input = resource.getInputStream()) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return content.lines()
                    .filter(line -> !line.isEmpty())
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("無法讀取地圖檔案: " + fileName, ex);
        }
    }

    private String teleportKey(String mapId, int x, int y) {
        return mapId + ":" + x + "," + y;
    }
}
