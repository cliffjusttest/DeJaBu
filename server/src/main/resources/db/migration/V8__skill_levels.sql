ALTER TABLE skills
    ADD COLUMN max_level INT NOT NULL DEFAULT 1;

ALTER TABLE skills
    ADD CONSTRAINT chk_skills_max_level
        CHECK (max_level >= 1);

ALTER TABLE user_skills
    ADD COLUMN skill_level INT NOT NULL DEFAULT 1;

ALTER TABLE user_skills
    ADD CONSTRAINT chk_user_skills_skill_level
        CHECK (skill_level >= 1);
