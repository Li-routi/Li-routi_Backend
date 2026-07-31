-- 그룹 루틴·개인 루틴 인증. 챌린지 인증과 나란히 두되 테이블은 따로 둔다.
--
-- 하나로 합쳐 target_type + target_id 다형 참조를 쓰지 않는 이유는 FK 무결성을 잃기 때문이다.
-- 이 스키마는 전부 FK 로 잡고 있어 여기서만 원칙을 깨면 되돌리기 어렵다.
-- 게다가 셋의 유니크 키가 서로 다르다.

-- 그룹 루틴 인증.
--
-- 할당 1건에 인증 1건이다. group_routine_assignment 가 이미
-- (group_routine_id, member_id, assigned_date) 로 유니크하므로 그 위에 얹으면
-- "하루에 한 번"이 자동으로 따라온다. 회원·날짜를 다시 들고 있을 필요가 없다.
--
-- 완료 여부는 이 행이 아니라 assignment.status 가 들고 있다. 즉 이 행은
-- 완료에 붙는 증빙이지 완료 그 자체가 아니다. 개인 루틴과 반대다.
CREATE TABLE `group_routine_verification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_routine_assignment_id` bigint NOT NULL,
  `verified_at` datetime(6) NOT NULL,
  `image_url` varchar(2048) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_routine_verification_assignment` (`group_routine_assignment_id`),
  CONSTRAINT `FK_group_routine_verification_assignment`
      FOREIGN KEY (`group_routine_assignment_id`) REFERENCES `group_routine_assignment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 개인 루틴 인증.
--
-- 개인 루틴에는 날짜별 행이 없다(member_routine_schedule 은 요일만 정의한다).
-- 그래서 이 행이 곧 "그날 수행했다"는 기록이다. 그룹과 반대다.
--
-- 날짜를 직접 들고 유니크를 건다. 하루에 한 번만 인증할 수 있고, 같은 날 다시 올리면
-- 애플리케이션이 덮어쓸지 막을지 정한다.
CREATE TABLE `member_routine_verification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_routine_id` bigint NOT NULL,
  `verified_date` date NOT NULL,
  `verified_at` datetime(6) NOT NULL,
  `image_url` varchar(2048) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_routine_verification_routine_date` (`member_routine_id`,`verified_date`),
  -- 루틴 목록을 그릴 때 "오늘 완료했는가"를 루틴마다 묻지 않고 한 번에 가져오는 경로다.
  -- 회원의 오늘자 인증을 날짜로 먼저 좁힌다.
  KEY `idx_member_routine_verification_date` (`verified_date`,`member_routine_id`),
  CONSTRAINT `FK_member_routine_verification_routine`
      FOREIGN KEY (`member_routine_id`) REFERENCES `member_routine` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
