package com.dejebu.repository;

import com.dejebu.entity.QuestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestRepository extends JpaRepository<QuestEntity, Long> {
    List<QuestEntity> findByGiverNpcId(String giverNpcId);
    List<QuestEntity> findByQuestTypeAndTargetId(String questType, String targetId);
}
