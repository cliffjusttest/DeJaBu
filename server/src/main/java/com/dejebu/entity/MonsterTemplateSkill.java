package com.dejebu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "monster_template_skills")
@IdClass(MonsterTemplateSkillId.class)
public class MonsterTemplateSkill {

    @Id
    @Column(name = "template_id")
    private String templateId;

    @Id
    @Column(name = "skill_id")
    private Long skillId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", insertable = false, updatable = false)
    private MonsterTemplateEntity template;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", insertable = false, updatable = false)
    private Skill skill;

    @Column(name = "slot_order", nullable = false)
    private int slotOrder;

    public String getTemplateId() {
        return templateId;
    }

    public Long getSkillId() {
        return skillId;
    }

    public MonsterTemplateEntity getTemplate() {
        return template;
    }

    public Skill getSkill() {
        return skill;
    }

    public int getSlotOrder() {
        return slotOrder;
    }
}
