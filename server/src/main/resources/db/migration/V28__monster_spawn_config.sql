ALTER TABLE monster_templates
    ADD COLUMN min_level INT NOT NULL DEFAULT 1,
    ADD COLUMN max_level INT NOT NULL DEFAULT 99,
    ADD COLUMN visible_spawn BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dark_spawn BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE monster_templates
    ADD CONSTRAINT chk_monster_templates_level_range
        CHECK (min_level >= 1 AND max_level >= min_level);

CREATE TABLE monster_template_spawn_maps (
    template_id VARCHAR(32) NOT NULL REFERENCES monster_templates(id) ON DELETE CASCADE,
    map_id VARCHAR(32) NOT NULL,
    PRIMARY KEY (template_id, map_id)
);

UPDATE monster_templates
SET min_level = 1,
    max_level = 10,
    visible_spawn = TRUE,
    dark_spawn = TRUE
WHERE id = 'wild_wolf';

UPDATE monster_templates
SET min_level = 3,
    max_level = 12,
    visible_spawn = TRUE,
    dark_spawn = TRUE
WHERE id = 'shadow_wisp';

INSERT INTO monster_template_spawn_maps (template_id, map_id) VALUES
    ('wild_wolf', 'forest'),
    ('shadow_wisp', 'forest');
