ALTER TABLE users
    ADD COLUMN stat_might INT NOT NULL DEFAULT 10;

ALTER TABLE users
    ADD COLUMN stat_intelligence INT NOT NULL DEFAULT 10;

ALTER TABLE users
    ADD COLUMN stat_vitality INT NOT NULL DEFAULT 10;

ALTER TABLE users
    ADD COLUMN stat_defense INT NOT NULL DEFAULT 10;

ALTER TABLE users
    ADD COLUMN stat_spirit INT NOT NULL DEFAULT 10;

ALTER TABLE users
    ADD COLUMN stat_luck INT NOT NULL DEFAULT 10;

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_might CHECK (stat_might BETWEEN 1 AND 99);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_intelligence CHECK (stat_intelligence BETWEEN 1 AND 99);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_vitality CHECK (stat_vitality BETWEEN 1 AND 99);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_defense CHECK (stat_defense BETWEEN 1 AND 99);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_spirit CHECK (stat_spirit BETWEEN 1 AND 99);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_luck CHECK (stat_luck BETWEEN 1 AND 99);
