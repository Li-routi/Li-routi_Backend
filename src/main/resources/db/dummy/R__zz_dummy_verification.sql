-- 로컬 개발용 더미 데이터: 인증(개인 루틴·그룹 루틴). local 프로파일에서만 적용된다.
--
-- R__dummy_local_data.sql·R__zz_dummy_routine.sql과 규칙은 같다
-- (9000번대 id, upsert, IF(id >= 9000, ...) 갱신).
--
-- 이 파일이 zz_dummy_verification인 이유는 실행 순서다.
--  * 개인 루틴 인증은 R__zz_dummy_routine.sql이 만든 member_routine(9001~9003)을 참조한다.
--    "zz_dummy_routine" < "zz_dummy_verification"(r < v)이라 이 파일이 그 뒤에 돈다.
--  * 그룹 루틴은 db/migration/R__seed_group_routine_category.sql의 고정 카테고리(id 1~6)를
--    참조한다. "dummy_local_data"(d)보다도, "seed_group_routine_category"(s)보다도 뒤에
--    와야 하는데 "zz_..."는 어차피 전부보다 뒤라 문제없다.
--
-- 챌린지 인증의 PENDING(심사 보류) 케이스는 R__dummy_local_data.sql 6번 섹션(kyuunn9@gmail.com,
-- id 9007)에 이미 있다. 여기서 다시 만들지 않는다.
--
-- 날짜는 CURRENT_DATE() 기준 상대값이라 실행 시점마다 "최근 일주일"로 다시 계산된다.

