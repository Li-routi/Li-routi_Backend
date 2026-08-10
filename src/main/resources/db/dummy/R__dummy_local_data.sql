-- 로컬 개발용 더미 데이터. local 프로파일에서만 적용된다(application.yaml의 flyway.locations 참고).
--
-- 운영에는 들어갈 수 없다. 프로파일 설정에만 기대지 않고, build.gradle의 bootJar가 db/dummy를
-- 배포 산출물에서 제외한다. 운영 이미지에는 이 파일이 아예 없다.
--
-- 운영 마스터(db/migration/R__seed_challenge.sql)와 달리 여기 있는 건 "화면이 돌아가는 걸 보기 위한"
-- 데이터다. 회원·참여·인증처럼 실제로는 사용자가 만드는 데이터를 미리 넣어 둔다.
--
-- id는 9000번대를 쓴다. 운영 마스터가 쓸 낮은 번호대와 겹치지 않게 해서,
-- 나중에 기획 확정 목록이 들어와도 충돌하지 않는다.
--
-- R__ 마이그레이션이라 파일을 고칠 때마다 다시 실행된다. 그래서 전부 upsert로 쓴다.
-- (한 파일에 몰아둔 것은 의도적이다. 파일을 나누면 R__는 이름 알파벳 순으로 실행되어
--  회원보다 참여가 먼저 도는 사고가 나기 쉽다.)
--
-- 예외가 하나 있다. 개인 루틴 더미는 R__zz_dummy_routine.sql에 따로 있다.
-- 운영 시드(R__seed_routine.sql)가 넣는 고정 카테고리·기본 루틴을 참조하는데
-- "dummy local data"가 "seed routine"보다 알파벳 앞이라, 여기 두면 순서가 뒤집힌다.

-- 아래 모든 upsert는 두 가지 형태를 지킨다.
--
--  * 새 행은 AS new_row 별칭으로 참조한다. VALUES(컬럼) 함수는 MySQL 8.0.19에서 deprecated 되어
--    부팅마다 경고가 찍히고 향후 제거 예정이다(운영도 MySQL 8.4).
--
--  * 갱신은 IF(테이블.id >= 9000, ...) 으로 더미 행에만 적용한다. ON DUPLICATE KEY UPDATE는
--    충돌한 행이 더미인지 확인하지 않으므로, 조건 없이 쓰면 누가 로컬에서 같은 id나 유니크 키
--    (member의 email, social_provider+social_id)에 만들어 둔 데이터를 조용히 덮어쓴다.
--    조건이 보는 id는 "이미 있던 행"의 id다. 그래서 다른 id를 가진 행이 유니크 키로 충돌해도
--    그 행은 손대지 않는다.
--    컬럼 참조를 테이블명으로 한정하는 이유는, 별칭에도 같은 이름의 컬럼이 있어
--    한정하지 않으면 "Column 'id' in field list is ambiguous"로 실패하기 때문이다.

-- 1) 회원
INSERT INTO member (id, social_provider, social_id, email, nickname, role, is_active, onboarding_completed, created_at, updated_at)
VALUES
  (9001, 'GOOGLE', 'dummy-google-9001', 'dummy1@lirouti.local', '더미유저1', 'ROLE_USER', TRUE, TRUE, NOW(), NOW()),
  (9002, 'KAKAO',  'dummy-kakao-9002',  'dummy2@lirouti.local', '더미유저2', 'ROLE_USER', TRUE, TRUE, NOW(), NOW())
AS new_row
ON DUPLICATE KEY UPDATE
  nickname             = IF(member.id >= 9000, new_row.nickname, member.nickname),
  role                 = IF(member.id >= 9000, new_row.role, member.role),
  is_active            = IF(member.id >= 9000, new_row.is_active, member.is_active),
  onboarding_completed = IF(member.id >= 9000, new_row.onboarding_completed, member.onboarding_completed),
  updated_at           = IF(member.id >= 9000, NOW(), member.updated_at);

