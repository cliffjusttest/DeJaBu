package com.dejebu.repository;

import com.dejebu.entity.UserCompanion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCompanionRepository extends JpaRepository<UserCompanion, Long> {

    List<UserCompanion> findByUserIdOrderByCapturedAtAsc(Long userId);

    List<UserCompanion> findByUserIdAndPartySlotIsNotNullOrderByPartySlotAsc(Long userId);

    long countByUserIdAndPartySlotIsNotNull(Long userId);

    Optional<UserCompanion> findByUserIdAndPartySlot(Long userId, int partySlot);

    Optional<UserCompanion> findByUserIdAndId(Long userId, Long id);
}
