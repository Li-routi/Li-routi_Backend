-- 정책서 최신본(45개) 기준으로 업적 마스터 데이터를 맞춘다.
-- 기존 24개 중 교환/구매 관련 2개는 비활성화하고, 나머지 22개는 유지한다.
-- (물리 삭제 대신 active=FALSE로 비활성화하는 이유: 이미 이 업적을 달성한 회원의
--  member_achievement 이력을 보존하기 위함. Group.delete()가 상태만 바꾸고
--  행을 지우지 않는 것과 같은 패턴이다.)

-- ==================== 1) 기존 업적 중 제외 대상 비활성화 ====================
-- ACH-ST-006 첫 토파즈 교환, ACH-AC-003 특별 후원가: 정책서 최신본에서 삭제됨.
UPDATE achievement SET active = FALSE WHERE code IN ('ACH-ST-006', 'ACH-AC-003');

-- ==================== 2) 달성 업적 신규 7개 ====================
-- 기존 AC-001(첫 인증), AC-002(꾸준한 사람)는 그대로 유지, sort_order 24부터 이어서 채운다.
INSERT INTO achievement
(code, category, name, condition_desc, progress_type, target_count, topaz_reward, badge_yn, limited_outfit_yn, sort_order, hidden_yn)
VALUES
    ('ACH-AC-004', 'EPIC', '작심삼일 탈출',        '서로 다른 날짜에 루틴 인증 3일 달성',              'DISTINCT_DAY_COUNT',         3, 100, TRUE, FALSE, 24, FALSE),
    ('ACH-AC-005', 'EPIC', '일주일 루틴러',        '한 주 동안 서로 다른 날짜에 루틴 인증 5일 이상',    'WEEKLY_DISTINCT_DAY_COUNT',  5, 100, TRUE, FALSE, 25, FALSE),
    ('ACH-AC-006', 'EPIC', '쿡쿡 장인',            '친구 쿡쿡 찌르기 누적 50회',                       'CUMULATIVE_COUNT',          50, 100, TRUE, FALSE, 26, FALSE),
    ('ACH-AC-007', 'EPIC', '빨리 좀 해',           '다른 사용자에게 아얏 받기 누적 50회',              'CUMULATIVE_COUNT',          50, 100, TRUE, FALSE, 27, FALSE),
    ('ACH-AC-008', 'EPIC', '루틴 탐험가',          '6개 카테고리 루틴을 각각 1회 이상 인증',            'CATEGORY_COVERAGE_COUNT',    6, 100, TRUE, FALSE, 28, FALSE),
    ('ACH-AC-009', 'EPIC', '우리 방 정상영업합니다', '한 루틴방의 구성원 전체 인증 합계 100회',          'GROUP_CUMULATIVE_COUNT',   100, 100, TRUE, FALSE, 29, FALSE),
    ('ACH-AC-010', 'EPIC', '꽉 찬 방',             '내가 참여한 루틴방이 최대 인원에 도달',             'NONE',                    NULL, 100, TRUE, FALSE, 30, FALSE);

UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT' WHERE code = 'ACH-AC-004';
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT' WHERE code = 'ACH-AC-005';
UPDATE achievement SET condition_key = 'POKE_COUNT'             WHERE code = 'ACH-AC-006'; -- 보내기 기준, 기존 ST-012/013과 동일 키
UPDATE achievement SET condition_key = 'POKE_RECEIVED_COUNT'    WHERE code = 'ACH-AC-007'; -- 아얏 = 쿡쿡 받기. GroupMember.totalPokeCount와 동일 관점(신규 키)
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT' WHERE code = 'ACH-AC-008';
-- AC-009/010은 group_achievement_progress(_event_log) 및 achievement_progress_event_log로 처리되어
-- condition_key가 아니라 group_id 단위로 판정되므로 값을 넣지 않는다.

-- 루틴 탐험가: 6개 카테고리 전부를 커버해야 하므로 조인 테이블에 6개 행을 모두 연결한다.
INSERT INTO achievement_routine_category (achievement_id, routine_category_id)
SELECT a.id, rc.id
FROM achievement a
         CROSS JOIN routine_category rc
WHERE a.code = 'ACH-AC-008' AND rc.id BETWEEN 1 AND 6;

