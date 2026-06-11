-- NPCs placed in the world
CREATE TABLE npcs (
    id VARCHAR(64) PRIMARY KEY,
    map_id VARCHAR(32) NOT NULL,
    grid_x INTEGER NOT NULL,
    grid_y INTEGER NOT NULL,
    name VARCHAR(64) NOT NULL,
    sprite_key VARCHAR(32) NOT NULL DEFAULT 'default',
    root_node_key VARCHAR(64) NOT NULL DEFAULT 'root'
);

-- Dialogue tree nodes (choices stored as JSON)
CREATE TABLE dialogue_nodes (
    id BIGSERIAL PRIMARY KEY,
    npc_id VARCHAR(64) NOT NULL REFERENCES npcs(id),
    node_key VARCHAR(64) NOT NULL,
    text TEXT NOT NULL,
    choices_json TEXT NOT NULL DEFAULT '[]',
    UNIQUE (npc_id, node_key)
);

-- Quest definitions
CREATE TABLE quests (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    quest_type VARCHAR(16) NOT NULL,       -- KILL, TALK
    target_id VARCHAR(64) NOT NULL,        -- monster template id (KILL) or npc id (TALK)
    required_count INTEGER NOT NULL DEFAULT 1,
    reward_exp INTEGER NOT NULL DEFAULT 0,
    reward_skill_points INTEGER NOT NULL DEFAULT 0,
    giver_npc_id VARCHAR(64) REFERENCES npcs(id)
);

-- Per-player quest progress
CREATE TABLE player_quests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    quest_id BIGINT NOT NULL REFERENCES quests(id),
    status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS, COMPLETED
    progress INTEGER NOT NULL DEFAULT 0,
    UNIQUE (user_id, quest_id)
);

-- ── Seed: NPCs ────────────────────────────────────────────────────────────────

INSERT INTO npcs (id, map_id, grid_x, grid_y, name, sprite_key, root_node_key) VALUES
    ('village_elder', 'village', 5, 8, '村長', 'elder', 'root'),
    ('forest_merchant', 'forest', 12, 5, '行商', 'merchant', 'root');

-- ── Seed: Quests ──────────────────────────────────────────────────────────────

INSERT INTO quests (name, description, quest_type, target_id, required_count, reward_exp, reward_skill_points, giver_npc_id) VALUES
    ('初試身手', '消滅村子附近出現的野狼，讓村民安心。', 'KILL', 'wild_wolf', 3, 60, 1, 'village_elder'),
    ('黑霧調查', '森林深處出現詭異的黑霧，調查其來源並消滅黑霧精靈。', 'KILL', 'shadow_wisp', 2, 80, 1, 'forest_merchant');

-- ── Seed: Dialogue nodes ──────────────────────────────────────────────────────

-- Village Elder
INSERT INTO dialogue_nodes (npc_id, node_key, text, choices_json) VALUES
    ('village_elder', 'root',
     '歡迎來到新手村，旅人！村子最近不太平靜，有任何需要嗎？',
     '[{"text":"我想接任務","nextKey":"quest_offer"},{"text":"再見","nextKey":null}]'),

    ('village_elder', 'quest_offer',
     '附近野狼橫行，能幫我們消滅三隻嗎？完成後有豐厚報酬。',
     '[{"text":"接受任務（初試身手）","nextKey":"quest_accepted","questAccept":1},{"text":"暫時不需要","nextKey":"root"}]'),

    ('village_elder', 'quest_accepted',
     '太好了！請小心行事，消滅三隻野狼後再回來找我。',
     '[{"text":"好的，出發","nextKey":null}]'),

    ('village_elder', 'quest_already',
     '你已接下任務，加油！消滅三隻野狼後再回來。',
     '[{"text":"明白了","nextKey":null}]'),

    ('village_elder', 'quest_complete',
     '你真厲害，已消滅了三隻野狼！這是你應得的報酬。',
     '[{"text":"領取報酬","nextKey":null,"questComplete":1}]');

-- Forest Merchant
INSERT INTO dialogue_nodes (npc_id, node_key, text, choices_json) VALUES
    ('forest_merchant', 'root',
     '哦，旅人！幽暗森林可危險得很，你還真勇敢。我是遠方行商，有任何需要嗎？',
     '[{"text":"我想接任務","nextKey":"quest_offer"},{"text":"這裡有何注意","nextKey":"tips"},{"text":"再見","nextKey":null}]'),

    ('forest_merchant', 'quest_offer',
     '森林深處黑霧越來越濃，黑霧精靈四處出沒。能幫我消滅兩隻嗎？',
     '[{"text":"接受任務（黑霧調查）","nextKey":"quest_accepted","questAccept":2},{"text":"暫時不需要","nextKey":"root"}]'),

    ('forest_merchant', 'quest_accepted',
     '感謝你！消滅兩隻黑霧精靈後再回來找我。',
     '[{"text":"好的，出發","nextKey":null}]'),

    ('forest_merchant', 'quest_already',
     '任務還沒完成，繼續加油！消滅兩隻黑霧精靈後回來。',
     '[{"text":"明白了","nextKey":null}]'),

    ('forest_merchant', 'quest_complete',
     '你成功了！黑霧似乎消散了不少，感謝你，這是報酬。',
     '[{"text":"領取報酬","nextKey":null,"questComplete":2}]'),

    ('forest_merchant', 'tips',
     '森林怪物屬性複雜，善用元素克制！另外，捕捉夥伴也是個好選擇，牠們能在戰鬥中幫助你。',
     '[{"text":"謝謝建議","nextKey":"root"}]');
