package com.dejebu.repository;

import com.dejebu.entity.MonsterDrop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonsterDropRepository extends JpaRepository<MonsterDrop, Long> {

    List<MonsterDrop> findByMonsterTemplate_Id(String monsterTemplateId);
}
