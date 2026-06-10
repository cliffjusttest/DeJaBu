package com.dejebu.repository;

import com.dejebu.entity.MonsterTemplateSkill;
import com.dejebu.entity.MonsterTemplateSkillId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonsterTemplateSkillRepository extends JpaRepository<MonsterTemplateSkill, MonsterTemplateSkillId> {

    List<MonsterTemplateSkill> findByTemplateIdOrderBySlotOrderAsc(String templateId);
}