-- 2) 챌린지 (로컬 전용 — 운영 마스터가 확정되면 그쪽이 진짜 목록이 된다)
INSERT INTO challenge (id, name, description, category, routine_cycle, reward, active, created_at, updated_at)
VALUES
  (9001, '[더미] 물 2L 마시기', '하루 2L 이상 물 마시기',   'HEALTH',   'DAILY', 10, TRUE, NOW(), NOW()),
  (9002, '[더미] 30분 걷기',    '하루 30분 이상 걷기',      'EXERCISE', 'DAILY', 10, TRUE, NOW(), NOW()),
  (9003, '[더미] 책 10쪽 읽기', '하루 10쪽 이상 독서',      'STUDY',    'DAILY',  5, TRUE, NOW(), NOW()),
  (9004, '[더미] 비활성 챌린지', 'active=false 동작 확인용', 'LIFE',     'DAILY',  0, FALSE, NOW(), NOW())
AS new_row
ON DUPLICATE KEY UPDATE
  name          = IF(challenge.id >= 9000, new_row.name, challenge.name),
  description   = IF(challenge.id >= 9000, new_row.description, challenge.description),
  category      = IF(challenge.id >= 9000, new_row.category, challenge.category),
  routine_cycle = IF(challenge.id >= 9000, new_row.routine_cycle, challenge.routine_cycle),
  reward        = IF(challenge.id >= 9000, new_row.reward, challenge.reward),
  active        = IF(challenge.id >= 9000, new_row.active, challenge.active),
  updated_at    = IF(challenge.id >= 9000, NOW(), challenge.updated_at);

-- 3) 참여
INSERT INTO member_challenge (id, member_id, challenge_id, active, current_streak, last_verified_date, participation_round, joined_at, created_at, updated_at)
VALUES
  (9001, 9001, 9001, TRUE,  2, CURRENT_DATE(),                    1, NOW(), NOW(), NOW()),
  (9002, 9001, 9002, TRUE,  0, NULL,                              1, NOW(), NOW(), NOW()),
  (9003, 9002, 9001, TRUE,  1, DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), 1, NOW(), NOW(), NOW()),
  -- 그만둔 참여(active=false) — 재참여·회차 증가 동작 확인용
  (9004, 9002, 9003, FALSE, 0, NULL,                              2, NOW(), NOW(), NOW())
AS new_row
ON DUPLICATE KEY UPDATE
  active              = IF(member_challenge.id >= 9000, new_row.active, member_challenge.active),
  current_streak      = IF(member_challenge.id >= 9000, new_row.current_streak, member_challenge.current_streak),
  last_verified_date  = IF(member_challenge.id >= 9000, new_row.last_verified_date, member_challenge.last_verified_date),
  participation_round = IF(member_challenge.id >= 9000, new_row.participation_round, member_challenge.participation_round),
  updated_at          = IF(member_challenge.id >= 9000, NOW(), member_challenge.updated_at);

-- 4) 인증 (피드 조회용)
-- image_url에는 전체 URL이 아니라 S3 오브젝트 key를 넣는다. 발급 규칙은 "prefix/UUID.확장자"다.
-- 로컬에는 실제 S3 객체가 없으므로 사진은 안 보인다. 피드 목록·커서 페이징 동작 확인용이다.
--
-- period_start_date 는 그 인증이 속한 주기 구간의 첫날이다. NOT NULL 이고 기본값이 없으므로
-- 빠뜨리면 STRICT 모드에서 INSERT 가 1364 로 실패하고, R__ 실패는 곧 Flyway 실패라
-- 앱이 아예 뜨지 않는다. 더미 챌린지(9001·9003)는 전부 DAILY 라 verified_date 와 같은 값이다.
-- 주간·월간 더미를 넣게 되면 그 주 일요일 / 그 달 1일로 계산해 넣어야 한다.
INSERT INTO challenge_verification (id, member_challenge_id, participation_round, verified_date, period_start_date, verified_at, image_url, content, created_at, updated_at)
VALUES
  (9001, 9001, 1, DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), NOW(), 'challenge-verifications/00000000-0000-4000-8000-000000009001.jpg', '어제 인증', NOW(), NOW()),
  (9002, 9001, 1, CURRENT_DATE(),                            CURRENT_DATE(),                            NOW(), 'challenge-verifications/00000000-0000-4000-8000-000000009002.jpg', '오늘 인증', NOW(), NOW()),
  (9003, 9003, 1, DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), NOW(), 'challenge-verifications/00000000-0000-4000-8000-000000009003.jpg', NULL,        NOW(), NOW())
