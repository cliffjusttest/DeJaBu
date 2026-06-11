package com.dejebu.repository;

import com.dejebu.entity.Item;
import com.dejebu.game.EquipmentSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findBySlot(EquipmentSlot slot);
}
