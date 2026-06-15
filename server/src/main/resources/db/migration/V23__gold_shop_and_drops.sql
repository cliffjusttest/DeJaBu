-- Player currency for shop purchases
ALTER TABLE users ADD COLUMN gold INT NOT NULL DEFAULT 100;

-- Gold dropped by monsters on victory (random in [min, max] inclusive)
ALTER TABLE monster_templates ADD COLUMN gold_drop_min INT NOT NULL DEFAULT 0;
ALTER TABLE monster_templates ADD COLUMN gold_drop_max INT NOT NULL DEFAULT 0;

UPDATE monster_templates SET gold_drop_min = 3, gold_drop_max = 8 WHERE id = 'wild_wolf';
UPDATE monster_templates SET gold_drop_min = 8, gold_drop_max = 15 WHERE id = 'shadow_wisp';

-- Monster item drops: drop_chance is 0.0–1.0 (e.g. 0.35 = 35%)
CREATE TABLE monster_drops (
    id                  BIGSERIAL PRIMARY KEY,
    monster_template_id VARCHAR(32) NOT NULL REFERENCES monster_templates(id) ON DELETE CASCADE,
    item_id             BIGINT      NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    drop_chance         DOUBLE PRECISION NOT NULL CHECK (drop_chance > 0 AND drop_chance <= 1),
    UNIQUE (monster_template_id, item_id)
);

-- Shop inventory per NPC merchant
CREATE TABLE shop_items (
    id      BIGSERIAL PRIMARY KEY,
    npc_id  VARCHAR(64) NOT NULL REFERENCES npcs(id) ON DELETE CASCADE,
    item_id BIGINT      NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    price   INT         NOT NULL CHECK (price > 0),
    UNIQUE (npc_id, item_id)
);

-- ── Seed: wild_wolf drops ─────────────────────────────────────────────────────

INSERT INTO monster_drops (monster_template_id, item_id, drop_chance)
SELECT 'wild_wolf', id, 0.35 FROM items WHERE name = '小型草藥';

INSERT INTO monster_drops (monster_template_id, item_id, drop_chance)
SELECT 'wild_wolf', id, 0.08 FROM items WHERE name = '中型草藥';

INSERT INTO monster_drops (monster_template_id, item_id, drop_chance)
SELECT 'wild_wolf', id, 0.05 FROM items WHERE name = '皮手套' AND slot = 'HAND';

-- ── Seed: shadow_wisp drops ───────────────────────────────────────────────────

INSERT INTO monster_drops (monster_template_id, item_id, drop_chance)
SELECT 'shadow_wisp', id, 0.25 FROM items WHERE name = '中型草藥';

INSERT INTO monster_drops (monster_template_id, item_id, drop_chance)
SELECT 'shadow_wisp', id, 0.10 FROM items WHERE name = '大型草藥';

INSERT INTO monster_drops (monster_template_id, item_id, drop_chance)
SELECT 'shadow_wisp', id, 0.04 FROM items WHERE name = '智者面具' AND slot = 'FACE';

-- ── Seed: shop items ──────────────────────────────────────────────────────────

INSERT INTO shop_items (npc_id, item_id, price)
SELECT 'forest_merchant', id, 15 FROM items WHERE name = '小型草藥';

INSERT INTO shop_items (npc_id, item_id, price)
SELECT 'forest_merchant', id, 40 FROM items WHERE name = '中型草藥';

INSERT INTO shop_items (npc_id, item_id, price)
SELECT 'forest_merchant', id, 90 FROM items WHERE name = '大型草藥';

INSERT INTO shop_items (npc_id, item_id, price)
SELECT 'forest_merchant', id, 80 FROM items WHERE name = '皮革頭盔' AND slot = 'HEAD';

INSERT INTO shop_items (npc_id, item_id, price)
SELECT 'forest_merchant', id, 60 FROM items WHERE name = '皮手套' AND slot = 'HAND';

INSERT INTO shop_items (npc_id, item_id, price)
SELECT 'village_elder', id, 10 FROM items WHERE name = '小型草藥';

INSERT INTO shop_items (npc_id, item_id, price)
SELECT 'village_elder', id, 35 FROM items WHERE name = '中型草藥';

-- ── Seed: NPC dialogue — add shop option ──────────────────────────────────────

UPDATE dialogue_nodes
SET choices_json = '[{"text":"我想接任務","nextKey":"quest_offer"},{"text":"我想買東西","action":"open_shop"},{"text":"再見","nextKey":null}]'
WHERE npc_id = 'village_elder' AND node_key = 'root';

UPDATE dialogue_nodes
SET choices_json = '[{"text":"我想接任務","nextKey":"quest_offer"},{"text":"我想買東西","action":"open_shop"},{"text":"這裡有何注意","nextKey":"tips"},{"text":"再見","nextKey":null}]'
WHERE npc_id = 'forest_merchant' AND node_key = 'root';
