package com.dejebu.service;

import com.dejebu.dto.EquipmentResponse;
import com.dejebu.dto.ItemDto;
import com.dejebu.entity.CompanionEquipment;
import com.dejebu.entity.Item;
import com.dejebu.entity.User;
import com.dejebu.entity.UserEquipment;
import com.dejebu.game.CharacterStats;
import com.dejebu.game.EquipmentSlot;
import com.dejebu.repository.CompanionEquipmentRepository;
import com.dejebu.repository.ItemRepository;
import com.dejebu.repository.UserEquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EquipmentService {

    private final ItemRepository itemRepository;
    private final UserEquipmentRepository userEquipmentRepository;
    private final CompanionEquipmentRepository companionEquipmentRepository;

    public EquipmentService(
            ItemRepository itemRepository,
            UserEquipmentRepository userEquipmentRepository,
            CompanionEquipmentRepository companionEquipmentRepository
    ) {
        this.itemRepository = itemRepository;
        this.userEquipmentRepository = userEquipmentRepository;
        this.companionEquipmentRepository = companionEquipmentRepository;
    }

    @Transactional(readOnly = true)
    public EquipmentResponse getStatus(User user) {
        Map<String, ItemDto> equipped = buildEquippedMap(user.getId());
        List<ItemDto> available = itemRepository.findAll().stream()
                .filter(item -> item.getRequiredLevel() <= user.getLevel())
                .map(ItemDto::from)
                .toList();
        return new EquipmentResponse(equipped, available, "");
    }

    @Transactional
    public EquipmentResponse equip(User user, Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("裝備不存在"));
        if (item.getRequiredLevel() > user.getLevel()) {
            throw new IllegalArgumentException(
                    "等級不足，此裝備需要 Lv." + item.getRequiredLevel()
            );
        }

        // Replace any existing equipment in that slot
        userEquipmentRepository.findByIdUserIdAndIdSlot(user.getId(), item.getSlot())
                .ifPresent(userEquipmentRepository::delete);

        userEquipmentRepository.save(new UserEquipment(user, item));

        Map<String, ItemDto> equipped = buildEquippedMap(user.getId());
        List<ItemDto> available = itemRepository.findAll().stream()
                .filter(i -> i.getRequiredLevel() <= user.getLevel())
                .map(ItemDto::from)
                .toList();
        return new EquipmentResponse(equipped, available, "已裝備「" + item.getName() + "」");
    }

    @Transactional
    public EquipmentResponse unequip(User user, String slotName) {
        EquipmentSlot slot = parseSlot(slotName);
        userEquipmentRepository.deleteByIdUserIdAndIdSlot(user.getId(), slot);

        Map<String, ItemDto> equipped = buildEquippedMap(user.getId());
        List<ItemDto> available = itemRepository.findAll().stream()
                .filter(i -> i.getRequiredLevel() <= user.getLevel())
                .map(ItemDto::from)
                .toList();
        return new EquipmentResponse(equipped, available, "已卸下「" + slot.getDisplayName() + "」部位的裝備");
    }

    @Transactional(readOnly = true)
    public CharacterStats getTotalEquipmentBonus(Long userId) {
        return sumItemBonuses(
                userEquipmentRepository.findByIdUserId(userId).stream()
                        .map(UserEquipment::getItem)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public CharacterStats getCompanionEquipmentBonus(Long companionId) {
        return sumItemBonuses(
                companionEquipmentRepository.findByIdCompanionId(companionId).stream()
                        .map(CompanionEquipment::getItem)
                        .toList()
        );
    }

    static CharacterStats sumItemBonuses(List<Item> items) {
        int might = 0, intel = 0, vit = 0, def = 0, spirit = 0, luck = 0, agility = 0;
        for (Item item : items) {
            might   += item.getBonusMight();
            intel   += item.getBonusIntelligence();
            vit     += item.getBonusVitality();
            def     += item.getBonusDefense();
            spirit  += item.getBonusSpirit();
            luck    += item.getBonusLuck();
            agility += item.getBonusAgility();
        }
        return new CharacterStats(might, intel, vit, def, spirit, luck, agility);
    }

    private Map<String, ItemDto> buildEquippedMap(Long userId) {
        List<UserEquipment> equipped = userEquipmentRepository.findByIdUserId(userId);
        Map<String, ItemDto> result = new LinkedHashMap<>();
        for (UserEquipment ue : equipped) {
            result.put(ue.getId().getSlot().name(), ItemDto.from(ue.getItem()));
        }
        return result;
    }

    private EquipmentSlot parseSlot(String slotName) {
        try {
            return EquipmentSlot.valueOf(slotName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不存在的裝備部位：" + slotName);
        }
    }
}
