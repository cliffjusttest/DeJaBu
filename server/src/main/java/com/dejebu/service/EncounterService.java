package com.dejebu.service;

import com.dejebu.entity.MonsterTemplateEntity;
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

    private static final String[] ENCOUNTER_TEMPLATE_IDS = {"wild_wolf", "wild_wolf", "shadow_wisp"};
    private static final int[] ENCOUNTER_SLOTS = {0, 1, 2};

    private final MonsterTemplateRepository monsterTemplateRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, PendingEncounter> pendingEncounters = new ConcurrentHashMap<>();

    public EncounterService(MonsterTemplateRepository monsterTemplateRepository, ObjectMapper objectMapper) {
        this.monsterTemplateRepository = monsterTemplateRepository;
        this.objectMapper = objectMapper;
    }

    public ObjectNode createEncounter(String sessionId, int playerLevel, ThreadLocalRandom random) {
        PendingEncounter encounter = new PendingEncounter(generateMonsters(playerLevel, random));
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

    private List<WildMonsterInstance> generateMonsters(int playerLevel, ThreadLocalRandom random) {
        List<WildMonsterInstance> monsters = new ArrayList<>();
        for (int i = 0; i < ENCOUNTER_TEMPLATE_IDS.length; i++) {
            String templateId = ENCOUNTER_TEMPLATE_IDS[i];
            MonsterTemplateEntity template = monsterTemplateRepository.findById(templateId)
                    .orElseThrow(() -> new IllegalStateException("怪物模板不存在: " + templateId));
            int level = rollMonsterLevel(playerLevel, random);
            var stats = MonsterStatsFactory.statsForLevel(template, level);
            int maxHp = MonsterStatsFactory.maxHpForStats(stats);
            monsters.add(new WildMonsterInstance(
                    101 + i,
                    ENCOUNTER_SLOTS[i],
                    template.getId(),
                    template.getName(),
                    template.getElement(),
                    level,
                    stats,
                    maxHp,
                    template.isCapturable()
            ));
        }
        return monsters;
    }

    private int rollMonsterLevel(int playerLevel, ThreadLocalRandom random) {
        int offset = random.nextInt(-2, 4);
        return Math.max(1, playerLevel + offset);
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

        public PendingEncounter(List<WildMonsterInstance> monsters) {
            this.monsters = new ArrayList<>(monsters);
        }

        public List<WildMonsterInstance> getMonsters() {
            return monsters;
        }
    }
}
