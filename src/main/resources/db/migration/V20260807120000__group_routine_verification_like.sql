-- 그룹 루틴 인증 게시물 좋아요 (#160).
-- 좋아요는 그룹 루틴 자체가 아니라 인증 한 건(group_routine_verification.id)에 붙는다.

CREATE TABLE `group_routine_verification_like` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_routine_verification_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_routine_verification_like_verification_member`
      (`group_routine_verification_id`, `member_id`),
  KEY `FK_group_routine_verification_like_member` (`member_id`),
  CONSTRAINT `FK_group_routine_verification_like_verification`
      FOREIGN KEY (`group_routine_verification_id`) REFERENCES `group_routine_verification` (`id`),
  CONSTRAINT `FK_group_routine_verification_like_member`
      FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
