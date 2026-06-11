CREATE TABLE items (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(64)  NOT NULL,
    description      VARCHAR(256),
    slot             VARCHAR(16)  NOT NULL,
    required_level   INT          NOT NULL DEFAULT 1,
    bonus_might       INT          NOT NULL DEFAULT 0,
    bonus_intelligence INT        NOT NULL DEFAULT 0,
    bonus_vitality    INT          NOT NULL DEFAULT 0,
    bonus_defense     INT          NOT NULL DEFAULT 0,
    bonus_spirit      INT          NOT NULL DEFAULT 0,
    bonus_luck        INT          NOT NULL DEFAULT 0,
    bonus_agility     INT          NOT NULL DEFAULT 0
);

CREATE TABLE user_equipment (
    user_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    slot     VARCHAR(16) NOT NULL,
    item_id  BIGINT      NOT NULL REFERENCES items(id),
    PRIMARY KEY (user_id, slot)
);

-- Seed: HEAD items
INSERT INTO items (name, description, slot, required_level, bonus_vitality, bonus_defense)
VALUES ('皮革頭盔', '基本皮革製頭盔，提供少量防護。', 'HEAD', 1, 1, 2);

INSERT INTO items (name, description, slot, required_level, bonus_vitality, bonus_defense, bonus_spirit)
VALUES ('鐵盔', '厚重的鐵製頭盔，大幅提升防禦力。', 'HEAD', 5, 2, 5, 1);

-- Seed: FACE items
INSERT INTO items (name, description, slot, required_level, bonus_luck)
VALUES ('幸運護目鏡', '奇特的護目鏡，佩戴者似乎更容易遇到好事。', 'FACE', 1, 3);

INSERT INTO items (name, description, slot, required_level, bonus_intelligence, bonus_spirit)
VALUES ('智者面具', '古老的面具，配戴時思路格外清晰。', 'FACE', 5, 3, 2);

-- Seed: SHOULDER items
INSERT INTO items (name, description, slot, required_level, bonus_defense, bonus_vitality)
VALUES ('護肩甲', '強化肩部的金屬護具。', 'SHOULDER', 1, 2, 1);

INSERT INTO items (name, description, slot, required_level, bonus_might, bonus_defense)
VALUES ('戰士肩甲', '粗獷的肩甲，令人望而生畏。', 'SHOULDER', 5, 3, 3);

-- Seed: HAND items
INSERT INTO items (name, description, slot, required_level, bonus_might)
VALUES ('皮手套', '基本皮革手套，攻擊時更順手。', 'HAND', 1, 2);

INSERT INTO items (name, description, slot, required_level, bonus_might, bonus_agility)
VALUES ('戰鬥護手', '強化拳力與速度的護手。', 'HAND', 5, 3, 2);

-- Seed: BODY items
INSERT INTO items (name, description, slot, required_level, bonus_defense, bonus_vitality)
VALUES ('皮革鎧甲', '輕便的皮革護甲，兼顧防護與機動。', 'BODY', 1, 3, 2);

INSERT INTO items (name, description, slot, required_level, bonus_defense, bonus_vitality, bonus_spirit)
VALUES ('鐵製鎧甲', '厚重的鐵鎧，抵禦強力衝擊。', 'BODY', 5, 6, 3, 2);

-- Seed: LEG items
INSERT INTO items (name, description, slot, required_level, bonus_defense, bonus_agility)
VALUES ('皮腿甲', '基本腿部護具。', 'LEG', 1, 2, 1);

INSERT INTO items (name, description, slot, required_level, bonus_defense, bonus_vitality, bonus_agility)
VALUES ('鐵腿甲', '重型腿部護甲，犧牲一點速度換取防護。', 'LEG', 5, 4, 2, -1);

-- Seed: FOOT items
INSERT INTO items (name, description, slot, required_level, bonus_agility)
VALUES ('輕便靴', '輕盈的靴子，讓腳步更加靈活。', 'FOOT', 1, 3);

INSERT INTO items (name, description, slot, required_level, bonus_agility, bonus_luck)
VALUES ('疾風靴', '傳說中的靴子，穿上後如風一般迅速。', 'FOOT', 5, 5, 2);

-- Seed: BACK items
INSERT INTO items (name, description, slot, required_level, bonus_spirit)
VALUES ('旅人披風', '結實的旅行披風，補充精力。', 'BACK', 1, 2);

INSERT INTO items (name, description, slot, required_level, bonus_spirit, bonus_intelligence)
VALUES ('法師斗篷', '充滿神秘力量的斗篷。', 'BACK', 5, 4, 3);

-- Seed: ACCESSORY items
INSERT INTO items (name, description, slot, required_level, bonus_luck)
VALUES ('幸運符咒', '據說能帶來幸運的小符咒。', 'ACCESSORY', 1, 2);

INSERT INTO items (name, description, slot, required_level, bonus_might, bonus_intelligence, bonus_luck)
VALUES ('勇者徽章', '彰顯勇者榮耀的徽章，各項能力均有提升。', 'ACCESSORY', 5, 2, 2, 3);
