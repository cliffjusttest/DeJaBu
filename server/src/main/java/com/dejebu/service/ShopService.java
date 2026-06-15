package com.dejebu.service;

import com.dejebu.dto.InventoryItemDto;
import com.dejebu.dto.ShopItemDto;
import com.dejebu.dto.ShopListResponse;
import com.dejebu.dto.ShopPurchaseResponse;
import com.dejebu.dto.ShopSellItemDto;
import com.dejebu.entity.Item;
import com.dejebu.entity.NpcEntity;
import com.dejebu.entity.ShopItem;
import com.dejebu.entity.User;
import com.dejebu.repository.NpcRepository;
import com.dejebu.repository.ShopItemRepository;
import com.dejebu.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ShopService {

    private final ShopItemRepository shopItemRepository;
    private final NpcRepository npcRepository;
    private final UserRepository userRepository;
    private final BackpackService backpackService;

    public ShopService(
            ShopItemRepository shopItemRepository,
            NpcRepository npcRepository,
            UserRepository userRepository,
            BackpackService backpackService) {
        this.shopItemRepository = shopItemRepository;
        this.npcRepository = npcRepository;
        this.userRepository = userRepository;
        this.backpackService = backpackService;
    }

    @Transactional(readOnly = true)
    public ShopListResponse listShop(User user, String npcId) {
        NpcEntity npc = npcRepository.findById(npcId)
                .orElseThrow(() -> new IllegalArgumentException("找不到商人"));

        if (!shopItemRepository.existsByNpcId(npcId)) {
            throw new IllegalArgumentException("此 NPC 沒有商店");
        }

        List<ShopItemDto> items = shopItemRepository.findByNpcIdOrderByIdAsc(npcId).stream()
                .map(this::toBuyDto)
                .toList();

        List<ShopSellItemDto> sellableItems = backpackService.getBackpack(user).inventory().stream()
                .filter(inv -> inv.sellPrice() > 0)
                .map(this::toSellDto)
                .toList();

        return new ShopListResponse(npcId, npc.getName(), user.getGold(), items, sellableItems, "");
    }

    @Transactional
    public ShopPurchaseResponse purchase(User user, String npcId, Long itemId) {
        ShopItem shopItem = shopItemRepository.findByNpcIdAndItem_Id(npcId, itemId)
                .orElseThrow(() -> new IllegalArgumentException("此商人沒有販售該商品"));

        if (user.getGold() < shopItem.getPrice()) {
            throw new IllegalArgumentException("金幣不足（需要 " + shopItem.getPrice() + "，持有 " + user.getGold() + "）");
        }

        user.setGold(user.getGold() - shopItem.getPrice());
        userRepository.save(user);
        backpackService.addItem(user, shopItem.getItem(), 1);

        Item item = shopItem.getItem();
        return new ShopPurchaseResponse(
                user.getGold(),
                "購買了「" + item.getName() + "」（-" + shopItem.getPrice() + " 金幣）"
        );
    }

    @Transactional
    public ShopPurchaseResponse sell(User user, String npcId, Long itemId) {
        if (!shopItemRepository.existsByNpcId(npcId)) {
            throw new IllegalArgumentException("此 NPC 沒有商店");
        }

        InventoryItemDto inventoryItem = backpackService.getBackpack(user).inventory().stream()
                .filter(inv -> inv.id().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("背包中沒有此道具"));

        if (inventoryItem.sellPrice() <= 0) {
            throw new IllegalArgumentException("此道具無法賣出");
        }

        backpackService.removeItem(user, itemId, 1);
        user.setGold(user.getGold() + inventoryItem.sellPrice());
        userRepository.save(user);

        return new ShopPurchaseResponse(
                user.getGold(),
                "賣出了「" + inventoryItem.name() + "」（+" + inventoryItem.sellPrice() + " 金幣）"
        );
    }

    private ShopItemDto toBuyDto(ShopItem shopItem) {
        Item item = shopItem.getItem();
        return new ShopItemDto(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getType().name(),
                item.getSlot() != null ? item.getSlot().name() : null,
                item.getSlot() != null ? item.getSlot().getDisplayName() : null,
                item.getRequiredLevel(),
                item.getHealHp(),
                shopItem.getPrice()
        );
    }

    private ShopSellItemDto toSellDto(InventoryItemDto inv) {
        return new ShopSellItemDto(
                inv.id(),
                inv.name(),
                inv.description(),
                inv.type(),
                inv.quantity(),
                inv.sellPrice()
        );
    }
}
