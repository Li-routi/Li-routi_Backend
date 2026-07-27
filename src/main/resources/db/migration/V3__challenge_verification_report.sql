-- 인증 신고 테이블 (#15).
--
-- 신고는 인증을 삭제하지 않는다. 피드 조회 시 신고자 본인에게만 그 인증을 제외한다
-- (database-schema.md). 신고 누적으로 전체에게 숨기는 처리는 이 범위에 없다 — #60에서 다룬다.
--
-- 유니크 제약이 중복 신고를 막는다. 서비스는 "이미 신고했는지"를 선조회하지 않고
-- 이 제약 위반을 409로 바꾼다 — 선조회 방식은 동시 요청에서 둘 다 통과하기 때문이다.
--
-- 이 제약은 피드 필터의 인덱스이기도 하다. 피드 쿼리가
-- NOT EXISTS (... where challenge_verification_id = ? and reporter_id = ?) 형태라
-- (challenge_verification_id, reporter_id) 두 컬럼을 그대로 탄다.
--
-- V1·V2와 같은 이유로 제약·인덱스 이름과 컬럼 순서를 Hibernate 생성본 그대로 두고,
-- CREATE TABLE IF NOT EXISTS를 쓴다(로컬에서 이미 update로 만들어졌을 수 있다).

CREATE TABLE IF NOT EXISTS `challenge_verification_report` (
  `challenge_verification_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reporter_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_verification_report_verification_reporter` (`challenge_verification_id`,`reporter_id`),
  KEY `FKlj3pwuxd9f1h1v6bgy2kbfxi8` (`reporter_id`),
  CONSTRAINT `FKaxb2n8j7twvof8obs21jxphb3` FOREIGN KEY (`challenge_verification_id`) REFERENCES `challenge_verification` (`id`),
  CONSTRAINT `FKlj3pwuxd9f1h1v6bgy2kbfxi8` FOREIGN KEY (`reporter_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
