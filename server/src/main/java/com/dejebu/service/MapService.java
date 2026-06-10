package com.dejebu.service;

import com.dejebu.game.MapTeleportTarget;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class MapService {

    private static final Set<String> WALKABLE_CHARS = Set.of(".", "P", "=", "@");

    private final ObjectMapper objectMapper;
    private final Map<String, List<String>> mapLines = new HashMap<>();
    private final Map<String, String> mapNames = new HashMap<>();
    private final Map<String, MapTeleportTarget> teleports = new HashMap<>();
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
