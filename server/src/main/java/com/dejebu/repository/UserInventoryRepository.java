package com.dejebu.repository;

import com.dejebu.entity.UserInventory;
import com.dejebu.entity.UserInventoryId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserInventoryRepository extends JpaRepository<UserInventory, UserInventoryId> {
    List<UserInventory> findByIdUserId(Long userId);
    Optional<UserInventory> findByIdUserIdAndIdItemId(Long userId, Long itemId);
    void deleteByIdUserIdAndIdItemId(Long userId, Long itemId);
}
