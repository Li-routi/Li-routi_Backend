-- 그룹 채팅 메시지·이모티콘 자산·회원별 읽음 위치를 저장한다.

CREATE TABLE `chat_emoticon` (
  `active` bit(1) NOT NULL,
  `animated` bit(1) NOT NULL,
  `display_order` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `asset_key` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_emoticon_code` (`code`),
  KEY `idx_chat_emoticon_active_order` (`active`,`display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `group_chat_message` (
  `created_at` datetime(6) NOT NULL,
  `emoticon_id` bigint DEFAULT NULL,
  `group_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sender_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `client_message_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_chat_message_sender_client` (`sender_id`,`client_message_id`),
  KEY `idx_group_chat_message_group_id_id` (`group_id`,`id`),
  CONSTRAINT `FK_group_chat_message_emoticon`
      FOREIGN KEY (`emoticon_id`) REFERENCES `chat_emoticon` (`id`),
  CONSTRAINT `FK_group_chat_message_group`
      FOREIGN KEY (`group_id`) REFERENCES `member_group` (`id`),
  CONSTRAINT `FK_group_chat_message_sender`
      FOREIGN KEY (`sender_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `group_chat_read` (
  `created_at` datetime(6) NOT NULL,
  `group_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_read_message_id` bigint DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_chat_read_group_member` (`group_id`,`member_id`),
  KEY `idx_group_chat_read_group_message` (`group_id`,`last_read_message_id`),
  CONSTRAINT `FK_group_chat_read_group`
      FOREIGN KEY (`group_id`) REFERENCES `member_group` (`id`),
  CONSTRAINT `FK_group_chat_read_member`
      FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FK_group_chat_read_message`
      FOREIGN KEY (`last_read_message_id`) REFERENCES `group_chat_message` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
