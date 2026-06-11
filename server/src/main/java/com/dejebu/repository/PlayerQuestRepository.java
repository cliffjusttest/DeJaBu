package com.dejebu.repository;

import com.dejebu.entity.PlayerQuestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerQuestRepository extends JpaRepository<PlayerQuestEntity, Long> {
    Optional<PlayerQuestEntity> findByUserIdAndQuestId(Long userId, Long questId);
    List<PlayerQuestEntity> findByUserId(Long userId);
    List<PlayerQuestEntity> findByUserIdAndStatus(Long userId, String status);
}
