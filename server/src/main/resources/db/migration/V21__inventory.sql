ALTER TABLE items ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'EQUIPMENT';

CREATE TABLE user_inventory (
    user_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id  BIGINT NOT NULL REFERENCES items(id),
    quantity INT    NOT NULL DEFAULT 1 CHECK (quantity > 0),
    PRIMARY KEY (user_id, item_id)
);

CREATE TABLE companion_equipment (
    companion_id BIGINT      NOT NULL REFERENCES user_companions(id) ON DELETE CASCADE,
    slot         VARCHAR(16) NOT NULL,
    item_id      BIGINT      NOT NULL REFERENCES items(id),
    PRIMARY KEY (companion_id, slot)
);

-- Give all existing users the level-1 equipment they don't already have equipped
INSERT INTO user_inventory (user_id, item_id, quantity)
SELECT u.id, i.id, 1
FROM users u
CROSS JOIN items i
WHERE i.required_level = 1
  AND NOT EXISTS (
    SELECT 1 FROM user_equipment ue
    WHERE ue.user_id = u.id AND ue.item_id = i.id
  );
