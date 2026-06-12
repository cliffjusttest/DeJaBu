package com.dejebu.repository;

import com.dejebu.entity.UserInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserInventoryRepository extends JpaRepository<UserInventory, Long> {
    List<UserInventory> findByUserId(Long userId);
    List<UserInventory> findByUserIdAndItemId(Long userId, Long itemId);
}
