package com.dejebu.repository;

import com.dejebu.entity.NpcEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NpcRepository extends JpaRepository<NpcEntity, String> {
    List<NpcEntity> findByMapId(String mapId);
    boolean existsByIdAndMapId(String id, String mapId);
}
