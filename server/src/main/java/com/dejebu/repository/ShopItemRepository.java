package com.dejebu.repository;

import com.dejebu.entity.ShopItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopItemRepository extends JpaRepository<ShopItem, Long> {

    List<ShopItem> findByNpcIdOrderByIdAsc(String npcId);

    Optional<ShopItem> findByNpcIdAndItem_Id(String npcId, Long itemId);

    boolean existsByNpcId(String npcId);
}
