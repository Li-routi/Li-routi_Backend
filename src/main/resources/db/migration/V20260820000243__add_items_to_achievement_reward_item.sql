INSERT INTO achievement_reward_item (achievement_id, item_category, item_name, item_ref_id)
SELECT a.id, 'OUTFIT', ai.name, ai.id
FROM achievement a
         JOIN avatar_item ai ON ai.name = '응원 폼폼'
WHERE a.code = 'ACH-SP-001';

INSERT INTO achievement_reward_item (achievement_id, item_category, item_name, item_ref_id)
SELECT a.id, 'OUTFIT', ai.name, ai.id
FROM achievement a
         JOIN avatar_item ai ON ai.name = '100일 왕관'
WHERE a.code = 'ACH-SP-002';

INSERT INTO achievement_reward_item (achievement_id, item_category, item_name, item_ref_id)
SELECT a.id, 'OUTFIT', ai.name, ai.id
FROM achievement a
         JOIN avatar_item ai ON ai.name = '파란 완주 유니폼'
WHERE a.code = 'ACH-SP-003';

INSERT INTO achievement_reward_item (achievement_id, item_category, item_name, item_ref_id)
SELECT a.id, 'OUTFIT', ai.name, ai.id
FROM achievement a
         JOIN avatar_item ai ON ai.name = '토마토 셔츠'
WHERE a.code = 'ACH-SP-004';

INSERT INTO achievement_reward_item (achievement_id, item_category, item_name, item_ref_id)
SELECT a.id, 'OUTFIT', ai.name, ai.id
FROM achievement a
         JOIN avatar_item ai ON ai.name = '임금님 망토'
WHERE a.code = 'ACH-SP-005';
