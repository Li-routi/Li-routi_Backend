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
-- id 1~12 는 처음 열두 챌린지가 쓰던 번호다. 목록을 갈아엎으면서도 그 번호를 그대로 두고
-- 이름·설명·분류만 새 목록에 맞춰 바꿨다. 참여 중인 사람이 있기 때문이다 — active 를 FALSE 로
-- 내려 갈아치우면 "내 챌린지" 조회가 challenge.active = true 로 거르므로 참여 행이 남아 있어도
-- 화면에서 통째로 사라진다(MemberChallengeRepository). id 를 유지하면 참여가 안 끊긴다.
--   id 12 는 분류가 HOBBY 에서 MIND 로 옮겨 갔다. '감사 기록 남기기' 는 취미가 아니라 마음관리다.
--
-- id 대역.
--   1~999       이 파일(운영 마스터). 지금 66 까지 썼다.
--   9001~9004   로컬 더미(db/dummy)가 쓰는 번호. <b>운영 DB 에도 들어 있다</b> — 배포로 들어온
--               것이 아니라(flyway 이력에 없다) 누가 클라이언트로 직접 넣었고, 그때 커넥션이
--               UTF-8 이 아니어서 이름의 한글이 버려졌다. 이 대역을 침범하면 덮어쓴다.
--
-- 그 위는 AUTO_INCREMENT 가 가져간다. 운영 카운터가 이미 9005 라 <b>코드나 콘솔로 만드는
-- 챌린지는 9005 부터 붙는다</b> — 1000 번대를 비워 둬도 그리로 가지 않는다. 이 파일은 언제나
-- id 를 직접 적으므로 그 카운터와 무관하고, 그래서 위 두 대역만 신경 쓰면 된다.
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

-- 아래 목록은 기획 확정본이 없어 백엔드가 잠정으로 정한 값이다. 확정본이 나오면 이 파일만 고치면
-- 다음 배포에 그대로 반영된다. id는 유지하고 name/description/category만 바꾸는 편이
-- 안전하다 — 이미 참여한 회원의 member_challenge가 id로 이 행을 가리키기 때문이다.
--
-- routine_cycle 분포: DAILY 38 / WEEKLY 15 / MONTHLY 13.
-- 스트릭은 주기 단위로 센다 — DAILY 면 연속 며칠, WEEKLY 면 연속 몇 주다. 셈법은
-- RoutineCycle 의 currentPeriodStart·previousPeriodStart 가 주기별로 각각 구현한다.
--
-- ⚠️ 이미 인증이 쌓인 챌린지의 routine_cycle을 바꾸려면 백필이 함께 필요하다.
--
-- 인증 행은 period_start_date(구간 첫날)를 들고 있는데, 그 값은 저장 시점의 주기로 계산해
-- 못 박은 것이다. 주기만 바꾸면 옛 행은 옛 기준으로 남아 "주기 1회"가 그 구간만큼 뚫린다.
--
--   DAILY 로 월요일에 인증 → period_start_date = 월요일
--   WEEKLY 로 바꾼 뒤 수요일에 다시 인증 → 이번 구간 첫날은 그 주 일요일
--   → 유니크 키에도 조회에도 안 걸린다 → 그 주에 한 건 더 들어간다
--
-- 스트릭도 같이 어긋난다. current_streak 은 last_verified_date 를 주기 단위로 해석해
-- 세는데, 일 단위로 쌓인 값을 주 단위로 읽게 된다("30일 연속"이 "30주 연속"이 된다).
--
-- 이 파일은 upsert 라 값을 한 글자 고치고 머지하면 다음 배포에 그냥 덮어써진다.
-- 마이그레이션처럼 이력이 남지도, 리뷰에서 눈에 띄지도 않는다. 그래서 여기 적어 둔다.
--
-- 절차는 database-schema.md 의 [주기를 바꿀 때는 백필이 함께 필요하다] 를 따른다.
-- 단순 UPDATE 로 끝나지 않는다 — 여러 행이 같은 구간으로 접히면서 유니크 키에 걸린다.
--
-- reward는 주기에 맞춰 DAILY 10 / WEEKLY 30 / MONTHLY 100 으로 둔다. 한 번 인증하기까지의
-- 무게가 다르므로 같은 값을 줄 수 없다. 상점·재화 정책이 정해지면 다시 조정한다.
--
-- 카테고리 분포(총 66): MIND 13 / LIFE 12 / HOBBY 11 / EXERCISE 10 / HEALTH 10 / STUDY 10.
-- 화면 필터 칩 6개가 모두 비지 않게 하려는 의도다.

