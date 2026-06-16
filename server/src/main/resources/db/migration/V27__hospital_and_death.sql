-- Hospital locations for death respawn and companion revival
CREATE TABLE hospitals (
    id VARCHAR(64) PRIMARY KEY,
    map_id VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    npc_grid_x INTEGER NOT NULL,
    npc_grid_y INTEGER NOT NULL,
    respawn_x INTEGER NOT NULL,
    respawn_y INTEGER NOT NULL
);

INSERT INTO hospitals (id, map_id, name, npc_grid_x, npc_grid_y, respawn_x, respawn_y) VALUES
    ('village_hospital', 'village', '新手村醫館', 4, 11, 5, 11),
    ('forest_hospital', 'forest', '森林醫館', 5, 10, 6, 10);

-- NPC role distinguishes hospital staff from other NPCs
ALTER TABLE npcs ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'default';

INSERT INTO npcs (id, map_id, grid_x, grid_y, name, sprite_key, root_node_key, role) VALUES
    ('village_healer', 'village', 4, 11, '村醫', 'healer', 'root', 'hospital'),
    ('forest_healer', 'forest', 5, 10, '森林醫師', 'healer', 'root', 'hospital');

INSERT INTO dialogue_nodes (npc_id, node_key, text, choices_json) VALUES
    ('village_healer', 'root',
     '歡迎來到新手村醫館。受傷的旅人與夥伴都可以在這裡休養。',
     '[{"text":"治療夥伴","action":"hospital_revive"},{"text":"離開","nextKey":null}]'),
    ('forest_healer', 'root',
     '森林裡危險重重，先把夥伴帶來這裡治療吧。',
     '[{"text":"治療夥伴","action":"hospital_revive"},{"text":"離開","nextKey":null}]');

-- Companion incapacitation after battle defeat
ALTER TABLE user_companions
    ADD COLUMN incapacitated_until TIMESTAMP NULL;
