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

-- 1) 회원
INSERT INTO member (id, social_provider, social_id, email, nickname, role, is_active, onboarding_completed, created_at, updated_at)
VALUES
  (9001, 'GOOGLE', 'dummy-google-9001', 'dummy1@lirouti.local', '더미유저1', 'ROLE_USER', TRUE, TRUE, NOW(), NOW()),
  (9002, 'KAKAO',  'dummy-kakao-9002',  'dummy2@lirouti.local', '더미유저2', 'ROLE_USER', TRUE, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  nickname             = VALUES(nickname),
  role                 = VALUES(role),
  is_active            = VALUES(is_active),
  onboarding_completed = VALUES(onboarding_completed),
  updated_at           = NOW();

-- 2) 챌린지 (로컬 전용 — 운영 마스터가 확정되면 그쪽이 진짜 목록이 된다)
INSERT INTO challenge (id, name, description, category, routine_cycle, reward, active, created_at, updated_at)
VALUES
  (9001, '[더미] 물 2L 마시기', '하루 2L 이상 물 마시기',   'HEALTH',   'DAILY', 10, TRUE, NOW(), NOW()),
  (9002, '[더미] 30분 걷기',    '하루 30분 이상 걷기',      'EXERCISE', 'DAILY', 10, TRUE, NOW(), NOW()),
  (9003, '[더미] 책 10쪽 읽기', '하루 10쪽 이상 독서',      'STUDY',    'DAILY',  5, TRUE, NOW(), NOW()),
  (9004, '[더미] 비활성 챌린지', 'active=false 동작 확인용', 'LIFE',     'DAILY',  0, FALSE, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  name          = VALUES(name),
  description   = VALUES(description),
  category      = VALUES(category),
  routine_cycle = VALUES(routine_cycle),
  reward        = VALUES(reward),
  active        = VALUES(active),
  updated_at    = NOW();

-- 3) 참여
INSERT INTO member_challenge (id, member_id, challenge_id, active, current_streak, last_verified_date, participation_round, joined_at, created_at, updated_at)
VALUES
  (9001, 9001, 9001, TRUE,  2, CURRENT_DATE(),                    1, NOW(), NOW(), NOW()),
  (9002, 9001, 9002, TRUE,  0, NULL,                              1, NOW(), NOW(), NOW()),
  (9003, 9002, 9001, TRUE,  1, DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), 1, NOW(), NOW(), NOW()),
  -- 그만둔 참여(active=false) — 재참여·회차 증가 동작 확인용
  (9004, 9002, 9003, FALSE, 0, NULL,                              2, NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE
  active              = VALUES(active),
  current_streak      = VALUES(current_streak),
  last_verified_date  = VALUES(last_verified_date),
  participation_round = VALUES(participation_round),
  updated_at          = NOW();

-- 4) 인증 (피드 조회용)
-- image_url에는 전체 URL이 아니라 S3 오브젝트 key를 넣는다. 발급 규칙은 "prefix/UUID.확장자"다.
-- 로컬에는 실제 S3 객체가 없으므로 사진은 안 보인다. 피드 목록·커서 페이징 동작 확인용이다.
INSERT INTO challenge_verification (id, member_challenge_id, participation_round, verified_date, verified_at, image_url, content, created_at, updated_at)
VALUES
  (9001, 9001, 1, DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), NOW(), 'challenge-verifications/00000000-0000-4000-8000-000000009001.jpg', '어제 인증', NOW(), NOW()),
  (9002, 9001, 1, CURRENT_DATE(),                            NOW(), 'challenge-verifications/00000000-0000-4000-8000-000000009002.jpg', '오늘 인증', NOW(), NOW()),
  (9003, 9003, 1, DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), NOW(), 'challenge-verifications/00000000-0000-4000-8000-000000009003.jpg', NULL,        NOW(), NOW())
ON DUPLICATE KEY UPDATE
  verified_at = VALUES(verified_at),
  image_url   = VALUES(image_url),
  content     = VALUES(content),
  updated_at  = NOW();