AS new_row
ON DUPLICATE KEY UPDATE
  verified_date     = IF(challenge_verification.id >= 9000, new_row.verified_date, challenge_verification.verified_date),
  period_start_date = IF(challenge_verification.id >= 9000, new_row.period_start_date, challenge_verification.period_start_date),
  verified_at = IF(challenge_verification.id >= 9000, new_row.verified_at, challenge_verification.verified_at),
  image_url   = IF(challenge_verification.id >= 9000, new_row.image_url, challenge_verification.image_url),
  content     = IF(challenge_verification.id >= 9000, new_row.content, challenge_verification.content),
  updated_at  = IF(challenge_verification.id >= 9000, NOW(), challenge_verification.updated_at);

-- 5) 그룹과 구성원 (영구 초대코드 조회 화면 확인용)
INSERT INTO member_group (
    id,
    invite_code,
    name,
    status,
    created_at,
    updated_at
)
VALUES (
    9001,
    'DUMMY01',
    '[더미] 함께하는 루틴 그룹',
    'ACTIVE',
    NOW(6),
    NOW(6)
)
AS new_row
ON DUPLICATE KEY UPDATE
  invite_code            = IF(member_group.id >= 9000, new_row.invite_code, member_group.invite_code),
  name                   = IF(member_group.id >= 9000, new_row.name, member_group.name),
  status                 = IF(member_group.id >= 9000, new_row.status, member_group.status),
  updated_at             = IF(member_group.id >= 9000, NOW(6), member_group.updated_at);

INSERT INTO group_member (
    id,
    member_id,
    group_id,
    role,
    status,
    joined_at,
    left_at,
    created_at,
    updated_at
)
VALUES
  (9001, 9001, 9001, 'OWNER',  'ACTIVE', NOW(6), NULL, NOW(6), NOW(6)),
  (9002, 9002, 9001, 'MEMBER', 'ACTIVE', NOW(6), NULL, NOW(6), NOW(6))
AS new_row
ON DUPLICATE KEY UPDATE
  role       = IF(group_member.id >= 9000, new_row.role, group_member.role),
  status     = IF(group_member.id >= 9000, new_row.status, group_member.status),
  joined_at  = IF(group_member.id >= 9000, new_row.joined_at, group_member.joined_at),
  left_at    = IF(group_member.id >= 9000, new_row.left_at, group_member.left_at),
  updated_at = IF(group_member.id >= 9000, NOW(6), group_member.updated_at);

-- 6) 규은dl123 (실제 소셜 로그인 회원) 챌린지 참여 + 인증 더미
-- member_id는 하드코딩하지 않고 nickname으로 조회한다. 프론트 팀원 계정은
-- 더미(9000번대)가 아니라 실제 소셜 로그인으로 생긴 행이라 id를 미리 알 수 없다.
-- 이 팀원이 최소 1회 로그인해 member 행이 존재해야 아래가 채워진다(없으면 0건 INSERT).

