package com.dejebu.repository;

import com.dejebu.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findByName(String name);

    @Query("SELECT DISTINCT s FROM Skill s LEFT JOIN FETCH s.prerequisites")
    List<Skill> findAllWithPrerequisites();
}
