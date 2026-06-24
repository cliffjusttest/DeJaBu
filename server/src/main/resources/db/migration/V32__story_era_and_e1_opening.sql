-- Personal story era (E1–E8) and E1 黃巾之亂 opening quest chain

ALTER TABLE users ADD COLUMN story_era VARCHAR(4) NOT NULL DEFAULT 'E1';

ALTER TABLE quests ADD COLUMN required_era VARCHAR(4) NOT NULL DEFAULT 'E1';
ALTER TABLE quests ADD COLUMN prerequisite_quest_id BIGINT REFERENCES quests(id);
ALTER TABLE quests ADD COLUMN unlocks_era VARCHAR(4);

UPDATE quests SET
    name = '初試身手',
    description = '中平元年，黃巾賊在潁川蠢動。前往潁川郊野，清除三隻出沒的野狼。',
    required_era = 'E1'
WHERE id = 1;

UPDATE quests SET
    name = '潁川賊營',
    description = '斥候回報潁川城北有賊營，黑霧繚繞。潜入營寨，消滅兩隻黑霧精靈。',
    required_era = 'E1',
    prerequisite_quest_id = 1
WHERE id = 2;

UPDATE dialogue_nodes SET text =
    '中平元年，黃巾賊起。許縣近来不太平靜，縣中正在徵募鄉勇。旅人，可願入伍助陣？'
WHERE npc_id = 'xuchang_elder' AND node_key = 'root';

UPDATE dialogue_nodes SET text =
    '潁川郊野有野狼傷人，先拿這些畜生練練手。完成後再來領賞。'
WHERE npc_id = 'xuchang_elder' AND node_key = 'quest_offer';

UPDATE dialogue_nodes SET text =
    '好！從東門出許縣，經許昌外郭往潁川郊野。小心黃巾旗號。'
WHERE npc_id = 'xuchang_elder' AND node_key = 'quest_accepted';

UPDATE dialogue_nodes SET text =
    '你已接下鄉勇差事，加油！'
WHERE npc_id = 'xuchang_elder' AND node_key = 'quest_already';

UPDATE dialogue_nodes SET text =
    '好漢！許縣上下都安心多了。這是你的酬勞。'
WHERE npc_id = 'xuchang_elder' AND node_key = 'quest_complete';

UPDATE dialogue_nodes SET text =
    '潁川方向傳來黃巾旗號，賊營就在城北。需要幫忙嗎？'
WHERE npc_id = 'yingchuan_scout' AND node_key = 'root';

UPDATE dialogue_nodes SET text =
    '賊營中黑霧精靈四處出沒，能幫忙消滅兩隻嗎？須先完成鄉老的試煉。'
WHERE npc_id = 'yingchuan_scout' AND node_key = 'quest_offer';

UPDATE dialogue_nodes SET text =
    '賊營在潁川城北，沿官道即可到達。請小心。'
WHERE npc_id = 'yingchuan_scout' AND node_key = 'quest_accepted';

UPDATE dialogue_nodes SET text =
    '辛苦了，這是酬勞。黃巾之亂尚未平息，後路還長。'
WHERE npc_id = 'yingchuan_scout' AND node_key = 'quest_complete';
