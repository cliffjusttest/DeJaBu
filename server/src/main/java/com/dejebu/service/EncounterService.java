package com.dejebu.service;

import com.dejebu.entity.MonsterTemplateEntity;
import com.dejebu.game.MapEncounterSettings;
import com.dejebu.game.MonsterStatsFactory;
import com.dejebu.game.WildMonsterInstance;
import com.dejebu.repository.MonsterTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EncounterService {

    private final MonsterTemplateRepository monsterTemplateRepository;
    private final MapService mapService;
    private final ObjectMapper objectMapper;
    private final Map<String, PendingEncounter> pendingEncounters = new ConcurrentHashMap<>();

    public EncounterService(
            MonsterTemplateRepository monsterTemplateRepository,
            MapService mapService,
            ObjectMapper objectMapper
    ) {
        this.monsterTemplateRepository = monsterTemplateRepository;
        this.mapService = mapService;
        this.objectMapper = objectMapper;
    }

    public ObjectNode createEncounter(String sessionId, String mapId, ThreadLocalRandom random) {
        MapEncounterSettings settings = mapService.getEncounterSettings(mapId);
        PendingEncounter encounter = new PendingEncounter(
                generateDarkMonsters(mapId, settings.maxDarkEnemies(), random),
                null,
                false
        );
        pendingEncounters.put(sessionId, encounter);
        return toEncounterJson(encounter);
    }

    public ObjectNode createVisibleEncounter(
            String sessionId,
            String templateId,
            String visibleEnemyId,
            String mapId,
            ThreadLocalRandom random
    ) {
        MapEncounterSettings settings = mapService.getEncounterSettings(mapId);
        PendingEncounter encounter = new PendingEncounter(
                generateVisibleMonsters(templateId, mapId, settings.maxVisibleEnemies(), random),
                visibleEnemyId,
                false
        );
        pendingEncounters.put(sessionId, encounter);
        return toEncounterJson(encounter);
    }

    public ObjectNode createDarkEncounter(String sessionId, String mapId, ThreadLocalRandom random) {
        MapEncounterSettings settings = mapService.getEncounterSettings(mapId);
        PendingEncounter encounter = new PendingEncounter(
                generateDarkMonsters(mapId, settings.maxDarkEnemies(), random),
                null,
                true
        );
        pendingEncounters.put(sessionId, encounter);
        return toEncounterJson(encounter);
    }

    public Optional<PendingEncounter> getEncounter(String sessionId) {
        return Optional.ofNullable(pendingEncounters.get(sessionId));
    }

    public PendingEncounter consumeEncounter(String sessionId) {
        PendingEncounter encounter = pendingEncounters.remove(sessionId);
        if (encounter == null) {
            throw new IllegalStateException("沒有待處理的野外遭遇");
        }
        return encounter;
    }

    public void clearEncounter(String sessionId) {
        pendingEncounters.remove(sessionId);
    }

    private List<WildMonsterInstance> generateVisibleMonsters(
            String templateId,
            String mapId,
            int maxCount,
            ThreadLocalRandom random
    ) {
        MonsterTemplateEntity template = requireVisibleSpawnTemplate(templateId, mapId);
        int count = rollEncounterCount(maxCount, random);
        List<WildMonsterInstance> monsters = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            monsters.add(createWildMonster(template, i, random));
        }
        return monsters;
    }

    private List<WildMonsterInstance> generateDarkMonsters(String mapId, int maxCount, ThreadLocalRandom random) {
        List<MonsterTemplateEntity> templates = findDarkSpawnTemplates(mapId);
        if (templates.isEmpty()) {
            throw new IllegalStateException("地圖沒有可生成的暗雷怪物: " + mapId);
        }
        int count = rollEncounterCount(maxCount, random);
        List<WildMonsterInstance> monsters = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MonsterTemplateEntity template = templates.get(random.nextInt(templates.size()));
            monsters.add(createWildMonster(template, i, random));
        }
        return monsters;
    }

    MonsterTemplateEntity requireVisibleSpawnTemplate(String templateId, String mapId) {
        MonsterTemplateEntity template = monsterTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalStateException("怪物模板不存在: " + templateId));
        if (!template.canVisibleSpawnOnMap(mapId)) {
            throw new IllegalStateException(
                    "怪物模板不可在此地圖作為明雷生成: " + templateId + " @ " + mapId
            );
        }
        return template;
    }

    List<MonsterTemplateEntity> findDarkSpawnTemplates(String mapId) {
        return monsterTemplateRepository.findByDarkSpawnTrue().stream()
                .filter(template -> template.canDarkSpawnOnMap(mapId))
                .toList();
    }

    private WildMonsterInstance createWildMonster(
            MonsterTemplateEntity template,
            int slot,
            ThreadLocalRandom random
    ) {
        int level = template.rollLevel(random);
        var stats = MonsterStatsFactory.statsForLevel(template, level);
        int maxHp = MonsterStatsFactory.maxHpForStats(stats);
        return new WildMonsterInstance(
                101 + slot,
                slot,
                template.getId(),
                template.getName(),
                template.getElement(),
                level,
                stats,
                maxHp,
                template.isCapturable()
        );
    }

    private int rollEncounterCount(int maxCount, ThreadLocalRandom random) {
        return random.nextInt(1, maxCount + 1);
    }

    private ObjectNode toEncounterJson(PendingEncounter encounter) {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode monsters = objectMapper.createArrayNode();
        for (WildMonsterInstance monster : encounter.monsters) {
            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("instanceId", monster.getInstanceId());
            entry.put("templateId", monster.getTemplateId());
            entry.put("name", monster.getName());
            entry.put("level", monster.getLevel());
            entry.put("hp", monster.getHp());
            entry.put("maxHp", monster.getMaxHp());
            entry.put("element", monster.getElement().getCode());
            entry.put("elementName", monster.getElement().getDisplayName());
            entry.put("capturable", monster.isCapturable());
            entry.set("stats", monster.getStats().toJsonNode(objectMapper));
            monsters.add(entry);
        }
        node.set("wildMonsters", monsters);
        return node;
    }

    public static class PendingEncounter {
        private final List<WildMonsterInstance> monsters;
        private final String visibleEnemyId;
        private final boolean fromDangerZone;

        public PendingEncounter(List<WildMonsterInstance> monsters, String visibleEnemyId, boolean fromDangerZone) {
            this.monsters = new ArrayList<>(monsters);
            this.visibleEnemyId = visibleEnemyId;
            this.fromDangerZone = fromDangerZone;
        }

        public List<WildMonsterInstance> getMonsters() {
            return monsters;
        }

        public String getVisibleEnemyId() {
            return visibleEnemyId;
        }

        public boolean isFromDangerZone() {
            return fromDangerZone;
        }
    }
}