-- ==================== 3) 캐릭터 알 업적 신규 12개 (category='EGG') ====================
-- 알 업적은 코인 보상이 없고 뱃지도 없다. 알 자체는 achievement_reward_item에 별도 기록한다.
INSERT INTO achievement
(code, category, name, condition_desc, progress_type, target_count, topaz_reward, badge_yn, limited_outfit_yn, sort_order, hidden_yn)
VALUES
    ('ACH-EG-001', 'EGG', '첫 루틴의 새싹',   '개인 루틴 또는 챌린지를 처음 완료',                       'NONE',                     NULL, 0, FALSE, FALSE, 31, FALSE),
    ('ACH-EG-002', 'EGG', '배움이 차곡차곡', '자기계발 루틴을 서로 다른 날짜에 20일 완료',              'DISTINCT_DAY_COUNT',        20, 0, FALSE, FALSE, 32, FALSE),
    ('ACH-EG-003', 'EGG', '일찍 일어난 새',   '오전 7시 이전에 루틴을 완료한 날 5회 달성',               'CUMULATIVE_COUNT',           5, 0, FALSE, FALSE, 33, TRUE),
    ('ACH-EG-004', 'EGG', '건강한 땀방울',   '운동 또는 건강 루틴을 서로 다른 날짜에 20일 완료',         'DISTINCT_DAY_COUNT',        20, 0, FALSE, FALSE, 34, FALSE),
    ('ACH-EG-005', 'EGG', '마음에 쉼표',     '마음관리 루틴을 서로 다른 날짜에 20일 완료',               'DISTINCT_DAY_COUNT',        20, 0, FALSE, FALSE, 35, FALSE),
    ('ACH-EG-006', 'EGG', '취미의 물결',     '취미 루틴을 서로 다른 날짜에 20일 완료',                   'DISTINCT_DAY_COUNT',        20, 0, FALSE, FALSE, 36, FALSE),
    ('ACH-EG-007', 'EGG', '알림 보고 왔어요', '루틴 알림을 눌러 앱에 접속한 횟수 10회 달성',              'CUMULATIVE_COUNT',          10, 0, FALSE, FALSE, 37, FALSE),
    ('ACH-EG-008', 'EGG', '정리하면 다미',   '생활정리 루틴을 서로 다른 날짜에 20일 완료',               'DISTINCT_DAY_COUNT',        20, 0, FALSE, FALSE, 38, FALSE),
    ('ACH-EG-009', 'EGG', '자정의 방문자',   '자정 00:00에 앱에 접속',                                  'NONE',                     NULL, 0, FALSE, FALSE, 39, TRUE),
    ('ACH-EG-010', 'EGG', '좋아요 요정',     '친구 인증에 내가 누른 좋아요 누적 100회',                  'CUMULATIVE_COUNT',         100, 0, FALSE, FALSE, 40, FALSE),
    ('ACH-EG-011', 'EGG', '딱 1분 남았어!',  '루틴 마감까지 1분 이하 남았을 때 완료',                    'NONE',                     NULL, 0, FALSE, FALSE, 41, TRUE),
    ('ACH-EG-012', 'EGG', '100일의 태양',    '루틴 연속 기록 100일 달성',                               'STREAK_DAYS',              100, 0, FALSE, FALSE, 42, FALSE);

UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT'       WHERE code = 'ACH-EG-001';
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT'       WHERE code = 'ACH-EG-002';
UPDATE achievement SET condition_key = 'EARLY_MORNING_COMPLETE_COUNT' WHERE code = 'ACH-EG-003'; -- 이벤트 발행 지점 미구현, 스펙만 선반영
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT'       WHERE code = 'ACH-EG-004';
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT'       WHERE code = 'ACH-EG-005';
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT'       WHERE code = 'ACH-EG-006';
UPDATE achievement SET condition_key = 'NOTIFICATION_CLICK_COUNT'     WHERE code = 'ACH-EG-007'; -- 이벤트 발행 지점 미구현, 스펙만 선반영
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT'       WHERE code = 'ACH-EG-008';
UPDATE achievement SET condition_key = 'MIDNIGHT_ACCESS'              WHERE code = 'ACH-EG-009'; -- 이벤트 발행 지점 미구현, 스펙만 선반영
UPDATE achievement SET condition_key = 'LIKE_COUNT'                   WHERE code = 'ACH-EG-010'; -- 기존 좋아요 이벤트 재사용
UPDATE achievement SET condition_key = 'DEADLINE_LAST_MINUTE_COMPLETE' WHERE code = 'ACH-EG-011'; -- 이벤트 발행 지점 미구현, 스펙만 선반영
UPDATE achievement SET condition_key = 'ROUTINE_STREAK_DAYS'          WHERE code = 'ACH-EG-012'; -- member_routine_streak 기반

-- 카테고리 한정 알 업적의 카테고리 연결.
-- 건강한 땀방울만 운동(1)+건강(2) 2개 카테고리에 걸친다. 나머지는 1개씩.
INSERT INTO achievement_routine_category (achievement_id, routine_category_id)
SELECT a.id, 3 FROM achievement a WHERE a.code = 'ACH-EG-002'; -- 자기계발
INSERT INTO achievement_routine_category (achievement_id, routine_category_id)
SELECT a.id, rc.id FROM achievement a CROSS JOIN routine_category rc
WHERE a.code = 'ACH-EG-004' AND rc.id IN (1, 2); -- 운동, 건강
INSERT INTO achievement_routine_category (achievement_id, routine_category_id)
SELECT a.id, 5 FROM achievement a WHERE a.code = 'ACH-EG-005'; -- 마음관리
INSERT INTO achievement_routine_category (achievement_id, routine_category_id)
SELECT a.id, 6 FROM achievement a WHERE a.code = 'ACH-EG-006'; -- 취미
INSERT INTO achievement_routine_category (achievement_id, routine_category_id)
SELECT a.id, 4 FROM achievement a WHERE a.code = 'ACH-EG-008'; -- 생활정리

