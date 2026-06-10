ALTER TABLE users
    ADD COLUMN player_current_hp INT NOT NULL DEFAULT 50;

ALTER TABLE users
    ADD CONSTRAINT chk_users_player_current_hp
        CHECK (player_current_hp >= 0);

UPDATE users
SET player_current_hp = 50 + stat_vitality * 5
WHERE has_character = true;
