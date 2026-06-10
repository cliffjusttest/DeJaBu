ALTER TABLE skills
    ADD COLUMN target_side VARCHAR(16) NOT NULL DEFAULT 'ENEMY';

ALTER TABLE skills
    ADD COLUMN target_range VARCHAR(32) NOT NULL DEFAULT 'SINGLE';

ALTER TABLE skills
    ADD CONSTRAINT chk_skills_target_side
        CHECK (target_side IN ('ALLY', 'ENEMY', 'ANY'));

ALTER TABLE skills
    ADD CONSTRAINT chk_skills_target_range
        CHECK (target_range IN ('SINGLE', 'ROW_ADJACENT_THREE', 'CROSS', 'ROW', 'ALL'));

UPDATE skills SET target_side = 'ENEMY', target_range = 'SINGLE'
WHERE name IN ('基礎劍術', '火球術', '重劈');

UPDATE skills SET target_side = 'ANY', target_range = 'SINGLE'
WHERE name = '基礎法術';

UPDATE skills SET target_side = 'ALLY', target_range = 'SINGLE'
WHERE name = '治療術';

UPDATE skills SET target_side = 'ENEMY', target_range = 'ROW'
WHERE name = '烈焰';

UPDATE skills SET target_side = 'ENEMY', target_range = 'ROW_ADJACENT_THREE'
WHERE name = '連斬';

UPDATE skills SET target_side = 'ENEMY', target_range = 'ALL'
WHERE name = '隕石';
