ALTER TABLE users
    ALTER COLUMN stat_might SET DEFAULT 0;

ALTER TABLE users
    ALTER COLUMN stat_intelligence SET DEFAULT 0;

ALTER TABLE users
    ALTER COLUMN stat_vitality SET DEFAULT 0;

ALTER TABLE users
    ALTER COLUMN stat_defense SET DEFAULT 0;

ALTER TABLE users
    ALTER COLUMN stat_spirit SET DEFAULT 0;

ALTER TABLE users
    ALTER COLUMN stat_luck SET DEFAULT 0;

ALTER TABLE users
    DROP CONSTRAINT chk_users_stat_might;

ALTER TABLE users
    DROP CONSTRAINT chk_users_stat_intelligence;

ALTER TABLE users
    DROP CONSTRAINT chk_users_stat_vitality;

ALTER TABLE users
    DROP CONSTRAINT chk_users_stat_defense;

ALTER TABLE users
    DROP CONSTRAINT chk_users_stat_spirit;

ALTER TABLE users
    DROP CONSTRAINT chk_users_stat_luck;

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_might CHECK (stat_might BETWEEN 0 AND 99);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_intelligence CHECK (stat_intelligence BETWEEN 0 AND 99);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_vitality CHECK (stat_vitality BETWEEN 0 AND 99);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_defense CHECK (stat_defense BETWEEN 0 AND 99);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_spirit CHECK (stat_spirit BETWEEN 0 AND 99);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_luck CHECK (stat_luck BETWEEN 0 AND 99);
