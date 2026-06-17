package com.dejebu.repository;

import com.dejebu.entity.MonsterTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonsterTemplateRepository extends JpaRepository<MonsterTemplateEntity, String> {

    List<MonsterTemplateEntity> findByDarkSpawnTrue();
}
