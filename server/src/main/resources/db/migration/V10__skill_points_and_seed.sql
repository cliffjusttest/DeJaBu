ALTER TABLE users
    ADD COLUMN skill_points INT NOT NULL DEFAULT 10;

ALTER TABLE users
    ADD COLUMN level INT NOT NULL DEFAULT 1;

ALTER TABLE users
    ADD CONSTRAINT chk_users_skill_points
        CHECK (skill_points >= 0);

ALTER TABLE users
    ADD CONSTRAINT chk_users_level
        CHECK (level >= 1);

INSERT INTO skills (name, element, might_coefficient, intelligence_coefficient, required_level, max_level, cooldown_turns)
VALUES
    ('基礎劍術', 'UNIVERSAL', 1.00, 0.00, 1, 5, 0),
    ('基礎法術', 'UNIVERSAL', 0.00, 1.00, 1, 5, 0),
    ('火球術', 'FIRE', 0.20, 1.20, 1, 5, 1),
    ('重劈', 'UNIVERSAL', 1.30, 0.00, 1, 5, 2),
    ('治療術', 'WATER', 0.00, 0.80, 1, 5, 3),
    ('烈焰', 'FIRE', 0.30, 1.50, 2, 5, 2),
    ('連斬', 'UNIVERSAL', 1.60, 0.00, 2, 5, 1),
    ('隕石', 'FIRE', 0.50, 2.00, 3, 3, 4);

INSERT INTO skill_prerequisites (skill_id, prerequisite_skill_id)
SELECT s.id, p.id
FROM skills s, skills p
WHERE s.name = '火球術' AND p.name = '基礎法術';

INSERT INTO skill_prerequisites (skill_id, prerequisite_skill_id)
SELECT s.id, p.id
FROM skills s, skills p
WHERE s.name = '重劈' AND p.name = '基礎劍術';

INSERT INTO skill_prerequisites (skill_id, prerequisite_skill_id)
SELECT s.id, p.id
FROM skills s, skills p
WHERE s.name = '治療術' AND p.name = '基礎法術';

INSERT INTO skill_prerequisites (skill_id, prerequisite_skill_id)
SELECT s.id, p.id
FROM skills s, skills p
WHERE s.name = '烈焰' AND p.name = '火球術';

INSERT INTO skill_prerequisites (skill_id, prerequisite_skill_id)
SELECT s.id, p.id
FROM skills s, skills p
WHERE s.name = '連斬' AND p.name = '重劈';

INSERT INTO skill_prerequisites (skill_id, prerequisite_skill_id)
SELECT s.id, p.id
FROM skills s, skills p
WHERE s.name = '隕石' AND p.name = '火球術';

INSERT INTO skill_prerequisites (skill_id, prerequisite_skill_id)
SELECT s.id, p.id
FROM skills s, skills p
WHERE s.name = '隕石' AND p.name = '重劈';
