-- 회원별 그룹 루틴 인증 읽음 위치. 인증 ID는 삭제와 독립적인 커서이므로 FK를 두지 않는다.
CREATE TABLE `group_routine_verification_read` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `last_read_verification_id` bigint DEFAULT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_routine_verification_read_group_member` (`group_id`,`member_id`),
  KEY `idx_group_routine_verification_read_group_member_cursor`
      (`group_id`,`member_id`,`last_read_verification_id`),
  CONSTRAINT `FK_group_routine_verification_read_group`
      FOREIGN KEY (`group_id`) REFERENCES `member_group` (`id`),
  CONSTRAINT `FK_group_routine_verification_read_member`
      FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
