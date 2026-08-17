-- 베이스라인. Flyway 도입 시점(2026-07-26)의 스키마를 그대로 옮긴 것이다.
--
-- 모든 DB에서 실행된다. 빈 DB에서는 전체 스키마를 만들고, 이미 테이블이 있는 DB
-- (도입 전부터 굴러온 로컬·운영)에서는 없는 테이블만 채운다.
--
-- 그래서 CREATE TABLE에 IF NOT EXISTS를 붙였다. baseline-version을 0으로 두어 기존 DB에서도
-- V1이 실행되게 한 것과 짝을 이룬다. V1을 건너뛰게 하면(baseline-version=1), 앱을 한동안
-- 안 띄워 스키마가 낡은 로컬 DB는 없는 테이블이 영영 안 생겨서 부팅이 validate에서 깨진다.
-- 팀원 전원에게 "DB를 지우고 다시 받으세요"를 요구하지 않으려고 이 방식을 택했다.
--
-- 다만 IF NOT EXISTS는 "테이블이 통째로 없는" 경우만 메운다. 이미 있는 테이블의 컬럼이
-- 어긋난 것까지 고쳐주지는 않는다. 그건 ddl-auto=validate가 부팅 시점에 잡는다.
--
-- 제약·인덱스 이름을 Hibernate가 생성한 그대로(FK7npmq... 같은 해시 이름 포함) 둔 것은 의도적이다.
-- 기존 DB들이 그 이름으로 만들어져 있으므로, 새로 만드는 DB도 같은 이름을 갖게 해야
-- 나중에 "ALTER TABLE ... DROP FOREIGN KEY <이름>" 같은 마이그레이션이 양쪽에서 똑같이 동작한다.
-- (Hibernate의 이름은 테이블·컬럼명 해시라 재생성해도 값이 같다.)
--
-- 컬럼 순서도 Hibernate가 만든 순서 그대로다. 보기엔 어색하지만 기존 DB와 맞추는 쪽을 택했다.
-- 이후의 모든 스키마 변경은 이 파일을 고치는 게 아니라 V2, V3... 를 새로 추가한다.
-- 이미 적용된 마이그레이션을 수정하면 체크섬이 어긋나 부팅이 실패한다.
--
-- FK 의존 순서로 정렬했다: member / challenge / member_group → member_challenge / group_member → challenge_verification

CREATE TABLE IF NOT EXISTS `member` (
  `is_active` bit(1) NOT NULL,
  `onboarding_completed` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nickname` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `social_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` enum('ROLE_ADMIN','ROLE_USER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `social_provider` enum('GOOGLE','KAKAO') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_social_provider_social_id` (`social_provider`,`social_id`),
  UNIQUE KEY `UKmbmcqelty0fbrvxp1q58dn57t` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `challenge` (
  `active` bit(1) NOT NULL,
  `reward` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `image_url` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` enum('EXERCISE','HEALTH','HOBBY','LIFE','STUDY') COLLATE utf8mb4_unicode_ci NOT NULL,
  `routine_cycle` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DAILY',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `member_group` (
  `invite_code` varchar(7) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','DELETED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKmgt3kl7whp0n031hlo8x6jupi` (`invite_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `member_challenge` (
  `active` bit(1) NOT NULL,
  `current_streak` int NOT NULL,
  `last_verified_date` date DEFAULT NULL,
  `participation_round` int NOT NULL,
  `challenge_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `joined_at` datetime(6) NOT NULL,
  `member_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_challenge` (`member_id`,`challenge_id`),
  KEY `idx_member_challenge_challenge_active` (`challenge_id`,`active`),
  KEY `idx_member_challenge_member_active` (`member_id`,`active`),
  CONSTRAINT `FK9x810nqdrhsdpp78017y3kqhe` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FKh9n4f0bidmjun3fvk2jp5netm` FOREIGN KEY (`challenge_id`) REFERENCES `challenge` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `group_member` (
  `created_at` datetime(6) NOT NULL,
  `group_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `joined_at` datetime(6) NOT NULL,
  `left_at` datetime(6) DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `role` enum('MEMBER','OWNER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','KICKED','LEFT') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_member_member_group` (`member_id`,`group_id`),
  KEY `FK4if69kgijmtu6wa8jh8a19mwf` (`group_id`),
  CONSTRAINT `FK4if69kgijmtu6wa8jh8a19mwf` FOREIGN KEY (`group_id`) REFERENCES `member_group` (`id`),
  CONSTRAINT `FKeamf7nngsg582uxwqgde8o28x` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `challenge_verification` (
  `participation_round` int NOT NULL,
  `verified_date` date NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_challenge_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `verified_at` datetime(6) NOT NULL,
  `image_url` varchar(2048) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_verification_round_date` (`member_challenge_id`,`participation_round`,`verified_date`),
  CONSTRAINT `FK7npmq3xoof1yatue2t1d6fahw` FOREIGN KEY (`member_challenge_id`) REFERENCES `member_challenge` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
