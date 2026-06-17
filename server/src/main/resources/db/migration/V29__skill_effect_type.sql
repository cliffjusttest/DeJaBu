ALTER TABLE skills
    ADD COLUMN effect_type VARCHAR(16) NOT NULL DEFAULT 'DAMAGE';

ALTER TABLE skills
    ADD CONSTRAINT chk_skills_effect_type
        CHECK (effect_type IN ('DAMAGE', 'HEAL', 'REVIVE', 'BUFF'));

UPDATE skills SET effect_type = 'HEAL' WHERE name = '治療術';
