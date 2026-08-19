-- 재인증된 그룹 루틴 인증을 다시 확인해야 하는 회원별 marker다.
-- verification_id는 기존 읽음 커서처럼 FK를 두지 않는다. 그룹 hard delete 전에 애플리케이션이
-- group_id 기준으로 먼저 제거하며, 인증 삭제 cascade 순서와 결합하지 않는다.
CREATE TABLE `group_routine_verification_reread` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `verification_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_routine_verification_reread_group_member_verification`
      (`group_id`, `member_id`, `verification_id`),
  CONSTRAINT `FK_group_routine_verification_reread_group`
      FOREIGN KEY (`group_id`) REFERENCES `member_group` (`id`),
  CONSTRAINT `FK_group_routine_verification_reread_member`
      FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
