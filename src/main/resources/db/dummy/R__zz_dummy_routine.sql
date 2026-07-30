-- 로컬 개발용 더미 데이터: 개인 루틴. local 프로파일에서만 적용된다.
--
-- R__dummy_local_data.sql과 규칙은 같다(9000번대 id, upsert, IF(id >= 9000, ...) 갱신).
-- 나눈 이유는 하나뿐이다 — 실행 순서.
--
-- Flyway는 R__ 마이그레이션을 파일명이 아니라 "설명"(R__ 뒤 부분)의 알파벳 순으로 돌린다.
-- 이 데이터는 운영 마스터인 db/migration/R__seed_routine.sql이 넣는 고정 카테고리(id 1~6)와
-- 기본 제공 루틴(id 101~)을 참조하는데, "dummy local data"는 "seed routine"보다 앞이라
-- 같은 파일에 두면 새 DB에서 FK 위반으로 부팅이 깨진다. zz 접두사는 이 파일을 시드 뒤로
-- 보내려는 것이다. 새 더미를 추가할 때 운영 시드를 참조하지 않는다면 원래 파일에 넣으면 된다.

-- 1) 사용자 카테고리
--
-- 고정 카테고리는 시드가 넣으므로 여기서는 "회원이 직접 만든 것"만 넣는다.
-- display_order는 사용자 카테고리 규칙대로 0이다(목록에서 생성 순서로 정렬된다).
INSERT INTO routine_category (id, member_id, name, color, display_order, active, created_at, updated_at)
VALUES
  (9001, 9001, '[더미]사이드', 'BLUE', 0, TRUE, NOW(), NOW())
AS new_row
ON DUPLICATE KEY UPDATE
  member_id  = IF(routine_category.id >= 9000, new_row.member_id, routine_category.member_id),
  name       = IF(routine_category.id >= 9000, new_row.name, routine_category.name),
  color      = IF(routine_category.id >= 9000, new_row.color, routine_category.color),
  active     = IF(routine_category.id >= 9000, new_row.active, routine_category.active),
  updated_at = IF(routine_category.id >= 9000, NOW(), routine_category.updated_at);

-- 2) 개인 루틴
--
-- 더미유저1(9001)에게 세 가지 상태를 하나씩 만들어 둔다. 세 경우가 목록·생성 API에서
-- 각각 다르게 보여야 한다.
--  * 9001 — 이름을 그대로 둔 기본 루틴. templates 조회에서 alreadyAdded=true로 나오고,
--           같은 templateId로 다시 생성하면 409가 나야 한다.
--  * 9002 — 기본 루틴의 이름을 바꾼 루틴. routine_template_id가 없으므로 원본(101 산책하기)은
--           여전히 고를 수 있다.
--  * 9003 — 사용자 카테고리에 직접 추가한 루틴.
--
-- category_id 2 = 건강, 1 = 운동, routine_template_id 201 = '물 챙겨 마시기'.
-- 모두 db/migration/R__seed_routine.sql의 고정 id다.
INSERT INTO member_routine
  (id, member_id, category_id, routine_template_id, name, end_time, alarm_time, active, created_at, updated_at)
VALUES
  (9001, 9001,    2,  201, '물 챙겨 마시기', '23:59:00', NULL,       TRUE, NOW(), NOW()),
  (9002, 9001,    1, NULL, '아침 산책',      '09:00:00', '08:30:00', TRUE, NOW(), NOW()),
  (9003, 9001, 9001, NULL, '사이드 30분',    '22:00:00', NULL,       TRUE, NOW(), NOW())
AS new_row
ON DUPLICATE KEY UPDATE
  category_id         = IF(member_routine.id >= 9000, new_row.category_id, member_routine.category_id),
  routine_template_id = IF(member_routine.id >= 9000, new_row.routine_template_id, member_routine.routine_template_id),
  name                = IF(member_routine.id >= 9000, new_row.name, member_routine.name),
  end_time            = IF(member_routine.id >= 9000, new_row.end_time, member_routine.end_time),
  alarm_time          = IF(member_routine.id >= 9000, new_row.alarm_time, member_routine.alarm_time),
  active              = IF(member_routine.id >= 9000, new_row.active, member_routine.active),
  updated_at          = IF(member_routine.id >= 9000, NOW(), member_routine.updated_at);

-- 3) 반복 요일
--
-- 9001은 매일, 9002는 주중, 9003은 주말이다. 화면의 반복 표기(매일·주중·금요일마다)를
-- 만들어 내는 쪽에서 세 패턴을 다 볼 수 있게 한 것이다.
INSERT INTO member_routine_schedule (id, member_routine_id, repeat_day, created_at, updated_at)
VALUES
  (9001, 9001, 'MONDAY',    NOW(), NOW()),
  (9002, 9001, 'TUESDAY',   NOW(), NOW()),
  (9003, 9001, 'WEDNESDAY', NOW(), NOW()),
  (9004, 9001, 'THURSDAY',  NOW(), NOW()),
  (9005, 9001, 'FRIDAY',    NOW(), NOW()),
  (9006, 9001, 'SATURDAY',  NOW(), NOW()),
  (9007, 9001, 'SUNDAY',    NOW(), NOW()),
  (9008, 9002, 'MONDAY',    NOW(), NOW()),
  (9009, 9002, 'TUESDAY',   NOW(), NOW()),
  (9010, 9002, 'WEDNESDAY', NOW(), NOW()),
  (9011, 9002, 'THURSDAY',  NOW(), NOW()),
  (9012, 9002, 'FRIDAY',    NOW(), NOW()),
  (9013, 9003, 'SATURDAY',  NOW(), NOW()),
  (9014, 9003, 'SUNDAY',    NOW(), NOW())
AS new_row
ON DUPLICATE KEY UPDATE
  member_routine_id = IF(member_routine_schedule.id >= 9000, new_row.member_routine_id, member_routine_schedule.member_routine_id),
  repeat_day        = IF(member_routine_schedule.id >= 9000, new_row.repeat_day, member_routine_schedule.repeat_day),
  updated_at        = IF(member_routine_schedule.id >= 9000, NOW(), member_routine_schedule.updated_at);
