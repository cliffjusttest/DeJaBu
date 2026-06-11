package com.dejebu.repository;

import com.dejebu.entity.UserEquipment;
import com.dejebu.entity.UserEquipmentId;
import com.dejebu.game.EquipmentSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserEquipmentRepository extends JpaRepository<UserEquipment, UserEquipmentId> {
    List<UserEquipment> findByIdUserId(Long userId);
    Optional<UserEquipment> findByIdUserIdAndIdSlot(Long userId, EquipmentSlot slot);
    void deleteByIdUserIdAndIdSlot(Long userId, EquipmentSlot slot);
}
