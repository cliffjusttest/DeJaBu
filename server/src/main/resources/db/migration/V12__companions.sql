CREATE TABLE monster_templates (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    element VARCHAR(16) NOT NULL,
    base_might INT NOT NULL DEFAULT 5,
    base_intelligence INT NOT NULL DEFAULT 5,
    base_vitality INT NOT NULL DEFAULT 5,
    base_defense INT NOT NULL DEFAULT 5,
    base_spirit INT NOT NULL DEFAULT 5,
    base_luck INT NOT NULL DEFAULT 5,
    capturable BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE user_companions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    template_id VARCHAR(32) NOT NULL REFERENCES monster_templates(id),
    nickname VARCHAR(32) NOT NULL,
    level INT NOT NULL DEFAULT 1,
    stat_might INT NOT NULL,
    stat_intelligence INT NOT NULL,
    stat_vitality INT NOT NULL,
    stat_defense INT NOT NULL,
    stat_spirit INT NOT NULL,
    stat_luck INT NOT NULL,
    current_hp INT NOT NULL,
    max_hp INT NOT NULL,
    party_slot INT NULL,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_companions_level CHECK (level >= 1),
    CONSTRAINT chk_user_companions_party_slot CHECK (party_slot IS NULL OR (party_slot >= 0 AND party_slot < 10))
);

CREATE UNIQUE INDEX uq_user_companions_party_slot
    ON user_companions (user_id, party_slot)
    WHERE party_slot IS NOT NULL;

INSERT INTO monster_templates (
    id, name, element,
    base_might, base_intelligence, base_vitality, base_defense, base_spirit, base_luck, capturable
) VALUES
    ('wild_wolf', '野狼', 'WIND', 8, 4, 7, 5, 4, 5, TRUE),
    ('shadow_wisp', '幽影', 'NONE', 5, 10, 6, 4, 8, 6, TRUE);
