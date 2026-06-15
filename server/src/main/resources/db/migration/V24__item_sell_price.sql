-- Base sell/recycle price when player sells item to a merchant (0 = cannot sell)
ALTER TABLE items ADD COLUMN sell_price INT NOT NULL DEFAULT 0;

-- Consumables
UPDATE items SET sell_price = 5  WHERE type = 'CONSUMABLE' AND heal_hp = 20;
UPDATE items SET sell_price = 15 WHERE type = 'CONSUMABLE' AND heal_hp = 50;
UPDATE items SET sell_price = 35 WHERE type = 'CONSUMABLE' AND heal_hp = 120;

-- Equipment: Lv.1 gear
UPDATE items SET sell_price = 25 WHERE type = 'EQUIPMENT' AND required_level = 1;

-- Equipment: Lv.5 gear
UPDATE items SET sell_price = 55 WHERE type = 'EQUIPMENT' AND required_level = 5;
