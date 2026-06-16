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
    DROP CONSTRAINT chk_users_stat_agility;

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_might CHECK (stat_might >= 0);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_intelligence CHECK (stat_intelligence >= 0);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_vitality CHECK (stat_vitality >= 0);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_defense CHECK (stat_defense >= 0);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_spirit CHECK (stat_spirit >= 0);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_luck CHECK (stat_luck >= 0);

ALTER TABLE users
    ADD CONSTRAINT chk_users_stat_agility CHECK (stat_agility >= 0);
