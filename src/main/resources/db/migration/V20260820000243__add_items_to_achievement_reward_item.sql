INSERT INTO achievement_reward_item (achievement_id, item_category, item_name, item_ref_id)
SELECT a.id, 'OUTFIT', ai.name, ai.id
FROM achievement a
         JOIN avatar_item ai ON ai.item_code = 'ACH-SP-001-ITEM'
WHERE a.code = 'ACH-SP-001';

INSERT INTO achievement_reward_item (achievement_id, item_category, item_name, item_ref_id)
SELECT a.id, 'OUTFIT', ai.name, ai.id
FROM achievement a
         JOIN avatar_item ai ON ai.item_code = 'ACH-SP-002-ITEM'
WHERE a.code = 'ACH-SP-002';

INSERT INTO achievement_reward_item (achievement_id, item_category, item_name, item_ref_id)
SELECT a.id, 'OUTFIT', ai.name, ai.id
FROM achievement a
         JOIN avatar_item ai ON ai.item_code = 'ACH-SP-003-ITEM'
WHERE a.code = 'ACH-SP-003';

INSERT INTO achievement_reward_item (achievement_id, item_category, item_name, item_ref_id)
SELECT a.id, 'OUTFIT', ai.name, ai.id
FROM achievement a
         JOIN avatar_item ai ON ai.item_code = 'ACH-SP-004-ITEM'
WHERE a.code = 'ACH-SP-004';

INSERT INTO achievement_reward_item (achievement_id, item_category, item_name, item_ref_id)
SELECT a.id, 'OUTFIT', ai.name, ai.id
FROM achievement a
         JOIN avatar_item ai ON ai.item_code = 'ACH-SP-005-ITEM'
WHERE a.code = 'ACH-SP-005';
