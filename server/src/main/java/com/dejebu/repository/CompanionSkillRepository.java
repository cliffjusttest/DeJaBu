package com.dejebu.repository;

import com.dejebu.entity.CompanionSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanionSkillRepository extends JpaRepository<CompanionSkill, Long> {

    List<CompanionSkill> findByCompanionIdOrderByIdAsc(Long companionId);

    Optional<CompanionSkill> findByCompanionIdAndSkillId(Long companionId, Long skillId);
}
