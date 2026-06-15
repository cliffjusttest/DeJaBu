ALTER TABLE skills
    ADD COLUMN mp_cost INT NOT NULL DEFAULT 0;

ALTER TABLE skills
    ADD CONSTRAINT chk_skills_mp_cost
        CHECK (mp_cost >= 0);

UPDATE skills
SET mp_cost = CASE name
    WHEN '基礎劍術' THEN 0
    WHEN '基礎法術' THEN 5
    WHEN '火球術' THEN 8
    WHEN '重劈' THEN 0
    WHEN '治療術' THEN 12
    WHEN '烈焰' THEN 15
    WHEN '連斬' THEN 0
    WHEN '隕石' THEN 25
    ELSE 0
END;

ALTER TABLE users
    ADD COLUMN player_current_mp INT NOT NULL DEFAULT 20;

ALTER TABLE users
    ADD CONSTRAINT chk_users_player_current_mp
        CHECK (player_current_mp >= 0);

UPDATE users
SET player_current_mp = 20 + stat_intelligence * 3 + stat_spirit * 2
WHERE has_character = true;

ALTER TABLE user_companions
    ADD COLUMN current_mp INT NOT NULL DEFAULT 20;

ALTER TABLE user_companions
    ADD COLUMN max_mp INT NOT NULL DEFAULT 20;

ALTER TABLE user_companions
    ADD CONSTRAINT chk_user_companions_current_mp
        CHECK (current_mp >= 0);

ALTER TABLE user_companions
    ADD CONSTRAINT chk_user_companions_max_mp
        CHECK (max_mp >= 0);

UPDATE user_companions
SET max_mp = 20 + stat_intelligence * 3 + stat_spirit * 2,
    current_mp = 20 + stat_intelligence * 3 + stat_spirit * 2;
