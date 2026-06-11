package com.dejebu.repository;

import com.dejebu.entity.DialogueNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DialogueNodeRepository extends JpaRepository<DialogueNodeEntity, Long> {
    Optional<DialogueNodeEntity> findByNpcIdAndNodeKey(String npcId, String nodeKey);
}
