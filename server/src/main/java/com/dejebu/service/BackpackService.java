package com.dejebu.service;

import com.dejebu.dto.BackpackResponse;
import com.dejebu.dto.CompanionEquipmentDto;
import com.dejebu.dto.InventoryItemDto;
import com.dejebu.dto.ItemDto;
import com.dejebu.entity.CompanionEquipment;
import com.dejebu.entity.Item;
import com.dejebu.entity.User;
import com.dejebu.entity.UserCompanion;
import com.dejebu.entity.UserEquipment;
import com.dejebu.entity.UserInventory;
import com.dejebu.game.EquipmentSlot;
import com.dejebu.game.ItemType;
import com.dejebu.repository.CompanionEquipmentRepository;
import com.dejebu.repository.UserCompanionRepository;
import com.dejebu.repository.UserEquipmentRepository;
import com.dejebu.repository.UserInventoryRepository;
import com.dejebu.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BackpackService {

    private final UserInventoryRepository inventoryRepository;
    private final UserEquipmentRepository userEquipmentRepository;
    private final CompanionEquipmentRepository companionEquipmentRepository;
    private final UserCompanionRepository companionRepository;
    private final UserRepository userRepository;

    public BackpackService(
            UserInventoryRepository inventoryRepository,
            UserEquipmentRepository userEquipmentRepository,
            CompanionEquipmentRepository companionEquipmentRepository,
            UserCompanionRepository companionRepository,
            UserRepository userRepository) {
        this.inventoryRepository = inventoryRepository;
        this.userEquipmentRepository = userEquipmentRepository;
        this.companionEquipmentRepository = companionEquipmentRepository;
        this.companionRepository = companionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public BackpackResponse getBackpack(User user) {
        return buildResponse(user, "");
    }

    @Transactional
    public BackpackResponse useConsumable(User user, Long itemId) {
        UserInventory inv = findAnyStack(user.getId(), itemId)
                .orElseThrow(() -> new IllegalArgumentException("背包中沒有此道具"));
        Item item = inv.getItem();

        if (item.getType() != ItemType.CONSUMABLE) {
            throw new IllegalArgumentException("此物品不是消耗道具");
        }

        String effect = "";
        if (item.getHealHp() > 0) {
            int maxHp = user.resolveMaxHp();
            int before = user.resolveCurrentHp();
            int healed = Math.min(item.getHealHp(), maxHp - before);
            user.setPlayerCurrentHp(before + healed);
            userRepository.save(user);
            effect = "，恢復了 " + healed + " 點 HP";
        }

        removeFromInventory(user.getId(), itemId);
        return buildResponse(user, "使用了「" + item.getName() + "」" + effect);
    }

    @Transactional
    public BackpackResponse equipToPlayer(User user, Long itemId) {
        UserInventory inv = findAnyStack(user.getId(), itemId)
                .orElseThrow(() -> new IllegalArgumentException("背包中沒有此裝備"));
        Item item = inv.getItem();

        if (item.getType() != ItemType.EQUIPMENT) {
            throw new IllegalArgumentException("此物品無法裝備");
        }
        if (item.getRequiredLevel() > user.getLevel()) {
            throw new IllegalArgumentException("等級不足，此裝備需要 Lv." + item.getRequiredLevel());
        }

        userEquipmentRepository.findByIdUserIdAndIdSlot(user.getId(), item.getSlot())
                .ifPresent(old -> {
                    returnToInventory(user, old.getItem());
                    userEquipmentRepository.delete(old);
                });

        userEquipmentRepository.save(new UserEquipment(user, item));
        removeFromInventory(user.getId(), itemId);

        return buildResponse(user, "已裝備「" + item.getName() + "」");
    }

    @Transactional
    public BackpackResponse unequipFromPlayer(User user, String slotName) {
        EquipmentSlot slot = parseSlot(slotName);
        userEquipmentRepository.findByIdUserIdAndIdSlot(user.getId(), slot)
                .ifPresent(ue -> {
                    returnToInventory(user, ue.getItem());
                    userEquipmentRepository.delete(ue);
                });
        return buildResponse(user, "已卸下「" + slot.getDisplayName() + "」部位的裝備");
    }

    @Transactional
    public BackpackResponse equipToCompanion(User user, Long companionId, Long itemId) {
        UserCompanion companion = companionRepository.findByUserIdAndId(user.getId(), companionId)
                .orElseThrow(() -> new IllegalArgumentException("找不到此夥伴"));

        UserInventory inv = findAnyStack(user.getId(), itemId)
                .orElseThrow(() -> new IllegalArgumentException("背包中沒有此裝備"));
        Item item = inv.getItem();

        if (item.getType() != ItemType.EQUIPMENT) {
            throw new IllegalArgumentException("此物品無法裝備");
        }
        if (item.getRequiredLevel() > companion.getLevel()) {
            throw new IllegalArgumentException("夥伴等級不足，此裝備需要 Lv." + item.getRequiredLevel());
        }

        companionEquipmentRepository.findByIdCompanionIdAndIdSlot(companionId, item.getSlot())
                .ifPresent(old -> {
                    returnToInventory(user, old.getItem());
                    companionEquipmentRepository.delete(old);
                });

        companionEquipmentRepository.save(new CompanionEquipment(companion, item));
        removeFromInventory(user.getId(), itemId);

        return buildResponse(user, "已為「" + companion.getNickname() + "」裝備「" + item.getName() + "」");
    }

    @Transactional
    public BackpackResponse unequipFromCompanion(User user, Long companionId, String slotName) {
        UserCompanion companion = companionRepository.findByUserIdAndId(user.getId(), companionId)
                .orElseThrow(() -> new IllegalArgumentException("找不到此夥伴"));

        EquipmentSlot slot = parseSlot(slotName);
        companionEquipmentRepository.findByIdCompanionIdAndIdSlot(companionId, slot)
                .ifPresent(ce -> {
                    returnToInventory(user, ce.getItem());
                    companionEquipmentRepository.delete(ce);
                });

        return buildResponse(user, "已卸下「" + companion.getNickname() + "」的「" + slot.getDisplayName() + "」部位裝備");
    }

    private BackpackResponse buildResponse(User user, String message) {
        List<InventoryItemDto> inventory = aggregateInventory(user.getId());
        Map<String, ItemDto> playerEquipped = buildPlayerEquippedMap(user.getId());
        List<CompanionEquipmentDto> companions = buildCompanionList(user.getId());
        return new BackpackResponse(inventory, playerEquipped, companions,
                user.resolveCurrentHp(), user.resolveMaxHp(), message);
    }

    // Aggregates all stacks of the same item into one DTO showing the combined quantity.
    // Stacks are still stored separately in the DB; the client always operates by item_id.
    private List<InventoryItemDto> aggregateInventory(Long userId) {
        Map<Long, List<UserInventory>> byItem = inventoryRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.groupingBy(inv -> inv.getItem().getId(),
                        LinkedHashMap::new, Collectors.toList()));
        List<InventoryItemDto> result = new ArrayList<>();
        for (List<UserInventory> stacks : byItem.values()) {
            int total = stacks.stream().mapToInt(UserInventory::getQuantity).sum();
            result.add(InventoryItemDto.fromWithQuantity(stacks.get(0), total));
        }
        return result;
    }

    private Map<String, ItemDto> buildPlayerEquippedMap(Long userId) {
        Map<String, ItemDto> result = new LinkedHashMap<>();
        for (UserEquipment ue : userEquipmentRepository.findByIdUserId(userId)) {
            result.put(ue.getId().getSlot().name(), ItemDto.from(ue.getItem()));
        }
        return result;
    }

    private List<CompanionEquipmentDto> buildCompanionList(Long userId) {
        return companionRepository.findByUserIdOrderByCapturedAtAsc(userId).stream()
                .map(c -> {
                    Map<String, ItemDto> equipped = new LinkedHashMap<>();
                    for (CompanionEquipment ce : companionEquipmentRepository.findByIdCompanionId(c.getId())) {
                        equipped.put(ce.getId().getSlot().name(), ItemDto.from(ce.getItem()));
                    }
                    return new CompanionEquipmentDto(c.getId(), c.getNickname(), c.getLevel(), equipped);
                })
                .toList();
    }

    // Finds any existing stack for this (user, item) pair.
    private java.util.Optional<UserInventory> findAnyStack(Long userId, Long itemId) {
        return inventoryRepository.findByUserIdAndItemId(userId, itemId)
                .stream().findFirst();
    }

    // Adds one item back to inventory: fills the first non-full stack, or creates a new one.
    private void returnToInventory(User user, Item item) {
        inventoryRepository.findByUserIdAndItemId(user.getId(), item.getId())
                .stream()
                .filter(inv -> inv.getQuantity() < 999)
                .findFirst()
                .ifPresentOrElse(
                        inv -> inv.setQuantity(inv.getQuantity() + 1),
                        () -> inventoryRepository.save(new UserInventory(user, item, 1))
                );
    }

    // Removes one item from inventory: decrements the first stack found, deletes it if empty.
    private void removeFromInventory(Long userId, Long itemId) {
        UserInventory inv = inventoryRepository.findByUserIdAndItemId(userId, itemId)
                .stream().findFirst().orElseThrow();
        if (inv.getQuantity() <= 1) {
            inventoryRepository.delete(inv);
        } else {
            inv.setQuantity(inv.getQuantity() - 1);
        }
    }

    private EquipmentSlot parseSlot(String slotName) {
        try {
            return EquipmentSlot.valueOf(slotName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不存在的裝備部位：" + slotName);
        }
    }
}
