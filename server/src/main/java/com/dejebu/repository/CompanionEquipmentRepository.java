package com.dejebu.repository;

import com.dejebu.entity.CompanionEquipment;
import com.dejebu.entity.CompanionEquipmentId;
import com.dejebu.game.EquipmentSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanionEquipmentRepository extends JpaRepository<CompanionEquipment, CompanionEquipmentId> {
    List<CompanionEquipment> findByIdCompanionId(Long companionId);
    Optional<CompanionEquipment> findByIdCompanionIdAndIdSlot(Long companionId, EquipmentSlot slot);
    void deleteByIdCompanionIdAndIdSlot(Long companionId, EquipmentSlot slot);
}
