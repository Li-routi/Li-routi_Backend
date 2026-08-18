-- '빨리 좀 해'(ACH-AC-007) 기획 제거.
-- 이 업적에 진행 기록이 있던 회원 4명은 전부 IN_PROGRESS(미달성) 상태였음을
-- 배포 전 확인했다 - 보상이 지급된 적이 없으므로 재화 회수 등 부가 조치가 필요 없다.

DELETE FROM member_achievement_progress_day
WHERE member_achievement_id IN (
    SELECT id FROM member_achievement
    WHERE achievement_id = (SELECT id FROM achievement WHERE code = 'ACH-AC-007')
);

DELETE FROM member_achievement_progress_category
WHERE member_achievement_id IN (
    SELECT id FROM member_achievement
    WHERE achievement_id = (SELECT id FROM achievement WHERE code = 'ACH-AC-007')
);

DELETE FROM member_achievement_condition
WHERE member_achievement_id IN (
    SELECT id FROM member_achievement
    WHERE achievement_id = (SELECT id FROM achievement WHERE code = 'ACH-AC-007')
);

DELETE FROM member_achievement
WHERE achievement_id = (SELECT id FROM achievement WHERE code = 'ACH-AC-007');

DELETE FROM achievement_routine_category
WHERE achievement_id = (SELECT id FROM achievement WHERE code = 'ACH-AC-007');

DELETE FROM achievement WHERE code = 'ACH-AC-007';
