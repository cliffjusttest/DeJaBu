package com.dejebu.service;

import com.dejebu.entity.MonsterTemplateEntity;
import com.dejebu.game.VisibleEnemySpawn;
import com.dejebu.repository.MonsterTemplateRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class SpawnConfigValidator implements ApplicationRunner {

    private final MapService mapService;
    private final EncounterService encounterService;
    private final MonsterTemplateRepository monsterTemplateRepository;

    public SpawnConfigValidator(
            MapService mapService,
            EncounterService encounterService,
            MonsterTemplateRepository monsterTemplateRepository
    ) {
        this.mapService = mapService;
        this.encounterService = encounterService;
        this.monsterTemplateRepository = monsterTemplateRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String mapId : mapService.getMapIds()) {
            validateVisibleEnemies(mapId);
            validateDarkSpawnTemplates(mapId);
        }
        validateTemplateLevelRanges();
    }

    private void validateVisibleEnemies(String mapId) {
        for (VisibleEnemySpawn spawn : mapService.getVisibleEnemies(mapId)) {
            encounterService.requireVisibleSpawnTemplate(spawn.templateId(), mapId);
        }
    }

    private void validateDarkSpawnTemplates(String mapId) {
        if (mapService.getDangerZones(mapId).isEmpty()) {
            return;
        }
        if (encounterService.findDarkSpawnTemplates(mapId).isEmpty()) {
            throw new IllegalStateException("地圖設有危險區但沒有可生成的暗雷怪物: " + mapId);
        }
    }

    private void validateTemplateLevelRanges() {
        for (MonsterTemplateEntity template : monsterTemplateRepository.findAll()) {
            if (template.getMinLevel() > template.getMaxLevel()) {
                throw new IllegalStateException("怪物模板等級範圍無效: " + template.getId());
            }
        }
    }
}
