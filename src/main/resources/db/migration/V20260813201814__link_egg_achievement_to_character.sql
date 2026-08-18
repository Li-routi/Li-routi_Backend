UPDATE achievement SET target_count = 20 WHERE code = 'ACH-EG-007';
-- 캐릭터알 12종을 해당 업적 claim에 연결한다.
-- 노아(ACH-EG-003)·파도(ACH-EG-006)는 최신 세부조건(연속 기록 기반)과 실제로는 다르다 -
-- 세부조건에 맞는 "특정 루틴 연속 완료" 메커니즘이 아직 없어 임시로 기존 누적/일수 기반
-- achievement에 연결해둔다. TODO: 노아·파도 전용 연속 추적 메커니즘 별도 구현 필요.
INSERT INTO character_unlock_condition (character_id, condition_key, condition_param, target_count, sort_order, created_at, updated_at)
SELECT ac.id, 'ACHIEVEMENT_CLAIMED', v.achievement_code, 1, 1, NOW(), NOW()
FROM avatar_character ac
         JOIN (
    SELECT '민트' AS char_name, 'ACH-EG-001' AS achievement_code UNION ALL
    SELECT '동글', 'ACH-EG-002' UNION ALL
    SELECT '노아', 'ACH-EG-003' UNION ALL  -- TODO: 임시 매핑, 세부조건과 다름
    SELECT '코코', 'ACH-EG-004' UNION ALL
    SELECT '유키', 'ACH-EG-005' UNION ALL
    SELECT '파도', 'ACH-EG-006' UNION ALL  -- TODO: 임시 매핑, 세부조건과 다름
    SELECT '까루', 'ACH-EG-007' UNION ALL
    SELECT '다미', 'ACH-EG-008' UNION ALL
    SELECT '모리', 'ACH-EG-009' UNION ALL
    SELECT '삐아', 'ACH-EG-010' UNION ALL
    SELECT '호롱', 'ACH-EG-011' UNION ALL
    SELECT '솔라', 'ACH-EG-012'
) AS v ON v.char_name = ac.name;