-- 알 보상 아이템 (achievement_reward_item.item_category='EGG'는 신규 값, 컬럼이 자유 VARCHAR라 스키마 변경 불필요)
INSERT INTO achievement_reward_item (achievement_id, item_category, item_name)
SELECT a.id, 'EGG', v.egg_name
FROM achievement a
         JOIN (VALUES
                   ROW('ACH-EG-001', '민트의 알'),
                   ROW('ACH-EG-002', '동글의 알'),
                   ROW('ACH-EG-003', '노아의 알'),
                   ROW('ACH-EG-004', '코코의 알'),
                   ROW('ACH-EG-005', '유키의 알'),
                   ROW('ACH-EG-006', '파도의 알'),
                   ROW('ACH-EG-007', '까루의 알'),
                   ROW('ACH-EG-008', '다미의 알'),
                   ROW('ACH-EG-009', '모리의 알'),
                   ROW('ACH-EG-010', '삐아의 알'),
                   ROW('ACH-EG-011', '호롱의 알'),
                   ROW('ACH-EG-012', '솔라의 알')
) AS v(code, egg_name) ON v.code = a.code;

-- ==================== 4) 스페셜 업적 신규 4개 ====================
-- 기존 SP-001(모두의 응원단장)은 그대로 유지.
-- SP-005 특별 후원자는 유료 결제 연동 전이라 우선 비활성 상태로 넣는다 (ST-006/AC-003과 동일 처리).
INSERT INTO achievement
(code, category, name, condition_desc, progress_type, target_count, topaz_reward, badge_yn, limited_outfit_yn, sort_order, hidden_yn, active)
VALUES
    ('ACH-SP-002', 'UNIQUE', '100일 완주',        '루틴 연속 기록 100일 달성',                       'STREAK_DAYS',              100, 200, TRUE, TRUE, 43, FALSE, TRUE),
    ('ACH-SP-003', 'UNIQUE', '한 달의 루틴러',    '한 달 동안 서로 다른 날짜에 루틴 인증 20일 이상',   'MONTHLY_DISTINCT_DAY_COUNT', 20, 200, TRUE, TRUE, 44, FALSE, TRUE),
    ('ACH-SP-004', 'UNIQUE', '루틴 하우스 메이트', '같은 방의 모든 구성원이 함께 인증한 날 10회',       'GROUP_DISTINCT_DAY_COUNT',   10, 200, TRUE, TRUE, 45, FALSE, TRUE),
    ('ACH-SP-005', 'UNIQUE', '특별 후원자',        '금액과 관계없이 유료 재화를 처음 한 번 결제',       'NONE',                     NULL, 200, TRUE, TRUE, 46, FALSE, FALSE);

UPDATE achievement SET condition_key = 'ROUTINE_STREAK_DAYS' WHERE code = 'ACH-SP-002'; -- EG-012와 같은 스트릭 소스, 100일 동시 달성 정책과 일치
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT' WHERE code = 'ACH-SP-003';
-- SP-004는 group_achievement_progress_event_log로 처리되어 condition_key 불필요.
UPDATE achievement SET condition_key = 'PAID_PURCHASE_COUNT' WHERE code = 'ACH-SP-005'; -- 결제 이벤트 미구현, 스펙만 선반영

-- 배지/한정 의상 아이템도 정책서 콘셉트 그대로 기록 (선택: 굳이 안 써도 무방하지만 뱃지/의상 이름이 앱에 노출되므로 미리 반영)
INSERT INTO achievement_reward_item (achievement_id, item_category, item_name)
SELECT id, 'BADGE', '100 달력·왕관·체크 도장 뱃지' FROM achievement WHERE code = 'ACH-SP-002';
INSERT INTO achievement_reward_item (achievement_id, item_category, item_name)
SELECT id, 'OUTFIT', '왕관' FROM achievement WHERE code = 'ACH-SP-002';
INSERT INTO achievement_reward_item (achievement_id, item_category, item_name)
SELECT id, 'OUTFIT', '선글라스' FROM achievement WHERE code = 'ACH-SP-002';
INSERT INTO achievement_reward_item (achievement_id, item_category, item_name)
SELECT id, 'OUTFIT', '로열 망토' FROM achievement WHERE code = 'ACH-SP-002';
INSERT INTO achievement_reward_item (achievement_id, item_category, item_name)
SELECT id, 'OUTFIT', '뿌듯 표정' FROM achievement WHERE code = 'ACH-SP-002';