-- 6-1) 참여: 물 2L 마시기(9001) + 30분 걷기(9002)
INSERT INTO member_challenge
(id, member_id, challenge_id, active, current_streak, last_verified_date, participation_round, joined_at, created_at, updated_at)
SELECT 9005, m.id, 9001, TRUE, 1, DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), 1, NOW(), NOW(), NOW()
FROM member m WHERE m.nickname = '규은dl123'
    ON DUPLICATE KEY UPDATE
                         member_id           = IF(member_challenge.id >= 9000, (SELECT id FROM member WHERE nickname = '규은dl123'), member_challenge.member_id),
                         active               = IF(member_challenge.id >= 9000, TRUE, member_challenge.active),
                         current_streak       = IF(member_challenge.id >= 9000, 1, member_challenge.current_streak),
                         last_verified_date   = IF(member_challenge.id >= 9000, DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), member_challenge.last_verified_date),
                         participation_round  = IF(member_challenge.id >= 9000, 1, member_challenge.participation_round),
                         updated_at           = IF(member_challenge.id >= 9000, NOW(), member_challenge.updated_at);

INSERT INTO member_challenge
(id, member_id, challenge_id, active, current_streak, last_verified_date, participation_round, joined_at, created_at, updated_at)
SELECT 9006, m.id, 9002, TRUE, 0, NULL, 1, NOW(), NOW(), NOW()
FROM member m WHERE m.nickname = '규은dl123'
    ON DUPLICATE KEY UPDATE
                         member_id           = IF(member_challenge.id >= 9000, (SELECT id FROM member WHERE nickname = '규은dl123'), member_challenge.member_id),
                         active               = IF(member_challenge.id >= 9000, TRUE, member_challenge.active),
                         current_streak       = IF(member_challenge.id >= 9000, 0, member_challenge.current_streak),
                         last_verified_date   = IF(member_challenge.id >= 9000, NULL, member_challenge.last_verified_date),
                         participation_round  = IF(member_challenge.id >= 9000, 1, member_challenge.participation_round),
                         updated_at           = IF(member_challenge.id >= 9000, NOW(), member_challenge.updated_at);

-- 6-2) 인증: 어제(승인) + 오늘(승인) + 오늘 다른 챌린지(심사 대기)
-- GET /api/members/me/verifications 의 date·status 필터를 모두 확인할 수 있도록 구성.
INSERT INTO challenge_verification
(id, member_challenge_id, participation_round, verified_date, period_start_date,
 verified_at, image_url, content, review_status, review_attempts, pending_since,
 created_at, updated_at)
VALUES
    (9005, 9005, 1, DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), NOW(),
     'challenge-verifications/00000000-0000-4000-8000-000000009005.jpg', '규은 어제 인증', 'APPROVED', 0, NULL, NOW(), NOW()),
    (9006, 9005, 1, CURRENT_DATE(), CURRENT_DATE(), NOW(),
     'challenge-verifications/00000000-0000-4000-8000-000000009006.jpg', '규은 오늘 인증', 'APPROVED', 0, NULL, NOW(), NOW()),
    (9007, 9006, 1, CURRENT_DATE(), CURRENT_DATE(), NOW(),
     'challenge-verifications/pending/00000000-0000-4000-8000-000000009007.jpg', '규은 검수 대기중', 'PENDING', 0, NOW(), NOW(), NOW())
    AS new_row
ON DUPLICATE KEY UPDATE
                     verified_date     = IF(challenge_verification.id >= 9000, new_row.verified_date, challenge_verification.verified_date),
                     period_start_date = IF(challenge_verification.id >= 9000, new_row.period_start_date, challenge_verification.period_start_date),
                     verified_at       = IF(challenge_verification.id >= 9000, new_row.verified_at, challenge_verification.verified_at),
                     image_url         = IF(challenge_verification.id >= 9000, new_row.image_url, challenge_verification.image_url),
                     content           = IF(challenge_verification.id >= 9000, new_row.content, challenge_verification.content),
                     review_status     = IF(challenge_verification.id >= 9000, new_row.review_status, challenge_verification.review_status),
                     review_attempts   = IF(challenge_verification.id >= 9000, new_row.review_attempts, challenge_verification.review_attempts),
                     pending_since     = IF(challenge_verification.id >= 9000, new_row.pending_since, challenge_verification.pending_since),
                     updated_at        = IF(challenge_verification.id >= 9000, NOW(), challenge_verification.updated_at);
