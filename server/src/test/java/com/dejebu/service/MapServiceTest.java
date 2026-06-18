package com.dejebu.service;

import com.dejebu.game.MapEncounterSettings;
import com.dejebu.game.MapTeleportTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapServiceTest {

    private MapService mapService;

    @BeforeEach
    void setUp() throws Exception {
        mapService = new MapService(new ObjectMapper());
        mapService.loadMaps();
    }

    @Test
    void defaultMapIsXuchang() {
        assertEquals("xuchang", mapService.getDefaultMapId());
    }

    @Test
    void worldMapCountMatchesDesign() {
        Set<String> mapIds = mapService.getMapIds();
        assertEquals(216, mapIds.size());
        assertTrue(mapIds.contains("luoyang"));
        assertTrue(mapIds.contains("jiankang"));
        assertTrue(mapIds.contains("chengdu"));
        assertFalse(mapIds.contains("village"));
        assertFalse(mapIds.contains("forest"));
    }

    @Test
    void xuchangLinksToSuburb() {
        assertTrue(mapService.isWalkable("xuchang", 1, 1));

        Optional<MapTeleportTarget> teleport = mapService.resolveTeleport("xuchang", 1, 1);
        assertTrue(teleport.isPresent());
        assertEquals("xuchang_suburb", teleport.get().mapId());
        assertEquals(25, teleport.get().x());
        assertEquals(18, teleport.get().y());
    }

    @Test
    void xuchangSuburbReturnsToXuchang() {
        assertTrue(mapService.isWalkable("xuchang_suburb", 25, 18));

        Optional<MapTeleportTarget> teleport = mapService.resolveTeleport("xuchang_suburb", 25, 18);
        assertTrue(teleport.isPresent());
        assertEquals("xuchang", teleport.get().mapId());
        assertEquals(1, teleport.get().x());
        assertEquals(1, teleport.get().y());
    }

    @Test
    void yingchuanSuburbHasTutorialEncounterLimits() {
        MapEncounterSettings settings = mapService.getEncounterSettings("yingchuan_suburb");
        assertEquals(2, settings.maxVisibleEnemies());
        assertEquals(3, settings.maxDarkEnemies());
    }

    @Test
    void rebelCampHasHigherEncounterLimits() {
        MapEncounterSettings settings = mapService.getEncounterSettings("rebel_camp_yingchuan");
        assertEquals(1, settings.maxVisibleEnemies());
        assertEquals(4, settings.maxDarkEnemies());
    }
}
