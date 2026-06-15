package com.dejebu.service;

import com.dejebu.entity.Item;
import com.dejebu.entity.MonsterDrop;
import com.dejebu.entity.MonsterTemplateEntity;
import com.dejebu.entity.User;
import com.dejebu.repository.MonsterDropRepository;
import com.dejebu.repository.MonsterTemplateRepository;
import com.dejebu.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class LootService {

    public record DroppedItem(Long itemId, String name, int quantity) {}

    public record BattleLootResult(int goldGained, int playerGold, List<DroppedItem> items) {}

    private final MonsterDropRepository monsterDropRepository;
    private final MonsterTemplateRepository monsterTemplateRepository;
    private final UserRepository userRepository;
    private final BackpackService backpackService;

    public LootService(
            MonsterDropRepository monsterDropRepository,
            MonsterTemplateRepository monsterTemplateRepository,
            UserRepository userRepository,
            BackpackService backpackService) {
        this.monsterDropRepository = monsterDropRepository;
        this.monsterTemplateRepository = monsterTemplateRepository;
        this.userRepository = userRepository;
        this.backpackService = backpackService;
    }

    @Transactional
    public BattleLootResult grantBattleLoot(Long userId, List<String> killedTemplateIds, ThreadLocalRandom random) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("找不到玩家"));

        int goldGained = 0;
        Map<Long, DroppedItem> aggregated = new LinkedHashMap<>();

        for (String templateId : killedTemplateIds) {
            goldGained += rollGold(templateId, random);

            for (MonsterDrop drop : monsterDropRepository.findByMonsterTemplate_Id(templateId)) {
                if (random.nextDouble() >= drop.getDropChance()) {
                    continue;
                }
                Item item = drop.getItem();
                backpackService.addItem(user, item, 1);
                aggregated.merge(
                        item.getId(),
                        new DroppedItem(item.getId(), item.getName(), 1),
                        (existing, added) -> new DroppedItem(
                                existing.itemId(), existing.name(), existing.quantity() + added.quantity())
                );
            }
        }

        if (goldGained > 0) {
            user.setGold(user.getGold() + goldGained);
            userRepository.save(user);
        }

        return new BattleLootResult(goldGained, user.getGold(), new ArrayList<>(aggregated.values()));
    }

    private int rollGold(String templateId, ThreadLocalRandom random) {
        return monsterTemplateRepository.findById(templateId)
                .map(template -> rollGoldForTemplate(template, random))
                .orElse(0);
    }

    private int rollGoldForTemplate(MonsterTemplateEntity template, ThreadLocalRandom random) {
        int min = template.getGoldDropMin();
        int max = template.getGoldDropMax();
        if (max <= 0) {
            return 0;
        }
        if (min >= max) {
            return min;
        }
        return random.nextInt(min, max + 1);
    }
}
