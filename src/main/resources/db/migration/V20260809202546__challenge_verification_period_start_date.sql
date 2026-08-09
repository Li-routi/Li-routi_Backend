-- 인증의 하루 1회를 "주기 1회"로 넓힌다.
--
-- 지금 유니크 키가 verified_date 라 날짜만 다르면 얼마든지 들어간다. WEEKLY 챌린지에
-- 월·화·수 매일 인증해도 DB 가 막지 않는다. 애플리케이션에서 "이번 주에 있나"를 조회해
-- 막아도 동시 요청 두 건은 둘 다 통과한다 — 이 프로젝트는 인증의 동시성을 유니크 제약에
-- 맡기고 있어서, 그 보장이 사라지면 대체할 것이 없다.
--
-- 그래서 구간의 첫날을 컬럼으로 두고 유니크 키를 그것으로 바꾼다.
--   DAILY   그날            → verified_date 와 같은 값
--   WEEKLY  그 주 일요일
--   MONTHLY 그 달 1일
--
-- DAILY 에서는 두 값이 같으므로 새 유니크 키가 옛 키와 완전히 같아진다. 즉 기존 데이터와
-- 기존 동작이 그대로 유지되고, 백필도 verified_date 를 복사하는 것으로 끝난다.
-- 현재 운영 챌린지는 전부 DAILY 라 이 마이그레이션으로 거절되는 기존 행이 없다.

-- ⚠️ 이 마이그레이션을 배포한 뒤에는 "앱만 롤백" 이 챌린지 인증 쓰기를 죽인다.
--
-- period_start_date 는 NOT NULL 이고 기본값이 없다. 옛 앱의 엔티티에는 이 필드가 없어
-- INSERT 문에서 컬럼이 빠지고, STRICT 모드에서 errno 1364 로 실패한다. 나쁜 것은 실패
-- 형태다 — Hibernate 의 validate 는 DB 에만 있는 여분 컬럼을 문제 삼지 않으므로
-- 옛 앱은 정상 기동하고 헬스체크도 통과한다. 인증 저장 요청만 전부 500 이 된다.
-- 옛 앱이 전제하는 UNIQUE(..., verified_date) 도 이 파일이 이미 지운 뒤다.
--
-- deploy/README.md 가 Flyway 사고의 기본 복구 경로로 롤백을 권하는데, 이 배포 뒤에는
-- 그 경로가 인증 쓰기에 대해서만 막힌다. 문제가 생기면 되돌리지 말고 앞으로 배포한다.
--
-- database-schema.md 의 "파괴적 변경은 두 배포로 나눈다" 규칙을 따르지 않은 것은
-- 의도적이다. 나누면 NULL 허용 상태로 한 배포를 더 살아야 하는데, 그동안 값이 빈 행이
-- 들어오면 유니크 키를 걸 수 없어 "주기 1회" 보장이 그 기간만큼 비게 된다. 지금은
-- 주간·월간 챌린지가 하나도 없어 롤백 위험이 실제로 커지는 창도 좁다.

ALTER TABLE `challenge_verification`
    ADD COLUMN `period_start_date` DATE NULL AFTER `verified_date`;

-- 기존 행은 전부 DAILY 이므로 구간 첫날이 곧 인증일이다.
UPDATE `challenge_verification`
SET `period_start_date` = `verified_date`
WHERE `period_start_date` IS NULL;

ALTER TABLE `challenge_verification`
    MODIFY COLUMN `period_start_date` DATE NOT NULL;

-- 새 키를 먼저 걸고 옛 키를 지운다. 순서가 중요하다.
--
-- 옛 유니크 키는 member_challenge_id 를 선두 컬럼으로 가져서 member_challenge 로 향하는
-- 외래 키의 인덱스 역할을 겸하고 있다. 먼저 지우면 MySQL 이 "외래 키에 필요한 인덱스"라며
-- 거절한다(errno 1553). 실제로 그 순서로 썼다가 마이그레이션이 중간에 죽었다 — 컬럼만
-- 추가된 채 이력에 실패로 남았다.
--
-- 새 키도 선두 컬럼이 member_challenge_id 라, 먼저 걸어 두면 그것이 외래 키의 인덱스를
-- 이어받아 옛 키를 안전하게 뺄 수 있다. 조인용 인덱스도 그대로 유지된다 — 챌린지 피드가
-- 쓰는 "member_challenge_id 로 조인"과 "그 참여의 인증 찾기"를 새 키가 커버한다.
ALTER TABLE `challenge_verification`
    ADD UNIQUE KEY `uk_verification_round_period` (
        `member_challenge_id`, `participation_round`, `period_start_date`
    );

ALTER TABLE `challenge_verification`
    DROP INDEX `uk_verification_round_date`;
