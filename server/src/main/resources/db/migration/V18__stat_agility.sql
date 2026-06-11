ALTER TABLE users
    ADD COLUMN stat_agility INT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_agility CHECK (stat_agility BETWEEN 0 AND 99);

ALTER TABLE user_companions
    ADD COLUMN stat_agility INT NOT NULL DEFAULT 0;

ALTER TABLE monster_templates
    ADD COLUMN base_agility INT NOT NULL DEFAULT 5;
