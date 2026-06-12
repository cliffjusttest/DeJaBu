-- Consumable items do not belong to any equipment slot
ALTER TABLE items ALTER COLUMN slot DROP NOT NULL;

-- Heal HP effect value (0 = no heal)
ALTER TABLE items ADD COLUMN heal_hp INT NOT NULL DEFAULT 0;

-- Replace the (user_id, item_id) composite PK with a surrogate PK so that
-- a single item type can occupy multiple stacks (each capped at 999).
ALTER TABLE user_inventory DROP CONSTRAINT user_inventory_pkey;
ALTER TABLE user_inventory ADD COLUMN id BIGSERIAL;
ALTER TABLE user_inventory ADD PRIMARY KEY (id);
CREATE INDEX idx_user_inventory_user_item ON user_inventory(user_id, item_id);

-- Cap each stack at 999
ALTER TABLE user_inventory
    ADD CONSTRAINT chk_inventory_quantity_max
        CHECK (quantity <= 999);

-- Seed: Consumable items
INSERT INTO items (name, description, type, heal_hp)
VALUES
    ('小型草藥', '簡單的治癒草藥，恢復 20 點 HP。', 'CONSUMABLE', 20),
    ('中型草藥', '品質良好的草藥，恢復 50 點 HP。', 'CONSUMABLE', 50),
    ('大型草藥', '珍貴的高品質草藥，恢復 120 點 HP。', 'CONSUMABLE', 120);

-- Give all existing users 5 small herbs
INSERT INTO user_inventory (user_id, item_id, quantity)
SELECT u.id, i.id, 5
FROM users u
CROSS JOIN items i
WHERE i.type = 'CONSUMABLE' AND i.heal_hp = 20
  AND NOT EXISTS (
    SELECT 1 FROM user_inventory ui
    WHERE ui.user_id = u.id AND ui.item_id = i.id
  );
