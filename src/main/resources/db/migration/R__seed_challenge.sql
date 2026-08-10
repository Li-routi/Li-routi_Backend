-- 운영 마스터 데이터: 앱이 제공하는 챌린지 목록.
--
-- 이 파일이 challenge 테이블의 단일 진실 공급원이다. 운영 DB에서 값을 직접 고쳐도
-- 다음 배포에 여기 적힌 값으로 되돌아간다.
--
-- R__(repeatable) 마이그레이션이라 "파일 내용이 바뀔 때마다" 다시 실행된다.
-- 항상 V__ 마이그레이션이 모두 끝난 뒤에 돌기 때문에, 구조를 바꾸면서(V2 추가) 이 파일을
-- 같이 고치면 구조 → 데이터 순서로 알아서 재적용된다.
--
-- 규칙 두 가지를 반드시 지킨다.
--  1) id를 고정한다. auto increment에 맡기면 재적용 때 다른 id가 생겨서,
--     member_challenge가 참조하던 챌린지가 바뀌어 버린다.
--  2) INSERT ... ON DUPLICATE KEY UPDATE(upsert)로 쓴다. 매 배포마다 실행되므로
--     멱등해야 한다. INSERT IGNORE는 첫 삽입만 하고 이후 값 변경이 반영되지 않아 쓰지 않는다.
--  3) 챌린지를 내릴 때는 아래 목록에서 줄을 지우지 말고 active를 FALSE로 바꾼다.
--     upsert는 추가·수정만 하므로 여기서 줄을 지워도 DB에서는 사라지지 않는다. 지운 채로 두면
--     그 행만 아무도 관리하지 않는 상태로 운영에 계속 노출된다. 마스터 데이터는 원래
--     소프트 삭제 대신 active로 노출을 제어하고(database-schema.md), member_challenge가
--     참조 중인 챌린지는 물리 삭제도 불가능하다.
--
-- image_url은 넣지 않는다. 비어 있으면 그 챌린지에서 좋아요를 가장 많이 받은 인증 사진이
-- 표지가 되고, 인증이 없으면 클라이언트가 분류별 기본 이미지를 그린다.
--
-- 여기에 기본 이미지를 채우면 안 된다. 이 컬럼은 표지 폴백의 1순위(운영이 지정한 표지)라,
-- 값이 있으면 인증 사진을 항상 이긴다. 모든 챌린지에 같은 그림을 넣어 두면 인증이 아무리
-- 쌓여도 영원히 그 그림만 보이는데, 에러는 하나도 나지 않아 알아채기 어렵다.
-- 이 자리는 운영이 특정 챌린지에 표지를 따로 지정할 때만 쓴다.
--
-- 그래서 image_url은 아래 ON DUPLICATE KEY UPDATE 목록에도 없다. 이 파일이 마스터라는
-- 원칙의 유일한 예외다 — 운영이 콘솔에서 표지를 지정했는데 다음 배포가 그것을 NULL로
-- 되돌리면, 지정한 사람은 자기가 뭘 잘못했는지 알 수 없다.
-- created_at/updated_at은 NOT NULL이라 함께 넣되, updated_at만 재적용 때 갱신한다.

-- 아래 목록은 백엔드에서 잠정으로 정한 값이다(#46). 기획 확정본이 나오면 이 파일만 고치면
-- 다음 배포에 그대로 반영된다. id는 유지하고 name/description/category만 바꾸는 편이
-- 안전하다 — 이미 참여한 회원의 member_challenge가 id로 이 행을 가리키기 때문이다.
--
-- routine_cycle은 전부 DAILY다. 스트릭 셈법이 DAILY 기준으로만 구현되어 있어
-- (RoutineCycle 주석 참고) WEEKLY·MONTHLY 챌린지는 그 규칙이 정의된 뒤에 추가한다.
--
-- reward는 일단 전부 10으로 둔다. 재화 적립·챌린지 성공 판정이 아직 미구현이라
-- (Challenge 엔티티 주석) 값을 차등하는 근거가 없다. 상점·재화 정책이 정해지면 조정한다.
--
-- 카테고리 분포: HEALTH 3 / EXERCISE 3 / STUDY 2 / LIFE 2 / HOBBY 2.
-- 화면 필터 칩 5개가 모두 비지 않게 하려는 의도다.

INSERT INTO challenge (id, name, description, category, routine_cycle, reward, active, created_at, updated_at)
VALUES
  (1,  '물 2L 마시기',      '하루 2L 이상 물 마시기',              'HEALTH',   'DAILY', 10, TRUE, NOW(), NOW()),
  (2,  '아침 챙겨 먹기',    '거르지 않고 아침 식사하기',            'HEALTH',   'DAILY', 10, TRUE, NOW(), NOW()),
  (3,  '자정 전에 잠들기',  '12시 넘기지 않고 잠자리에 들기',        'HEALTH',   'DAILY', 10, TRUE, NOW(), NOW()),
  (4,  '30분 걷기',         '하루 30분 이상 걷기',                 'EXERCISE', 'DAILY', 10, TRUE, NOW(), NOW()),
  (5,  '스트레칭 10분',     '자기 전 10분 스트레칭하기',            'EXERCISE', 'DAILY', 10, TRUE, NOW(), NOW()),
  (6,  '계단 이용하기',     '엘리베이터 대신 계단으로 오르내리기',    'EXERCISE', 'DAILY', 10, TRUE, NOW(), NOW()),
  (7,  '책 10쪽 읽기',      '하루 10쪽 이상 책 읽기',               'STUDY',    'DAILY', 10, TRUE, NOW(), NOW()),
  (8,  '영어 단어 10개',    '하루 영어 단어 10개 외우기',           'STUDY',    'DAILY', 10, TRUE, NOW(), NOW()),
  (9,  '설거지 바로 하기',  '식사 후 미루지 않고 설거지하기',        'LIFE',     'DAILY', 10, TRUE, NOW(), NOW()),
  (10, '자기 전 책상 정리', '하루를 마치며 책상 위 비우기',          'LIFE',     'DAILY', 10, TRUE, NOW(), NOW()),
  (11, '하루 한 장 사진',   '오늘의 순간을 사진 한 장으로 남기기',    'HOBBY',    'DAILY', 10, TRUE, NOW(), NOW()),
  (12, '감사일기 쓰기',     '하루 한 줄, 감사한 일 기록하기',        'HOBBY',    'DAILY', 10, TRUE, NOW(), NOW())
-- 새 행은 VALUES(컬럼)이 아니라 별칭으로 참조한다. VALUES() 함수는 MySQL 8.0.19에서
-- deprecated 되어 부팅마다 경고가 찍히고 향후 제거 예정이다(운영도 MySQL 8.4).
AS new_row
ON DUPLICATE KEY UPDATE
  name          = new_row.name,
  description   = new_row.description,
  category      = new_row.category,
  routine_cycle = new_row.routine_cycle,
  reward        = new_row.reward,
  active        = new_row.active,
  updated_at    = NOW();
