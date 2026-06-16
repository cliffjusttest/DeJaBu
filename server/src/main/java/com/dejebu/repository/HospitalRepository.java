package com.dejebu.repository;

import com.dejebu.entity.HospitalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HospitalRepository extends JpaRepository<HospitalEntity, String> {

    List<HospitalEntity> findAllByOrderByIdAsc();

    Optional<HospitalEntity> findFirstByMapId(String mapId);
}
