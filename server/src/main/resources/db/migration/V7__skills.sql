CREATE TABLE skills (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    element VARCHAR(16) NOT NULL,
    might_coefficient NUMERIC(5, 2) NOT NULL,
    intelligence_coefficient NUMERIC(5, 2) NOT NULL,
    required_level INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_skills_element
        CHECK (element IN ('FIRE', 'WIND', 'EARTH', 'THUNDER', 'WATER', 'UNIVERSAL')),
    CONSTRAINT chk_skills_might_coefficient
        CHECK (might_coefficient >= 0),
    CONSTRAINT chk_skills_intelligence_coefficient
        CHECK (intelligence_coefficient >= 0),
    CONSTRAINT chk_skills_required_level
        CHECK (required_level >= 1)
);

CREATE TABLE skill_prerequisites (
    skill_id BIGINT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    prerequisite_skill_id BIGINT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    PRIMARY KEY (skill_id, prerequisite_skill_id),
    CONSTRAINT chk_skill_prerequisites_not_self
        CHECK (skill_id <> prerequisite_skill_id)
);

CREATE INDEX idx_skill_prerequisites_prerequisite ON skill_prerequisites(prerequisite_skill_id);

CREATE TABLE user_skills (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    learned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_skills_user_skill UNIQUE (user_id, skill_id)
);

CREATE INDEX idx_user_skills_user_id ON user_skills(user_id);
CREATE INDEX idx_user_skills_skill_id ON user_skills(skill_id);
