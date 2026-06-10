ALTER TABLE skills
    ADD COLUMN cooldown_turns INT NOT NULL DEFAULT 0;

ALTER TABLE skills
    ADD CONSTRAINT chk_skills_cooldown_turns
        CHECK (cooldown_turns >= 0);