INSERT INTO challenge (id, name, description, category, routine_cycle, reward, active, created_at, updated_at)
VALUES
  ( 5, '스트레칭하기',                  '굳은 몸을 풀어 주는 스트레칭하기', 'EXERCISE', 'DAILY',  10, TRUE, NOW(), NOW()),
  (13, '홈트레이닝하기',                 '집에서 할 수 있는 운동 한 세트 하기', 'EXERCISE', 'DAILY',  10, TRUE, NOW(), NOW()),
  (14, '요가 자세 따라 하기',             '요가 동작을 하나 골라 따라 하기', 'EXERCISE', 'DAILY',  10, TRUE, NOW(), NOW()),
  (15, '플랭크 자세 인증하기',             '플랭크 자세를 버티고 남기기', 'EXERCISE', 'DAILY',  10, TRUE, NOW(), NOW()),
  ( 6, '계단 이용하기',                 '엘리베이터 대신 계단으로 오르내리기', 'EXERCISE', 'DAILY',  10, TRUE, NOW(), NOW()),
  ( 4, '30분 산책하기',                '밖으로 나가 30분 이상 걷기', 'EXERCISE', 'DAILY',  10, TRUE, NOW(), NOW()),
  (16, '만 보 걷기',                  '하루 걸음 수 만 보 채우기', 'EXERCISE', 'DAILY',  10, TRUE, NOW(), NOW()),
  (17, '헬스장 다녀오기',                '이번 주에 헬스장에 다녀오기', 'EXERCISE', 'WEEKLY',  30, TRUE, NOW(), NOW()),
  (18, '자전거 타기',                  '이번 주에 자전거를 타고 나가기', 'EXERCISE', 'WEEKLY',  30, TRUE, NOW(), NOW()),
  (19, '등산하기',                    '이번 달에 산에 다녀오기', 'EXERCISE', 'MONTHLY', 100, TRUE, NOW(), NOW()),
  ( 1, '물 2L 마시기',                '하루 2L 이상 물 마시기', 'HEALTH', 'DAILY',  10, TRUE, NOW(), NOW()),
  (20, '과일 챙겨 먹기',                '하루에 과일 한 번 챙겨 먹기', 'HEALTH', 'DAILY',  10, TRUE, NOW(), NOW()),
  (21, '채소가 있는 한 끼 먹기',           '채소가 들어간 식사 한 끼 하기', 'HEALTH', 'DAILY',  10, TRUE, NOW(), NOW()),
  (22, '단백질이 있는 한 끼 먹기',          '단백질이 들어간 식사 한 끼 하기', 'HEALTH', 'DAILY',  10, TRUE, NOW(), NOW()),
  ( 2, '아침 식사 챙겨 먹기',             '거르지 않고 아침 식사하기', 'HEALTH', 'DAILY',  10, TRUE, NOW(), NOW()),
  (23, '영양제 챙겨 먹기',               '정한 시간에 영양제 챙겨 먹기', 'HEALTH', 'DAILY',  10, TRUE, NOW(), NOW()),
  ( 3, '7시간 이상 자기',               '잠을 7시간 이상 자기', 'HEALTH', 'DAILY',  10, TRUE, NOW(), NOW()),
  (24, '하루 세끼 모두 직접 건강식으로 차려 먹기', '하루 세 끼를 직접 건강하게 차려 먹기', 'HEALTH', 'MONTHLY', 100, TRUE, NOW(), NOW()),
  (25, '일주일치 밀프렙 만들기',            '한 주 먹을 식사를 미리 만들어 두기', 'HEALTH', 'MONTHLY', 100, TRUE, NOW(), NOW()),
  (26, '한 달 식단 짜기',               '한 달 치 식단을 계획해 적어 두기', 'HEALTH', 'MONTHLY', 100, TRUE, NOW(), NOW()),
  (27, '책 펼쳐 읽기',                 '분량과 상관없이 책을 펼쳐 읽기', 'STUDY', 'DAILY',  10, TRUE, NOW(), NOW()),
  ( 7, '책 10쪽 읽기',                '하루 10쪽 이상 책 읽기', 'STUDY', 'DAILY',  10, TRUE, NOW(), NOW()),
  (28, '1시간 공부하기',                '집중해서 1시간 공부하기', 'STUDY', 'DAILY',  10, TRUE, NOW(), NOW()),
  (29, '공부 노트 남기기',               '오늘 공부한 것을 노트로 남기기', 'STUDY', 'DAILY',  10, TRUE, NOW(), NOW()),
  ( 8, '외국어 공부하기',                '외국어를 하루 한 번 공부하기', 'STUDY', 'DAILY',  10, TRUE, NOW(), NOW()),
  (30, '뉴스 읽고 정리하기',              '뉴스를 읽고 요점을 정리하기', 'STUDY', 'DAILY',  10, TRUE, NOW(), NOW()),
  (31, '한 주 공부 내용 정리하기',          '이번 주에 공부한 것을 모아 정리하기', 'STUDY', 'WEEKLY',  30, TRUE, NOW(), NOW()),
  (32, '포트폴리오 작업하기',              '이번 주에 포트폴리오를 손보기', 'STUDY', 'WEEKLY',  30, TRUE, NOW(), NOW()),
  (33, '온라인 강의 한 과정 완강하기',        '이번 달에 강의 한 과정을 끝내기', 'STUDY', 'MONTHLY', 100, TRUE, NOW(), NOW()),
  (34, '원서 한 권 완독하기',             '이번 달에 원서 한 권을 끝까지 읽기', 'STUDY', 'MONTHLY', 100, TRUE, NOW(), NOW()),
  (35, '침대 정리하기',                 '일어나서 이불과 침대 정리하기', 'LIFE', 'DAILY',  10, TRUE, NOW(), NOW()),
  ( 9, '설거지 완료하기',                '식사 후 미루지 않고 설거지 끝내기', 'LIFE', 'DAILY',  10, TRUE, NOW(), NOW()),
  (36, '빨래 개기',                   '마른 빨래를 개어 정리하기', 'LIFE', 'DAILY',  10, TRUE, NOW(), NOW()),
  (10, '책상 위 정리하기',               '하루를 마치며 책상 위 비우기', 'LIFE', 'DAILY',  10, TRUE, NOW(), NOW()),
  (37, '쓰레기 버리기',                 '쌓인 쓰레기를 그날 버리기', 'LIFE', 'DAILY',  10, TRUE, NOW(), NOW()),
  (38, '방 정리하기',                  '이번 주에 방을 정리하기', 'LIFE', 'WEEKLY',  30, TRUE, NOW(), NOW()),
  (39, '욕실 청소하기',                 '이번 주에 욕실을 청소하기', 'LIFE', 'WEEKLY',  30, TRUE, NOW(), NOW()),
  (40, '냉장고 정리하기',                '이번 주에 냉장고 안을 정리하기', 'LIFE', 'WEEKLY',  30, TRUE, NOW(), NOW()),
  (41, '분리수거하기',                  '이번 주에 분리수거 내놓기', 'LIFE', 'WEEKLY',  30, TRUE, NOW(), NOW()),
  (42, '방 전체 대청소하기',              '이번 달에 방을 통째로 청소하기', 'LIFE', 'MONTHLY', 100, TRUE, NOW(), NOW()),
  (43, '집 안 물건 비우기',              '안 쓰는 물건을 골라 비우기', 'LIFE', 'MONTHLY', 100, TRUE, NOW(), NOW()),
  (44, '냉장고 전체 정리하기',             '냉장고를 비우고 전체를 정리하기', 'LIFE', 'MONTHLY', 100, TRUE, NOW(), NOW()),
  (12, '감사 기록 남기기',               '하루 한 줄, 감사한 일 기록하기', 'MIND', 'DAILY',  10, TRUE, NOW(), NOW()),
  (45, '일기 쓰기',                   '오늘 하루를 일기로 남기기', 'MIND', 'DAILY',  10, TRUE, NOW(), NOW()),
  (46, '감정 기록 남기기',               '오늘 느낀 감정을 적어 두기', 'MIND', 'DAILY',  10, TRUE, NOW(), NOW()),
  (47, '명상하기',                    '조용히 앉아 명상하기', 'MIND', 'DAILY',  10, TRUE, NOW(), NOW()),
  (48, '오늘 잘한 일 적기',              '오늘 잘한 일을 하나 적기', 'MIND', 'DAILY',  10, TRUE, NOW(), NOW()),
  (49, '나에게 긍정적인 문장 남기기',         '스스로에게 건네는 문장 남기기', 'MIND', 'DAILY',  10, TRUE, NOW(), NOW()),
  (50, '차 한 잔 마시며 쉬기',            '차를 마시며 잠깐 쉬어 가기', 'MIND', 'DAILY',  10, TRUE, NOW(), NOW()),
  (51, '좋아하는 음악 듣기',              '좋아하는 음악을 골라 듣기', 'MIND', 'DAILY',  10, TRUE, NOW(), NOW()),
  (52, '햇빛을 받으며 20분 쉬기',          '햇빛 아래에서 20분 쉬기', 'MIND', 'DAILY',  10, TRUE, NOW(), NOW()),
  (53, '한 주 동안 힘들었던 일 정리하기',      '이번 주에 힘들었던 일을 적어 정리하기', 'MIND', 'WEEKLY',  30, TRUE, NOW(), NOW()),
  (54, '혼자만의 시간 보내고 기록하기',        '혼자 보낸 시간을 기록으로 남기기', 'MIND', 'WEEKLY',  30, TRUE, NOW(), NOW()),
  (55, '가벼운 여행 다녀오기',             '이번 달에 가볍게 다녀오기', 'MIND', 'MONTHLY', 100, TRUE, NOW(), NOW()),
  (56, '미래의 나에게 편지 쓰기',           '앞으로의 나에게 편지 남기기', 'MIND', 'MONTHLY', 100, TRUE, NOW(), NOW()),
  (57, '그림 그리기',                  '무엇이든 하나 그려 보기', 'HOBBY', 'DAILY',  10, TRUE, NOW(), NOW()),
  (58, '악기 연습하기',                 '악기를 꺼내 연습하기', 'HOBBY', 'DAILY',  10, TRUE, NOW(), NOW()),
  (59, '글쓰기',                     '분량과 상관없이 글을 쓰기', 'HOBBY', 'DAILY',  10, TRUE, NOW(), NOW()),
  (11, '사진 한 장 찍기',               '오늘의 순간을 사진 한 장으로 남기기', 'HOBBY', 'DAILY',  10, TRUE, NOW(), NOW()),
  (60, '새로운 요리 만들기',              '안 해 본 요리를 이번 주에 만들기', 'HOBBY', 'WEEKLY',  30, TRUE, NOW(), NOW()),
  (61, '베이킹하기',                   '이번 주에 직접 구워 보기', 'HOBBY', 'WEEKLY',  30, TRUE, NOW(), NOW()),
  (62, '식물 돌보기',                  '이번 주에 식물을 살피고 돌보기', 'HOBBY', 'WEEKLY',  30, TRUE, NOW(), NOW()),
  (63, '공예품 만들기',                 '이번 주에 손으로 무언가 만들기', 'HOBBY', 'WEEKLY',  30, TRUE, NOW(), NOW()),
  (64, '영화보고 감상 기록하기',            '영화를 보고 감상을 남기기', 'HOBBY', 'WEEKLY',  30, TRUE, NOW(), NOW()),
  (65, '하루 종일 취미 활동하기',           '이번 달에 하루를 통째로 취미에 쓰기', 'HOBBY', 'MONTHLY', 100, TRUE, NOW(), NOW()),
  (66, '미뤄둔 취미 결과물 완성하기',         '미뤄 둔 작업을 이번 달에 끝내기', 'HOBBY', 'MONTHLY', 100, TRUE, NOW(), NOW())
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
