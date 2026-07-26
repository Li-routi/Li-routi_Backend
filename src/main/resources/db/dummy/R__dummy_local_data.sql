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
INSERT INTO challenge_verification (id, member_challenge_id, participation_round, verified_date, verified_at, image_url, content, created_at, updated_at)
VALUES
  (9001, 9001, 1, DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), NOW(), 'challenge-verifications/00000000-0000-4000-8000-000000009001.jpg', '어제 인증', NOW(), NOW()),
  (9002, 9001, 1, CURRENT_DATE(),                            NOW(), 'challenge-verifications/00000000-0000-4000-8000-000000009002.jpg', '오늘 인증', NOW(), NOW()),
  (9003, 9003, 1, DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), NOW(), 'challenge-verifications/00000000-0000-4000-8000-000000009003.jpg', NULL,        NOW(), NOW())
AS new_row
ON DUPLICATE KEY UPDATE
  verified_at = IF(challenge_verification.id >= 9000, new_row.verified_at, challenge_verification.verified_at),
  image_url   = IF(challenge_verification.id >= 9000, new_row.image_url, challenge_verification.image_url),
  content     = IF(challenge_verification.id >= 9000, new_row.content, challenge_verification.content),
  updated_at  = IF(challenge_verification.id >= 9000, NOW(), challenge_verification.updated_at);
