-- 운영 마스터 데이터: 고정 루틴 카테고리와 카테고리별 기본 제공 루틴.
--
-- 이 파일이 routine_category의 고정 카테고리와 routine_template의 단일 진실 공급원이다.
-- 운영 DB에서 값을 직접 고쳐도 다음 배포에 여기 적힌 값으로 되돌아간다.
--
-- R__seed_challenge.sql과 같은 규칙을 따른다.
--  1) id를 고정한다. auto increment에 맡기면 재적용 때 id가 달라져, member_routine이
--     참조하던 카테고리·템플릿이 바뀌어 버린다.
--  2) upsert로 쓴다. 매 배포마다 실행되므로 멱등해야 한다.
--  3) 목록에서 내릴 때는 줄을 지우지 말고 active를 FALSE로 바꾼다. upsert는 추가·수정만
--     하므로 줄을 지워도 DB에서는 사라지지 않는다.
--
-- 파일 이름이 R__seed_challenge.sql보다 알파벳 뒤라 챌린지 시드 다음에 실행된다.
-- 두 시드는 서로를 참조하지 않으므로 순서는 문제되지 않는다.

-- 고정 카테고리 (member_id NULL = 앱 제공)
--
-- id 1~6을 쓴다. V2에서 routine_category가 만들어진 뒤 시드가 없었으므로 운영에는 행이 없다.
-- 다만 로컬에서 손으로 카테고리를 넣어 본 DB는 id 1~6이 다른 이름으로 차 있을 수 있고,
-- 그런 행은 이 upsert가 덮어쓴다(그게 의도다 — 여기가 마스터다).
--
-- display_order가 목록 노출 순서다. 기획이 정한 순서를 그대로 넣는다.
-- 기획의 `기타` 카테고리는 쓰지 않기로 해서 목록에 없다.
INSERT INTO routine_category (id, member_id, name, color, display_order, active, created_at, updated_at)
VALUES
  (1, NULL, '운동',     NULL, 1, TRUE, NOW(), NOW()),
  (2, NULL, '건강',     NULL, 2, TRUE, NOW(), NOW()),
  (3, NULL, '자기계발', NULL, 3, TRUE, NOW(), NOW()),
  (4, NULL, '생활정리', NULL, 4, TRUE, NOW(), NOW()),
  (5, NULL, '마음관리', NULL, 5, TRUE, NOW(), NOW()),
  (6, NULL, '취미',     NULL, 6, TRUE, NOW(), NOW())
AS new_row
ON DUPLICATE KEY UPDATE
  member_id     = new_row.member_id,
  name          = new_row.name,
  color         = new_row.color,
  display_order = new_row.display_order,
  active        = new_row.active,
  updated_at    = NOW();

-- 기본 제공 루틴
--
-- id는 카테고리 번호 × 100 + 순번으로 둔다(운동 101~, 건강 201~ …). 카테고리에 루틴을
-- 추가할 때 그 카테고리 구간 뒤에 붙이면 되므로 다른 카테고리의 id를 건드리지 않는다.
--
-- display_order는 카테고리 안에서의 순서다. 회원이 직접 추가한 루틴은 display_order가
-- 0이 아니라 애초에 이 테이블에 없다 — member_routine에 생성 순서대로 쌓인다.
INSERT INTO routine_template (id, category_id, name, display_order, active, created_at, updated_at)
VALUES
  -- 운동
  (101, 1, '산책하기',            1, TRUE, NOW(), NOW()),
  (102, 1, '스트레칭하기',        2, TRUE, NOW(), NOW()),
  (103, 1, '홈트레이닝 하기',     3, TRUE, NOW(), NOW()),
  (104, 1, '러닝하기',            4, TRUE, NOW(), NOW()),
  (105, 1, '근력 운동하기',       5, TRUE, NOW(), NOW()),
  (106, 1, '헬스장 다녀오기',     6, TRUE, NOW(), NOW()),
  -- 건강
  (201, 2, '물 챙겨 마시기',      1, TRUE, NOW(), NOW()),
  (202, 2, '영양제 챙겨 먹기',    2, TRUE, NOW(), NOW()),
  (203, 2, '건강한 한 끼 먹기',   3, TRUE, NOW(), NOW()),
  (204, 2, '채소 챙겨 먹기',      4, TRUE, NOW(), NOW()),
  (205, 2, '단백질 챙겨 먹기',    5, TRUE, NOW(), NOW()),
  (206, 2, '집밥 먹기',           6, TRUE, NOW(), NOW()),
  -- 자기계발
  (301, 3, '책 읽기',                 1, TRUE, NOW(), NOW()),
  (302, 3, '뉴스 읽기',               2, TRUE, NOW(), NOW()),
  (303, 3, '관심 분야 자료 정리하기', 3, TRUE, NOW(), NOW()),
  (304, 3, '외국어 공부하기',         4, TRUE, NOW(), NOW()),
  (305, 3, '강연 보고 기록 남기기',   5, TRUE, NOW(), NOW()),
  (306, 3, '포트폴리오 작업하기',     6, TRUE, NOW(), NOW()),
  -- 생활정리
  (401, 4, '침대 정리하기',   1, TRUE, NOW(), NOW()),
  (402, 4, '쓰레기 버리기',   2, TRUE, NOW(), NOW()),
  (403, 4, '책상 정리하기',   3, TRUE, NOW(), NOW()),
  (404, 4, '설거지하기',      4, TRUE, NOW(), NOW()),
  (405, 4, '빨래하기',        5, TRUE, NOW(), NOW()),
  (406, 4, '방 정리하기',     6, TRUE, NOW(), NOW()),
  (407, 4, '바닥 청소하기',   7, TRUE, NOW(), NOW()),
  -- 마음관리
  (501, 5, '긍정 문장 남기기', 1, TRUE, NOW(), NOW()),
  (502, 5, '일기 쓰기',        2, TRUE, NOW(), NOW()),
  (503, 5, '감사 기록 쓰기',   3, TRUE, NOW(), NOW()),
  (504, 5, '감정 기록 남기기', 4, TRUE, NOW(), NOW()),
  -- 취미
  (601, 6, '사진 찍기',       1, TRUE, NOW(), NOW()),
  (602, 6, '다이어리 쓰기',   2, TRUE, NOW(), NOW()),
  (603, 6, '글쓰기',          3, TRUE, NOW(), NOW()),
  (604, 6, '그림 그리기',     4, TRUE, NOW(), NOW()),
  (605, 6, '요리하기',        5, TRUE, NOW(), NOW()),
  (606, 6, '악기 연습하기',   6, TRUE, NOW(), NOW()),
  (607, 6, '취미생활하기',    7, TRUE, NOW(), NOW())
AS new_row
ON DUPLICATE KEY UPDATE
  category_id   = new_row.category_id,
  name          = new_row.name,
  display_order = new_row.display_order,
  active        = new_row.active,
  updated_at    = NOW();