-- ============================================================
-- 1) 개인 루틴 인증
-- ============================================================
-- member_routine 9001(물 챙겨 마시기·매일) 9002(아침 산책·월~금) 9003(사이드 30분·토·일)
-- 기준. 하루 안에서 완료/미완료를 섞어 달성률이 100%/부분/0%로 다양하게 나오게 한다 —
-- 리포트 화면(주간 바·월간 캘린더) 테스트가 이 다양성에 의존한다.
--
--   오늘        9001 O, 9002 O  (해당 요일이면) → 100%
--   -1일        9001 O            → 부분
--   -2일        9001 O, 9003 O  (해당 요일이면) → 100%
--   -4일        9001 O, 9002 O  (해당 요일이면) → 100%
--   -5일        9002 O            → 부분
-- 나머지 날짜는 행을 넣지 않아 0%로 남는다.
INSERT INTO member_routine_verification
(id, member_routine_id, verified_date, verified_at, image_url, content, created_at, updated_at)
VALUES
    (9001, 9001, CURRENT_DATE(),                           NOW(),                          'routine-verifications/00000000-0000-4000-8000-000000019001.jpg', NULL, NOW(), NOW()),
    (9002, 9002, CURRENT_DATE(),                           NOW(),                          'routine-verifications/00000000-0000-4000-8000-000000019002.jpg', NULL, NOW(), NOW()),
    (9003, 9001, DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 'routine-verifications/00000000-0000-4000-8000-000000019003.jpg', NULL, NOW(), NOW()),
    (9004, 9001, DATE_SUB(CURRENT_DATE(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 'routine-verifications/00000000-0000-4000-8000-000000019004.jpg', NULL, NOW(), NOW()),
    (9005, 9003, DATE_SUB(CURRENT_DATE(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 'routine-verifications/00000000-0000-4000-8000-000000019005.jpg', NULL, NOW(), NOW()),
    (9006, 9001, DATE_SUB(CURRENT_DATE(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 'routine-verifications/00000000-0000-4000-8000-000000019006.jpg', NULL, NOW(), NOW()),
    (9007, 9002, DATE_SUB(CURRENT_DATE(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 'routine-verifications/00000000-0000-4000-8000-000000019007.jpg', NULL, NOW(), NOW()),
    (9008, 9002, DATE_SUB(CURRENT_DATE(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 'routine-verifications/00000000-0000-4000-8000-000000019008.jpg', NULL, NOW(), NOW())
    AS new_row
ON DUPLICATE KEY UPDATE
                     verified_date = IF(member_routine_verification.id >= 9000, new_row.verified_date, member_routine_verification.verified_date),
                     verified_at   = IF(member_routine_verification.id >= 9000, new_row.verified_at, member_routine_verification.verified_at),
                     image_url     = IF(member_routine_verification.id >= 9000, new_row.image_url, member_routine_verification.image_url),
                     updated_at    = IF(member_routine_verification.id >= 9000, NOW(), member_routine_verification.updated_at);

-- ============================================================
-- 2) 그룹 루틴 (더미 그룹 9001 위에 하나 만든다)
-- ============================================================
-- group_routine_category 1='운동'(운영 시드, R__seed_group_routine_category.sql 고정 id).
-- 월~금 저녁 운동 루틴으로 잡는다. category_id는 폐기 예정 컬럼이라(V12) NULL로 둔다.
INSERT INTO group_routine
(id, group_id, category_id, group_routine_category_id, title, description, active, created_at, updated_at)
VALUES
    (9001, 9001, NULL, 1, '[더미] 저녁 운동', '평일 저녁 함께 운동하기', TRUE, NOW(6), NOW(6))
    AS new_row
ON DUPLICATE KEY UPDATE
                     group_routine_category_id = IF(group_routine.id >= 9000, new_row.group_routine_category_id, group_routine.group_routine_category_id),
                     title       = IF(group_routine.id >= 9000, new_row.title, group_routine.title),
                     description = IF(group_routine.id >= 9000, new_row.description, group_routine.description),
                     active      = IF(group_routine.id >= 9000, new_row.active, group_routine.active),
                     updated_at  = IF(group_routine.id >= 9000, NOW(6), group_routine.updated_at);

INSERT INTO group_routine_schedule
(id, group_routine_id, start_time, end_time, repeat_day, created_at, updated_at)
VALUES
    (9001, 9001, '19:00:00', '20:00:00', 'MONDAY',    NOW(6), NOW(6)),
    (9002, 9001, '19:00:00', '20:00:00', 'TUESDAY',   NOW(6), NOW(6)),
    (9003, 9001, '19:00:00', '20:00:00', 'WEDNESDAY', NOW(6), NOW(6)),
    (9004, 9001, '19:00:00', '20:00:00', 'THURSDAY',  NOW(6), NOW(6)),
    (9005, 9001, '19:00:00', '20:00:00', 'FRIDAY',    NOW(6), NOW(6))
    AS new_row
ON DUPLICATE KEY UPDATE
                     start_time = IF(group_routine_schedule.id >= 9000, new_row.start_time, group_routine_schedule.start_time),
                     end_time   = IF(group_routine_schedule.id >= 9000, new_row.end_time, group_routine_schedule.end_time),
                     updated_at = IF(group_routine_schedule.id >= 9000, NOW(6), group_routine_schedule.updated_at);

-- 오늘·어제 배정. 더미유저1(9001)은 둘 다 완료, 더미유저2(9002)는 오늘은 대기중(PENDING),
-- 어제는 미이행(MISSED) — 상태별 화면을 한 번에 볼 수 있게 한다.
INSERT INTO group_routine_assignment
(id, group_routine_id, member_id, assigned_date, scheduled_start_time, scheduled_end_time, status, version, created_at, updated_at)
VALUES
    (9001, 9001, 9001, CURRENT_DATE(),                           '19:00:00', '20:00:00', 'COMPLETED', 0, NOW(6), NOW(6)),
    (9002, 9001, 9002, CURRENT_DATE(),                           '19:00:00', '20:00:00', 'PENDING',   0, NOW(6), NOW(6)),
    (9003, 9001, 9001, DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), '19:00:00', '20:00:00', 'COMPLETED', 0, NOW(6), NOW(6)),
    (9004, 9001, 9002, DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), '19:00:00', '20:00:00', 'MISSED',    0, NOW(6), NOW(6))
    AS new_row
ON DUPLICATE KEY UPDATE
                     status     = IF(group_routine_assignment.id >= 9000, new_row.status, group_routine_assignment.status),
                     updated_at = IF(group_routine_assignment.id >= 9000, NOW(6), group_routine_assignment.updated_at);

-- COMPLETED 건에만 인증을 붙인다(9001·9003 — 더미유저1의 오늘·어제).
INSERT INTO group_routine_verification
(id, group_routine_assignment_id, verified_at, image_url, content, created_at, updated_at)
VALUES
    (9001, 9001, NOW(),                          'group-routine-verifications/00000000-0000-4000-8000-000000029001.jpg', '오늘 운동 완료', NOW(6), NOW(6)),
    (9002, 9003, DATE_SUB(NOW(), INTERVAL 1 DAY), 'group-routine-verifications/00000000-0000-4000-8000-000000029002.jpg', '어제 운동 완료', NOW(6), NOW(6))
    AS new_row
ON DUPLICATE KEY UPDATE
                     verified_at = IF(group_routine_verification.id >= 9000, new_row.verified_at, group_routine_verification.verified_at),
                     image_url   = IF(group_routine_verification.id >= 9000, new_row.image_url, group_routine_verification.image_url),
                     content     = IF(group_routine_verification.id >= 9000, new_row.content, group_routine_verification.content),
                     updated_at  = IF(group_routine_verification.id >= 9000, NOW(6), group_routine_verification.updated_at);
