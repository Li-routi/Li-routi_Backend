-- 그룹 루틴 도메인 테이블 (#28, PR #42).
--
-- 엔티티는 들어왔는데 마이그레이션이 없어서 ddl-auto=validate가 부팅을 막았다
-- ("Schema validation: missing table [group_routine]"). Flyway 도입(#46) PR과 그룹 루틴 PR이
-- 같은 시기에 각자 develop을 향해 열려 있었고, 서로 파일이 겹치지 않아 머지는 통과했지만
-- "새 엔티티에는 마이그레이션이 필요하다"는 제약은 파일 충돌로 드러나지 않았다.
--
-- 내용은 현재 엔티티에서 Hibernate가 생성한 DDL을 그대로 옮긴 것이다.
-- V1과 같은 이유로 제약·인덱스 이름과 컬럼 순서를 Hibernate가 만든 그대로 둔다.
-- (도입 전부터 ddl-auto=update로 이 테이블이 이미 만들어진 DB와 이름이 어긋나지 않게 하기 위함)
--
-- V1처럼 IF NOT EXISTS를 쓴다. 이 테이블들은 #42 머지 후 update로 이미 만들어진 로컬 DB가
-- 있을 수 있어서, 그런 DB에서도 이 마이그레이션이 그냥 통과해야 한다.
--
-- FK 의존 순서: routine_category → group_routine → group_routine_schedule / group_routine_assignment
-- (group_routine은 member_group을, assignment는 member를 참조하므로 V1이 먼저 적용돼 있어야 한다)

CREATE TABLE IF NOT EXISTS `routine_category` (
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_routine_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `group_routine` (
  `category_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `group_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `title` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_routine_group_title` (`group_id`,`title`),
  KEY `FK9bqfxl6bqtl19pgar0j1rcog8` (`category_id`),
  CONSTRAINT `FK9bqfxl6bqtl19pgar0j1rcog8` FOREIGN KEY (`category_id`) REFERENCES `routine_category` (`id`),
  CONSTRAINT `FKcnead20wymjjk8jkx6d8fl1hu` FOREIGN KEY (`group_id`) REFERENCES `member_group` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `group_routine_schedule` (
  `end_time` time NOT NULL,
  `start_time` time NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `group_routine_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `repeat_day` enum('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_routine_schedule_day` (`group_routine_id`,`repeat_day`),
  CONSTRAINT `FKikkq71t1bmvvlxbt4mfo3g19q` FOREIGN KEY (`group_routine_id`) REFERENCES `group_routine` (`id`),
  CONSTRAINT `ck_group_routine_schedule_time_range` CHECK ((`start_time` < `end_time`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `group_routine_assignment` (
  `assigned_date` date NOT NULL,
  `scheduled_end_time` time NOT NULL,
  `scheduled_start_time` time NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `group_routine_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  `status` enum('COMPLETED','IN_PROGRESS','MISSED','PENDING') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_routine_assignment_routine_member_date` (`group_routine_id`,`member_id`,`assigned_date`),
  KEY `idx_group_routine_assignment_member_date` (`member_id`,`assigned_date`),
  KEY `idx_group_routine_assignment_status_date_end` (`status`,`assigned_date`,`scheduled_end_time`),
  KEY `idx_group_routine_assignment_status_date_start` (`status`,`assigned_date`,`scheduled_start_time`),
  CONSTRAINT `FKe3ltixsobjfxyu1qn415k5s5y` FOREIGN KEY (`group_routine_id`) REFERENCES `group_routine` (`id`),
  CONSTRAINT `FKkcjwngbwl1c5m1s4nnevwe47h` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
