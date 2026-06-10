CREATE TABLE monster_template_skills (
    template_id VARCHAR(32) NOT NULL REFERENCES monster_templates(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    slot_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (template_id, skill_id)
);

CREATE TABLE companion_skills (
    id BIGSERIAL PRIMARY KEY,
    companion_id BIGINT NOT NULL REFERENCES user_companions(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    skill_level INT NOT NULL DEFAULT 1,
    CONSTRAINT uq_companion_skills UNIQUE (companion_id, skill_id),
    CONSTRAINT chk_companion_skills_level CHECK (skill_level >= 1)
);

INSERT INTO monster_template_skills (template_id, skill_id, slot_order)
SELECT 'wild_wolf', id, 0 FROM skills WHERE name = '基礎劍術';

INSERT INTO monster_template_skills (template_id, skill_id, slot_order)
SELECT 'wild_wolf', id, 1 FROM skills WHERE name = '重劈';

INSERT INTO monster_template_skills (template_id, skill_id, slot_order)
SELECT 'shadow_wisp', id, 0 FROM skills WHERE name = '基礎法術';

INSERT INTO monster_template_skills (template_id, skill_id, slot_order)
SELECT 'shadow_wisp', id, 1 FROM skills WHERE name = '火球術';

INSERT INTO companion_skills (companion_id, skill_id, skill_level)
SELECT uc.id, mts.skill_id, 1
FROM user_companions uc
JOIN monster_template_skills mts ON mts.template_id = uc.template_id;
