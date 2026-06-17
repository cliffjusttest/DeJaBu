package com.dejebu.service;

import com.dejebu.entity.MonsterTemplateEntity;
import com.dejebu.game.Element;
import com.dejebu.game.MapEncounterSettings;
import com.dejebu.repository.MonsterTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncounterServiceTest {

    @Mock
    private MonsterTemplateRepository monsterTemplateRepository;

    @Mock
    private MapService mapService;

    private EncounterService encounterService;

    @BeforeEach
    void setUp() {
        encounterService = new EncounterService(monsterTemplateRepository, mapService, new ObjectMapper());
        when(mapService.getEncounterSettings(anyString())).thenReturn(MapEncounterSettings.DEFAULT);
    }

    @Test
    void visibleEncounterUsesTemplateLevelRange() {
        MonsterTemplateEntity wolf = template("wild_wolf", 4, 6, true, false, Set.of("forest"));
        when(monsterTemplateRepository.findById("wild_wolf")).thenReturn(Optional.of(wolf));

        var monsters = encounterService.createVisibleEncounter(
                "session-1",
                "wild_wolf",
                "forest_wolf_1",
                "forest",
                ThreadLocalRandom.current()
        ).get("wildMonsters");

        assertEquals(1, monsters.size());
        int level = monsters.get(0).get("level").asInt();
        assertTrue(level >= 4 && level <= 6);
    }

    @Test
    void darkEncounterOnlyUsesTemplatesAllowedOnMap() {
        MonsterTemplateEntity wolf = template("wild_wolf", 1, 5, false, true, Set.of("forest"));
        MonsterTemplateEntity wisp = template("shadow_wisp", 3, 8, false, true, Set.of("village"));
        when(monsterTemplateRepository.findByDarkSpawnTrue()).thenReturn(List.of(wolf, wisp));

        var monsters = encounterService.createDarkEncounter(
                "session-2",
                "forest",
                ThreadLocalRandom.current()
        ).get("wildMonsters");

        assertTrue(monsters.size() >= 1);
        for (int i = 0; i < monsters.size(); i++) {
            assertEquals("wild_wolf", monsters.get(i).get("templateId").asText());
        }
    }

    @Test
    void rejectVisibleTemplateNotConfiguredForMap() {
        MonsterTemplateEntity wolf = template("wild_wolf", 1, 5, true, false, Set.of("village"));
        when(monsterTemplateRepository.findById("wild_wolf")).thenReturn(Optional.of(wolf));

        assertThrows(
                IllegalStateException.class,
                () -> encounterService.requireVisibleSpawnTemplate("wild_wolf", "forest")
        );
    }

    private MonsterTemplateEntity template(
            String id,
            int minLevel,
            int maxLevel,
            boolean visibleSpawn,
            boolean darkSpawn,
            Set<String> spawnMaps
    ) {
        MonsterTemplateEntity template = new MonsterTemplateEntity();
        setField(template, "id", id);
        setField(template, "name", id);
        setField(template, "element", Element.WIND);
        setField(template, "minLevel", minLevel);
        setField(template, "maxLevel", maxLevel);
        setField(template, "visibleSpawn", visibleSpawn);
        setField(template, "darkSpawn", darkSpawn);
        setField(template, "spawnMaps", spawnMaps);
        return template;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
