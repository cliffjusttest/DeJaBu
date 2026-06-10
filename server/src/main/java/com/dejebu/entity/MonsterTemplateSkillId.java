package com.dejebu.entity;

import java.io.Serializable;
import java.util.Objects;

public class MonsterTemplateSkillId implements Serializable {

    private String templateId;
    private Long skillId;

    public MonsterTemplateSkillId() {
    }

    public MonsterTemplateSkillId(String templateId, Long skillId) {
        this.templateId = templateId;
        this.skillId = skillId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        MonsterTemplateSkillId that = (MonsterTemplateSkillId) other;
        return Objects.equals(templateId, that.templateId) && Objects.equals(skillId, that.skillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(templateId, skillId);
    }
}
