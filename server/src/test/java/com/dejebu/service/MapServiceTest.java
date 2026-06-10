package com.dejebu.service;

import com.dejebu.game.MapTeleportTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
    void villagePortalIsWalkableAndLinksToForest() {
        assertTrue(mapService.isWalkable("village", 12, 6));

        Optional<MapTeleportTarget> teleport = mapService.resolveTeleport("village", 12, 6);
        assertTrue(teleport.isPresent());
        assertEquals("forest", teleport.get().mapId());
        assertEquals(9, teleport.get().x());
        assertEquals(3, teleport.get().y());
    }

    @Test
    void forestPortalReturnsToVillage() {
        assertTrue(mapService.isWalkable("forest", 9, 3));

        Optional<MapTeleportTarget> teleport = mapService.resolveTeleport("forest", 9, 3);
        assertTrue(teleport.isPresent());
        assertEquals("village", teleport.get().mapId());
        assertEquals(12, teleport.get().x());
        assertEquals(6, teleport.get().y());
    }

    @Test
    void wallsAreNotWalkable() {
        assertFalse(mapService.isWalkable("village", 0, 0));
        assertFalse(mapService.isWalkable("forest", 0, 1));
    }
}
